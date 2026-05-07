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
 * CUBRID 11.4 Testcontainer. {@code CUBRID_DB} env fixes the DB name; {@code --privileged} is
 * required so the image can tune kernel parameters (vm.swappiness, kernel.shmmax) at startup.
 */
public class CubridContainer implements DatabaseContainer {
    private static final DockerImageName IMAGE = DockerImageName.parse("cubrid/cubrid:11.4");
    private static final int CUBRID_BROKER_PORT = 33000;
    private static final String DATABASE_NAME = "e2e_db";

    private final GenericContainer<?> container;

    private CubridContainer() {
        this.container =
                new GenericContainer<>(IMAGE)
                        .withPrivilegedMode(true)
                        .withEnv("CUBRID_DB", DATABASE_NAME)
                        .withEnv("CUBRID_COMPONENTS", "ALL")
                        .withExposedPorts(CUBRID_BROKER_PORT)
                        .waitingFor(
                                Wait.forLogMessage(".*\\+\\+ cubrid server start: success.*", 1))
                        .withStartupTimeout(Duration.ofMinutes(8));
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
}
