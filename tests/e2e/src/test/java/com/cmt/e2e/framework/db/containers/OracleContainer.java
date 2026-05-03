package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Oracle 11g XE Testcontainer ({@code jdbc:oracle:thin:@host:port:XE}). */
public class OracleContainer implements DatabaseContainer {

    private static final DockerImageName IMAGE =
        DockerImageName.parse("gvenzl/oracle-xe:11.2.0.2-slim-faststart");

    private static final int    ORACLE_PORT      = 1521;
    private static final String SID              = "XE";
    private static final String ORACLE_PASSWORD  = "oracle";
    private static final String MAIN_USER        = "MAIN_SCHEMA";
    private static final String MAIN_PASSWORD    = "cmt";
    private static final String REF_USER         = "REF_SCHEMA";
    private static final String REF_PASSWORD     = "cmt";

    private static final String REF_INIT_CLASSPATH =
        "db/oracle/init/00_prepare_database.sql";
    private static final String REF_INIT_CONTAINER_PATH =
        "/container-entrypoint-initdb.d/00_prepare_database.sql";

    private final GenericContainer<?> container;

    private OracleContainer() {
        this.container = new GenericContainer<>(IMAGE)
            .withExposedPorts(ORACLE_PORT)
            .withEnv("ORACLE_PASSWORD",   ORACLE_PASSWORD)
            .withEnv("APP_USER",          MAIN_USER)
            .withEnv("APP_USER_PASSWORD", MAIN_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(REF_INIT_CLASSPATH),
                REF_INIT_CONTAINER_PATH
            )
            .waitingFor(
                Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(5))
            );
    }

    public static OracleContainer withTwoUsers() { return new OracleContainer(); }

    @Override
    public void start() {
        // Oracle 11g XE tz file (v4) → ORA-01882 on newer region IDs unless ojdbc sends UTC offset.
        System.setProperty("oracle.jdbc.timezoneAsRegion", "false");
        container.start();
    }

    @Override public void stop()  { container.stop(); }
    @Override public String getHost() { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(ORACLE_PORT); }
    @Override public DB getDbType() { return DB.ORACLE; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:oracle:thin:@%s:%d:%s",
            getHost(), getDatabasePort(), SID);
    }

    public String getMainUser()     { return MAIN_USER; }
    public String getMainPassword() { return MAIN_PASSWORD; }
    public String getRefUser()      { return REF_USER; }
    public String getRefPassword()  { return REF_PASSWORD; }
    public String getSid()          { return SID; }
}
