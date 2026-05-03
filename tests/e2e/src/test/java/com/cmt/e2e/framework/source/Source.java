package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;

/**
 * Source database for E2E migration. Wraps container lifecycle and seed
 * application behind a single {@link #start()}. Lifecycle: construct →
 * start (container + seed, idempotent) → connection() / type() → close().
 * Tests obtain instances via {@link Sources}.
 */
public interface Source extends AutoCloseable {

    void start();

    /** Connection identity CMT uses to introspect this source. Call only after {@link #start()}. */
    ConnectionConfig connection();

    DB type();

    @Override void close();
}
