package com.cmt.e2e.framework.verify;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Fluent CUBRID catalog snapshot. Each {@link #matchesSnapshot(String)}
 * runs the named query from {@link CatalogQueries}, formats with
 * {@link Tabulator}, and compares against
 * {@code snapshots/<scenario>/<name>.txt}.
 */
public final class CatalogSnapshot {

    static final Path SNAPSHOT_ROOT =
        Paths.get("src", "test", "resources", "snapshots");

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
