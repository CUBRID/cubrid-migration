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

package com.cmt.e2e.framework.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Cleans the CMT Console working directory ({@code workspace/} and {@code output/}) between tests.
 * Nothing happens in beforeEach; cleanup runs only in afterEach.
 */
public class WorkspaceCleaner {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceCleaner.class);

    private final Path cmtConsoleDir;
    private final Path workspaceReportDir;

    public WorkspaceCleaner(File cmtConsoleWorkDir) {
        this.cmtConsoleDir = cmtConsoleWorkDir.toPath();
        this.workspaceReportDir = this.cmtConsoleDir.resolve("workspace/cmt/report");
    }

    public void cleanupWorkspace() throws IOException {
        if (!Files.exists(workspaceReportDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspaceReportDir)) {
            walk.filter(path -> !path.equals(workspaceReportDir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(
                            f -> {
                                if (!f.delete()) {
                                    log.warn(
                                            "Failed to delete workspace file: {}",
                                            f.getAbsolutePath());
                                }
                            });
        }
    }

    /** Cleans the {@code output/} directory created by CMT for dump migrations. */
    public void cleanupOutput() throws IOException {
        Path outputDir = cmtConsoleDir.resolve("output");
        if (!Files.exists(outputDir)) {
            return;
        }
        log.debug("Cleaning up migration output directory: {}", outputDir);
        try (Stream<Path> walk = Files.walk(outputDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(
                            f -> {
                                if (!f.delete()) {
                                    log.warn(
                                            "Failed to delete output file: {}",
                                            f.getAbsolutePath());
                                }
                            });
        }
    }
}
