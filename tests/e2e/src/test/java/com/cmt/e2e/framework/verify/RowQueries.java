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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-query snapshot. Reads a labelled SQL file, runs each query, and concatenates formatted
 * results into one snapshot file. File format: each {@code -- @label <text>} introduces a query
 * terminated by {@code ;}; other comment / blank lines are ignored. Snapshot output uses {@code ==>
 * <label>} as a section header per query.
 */
public final class RowQueries {

    private final Path sqlFile;
    private final ConnectionConfig connection;
    private final String scenarioName;

    public RowQueries(Path sqlFile, ConnectionConfig connection, String scenarioName) {
        if (connection.type() != DB.CUBRID) {
            throw new IllegalArgumentException(
                    "RowQueries is CUBRID-specific (got " + connection.type() + ")");
        }
        this.sqlFile = sqlFile;
        this.connection = connection;
        this.scenarioName = scenarioName;
    }

    public RowQueries matchesSnapshot(String name) {
        List<LabeledQuery> queries = parse(readFile(sqlFile));
        StringBuilder sb = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(connection.cubridJdbcUrl())) {
            for (int i = 0; i < queries.size(); i++) {
                LabeledQuery q = queries.get(i);
                if (i > 0) sb.append('\n');
                sb.append("==> ").append(q.label()).append('\n');
                sb.append(runOne(conn, q.sql()));
            }
        } catch (SQLException e) {
            throw new RuntimeException("RowQueries connection failed", e);
        }
        Path snap = CatalogSnapshot.SNAPSHOT_ROOT.resolve(scenarioName).resolve(name + ".txt");
        SnapshotStore.match(snap, sb.toString());
        return this;
    }

    public record LabeledQuery(String label, String sql) {}

    static List<LabeledQuery> parse(String content) {
        List<LabeledQuery> out = new ArrayList<>();
        String currentLabel = null;
        StringBuilder currentSql = new StringBuilder();

        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-- @label")) {
                if (currentLabel != null) {
                    throw new IllegalStateException(
                            "Missing trailing ';' for query: '" + currentLabel + "'");
                }
                currentLabel = trimmed.substring("-- @label".length()).trim();
                if (currentLabel.isEmpty()) {
                    throw new IllegalStateException("-- @label line must include a label name");
                }
                continue;
            }
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            if (currentLabel == null) {
                throw new IllegalStateException("SQL line before any -- @label: " + trimmed);
            }
            currentSql.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String sql = currentSql.toString().trim();
                if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
                out.add(new LabeledQuery(currentLabel, sql));
                currentLabel = null;
                currentSql.setLength(0);
            }
        }
        if (currentLabel != null) {
            throw new IllegalStateException(
                    "Missing trailing ';' for query: '" + currentLabel + "'");
        }
        return out;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read query file: " + file, e);
        }
    }

    private String runOne(Connection conn, String sql) {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            return Tabulator.format(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Query failed:\n  " + sql, e);
        }
    }
}
