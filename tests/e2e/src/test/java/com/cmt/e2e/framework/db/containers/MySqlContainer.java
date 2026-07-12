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

package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;

/**
 * MySQL 8.0 test container backed by the official {@code org.testcontainers:testcontainers-mysql}
 * module. MySQL treats a schema as a database, so the e2e seed lives in a single database ({@code
 * main_schema}) — there is no REF/MAIN split as with Oracle. The module creates the database and
 * user and reports readiness via JDBC, so no extra configuration is needed.
 */
public class MySqlContainer implements DatabaseContainer {

    private static final String IMAGE = "mysql:8.0";
    private static final String DATABASE_NAME = "main_schema";
    // User name doubles as the CUBRID target owner (CMT preserves it, uppercased as MAIN_SCHEMA),
    // keeping mysql_to_cubrid snapshots consistent with the other engines.
    private static final String USER = "main_schema";
    private static final String PASSWORD = "cmt";
    private static final int MYSQL_PORT = 3306;

    private final org.testcontainers.mysql.MySQLContainer container;

    private MySqlContainer() {
        this.container =
                new org.testcontainers.mysql.MySQLContainer(IMAGE)
                        .withDatabaseName(DATABASE_NAME)
                        .withUsername(USER)
                        .withPassword(PASSWORD)
                        // Let the non-SUPER app user CREATE FUNCTION while binary logging is on
                        // (MySQL 8 blocks it otherwise). Passed as a mysqld arg by the image
                        // entrypoint.
                        .withCommand("--log-bin-trust-function-creators=1");
    }

    public static MySqlContainer withMainSchema() {
        return new MySqlContainer();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(MYSQL_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.MYSQL;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
                "jdbc:mysql://%s:%d/%s?allowPublicKeyRetrieval=true&useSSL=false",
                getHost(), getDatabasePort(), dbName);
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    public String getDatabaseName() {
        return DATABASE_NAME;
    }

    public String getUser() {
        return USER;
    }

    public String getPassword() {
        return PASSWORD;
    }
}
