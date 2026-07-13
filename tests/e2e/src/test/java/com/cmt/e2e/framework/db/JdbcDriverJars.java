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

package com.cmt.e2e.framework.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JdbcDriverJars {
    private JdbcDriverJars() {}

    /** Override with system property: -De2e.driver.dir=/abs/path/to/driver */
    private static final String PROP_DIR = "e2e.driver.dir";

    /**
     * Default driver directory: {@code target/test-classes/driver}. The maven-dependency-plugin
     * copies JDBC jars there during the build. Override with {@code
     * -De2e.driver.dir=/absolute/path}.
     */
    private static Path defaultDir() {
        Path base = Paths.get("").toAbsolutePath();
        Path d = base.resolve("target").resolve("test-classes").resolve("driver");
        String override = System.getProperty(PROP_DIR);
        Path candidate = override != null ? Paths.get(override).toAbsolutePath() : d;
        if (!Files.isDirectory(candidate)) {
            throw new IllegalStateException(
                    "Driver directory not found: "
                            + candidate
                            + "\nHint: run 'mvn generate-test-resources' first, "
                            + "or override with -D"
                            + PROP_DIR
                            + "=/absolute/path");
        }
        return candidate;
    }

    public enum DB {
        CUBRID,
        ORACLE,
        TIBERO,
        MYSQL,
        MARIADB,
        INFORMIX,
        MSSQL
    }

    private static final Map<DB, List<String>> PATTERNS =
            Map.of(
                    DB.CUBRID, List.of("JDBC-*-cubrid.jar", "cubrid-jdbc-*.jar"),
                    DB.ORACLE, List.of("ojdbc8-*.jar", "ojdbc8.jar", "ojdbc*.jar"),
                    DB.TIBERO, List.of("tibero7-jdbc-17.jar", "tibero7-jdbc-*.jar"),
                    DB.MYSQL, List.of("mysql-connector-j-*.jar", "mysql-connector-java-*.jar"),
                    DB.MARIADB, List.of("mariadb-java-client-*.jar"),
                    DB.INFORMIX, List.of("informix-jdbc-*.jar"),
                    DB.MSSQL, List.of("mssql-jdbc-*.jar"));

    private static final Map<DB, Path> CACHE = new ConcurrentHashMap<>();

    public static Path latest(DB db) {
        return CACHE.computeIfAbsent(
                db,
                k -> {
                    try {
                        Path dir = defaultDir();
                        List<Path> candidates = findCandidates(dir, db);

                        if (candidates.isEmpty()) {
                            throw new IllegalStateException(
                                    "No JDBC jar found for "
                                            + db
                                            + " under "
                                            + dir
                                            + "\nLooked for patterns: "
                                            + PATTERNS.get(db));
                        }

                        candidates.sort(
                                Comparator.comparing(
                                                JdbcDriverJars::extractVersionTokens,
                                                JdbcDriverJars::compareVersionLists)
                                        .reversed());
                        return candidates.get(0).toAbsolutePath().normalize();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private static List<Path> findCandidates(Path dir, DB db) throws IOException {
        List<String> globs = PATTERNS.getOrDefault(db, List.of("*.jar"));
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> all = s.filter(Files::isRegularFile).collect(Collectors.toList());
            return all.stream()
                    .filter(p -> matchAny(globs, p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    private static boolean matchAny(List<String> globs, String filename) {
        for (String g : globs) {
            String regex = globToRegex(g);
            if (filename.matches(regex)) return true;
        }
        return false;
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append('.');
                    break;
                case '.':
                    sb.append("\\.");
                    break;
                default:
                    sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static List<Integer> extractVersionTokens(Path p) {
        String name = p.getFileName().toString();
        Matcher m = Pattern.compile("(\\d+(?:[._]\\d+)+)").matcher(name);
        if (m.find()) {
            String[] parts = m.group(1).replace('_', '.').split("\\.");
            List<Integer> nums = new ArrayList<>(parts.length);
            for (String part : parts) {
                try {
                    nums.add(Integer.parseInt(part));
                } catch (NumberFormatException e) {
                    nums.add(0);
                }
            }
            return nums;
        }
        return List.of();
    }

    private static int compareVersionLists(List<Integer> a, List<Integer> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int ai = i < a.size() ? a.get(i) : 0;
            int bi = i < b.size() ? b.get(i) : 0;
            int cmp = Integer.compare(ai, bi);
            if (cmp != 0) return cmp;
        }
        return 0;
    }
}
