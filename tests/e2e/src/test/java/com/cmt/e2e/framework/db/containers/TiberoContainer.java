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
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Tibero 6 Testcontainer.
 *
 * <p>Image, hostname, license path, and clock offset come from {@link TiberoEnvironment} (config
 * keys e2e.tibero.image / hostname / license / faketime — see {@code e2e-test.properties.example}).
 * Constants below ({@code TIBERO_PORT}, {@code SID}, container paths, DBA credentials, schema
 * users) are fixed by the bundled image and seed scripts.
 *
 * <p>The image creates the database on first boot from three files: the license, an {@code
 * account-list} (empty is fine, but a missing one aborts the non-interactive init), and an {@code
 * init.sql} that sets the SYS password ({@code TB_ROOT_PASSWORD} is ignored). They are copied, not
 * bind-mounted: init ends by deleting {@code account-list}, which fails on a mount and takes the
 * container down with it.
 */
public final class TiberoContainer implements DatabaseContainer {

    private static final int TIBERO_PORT = 8629;
    private static final String SID = "tibero";
    private static final String PERSIST_DIR = "/opt/tibero/persistable";
    private static final String LICENSE_IN_CONTAINER = PERSIST_DIR + "/config/license/license.xml";
    private static final String ACCOUNT_LIST_IN_CONTAINER = PERSIST_DIR + "/account-list";
    private static final String INIT_SQL_IN_CONTAINER = PERSIST_DIR + "/config/sql/init.sql";

    /** Printed verbatim by the image once the service is up. */
    private static final String READY_MARKER = "CMT_E2E_TIBERO_READY";

    private static final long SHM_GB = 1L;
    private static final long MEMORY_TARGET_GB = 2L;

    private static final String DBA_USER = "sys";
    private static final String DBA_PASSWORD = "tibero123";
    private static final String MAIN_USER = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD = "cmt";
    private static final String REF_USER = "REF_SCHEMA";
    private static final String REF_PASSWORD = "cmt";

    private final GenericContainer<?> container;

    private TiberoContainer() {
        DockerImageName image = DockerImageName.parse(TiberoEnvironment.image());
        String hostname = TiberoEnvironment.hostname();
        Path licenseHostPath = TiberoEnvironment.licensePath();
        String clockOffset = "-" + TiberoEnvironment.faketimeDaysBack() + "d";

        this.container =
                new GenericContainer<>(image)
                        .withCreateContainerCmdModifier(
                                cmd -> {
                                    cmd.withHostName(hostname);
                                    cmd.withPlatform("linux/amd64");
                                })
                        .withExposedPorts(TIBERO_PORT)
                        .withEnv("FAKETIME", clockOffset)
                        .withEnv("TB_READY_SIGNAL_PATTERN", READY_MARKER)
                        .withEnv("TB_TOTAL_SHM_SIZE", SHM_GB + "G")
                        .withEnv("TB_MEMORY_TARGET", MEMORY_TARGET_GB + "G")
                        .withCopyFileToContainer(
                                MountableFile.forHostPath(licenseHostPath.toString()),
                                LICENSE_IN_CONTAINER)
                        .withCopyToContainer(Transferable.of(""), ACCOUNT_LIST_IN_CONTAINER)
                        .withCopyToContainer(
                                Transferable.of(
                                        "ALTER USER SYS IDENTIFIED BY '" + DBA_PASSWORD + "';\n"),
                                INIT_SQL_IN_CONTAINER)
                        .withSharedMemorySize(SHM_GB * 1024 * 1024 * 1024)
                        .waitingFor(
                                Wait.forLogMessage(".*" + READY_MARKER + ".*", 1)
                                        .withStartupTimeout(Duration.ofMinutes(15)))
                        .withStartupAttempts(2);
    }

    public static TiberoContainer create() {
        return new TiberoContainer();
    }

    @Override
    public void start() {
        container.start();
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public void close() {
        container.close();
    }

    @Override
    public String getHost() {
        return container.getHost();
    }

    @Override
    public Integer getDatabasePort() {
        return container.getMappedPort(TIBERO_PORT);
    }

    @Override
    public DB getDbType() {
        return DB.TIBERO;
    }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:tibero:thin:@%s:%d:%s", getHost(), getDatabasePort(), SID);
    }

    public String getSid() {
        return SID;
    }

    public String getDbaUser() {
        return DBA_USER;
    }

    public String getDbaPassword() {
        return DBA_PASSWORD;
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
}
