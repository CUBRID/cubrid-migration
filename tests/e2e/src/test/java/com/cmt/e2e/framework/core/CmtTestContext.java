package com.cmt.e2e.framework.core;

import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * JUnit 5 extension wiring per-test plumbing (CommandRunner,
 * WorkspaceCleaner) and per-test log routing via MDC. Register with
 * {@code @RegisterExtension final CmtTestContext ctx = ...}.
 */
public class CmtTestContext implements BeforeEachCallback, AfterEachCallback {
    private static final Logger log = LoggerFactory.getLogger(CmtTestContext.class);

    /** SiftingAppender discriminator — must match {@code logback-test.xml}. */
    private static final String MDC_TEST_ID = "testId";

    private CommandRunner commandRunner;
    private WorkspaceCleaner workspaceCleaner;
    private Path cmtConsoleHome;

    public CmtTestContext() {}

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        Method testMethod = context.getRequiredTestMethod();

        // MDC first so any log line (including failures here) lands in the per-test file.
        MDC.put(MDC_TEST_ID, testClass.getSimpleName() + "/" + testMethod.getName());

        this.cmtConsoleHome = CmtConsoleEnv.resolve();
        this.commandRunner = new CommandRunner(cmtConsoleHome.toFile());
        this.workspaceCleaner = new WorkspaceCleaner(cmtConsoleHome.toFile());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        try {
            if (workspaceCleaner != null) {
                workspaceCleaner.cleanupWorkspace();
                workspaceCleaner.cleanupOutput();
            }
        } finally {
            MDC.remove(MDC_TEST_ID);
        }
    }

    public CommandRunner commandRunner() { return commandRunner; }
    public Path cmtConsoleHome() { return cmtConsoleHome; }
}
