package com.cmt.e2e.framework.verify;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * Row-count snapshot — enumerates user tables (DBA/PUBLIC excluded,
 * Flyway history filtered) and captures {@code SELECT COUNT(*)} as
 * {@code (owner, class, count)}. Pass owner names to restrict further.
 */
public final class RowCounts {

    private final ConnectionConfig connection;
    private final String scenarioName;
    private final List<String> ownerAllowList;   // empty = exclude DBA/PUBLIC only

    public RowCounts(ConnectionConfig connection, String scenarioName, List<String> ownerAllowList) {
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
        return Tabulator.format(
            List.of("OWNER_NAME", "CLASS_NAME", "ROW_COUNT"),
            rows);
    }

    private List<String[]> listUserTables(Connection conn) throws SQLException {
        String sql;
        if (ownerAllowList.isEmpty()) {
            sql = """
                SELECT owner_name, class_name
                FROM db_class
                WHERE class_type = 'CLASS'
                  AND owner_name NOT IN ('DBA', 'PUBLIC')
                  AND class_name NOT LIKE 'flyway_%'
                ORDER BY owner_name, class_name
                """;
        } else {
            String inList = ownerAllowList.stream()
                .map(s -> "'" + s.replace("'", "''") + "'")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
            sql = "SELECT owner_name, class_name FROM db_class "
                + "WHERE class_type = 'CLASS' "
                + "  AND owner_name IN (" + inList + ") "
                + "  AND class_name NOT LIKE 'flyway_%' "
                + "ORDER BY owner_name, class_name";
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String[]> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new String[]{ rs.getString(1), rs.getString(2) });
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
