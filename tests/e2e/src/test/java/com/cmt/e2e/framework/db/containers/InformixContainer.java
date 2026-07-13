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

import java.time.Duration;

/**
 * Informix 14.10 test container (IBM developer edition). Testcontainers has no official Informix
 * module, so this wraps a {@link GenericContainer} directly (like Oracle/Tibero). The image is
 * amd64-only and runs under emulation on arm64 hosts. INFORMIXSERVER is the image default {@code
 * informix}, which matches CMT's own default, so no server name has to be passed through db.conf.
 * The source database ({@code e2e_db}) is created by the seed, not the image.
 */
public class InformixContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("ibmcom/informix-developer-database:latest");
    private static final int INFORMIX_PORT = 9088;
    private static final String SERVER = "informix";
    private static final String DATABASE_NAME = "e2e_db";
    private static final String USER = "informix";
    private static final String PASSWORD = "in4mix";

    private final GenericContainer<?> container;

    private InformixContainer() {
        this.container =
                new GenericContainer<>(IMAGE)
                        .withEnv("LICENSE", "accept")
                        .withExposedPorts(INFORMIX_PORT)
                        // The bundled listeners start last; wait for the final one so the SQLI
                        // listener and system databases are fully up before seeding.
                        .waitingFor(
                                Wait.forLogMessage(".*starting mqtt listener.*", 1)
                                        .withStartupTimeout(Duration.ofMinutes(5)));
    }

    public static InformixContainer create() {
        return new InformixContainer();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(INFORMIX_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.INFORMIX;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
                "jdbc:informix-sqli://%s:%d/%s:INFORMIXSERVER=%s",
                getHost(), getDatabasePort(), dbName, SERVER);
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

    /** Always-present system database used to bootstrap {@code CREATE DATABASE}. */
    public String getSystemDatabase() {
        return "sysmaster";
    }
}
