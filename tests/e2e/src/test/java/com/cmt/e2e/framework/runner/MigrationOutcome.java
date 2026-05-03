package com.cmt.e2e.framework.runner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.target.Target;

/**
 * Outcome of one {@link Migration#run(java.nio.file.Path)} call. This
 * commit ships the L1 smoke surface ({@link #expectSuccess()},
 * {@link #expectNoFatalStderr()}); L2/L3/L4 verification helpers
 * (catalog / row counts / queries / dumpfile) arrive with the verify
 * package in a later commit.
 */
public final class MigrationOutcome {

    static final String SUCCESS_MARKER = "MIGRATION RESULT: SUCCESS";

    static final Pattern FATAL_STDERR = Pattern.compile(
        "(?m)^(?:ERROR\\b|FATAL\\b|Exception(?:\\s|:)|Caused by:|java\\.lang\\.[A-Za-z]+Exception)");

    private final CommandResult result;
    private final Target target;
    private final String migrationName;
    private final String scenarioName;

    public MigrationOutcome(CommandResult result, Target target,
                            String migrationName, String scenarioName) {
        this.result        = result;
        this.target        = target;
        this.migrationName = migrationName;
        this.scenarioName  = scenarioName;
    }

    /** Asserts: not timed out, exit 0, "MIGRATION RESULT: SUCCESS" in stdout. */
    public MigrationOutcome expectSuccess() {
        if (result.timedOut()) {
            throw new AssertionError("migration timed out");
        }
        if (result.exitCode() != 0) {
            throw new AssertionError(String.format(
                "migration failed (exit=%d)%nstdout:%n%s%nstderr:%n%s",
                result.exitCode(), result.stdout(), result.stderr()));
        }
        if (!result.stdout().contains(SUCCESS_MARKER)) {
            throw new AssertionError(
                "stdout missing '" + SUCCESS_MARKER + "':\n" + result.stdout());
        }
        return this;
    }

    /** Asserts no fatal-looking line in stderr (benign noise like JAVA_TOOL_OPTIONS is OK). */
    public MigrationOutcome expectNoFatalStderr() {
        Matcher m = FATAL_STDERR.matcher(result.stderr());
        if (m.find()) {
            throw new AssertionError(
                "stderr contained fatal pattern: " + extractLine(result.stderr(), m.start()));
        }
        return this;
    }

    private static String extractLine(String text, int index) {
        int start = text.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int end = text.indexOf('\n', index);
        if (end < 0) end = text.length();
        return text.substring(start, end);
    }
}
