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
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fluent CUBRID catalog snapshot. Each {@link #matchesSnapshot(String)} runs the named query from
 * {@link CatalogQueries}, formats with {@link Tabulator}, and compares against {@code
 * snapshots/<scenario>/<name>.txt}.
 */
public final class CatalogSnapshot {

    static final Path SNAPSHOT_ROOT = Paths.get("src", "test", "resources", "snapshots");

    private final ConnectionConfig connection;
    private final String scenarioName;

    public CatalogSnapshot(ConnectionConfig connection, String scenarioName) {
        if (connection.type() != DB.CUBRID) {
            throw new IllegalArgumentException(
                    "CatalogSnapshot is CUBRID-specific (got " + connection.type() + ")");
        }
        this.connection = connection;
        this.scenarioName = scenarioName;
    }

    public CatalogSnapshot matchesSnapshot(String name) {
        String sql = CatalogQueries.byName(name);
        String actual = runQueryAsTable(sql);
        Path snapshotPath = SNAPSHOT_ROOT.resolve(scenarioName).resolve(name + ".txt");
        SnapshotStore.match(snapshotPath, actual);
        return this;
    }

    private String runQueryAsTable(String sql) {
        String url = connection.cubridJdbcUrl();
        try (Connection conn = DriverManager.getConnection(url);
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return Tabulator.format(rs);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Catalog query failed:\n  url: " + url + "\n  sql: " + sql, e);
        }
    }
}
