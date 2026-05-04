package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.TiberoContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw-JDBC seed runner for Tibero (Flyway has no Tibero plugin). Two
 * phases mirror {@code OracleSource}: {@link #initAsSys} runs DBA-only
 * DDL (the Tibero image has no entrypoint init hook); {@link #migrateAs}
 * applies schema/data files in lexical order.
 */
public final class TiberoDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(TiberoDatabaseInitializer.class);

    private final TiberoContainer container;

    private TiberoDatabaseInitializer(TiberoContainer container) {
        this.container = container;
    }

    public static TiberoDatabaseInitializer of(TiberoContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new TiberoDatabaseInitializer(container);
    }

    /** Run init scripts as {@code SYS} (e.g. {@code CREATE USER}). */
    public TiberoDatabaseInitializer initAsSys(String classpathDir) {
        log.info("[TiberoDatabaseInitializer] init as SYS — classpathDir='{}'", classpathDir);
        ClasspathSqlRunner.runDirectory(
            container.getJdbcUrl(null, null),
            container.getDbaUser(),
            container.getDbaPassword(),
            classpathDir);
        return this;
    }

    /** Apply schema/data scripts as a non-DBA user. */
    public TiberoDatabaseInitializer migrateAs(String user, String password, String classpathDir) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        log.info("[TiberoDatabaseInitializer] migrate as user='{}' — classpathDir='{}'", user, classpathDir);
        ClasspathSqlRunner.runDirectory(
            container.getJdbcUrl(null, null),
            user, password, classpathDir);
        return this;
    }
}
