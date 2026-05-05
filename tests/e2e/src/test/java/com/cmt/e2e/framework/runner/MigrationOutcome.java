package com.cmt.e2e.framework.runner;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.verify.CatalogSnapshot;
import com.cmt.e2e.framework.verify.DumpSnapshot;
import com.cmt.e2e.framework.verify.RowCounts;
import com.cmt.e2e.framework.verify.RowQueries;

/**
 * Outcome of one {@link Migration#run(Path)} call. Verification surface
 * across four layers: L1 smoke ({@link #expectSuccess()},
 * {@link #expectNoFatalStderr()}); L2 coverage + L3 fidelity
 * ({@link #catalog()} / {@link #rowCounts} / {@link #queries} /
 * {@link #dumpfile()}); L4 regression (same surface, scoped per
 * {@code @Test}).
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

    /** Online-target catalog snapshot helper. Throws on dump-file targets. */
    public CatalogSnapshot catalog() {
        requireOnlineTarget("catalog()");
        return new CatalogSnapshot(target.connection(), scenarioName);
    }

    /** Online-target row-count snapshot. No args = all user tables; pass owners to restrict. */
    public RowCounts rowCounts(String... ownerSchemas) {
        requireOnlineTarget("rowCounts()");
        return new RowCounts(target.connection(), scenarioName, List.of(ownerSchemas));
    }

    /** Run all labelled queries from {@code queries/<scenario>.sql} and snapshot the output. */
    public RowQueries queries(Path sqlFile) {
        requireOnlineTarget("queries()");
        return new RowQueries(sqlFile, target.connection(), scenarioName);
    }

    /** Dump-file snapshot helper rooted at {@code $CMT_CONSOLE_HOME/output/<migration-name>/}. */
    public DumpSnapshot dumpfile() {
        if (!target.isDumpfile()) {
            throw new IllegalStateException(
                "dumpfile() is for dump-file targets; this is an online migration. "
                + "Use catalog() / rowCounts() / queries() instead.");
        }
        Path outputBase = CmtConsoleEnv.resolve()
            .resolve("output")
            .resolve(migrationName);
        return new DumpSnapshot(outputBase, scenarioName);
    }

    private void requireOnlineTarget(String op) {
        if (target.isDumpfile()) {
            throw new IllegalStateException(
                op + " is for online targets; this is a dump-file scenario. "
                + "Use dumpfile() instead.");
        }
    }
}
