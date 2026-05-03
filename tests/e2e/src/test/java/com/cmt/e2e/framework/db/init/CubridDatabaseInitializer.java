package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.CubridContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flyway-based seed helper for CUBRID. */
public final class CubridDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(CubridDatabaseInitializer.class);

    private static final String CUBRID_DRIVER = "cubrid.jdbc.driver.CUBRIDDriver";
    private static final String SCENARIO_BASE  = "classpath:db/";

    private final CubridContainer container;
    private final String dbName;
    private final String userName;
    private final String password;

    private CubridDatabaseInitializer(CubridContainer container, String dbName, String userName, String password) {
        this.container = container;
        this.dbName    = dbName;
        this.userName  = userName;
        this.password  = password;
    }

    /** Convenience overload for passwordless users (e.g. fresh-CUBRID dba). */
    public static CubridDatabaseInitializer of(CubridContainer container,
                                         String dbName,
                                         String userName) {
        return of(container, dbName, userName, "");
    }

    public static CubridDatabaseInitializer of(CubridContainer container,
                                         String dbName,
                                         String userName,
                                         String password) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        if (dbName == null || dbName.isBlank()) throw new IllegalArgumentException("dbName must not be blank");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName must not be blank");
        if (password == null) throw new IllegalArgumentException("password must not be null (use \"\" for none)");
        return new CubridDatabaseInitializer(container, dbName, userName, password);
    }

    public void migrate(String scenarioName) {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }

        // Flyway exits successfully with executed=0 if the folder is missing —
        // validate up front for a clear failure.
        String resourcePath = "db/" + scenarioName;
        if (Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null) {
            throw new DatabaseInitializationException(
                "Scenario not found on classpath: '" + resourcePath + "'. " +
                "Check src/test/resources/" + resourcePath + " exists.", null);
        }

        String location = SCENARIO_BASE + scenarioName;
        log.info("[CubridDatabaseInitializer] migrate start: scenario='{}', db='{}', user='{}'",
            scenarioName, dbName, userName);

        try {
            MigrateResult result = buildFlyway(location).migrate();
            log.info("[CubridDatabaseInitializer] migrate complete: executed={}, success={}",
                result.migrationsExecuted, result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                    "Flyway migration reported failure for scenario: " + scenarioName, null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                "Failed to migrate scenario '" + scenarioName + "': " + e.getMessage(), e);
        }
    }

    private Flyway buildFlyway(String location) {
        String jdbcUrl = container.getJdbcUrl(dbName, userName);
        return Flyway.configure()
            .dataSource(jdbcUrl, userName, password)
            .driver(CUBRID_DRIVER)
            .defaultSchema(userName)
            .locations(location)
            .cleanDisabled(true)
            // Baseline at 0: cross-schema GRANT leaves the user looking "non-empty" to Flyway.
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .load();
    }
}
