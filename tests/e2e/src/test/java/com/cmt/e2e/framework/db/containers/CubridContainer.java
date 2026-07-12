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
 * CUBRID 11.4 test container backed by the official {@code org.cubrid:testcontainers-cubrid}
 * module.
 */
public class CubridContainer implements DatabaseContainer {

    private static final String DATABASE_NAME = "e2e_db";
    private static final int CUBRID_BROKER_PORT = 33000;

    private final DbaProbeContainer container;

    private CubridContainer() {
        this.container = new DbaProbeContainer();
    }

    public static CubridContainer withEmptyDb() {
        return new CubridContainer();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(CUBRID_BROKER_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.CUBRID;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format(
                "jdbc:cubrid:%s:%d:%s:%s::", getHost(), getDatabasePort(), dbName, user);
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

    /**
     * Probes readiness as the built-in {@code dba} (only {@code CUBRID_DB} is set) so no app user
     * is created.
     */
    private static final class DbaProbeContainer extends org.testcontainers.cubrid.CubridContainer {

        private DbaProbeContainer() {
            super("cubrid/cubrid:11.4");
            withDatabaseName(DATABASE_NAME);
        }

        @Override
        protected void configure() {
            addEnv("CUBRID_DB", getDatabaseName());
        }

        @Override
        public String getUsername() {
            return "dba";
        }

        @Override
        public String getPassword() {
            return "";
        }
    }
}
