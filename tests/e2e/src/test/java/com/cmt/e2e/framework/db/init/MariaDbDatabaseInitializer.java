/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */

package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.MariaDbContainer;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flyway-based seed helper for MariaDB (schema == database; single {@code main_schema}). */
public final class MariaDbDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(MariaDbDatabaseInitializer.class);

    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final MariaDbContainer container;

    private MariaDbDatabaseInitializer(MariaDbContainer container) {
        this.container = container;
    }

    public static MariaDbDatabaseInitializer of(MariaDbContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new MariaDbDatabaseInitializer(container);
    }

    public MariaDbDatabaseInitializer migrate(String scenarioName) {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }

        String resourcePath = "db/" + scenarioName;
        if (Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null) {
            throw new DatabaseInitializationException(
                    "Scenario not found on classpath: '"
                            + resourcePath
                            + "'. Check src/test/resources/"
                            + resourcePath
                            + " exists.",
                    null);
        }

        String location = SCENARIO_BASE + scenarioName;
        String schema = container.getDatabaseName();
        log.info(
                "[MariaDbDatabaseInitializer] migrate start: scenario='{}', schema='{}'",
                scenarioName,
                schema);

        try {
            MigrateResult result = buildFlyway(location, schema).migrate();
            log.info(
                    "[MariaDbDatabaseInitializer] migrate complete: executed={}, success={}",
                    result.migrationsExecuted,
                    result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                        "Flyway migration reported failure for scenario: " + scenarioName, null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                    "Failed to migrate MariaDB scenario '" + scenarioName + "': " + e.getMessage(),
                    e);
        }
        return this;
    }

    private Flyway buildFlyway(String location, String schema) {
        return Flyway.configure()
                .dataSource(
                        container.getJdbcUrl(schema, container.getUser()),
                        container.getUser(),
                        container.getPassword())
                .driver(MARIADB_DRIVER)
                .defaultSchema(schema)
                .schemas(schema)
                .locations(location)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }
}
