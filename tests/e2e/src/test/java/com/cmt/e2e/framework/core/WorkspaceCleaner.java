package com.cmt.e2e.framework.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cleans the CMT Console working directory ({@code workspace/} and
 * {@code output/}) between tests. Nothing happens in beforeEach; cleanup
 * runs only in afterEach.
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
                .forEach(f -> {
                    if (!f.delete()) {
                        log.warn("Failed to delete workspace file: {}", f.getAbsolutePath());
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
                .forEach(f -> {
                    if (!f.delete()) {
                        log.warn("Failed to delete output file: {}", f.getAbsolutePath());
                    }
                });
        }
    }
}
