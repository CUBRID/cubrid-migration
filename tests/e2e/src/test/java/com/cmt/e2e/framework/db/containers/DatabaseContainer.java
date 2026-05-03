package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.lifecycle.Startable;

/**
 * Common surface for source/target database containers. Wraps
 * Testcontainers but exposes only host/port/jdbcUrl to keep tests
 * engine-agnostic.
 */
public interface DatabaseContainer extends Startable {

    String getHost();
    Integer getDatabasePort();
    DB getDbType();

    /**
     * {@code dbName} and {@code user} semantics are engine-specific:
     * CUBRID embeds both into the URL; Oracle ignores both (uses fixed SID).
     */
    String getJdbcUrl(String dbName, String user);
}
