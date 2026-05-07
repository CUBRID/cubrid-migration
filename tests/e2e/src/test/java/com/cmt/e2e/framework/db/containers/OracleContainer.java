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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/** Oracle 11g XE Testcontainer ({@code jdbc:oracle:thin:@host:port:XE}). */
public class OracleContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("gvenzl/oracle-xe:11.2.0.2-slim-faststart");

    private static final int ORACLE_PORT = 1521;
    private static final String SID = "XE";
    private static final String ORACLE_PASSWORD = "oracle";
    private static final String MAIN_USER = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String REF_USER = "REF_SCHEMA";
    private static final String REF_PASSWORD = "cmt";

    private static final String REF_INIT_CLASSPATH = "db/oracle/init/00_prepare_database.sql";
    private static final String REF_INIT_CONTAINER_PATH =
            "/container-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private OracleContainer() {
        this.container =
                new GenericContainer<>(IMAGE)
                        .withExposedPorts(ORACLE_PORT)
                        .withEnv("ORACLE_PASSWORD", ORACLE_PASSWORD)
                        .withEnv("APP_USER", MAIN_USER)
                        .withEnv("APP_USER_PASSWORD", MAIN_PASSWORD)
                        .withCopyFileToContainer(
                                MountableFile.forClasspathResource(REF_INIT_CLASSPATH),
                                REF_INIT_CONTAINER_PATH)
                        .waitingFor(
                                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                                        .withStartupTimeout(Duration.ofMinutes(5)));
    }

    public static OracleContainer withTwoUsers() {
        return new OracleContainer();
    }

    @Override
    public void start() {
        // Oracle 11g XE tz file (v4) → ORA-01882 on newer region IDs unless ojdbc sends UTC offset.
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(ORACLE_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.ORACLE;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:oracle:thin:@%s:%d:%s", getHost(), getDatabasePort(), SID);
    }

    public String getMainUser() {
        return MAIN_USER;
    }

    public String getMainPassword() {
        return MAIN_PASSWORD;
    }

    public String getRefUser() {
        return REF_USER;
    }

    public String getRefPassword() {
        return REF_PASSWORD;
    }

    public String getSid() {
        return SID;
    }
}
