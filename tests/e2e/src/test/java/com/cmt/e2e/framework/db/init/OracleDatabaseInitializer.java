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

import com.cmt.e2e.framework.db.containers.OracleContainer;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Flyway-based seed helper for Oracle (schema == user). */
public final class OracleDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(OracleDatabaseInitializer.class);

    private static final String ORACLE_DRIVER = "oracle.jdbc.OracleDriver";
    private static final String SCENARIO_BASE = "classpath:db/";

    private final OracleContainer container;

    private OracleDatabaseInitializer(OracleContainer container) {
        this.container = container;
    }

    public static OracleDatabaseInitializer of(OracleContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new OracleDatabaseInitializer(container);
    }

    /** Two-user mode: call REF_SCHEMA before MAIN_SCHEMA — MAIN synonyms point at REF. */
    public OracleDatabaseInitializer migrateAs(String user, String password, String scenarioName) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }

        String resourcePath = "db/" + scenarioName;
        if (Thread.currentThread().getContextClassLoader().getResource(resourcePath) == null) {
            throw new DatabaseInitializationException(
                    "Scenario not found on classpath: '"
                            + resourcePath
                            + "'. "
                            + "Check src/test/resources/"
                            + resourcePath
                            + " exists.",
                    null);
        }

        String location = SCENARIO_BASE + scenarioName;
        log.info(
                "[OracleDatabaseInitializer] migrate start: scenario='{}', user='{}'",
                scenarioName,
                user);

        try {
            MigrateResult result = buildFlyway(location, user, password).migrate();
            log.info(
                    "[OracleDatabaseInitializer] migrate complete: scenario='{}', user='{}',"
                            + " executed={}, success={}",
                    scenarioName,
                    user,
                    result.migrationsExecuted,
                    result.success);

            if (!result.success) {
                throw new DatabaseInitializationException(
                        "Flyway migration reported failure for scenario '"
                                + scenarioName
                                + "' as user '"
                                + user
                                + "'",
                        null);
            }
        } catch (FlywayException e) {
            throw new DatabaseInitializationException(
                    "Failed to migrate Oracle scenario '"
                            + scenarioName
                            + "' as user '"
                            + user
                            + "': "
                            + e.getMessage(),
                    e);
        }
        return this;
    }

    private Flyway buildFlyway(String location, String user, String password) {
        return Flyway.configure()
                .dataSource(container.getJdbcUrl(null, null), user, password)
                .driver(ORACLE_DRIVER)
                .defaultSchema(user)
                .schemas(user)
                .locations(location)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load();
    }
}
