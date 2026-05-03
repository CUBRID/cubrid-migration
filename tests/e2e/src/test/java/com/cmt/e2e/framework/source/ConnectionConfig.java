package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;

/** JDBC connection identity for CMT + verify layer. {@code timezone} may be null. */
public record ConnectionConfig(
    DB type,
    String host,
    int port,
    String dbname,
    String user,
    String password,
    String charset,
    String timezone
) {
    public ConnectionConfig {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host must not be blank");
        if (port <= 0) throw new IllegalArgumentException("port must be positive");
        if (dbname == null || dbname.isBlank()) throw new IllegalArgumentException("dbname must not be blank");
        if (user == null) throw new IllegalArgumentException("user must not be null");
        if (password == null) throw new IllegalArgumentException("password must not be null (use \"\" for none)");
        if (charset == null || charset.isBlank()) throw new IllegalArgumentException("charset must not be blank");
        // timezone may be null
    }

    /** CUBRID JDBC URL — verify layer uses this to introspect the target.
     *  CUBRID driver rejects empty password slot, so omit it when needed. */
    public String cubridJdbcUrl() {
        if (type != DB.CUBRID) {
            throw new IllegalStateException(
                "cubridJdbcUrl() is CUBRID-specific (got " + type + ")");
        }
        if (password.isEmpty()) {
            return String.format("jdbc:cubrid:%s:%d:%s:%s::",
                host, port, dbname, user);
        }
        return String.format("jdbc:cubrid:%s:%d:%s:%s:%s::",
            host, port, dbname, user, password);
    }
}
