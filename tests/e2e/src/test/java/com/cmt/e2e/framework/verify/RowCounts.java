/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */

package com.cmt.e2e.framework.verify;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Row-count snapshot — enumerates user tables (DBA/PUBLIC excluded, Flyway history filtered) and
 * captures {@code SELECT COUNT(*)} as {@code (owner, class, count)}. Pass owner names to restrict
 * further.
 */
public final class RowCounts {

    private final ConnectionConfig connection;
    private final String scenarioName;
    private final List<String> ownerAllowList; // empty = exclude DBA/PUBLIC only

    public RowCounts(
            ConnectionConfig connection, String scenarioName, List<String> ownerAllowList) {
        if (connection.type() != DB.CUBRID) {
            throw new IllegalArgumentException(
                    "RowCounts is CUBRID-specific (got " + connection.type() + ")");
        }
        this.connection = connection;
        this.scenarioName = scenarioName;
        this.ownerAllowList = List.copyOf(ownerAllowList);
    }

    public RowCounts matchesSnapshot(String name) {
        String text = collect();
        Path snap = CatalogSnapshot.SNAPSHOT_ROOT.resolve(scenarioName).resolve(name + ".txt");
        SnapshotStore.match(snap, text);
        return this;
    }

    private String collect() {
        String url = connection.cubridJdbcUrl();
        List<List<String>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url)) {
            for (String[] t : listUserTables(conn)) {
                long count = countRows(conn, t[0], t[1]);
                rows.add(List.of(t[0], t[1], Long.toString(count)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("RowCounts collection failed: " + url, e);
        }
        return Tabulator.format(List.of("OWNER_NAME", "CLASS_NAME", "ROW_COUNT"), rows);
    }

    private List<String[]> listUserTables(Connection conn) throws SQLException {
        String sql;
        if (ownerAllowList.isEmpty()) {
            sql =
                    """
                    SELECT owner_name, class_name
                    FROM db_class
                    WHERE class_type = 'CLASS'
                      AND owner_name NOT IN ('DBA', 'PUBLIC')
                      AND class_name NOT LIKE 'flyway_%'
                    ORDER BY owner_name, class_name
                    """;
        } else {
            String inList =
                    ownerAllowList.stream()
                            .map(s -> "'" + s.replace("'", "''") + "'")
                            .reduce((a, b) -> a + ", " + b)
                            .orElseThrow();
            sql =
                    "SELECT owner_name, class_name FROM db_class "
                            + "WHERE class_type = 'CLASS' "
                            + "  AND owner_name IN ("
                            + inList
                            + ") "
                            + "  AND class_name NOT LIKE 'flyway_%' "
                            + "ORDER BY owner_name, class_name";
        }
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            List<String[]> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new String[] {rs.getString(1), rs.getString(2)});
            }
            return out;
        }
    }

    private long countRows(Connection conn, String owner, String table) throws SQLException {
        String qualified = "\"" + owner + "\".\"" + table + "\"";
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + qualified)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }
}
