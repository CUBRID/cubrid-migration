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
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.ScriptCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a CMT {@code script.xml} via {@code migration.sh script} and normalizes timestamp +
 * seed-internal noise so the result is byte- deterministic across runs.
 */
public final class ScriptXmlBuilder {

    private static final Logger log = LoggerFactory.getLogger(ScriptXmlBuilder.class);

    private ScriptXmlBuilder() {}

    public record Result(Path scriptXml, String migrationName) {}

    /**
     * @param consoleHome {@code CMT_CONSOLE_HOME}
     * @param dbConf full {@code db.conf} text (use {@link DbConfBuilder})
     * @param outputDir destination for sanitized {@code script.xml}; unsanitized output goes under
     *     {@code outputDir/raw/}
     * @return sanitized script path + the {@code <migration name="...">} value (CMT writes dump
     *     output under {@code $CMT_CONSOLE_HOME/output/<name>/...})
     */
    public static Result generate(Path consoleHome, String dbConf, Path outputDir)
            throws Exception {
        Files.createDirectories(outputDir);
        Path rawDir = outputDir.resolve("raw");
        recreateDirectory(rawDir);

        ScriptCommand cmd =
                ScriptCommand.builder()
                        .sourceConfig(DbConfBuilder.SOURCE_NAME)
                        .targetConfig(DbConfBuilder.TARGET_NAME)
                        .outputDir(rawDir.toAbsolutePath().toString())
                        .build();

        CommandRunner runner = new CommandRunner(consoleHome.toFile());
        CommandResult result = runWithTemporaryDbConf(consoleHome, dbConf, () -> runner.run(cmd));

        log.debug("[ScriptXmlBuilder] migration.sh script — exit={}", result.exitCode());
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "migration.sh script failed (exit "
                            + result.exitCode()
                            + ")\n"
                            + "stdout:\n"
                            + result.stdout()
                            + "\n"
                            + "stderr:\n"
                            + result.stderr());
        }

        Path raw = findGeneratedXml(rawDir);
        String sanitized = sanitize(Files.readString(raw));
        Path out = outputDir.resolve("script.xml");
        Files.writeString(out, sanitized);
        return new Result(out.toAbsolutePath(), extractMigrationName(sanitized));
    }

    private static String extractMigrationName(String content) {
        Matcher m = Pattern.compile("<migration\\s+name=\"([^\"]+)\"").matcher(content);
        if (!m.find()) {
            throw new IllegalStateException("script.xml missing <migration name=...>");
        }
        return m.group(1);
    }

    /**
     * Strips noise that would defeat snapshot determinism: (1) the 12-digit wall-clock timestamps
     * CMT puts in {@code <migration name>} and {@code wizard_start_date_time}; (2) Flyway's {@code
     * flyway_schema_history} table (seed implementation detail); (3) CUBRID system schemas
     * DBA/PUBLIC (introspected when connecting as dba; CMT cannot migrate them); (4) {@code
     * e2e_cubrid_collection_types} (CUBRID-only SET/LIST/SEQUENCE — anti-coverage per cubrid
     * SEED_SPEC); (5) functional indexes on CUBRID source ({@code idxf_*}) — fetcher emits no
     * expression, import fails.
     */
    private static String sanitize(String content) {
        // (1) wall-clock timestamps
        content = content.replaceAll("(<migration\\s+name=\")([^\"]+?)_\\d{12}(\")", "$1$2$3");
        content = content.replaceAll("(wizard_start_date_time=\")\\d{12}(\")", "$1000000000000$2");

        // (2) flyway_schema_history — both <table>...</table> blocks and
        // self-closing tags that name it.
        content =
                content.replaceAll(
                        "(?s)\\s*<table\\b[^>]*\\bname=\"flyway_schema_history\"[^>]*>.*?</table>\\s*",
                        "\n            ");
        content =
                content.replaceAll(
                        "(?m)\\s*<\\w+\\b[^>]*\\bname=\"flyway_schema_history\"[^>]*/>\\s*\\R?",
                        "");

        // (3) CUBRID system schemas DBA/PUBLIC.
        content =
                content.replaceAll(
                        "(?m)\\s*<schema\\s+source=\"(?:DBA|PUBLIC)\"[^>]*/>\\s*\\R?", "");

        // (4) e2e_cubrid_collection_types (CUBRID anti-coverage).
        content =
                content.replaceAll(
                        "(?s)\\s*<table\\b[^>]*\\bname=\"e2e_cubrid_collection_types\"[^>]*>.*?</table>\\s*",
                        "\n            ");
        content =
                content.replaceAll(
                        "(?m)\\s*<\\w+\\b[^>]*\\bname=\"e2e_cubrid_collection_types\"[^>]*/>\\s*\\R?",
                        "");

        // (5) idxf_* on CUBRID source — fetcher does not emit the expression.
        content =
                content.replaceAll(
                        "(?m)\\s*<index\\b[^>]*\\bname=\"idxf_[^\"]*\"[^>]*/>\\s*\\R?", "");

        return content;
    }

    private static CommandResult runWithTemporaryDbConf(
            Path consoleHome, String dbConfContent, ThrowingSupplier<CommandResult> action)
            throws Exception {
        Path dbConf = consoleHome.resolve("db.conf");
        Path backup = null;
        if (Files.exists(dbConf)) {
            backup = Files.createTempFile(consoleHome, "db.conf.", ".bak");
            Files.copy(dbConf, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(dbConf, dbConfContent);
        try {
            return action.get();
        } finally {
            if (backup != null) {
                Files.move(backup, dbConf, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(dbConf);
            }
        }
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(
                                p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (IOException e) {
                                        throw new RuntimeException("delete failed: " + p, e);
                                    }
                                });
            }
        }
        Files.createDirectories(dir);
    }

    private static Path findGeneratedXml(Path rawDir) throws IOException {
        try (var walk = Files.list(rawDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".xml"))
                    .max(Comparator.comparingLong(ScriptXmlBuilder::lastModified))
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "CMT did not produce an XML under " + rawDir));
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            throw new RuntimeException("stat: " + p, e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
