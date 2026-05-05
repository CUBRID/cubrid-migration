package com.cmt.e2e.framework.db.containers;

import java.nio.file.Path;
import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Tibero 7 Testcontainer.
 *
 * <p>Image, hostname, license path, and FAKETIME come from {@link
 * TiberoEnvironment} (config keys e2e.tibero.image / hostname /
 * license / faketime — see {@code e2e-test.properties.example}).
 * Constants below ({@code TIBERO_PORT}, {@code SID},
 * {@code LICENSE_IN_CONTAINER}, DBA credentials, schema users) are
 * fixed by the bundled image and seed scripts.
 */
public final class TiberoContainer implements DatabaseContainer {

    private static final int    TIBERO_PORT          = 8629;
    private static final String SID                  = "tibero";
    private static final String LICENSE_IN_CONTAINER = "/opt/tibero7/license/license.xml";
    private static final String DBA_USER             = "sys";
    private static final String DBA_PASSWORD         = "tibero123";
    private static final String MAIN_USER            = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD        = "cmt";
    private static final String REF_USER             = "REF_SCHEMA";
    private static final String REF_PASSWORD         = "cmt";

    private final GenericContainer<?> container;

    private TiberoContainer() {
        DockerImageName image  = DockerImageName.parse(TiberoEnvironment.image());
        String hostname        = TiberoEnvironment.hostname();
        Path   licenseHostPath = TiberoEnvironment.licensePath();
        String faketime        = "-" + TiberoEnvironment.faketimeDaysBack() + "d";

        this.container = new GenericContainer<>(image)
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(hostname);
                cmd.withPlatform("linux/amd64");
            })
            .withExposedPorts(TIBERO_PORT)
            .withEnv("TB_ROOT_PASSWORD", DBA_PASSWORD)
            .withEnv("FAKETIME", faketime)
            .withCopyFileToContainer(
                MountableFile.forHostPath(licenseHostPath.toString()),
                LICENSE_IN_CONTAINER)
            .withSharedMemorySize(1024L * 1024 * 1024)  // 1 GB — Tibero requires this
            .waitingFor(
                Wait.forLogMessage(".*Tibero is Ready To Use.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(12)))
            .withStartupAttempts(2);
    }

    public static TiberoContainer create() { return new TiberoContainer(); }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }
    @Override public void close() { container.close(); }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(TIBERO_PORT); }
    @Override public DB      getDbType()       { return DB.TIBERO; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:tibero:thin:@%s:%d:%s",
            getHost(), getDatabasePort(), SID);
    }

    public String getSid()           { return SID; }
    public String getDbaUser()       { return DBA_USER; }
    public String getDbaPassword()   { return DBA_PASSWORD; }
    public String getMainUser()      { return MAIN_USER; }
    public String getMainPassword()  { return MAIN_PASSWORD; }
    public String getRefUser()       { return REF_USER; }
    public String getRefPassword()   { return REF_PASSWORD; }
}
