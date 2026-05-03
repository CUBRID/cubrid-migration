package com.cmt.e2e.framework.target;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.source.ConnectionConfig;

/**
 * CUBRID online target — empty CUBRID container that CMT migrates into.
 * Connects as {@code dba} so CMT can {@code CREATE USER} and
 * {@code GRANT} during {@code add_schema}.
 */
final class CubridOnlineTarget implements Target {

    private final CubridContainer container;
    private boolean started;

    CubridOnlineTarget() {
        this.container = CubridContainer.withEmptyDb();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
            DB.CUBRID,
            container.getHost(),
            container.getDatabasePort(),
            container.getDatabaseName(),
            "dba",
            "",
            "utf-8",
            null
        );
    }

    @Override public boolean isDumpfile()              { return false; }
    @Override public DumpfileOptions dumpfileOptions() { return null; }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
