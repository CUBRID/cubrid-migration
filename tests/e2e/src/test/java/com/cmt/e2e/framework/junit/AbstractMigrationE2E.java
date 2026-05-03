package com.cmt.e2e.framework.junit;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.cmt.e2e.framework.core.WorkspaceCleaner;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.runner.Migration;
import com.cmt.e2e.framework.runner.MigrationOutcome;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for migration E2E tests. Runs source/target/migration once
 * in {@code @BeforeAll} and caches the outcome so each {@code @Test}
 * verifies one fact against the same result.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMigrationE2E {

    private static final Logger log = LoggerFactory.getLogger(AbstractMigrationE2E.class);

    private static final Path WORK_ROOT = Paths.get("target", "e2e", "runs");

    private Source source;
    private Target target;
    private MigrationOutcome cachedOutcome;

    protected abstract Source source();
    protected abstract Target target();

    @BeforeAll
    final void e2eStartup() throws Exception {
        new WorkspaceCleaner(CmtConsoleEnv.resolve().toFile()).cleanupOutput();

        this.source = source();
        this.target = target();
        log.info("[{}] starting source ({}) and target", scenarioName(), source.type());
        source.start();
        target.start();

        Path workDir = WORK_ROOT.resolve(scenarioName());
        log.info("[{}] running migration; workDir={}", scenarioName(), workDir);
        this.cachedOutcome = new Migration(source, target, scenarioName()).run(workDir);
        log.info("[{}] migration finished", scenarioName());
    }

    @AfterAll
    final void e2eShutdown() {
        if (target != null) {
            try { target.close(); } catch (Exception e) { log.warn("target.close failed", e); }
        }
        if (source != null) {
            try { source.close(); } catch (Exception e) { log.warn("source.close failed", e); }
        }
    }

    protected final MigrationOutcome run() {
        if (cachedOutcome == null) {
            throw new IllegalStateException(
                "run() called before @BeforeAll completed — possible JUnit lifecycle misconfiguration.");
        }
        return cachedOutcome;
    }

    private String scenarioName() {
        MigrationE2E ann = getClass().getAnnotation(MigrationE2E.class);
        if (ann == null) {
            throw new IllegalStateException(
                "Test class " + getClass().getSimpleName()
                    + " is missing @MigrationE2E. Add @MigrationE2E(name = \"<scenario>\").");
        }
        return ann.name();
    }
}
