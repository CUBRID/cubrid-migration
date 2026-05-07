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

package com.cmt.e2e.framework.db.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs {@code *.sql} files from the test classpath through plain JDBC. Used for seed steps that
 * don't fit the Flyway model (e.g. CUBRID {@code CREATE USER} as dba). Files run in lexical order;
 * statements run one at a time so a syntax error names the offending statement.
 */
public final class ClasspathSqlRunner {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSqlRunner.class);

    private ClasspathSqlRunner() {}

    /**
     * Loads every {@code *.sql} file under {@code classpathDir} and runs it through a JDBC
     * connection authenticated as {@code user}.
     *
     * @throws SqlRunnerException wrapping any I/O or SQL failure
     */
    public static void runDirectory(
            String jdbcUrl, String user, String password, String classpathDir) {
        List<String> resourcePaths = listSqlResources(classpathDir);
        if (resourcePaths.isEmpty()) {
            log.info("[ClasspathSqlRunner] no .sql files under '{}' (skipping)", classpathDir);
            return;
        }

        log.info(
                "[ClasspathSqlRunner] running {} file(s) under '{}' as user='{}'",
                resourcePaths.size(),
                classpathDir,
                user);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            conn.setAutoCommit(true);
            for (String path : resourcePaths) {
                runResource(conn, path);
            }
        } catch (SQLException e) {
            throw new SqlRunnerException(
                    "Failed to open JDBC connection for SQL bootstrap '"
                            + classpathDir
                            + "' as user '"
                            + user
                            + "': "
                            + e.getMessage(),
                    e);
        }
    }

    private static void runResource(Connection conn, String resourcePath) {
        String sql = readResource(resourcePath);
        List<String> statements = splitStatements(sql);
        log.debug(
                "[ClasspathSqlRunner] {} ({} statement{})",
                resourcePath,
                statements.size(),
                statements.size() == 1 ? "" : "s");

        try (Statement st = conn.createStatement()) {
            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i).trim();
                if (stmt.isEmpty()) continue;
                try {
                    st.execute(stmt);
                } catch (SQLException e) {
                    throw new SqlRunnerException(
                            "SQL bootstrap failed at "
                                    + resourcePath
                                    + " statement #"
                                    + (i + 1)
                                    + " — "
                                    + firstLine(stmt)
                                    + ": "
                                    + e.getMessage(),
                            e);
                }
            }
        } catch (SQLException e) {
            throw new SqlRunnerException(
                    "Failed to create statement against " + resourcePath + ": " + e.getMessage(),
                    e);
        }
    }

    private static List<String> listSqlResources(String classpathDir) {
        URL url = Thread.currentThread().getContextClassLoader().getResource(classpathDir);
        if (url == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try {
            URI uri = url.toURI();
            Path dir;
            FileSystem fsToClose = null;
            try {
                if ("jar".equals(uri.getScheme())) {
                    fsToClose = FileSystems.newFileSystem(uri, java.util.Map.of());
                    dir = fsToClose.getPath(classpathDir);
                } else {
                    dir = Paths.get(uri);
                }
                try (Stream<Path> walk = Files.list(dir)) {
                    walk.filter(p -> p.getFileName().toString().endsWith(".sql"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .forEach(p -> out.add(classpathDir + "/" + p.getFileName().toString()));
                }
            } finally {
                if (fsToClose != null) fsToClose.close();
            }
        } catch (Exception e) {
            throw new SqlRunnerException(
                    "Failed to enumerate SQL resources under "
                            + classpathDir
                            + ": "
                            + e.getMessage(),
                    e);
        }
        return out;
    }

    private static String readResource(String resourcePath) {
        try (InputStream in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new SqlRunnerException("Resource not found: " + resourcePath, null);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SqlRunnerException(
                    "Failed to read " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Splits a SQL file into statements. Uses slash-mode (a line of just {@code /} ends a
     * statement) when the file contains at least one such line — required for PL/SQL {@code BEGIN
     * ... END;} bodies. Otherwise splits on top-level {@code ;} after stripping {@code --} line
     * comments. Limitations: only backslash string escape, no block comments, no dollar-quoting, no
     * mixing modes per file.
     */
    static List<String> splitStatements(String sql) {
        StringBuilder cleaned = new StringBuilder();
        boolean slashMode = false;
        for (String line : sql.split("\n", -1)) {
            String stripped = line.replaceAll("--.*$", "");
            // Detect on the original (un-stripped) trimmed line so an inline
            // "--" cannot accidentally remove the trailing "/".
            if (line.trim().equals("/")) slashMode = true;
            cleaned.append(stripped).append('\n');
        }
        return slashMode ? splitOnSlash(cleaned.toString()) : splitOnSemicolon(cleaned.toString());
    }

    private static List<String> splitOnSlash(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : sql.split("\n", -1)) {
            if (line.trim().equals("/")) {
                String s = cur.toString().trim();
                // Some JDBC drivers reject a trailing ";" on CREATE FUNCTION/PROCEDURE.
                if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
            } else {
                cur.append(line).append('\n');
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static List<String> splitOnSemicolon(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) {
                inSingle = !inSingle;
            }
            if (c == ';' && !inSingle) {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String first = (nl < 0 ? s : s.substring(0, nl)).trim();
        return first.length() > 80 ? first.substring(0, 80) + "..." : first;
    }

    public static final class SqlRunnerException extends RuntimeException {
        public SqlRunnerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
