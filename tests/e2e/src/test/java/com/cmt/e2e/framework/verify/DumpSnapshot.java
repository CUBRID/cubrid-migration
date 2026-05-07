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

package com.cmt.e2e.framework.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Dump-file snapshot — compares the entire CMT {@code unload} output tree against {@code
 * snapshots/<scenario>/dumpfile/}. Update mode ({@code -Dsnapshot.update=true}) wipes and recopies
 * the snapshot tree. For non-determinism in dump content, fix it CMT-side in {@link
 * com.cmt.e2e.framework.runner.ScriptXmlBuilder#sanitize(String)}.
 */
public final class DumpSnapshot {

    private final Path outputBase;
    private final String scenarioName;

    public DumpSnapshot(Path outputBase, String scenarioName) {
        this.outputBase = outputBase;
        this.scenarioName = scenarioName;
    }

    public DumpSnapshot matchesSnapshot() {
        Path snapshotBase = CatalogSnapshot.SNAPSHOT_ROOT.resolve(scenarioName).resolve("dumpfile");

        if (Boolean.getBoolean(SnapshotStore.UPDATE_PROP)) {
            captureTree(outputBase, snapshotBase);
            return this;
        }
        diffTree(outputBase, snapshotBase);
        return this;
    }

    private static void captureTree(Path actual, Path snapshot) {
        if (!Files.isDirectory(actual)) {
            throw new AssertionError(
                    "Cannot capture dump snapshot: CMT output not found at " + actual);
        }
        try {
            if (Files.exists(snapshot)) {
                deleteRecursively(snapshot);
            }
            Files.createDirectories(snapshot);
            try (Stream<Path> walk = Files.walk(actual)) {
                List<Path> all = walk.toList();
                for (Path src : all) {
                    Path rel = actual.relativize(src);
                    Path dst = snapshot.resolve(rel);
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to capture dump snapshot at " + snapshot, e);
        }
    }

    private static void diffTree(Path actual, Path snapshot) {
        if (!Files.isDirectory(actual)) {
            throw new AssertionError("CMT did not produce a dump output directory: " + actual);
        }
        if (!Files.isDirectory(snapshot)) {
            throw new AssertionError(
                    "Dump snapshot missing: "
                            + snapshot
                            + "\n"
                            + "Run with -D"
                            + SnapshotStore.UPDATE_PROP
                            + "=true to capture.");
        }

        // Each file in actual must match its snapshot counterpart.
        List<Path> actualFiles = listFiles(actual);
        for (Path file : actualFiles) {
            Path rel = actual.relativize(file);
            Path snap = snapshot.resolve(rel);
            String content = readString(file);
            SnapshotStore.match(snap, content);
        }

        // Each file in snapshot must exist in actual.
        List<Path> snapshotFiles = listFiles(snapshot);
        List<String> missing = new ArrayList<>();
        for (Path snap : snapshotFiles) {
            Path rel = snapshot.relativize(snap);
            Path actualFile = actual.resolve(rel);
            if (!Files.exists(actualFile)) {
                missing.add(rel.toString());
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError(
                    "Expected dump files missing from CMT output:\n  - "
                            + String.join("\n  - ", missing));
        }
    }

    private static List<Path> listFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("walk failed: " + root, e);
        }
    }

    private static String readString(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("read failed: " + file, e);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    throw new RuntimeException("delete: " + p, e);
                                }
                            });
        }
    }
}
