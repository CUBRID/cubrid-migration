package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.TiberoContainer;
import com.cmt.e2e.framework.db.init.TiberoDatabaseInitializer;

/**
 * Tibero 7 source with two-user seed (REF + MAIN). Init via JDBC after
 * boot (no entrypoint hook in the Tibero image); seed via raw JDBC
 * ({@link TiberoDatabaseInitializer}, Flyway has no Tibero plugin).
 * Order matters: SYS init → REF → MAIN, since MAIN's V5 synonym points
 * at REF's V1 object.
 */
final class TiberoSource implements Source {

    private final TiberoContainer container;
    private boolean started;

    TiberoSource() {
        this.container = TiberoContainer.create();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        TiberoDatabaseInitializer.of(container)
            .initAsSys("db/tibero/init")
            .migrateAs(container.getRefUser(),  container.getRefPassword(),  "db/tibero/ref_schema")
            .migrateAs(container.getMainUser(), container.getMainPassword(), "db/tibero/main_schema");
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
            DB.TIBERO,
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
        return DB.TIBERO;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
