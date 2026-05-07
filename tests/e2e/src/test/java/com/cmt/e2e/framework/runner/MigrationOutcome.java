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

package com.cmt.e2e.framework.runner;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.verify.CatalogSnapshot;
import com.cmt.e2e.framework.verify.DumpSnapshot;
import com.cmt.e2e.framework.verify.RowCounts;
import com.cmt.e2e.framework.verify.RowQueries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outcome of one {@link Migration#run(Path)} call. Verification surface across four layers: L1
 * smoke ({@link #expectSuccess()}, {@link #expectNoFatalStderr()}); L2 coverage + L3 fidelity
 * ({@link #catalog()} / {@link #rowCounts} / {@link #queries} / {@link #dumpfile()}); L4 regression
 * (same surface, scoped per {@code @Test}).
 */
public final class MigrationOutcome {

    static final String SUCCESS_MARKER = "MIGRATION RESULT: SUCCESS";

    static final Pattern FATAL_STDERR =
            Pattern.compile(
                    "(?m)^(?:ERROR\\b|FATAL\\b|Exception(?:\\s|:)|Caused"
                            + " by:|java\\.lang\\.[A-Za-z]+Exception)");

    /**
     * Matches one line of CMT 's "Migration Report summary" block, e.g. " table: Exported[10];
     * Imported[10]".
     */
    static final Pattern REPORT_LINE =
            Pattern.compile(
                    "(?m)^\\s+([\\w ]+?):\\s+Exported\\[(\\d+)\\];\\s+Imported\\[(\\d+)\\]");

    private final CommandResult result;
    private final Target target;
    private final String migrationName;
    private final String scenarioName;

    public MigrationOutcome(
            CommandResult result, Target target, String migrationName, String scenarioName) {
        this.result = result;
        this.target = target;
        this.migrationName = migrationName;
        this.scenarioName = scenarioName;
    }

    /** Asserts: not timed out, exit 0, "MIGRATION RESULT: SUCCESS" in stdout. */
    public MigrationOutcome expectSuccess() {
        if (result.timedOut()) {
            throw new AssertionError("migration timed out");
        }
        if (result.exitCode() != 0) {
            throw new AssertionError(
                    String.format(
                            "migration failed (exit=%d)%nstdout:%n%s%nstderr:%n%s",
                            result.exitCode(), result.stdout(), result.stderr()));
        }
        if (!result.stdout().contains(SUCCESS_MARKER)) {
            throw new AssertionError(
                    "stdout missing '" + SUCCESS_MARKER + "':\n" + result.stdout());
        }
        return this;
    }

    /**
     * Asserts every category in CMT's "Migration Report summary" block has {@code Imported[N]}
     * equal to {@code Exported[N]} — i.e. nothing was silently dropped between source extraction
     * and target import.
     */
    public MigrationOutcome expectImportMatchesExport() {
        if (!result.stdout().contains("Migration Report summary:")) {
            throw new AssertionError(
                    "Migration Report summary block not found in CMT stdout:\n" + result.stdout());
        }
        Matcher m = REPORT_LINE.matcher(result.stdout());
        List<String> mismatches = new ArrayList<>();
        while (m.find()) {
            String category = m.group(1).trim();
            int exported = Integer.parseInt(m.group(2));
            int imported = Integer.parseInt(m.group(3));
            if (exported != imported) {
                mismatches.add(
                        String.format(
                                "%s: exported=%d, imported=%d", category, exported, imported));
            }
        }
        if (!mismatches.isEmpty()) {
            throw new AssertionError(
                    "Migration Report shows export/import count mismatch:\n  - "
                            + String.join("\n  - ", mismatches));
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
        Path outputBase = CmtConsoleEnv.resolve().resolve("output").resolve(migrationName);
        return new DumpSnapshot(outputBase, scenarioName);
    }

    private void requireOnlineTarget(String op) {
        if (target.isDumpfile()) {
            throw new IllegalStateException(
                    op
                            + " is for online targets; this is a dump-file scenario. "
                            + "Use dumpfile() instead.");
        }
    }
}
