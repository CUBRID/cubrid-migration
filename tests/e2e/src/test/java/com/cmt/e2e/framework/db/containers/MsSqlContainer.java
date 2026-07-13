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

import org.testcontainers.mssqlserver.MSSQLServerContainer;

import java.time.Duration;

/**
 * SQL Server 2022 test container backed by the official {@code
 * org.testcontainers:testcontainers-mssqlserver} module. The image is amd64-only and runs under
 * emulation on arm64 hosts. The module starts a bare instance authenticated as {@code sa}; the e2e
 * source database ({@code e2e_db}) is created by the seed, not the image (like Informix).
 *
 * <p>A pre-10.2 mssql-jdbc driver is used deliberately: its default {@code encrypt=false} lets
 * CMT's fixed {@code jdbc:sqlserver://host:port;databaseName=db} URL connect to the cert-less
 * instance (CMT cannot pass {@code trustServerCertificate} through db.conf).
 */
public class MsSqlContainer implements DatabaseContainer {

    private static final String IMAGE = "mcr.microsoft.com/mssql/server:2022-latest";
    private static final int MSSQL_PORT = 1433;
    private static final String DATABASE_NAME = "e2e_db";
    private static final String USER = "sa";

    private final MSSQLServerContainer container;

    private MsSqlContainer() {
        MSSQLServerContainer c = new MSSQLServerContainer(IMAGE);
        c.acceptLicense();
        // SQL Server under emulation is slow to accept connections.
        c.withStartupTimeout(Duration.ofMinutes(5));
        this.container = c;
    }

    public static MsSqlContainer create() {
        return new MsSqlContainer();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(MSSQL_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.MSSQL;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
                "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false;trustServerCertificate=true",
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
        return container.getPassword();
    }

    /** Always-present system database used to bootstrap {@code CREATE DATABASE}. */
    public String getSystemDatabase() {
        return "master";
    }
}
