package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.OracleContainer;
import com.cmt.e2e.framework.db.init.OracleDatabaseInitializer;

/** Oracle 11g source with two-user (REF + MAIN) e2e seed. */
final class OracleSource implements Source {

    private final OracleContainer container;
    private boolean started;

    OracleSource() {
        this.container = OracleContainer.withTwoUsers();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        OracleDatabaseInitializer.of(container)
            .migrateAs(container.getRefUser(),  container.getRefPassword(),  "oracle/ref_schema")
            .migrateAs(container.getMainUser(), container.getMainPassword(), "oracle/main_schema");
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
            DB.ORACLE,
            container.getHost(),
            container.getDatabasePort(),
            container.getSid(),
            container.getMainUser(),
            container.getMainPassword(),
            "utf-8",
            "GMT+00:00"
        );
    }

    @Override
    public DB type() {
        return DB.ORACLE;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
