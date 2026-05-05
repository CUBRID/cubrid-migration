package com.cmt.e2e.framework.db.containers;

import java.time.Duration;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * CUBRID 11.4 Testcontainer. {@code CUBRID_DB} env fixes the DB name;
 * {@code --privileged} is required so the image can tune kernel
 * parameters (vm.swappiness, kernel.shmmax) at startup.
 */
public class CubridContainer implements DatabaseContainer {
    private static final DockerImageName IMAGE = DockerImageName.parse("cubrid/cubrid:11.4");
    private static final int CUBRID_BROKER_PORT = 33000;
    private static final String DATABASE_NAME = "e2e_db";

    private final GenericContainer<?> container;

    private CubridContainer() {
        this.container = new GenericContainer<>(IMAGE)
            .withPrivilegedMode(true)
            .withEnv("CUBRID_DB", DATABASE_NAME)
            .withEnv("CUBRID_COMPONENTS", "ALL")
            .withExposedPorts(CUBRID_BROKER_PORT)
            .waitingFor(Wait.forLogMessage(".*\\+\\+ cubrid server start: success.*", 1))
            .withStartupTimeout(Duration.ofMinutes(8));
    }

    public static CubridContainer withEmptyDb() {
        return new CubridContainer();
    }

    @Override public String  getHost()         { return container.getHost(); }
    @Override public Integer getDatabasePort() { return container.getMappedPort(CUBRID_BROKER_PORT); }
    @Override public DB      getDbType()       { return DB.CUBRID; }

    @Override
    public String getJdbcUrl(String dbName, String user) {
        return String.format("jdbc:cubrid:%s:%d:%s:%s::",
            getHost(), getDatabasePort(), dbName, user);
    }

    @Override public void start() { container.start(); }
    @Override public void stop()  { container.stop(); }

    public String getDatabaseName() { return DATABASE_NAME; }
}
