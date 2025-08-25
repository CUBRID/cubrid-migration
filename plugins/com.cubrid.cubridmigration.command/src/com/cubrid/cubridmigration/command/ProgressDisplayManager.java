package com.cubrid.cubridmigration.command;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.event.MigrationFinishedEvent;
import java.io.PrintStream;
import java.util.Set;


public class ProgressDisplayManager {

    private final PrintStream outPrinter = System.out;
    private final Object printLock = new Object();
    private final int monitorMode;

    private volatile boolean isFirstOutput = true;
    private volatile int lastLineCount = 0;

    public ProgressDisplayManager(int monitorMode) {
        this.monitorMode = monitorMode;
    }

    public void printProgressIfChanged(MigrationProgressTracker progressTracker) {
        if (!progressTracker.hasChanges()) {
            return;
        }

        Set<String> currentChangedTables = progressTracker.getAndClearChangedTables();
        Set<String> currentProcessingTables = progressTracker.getProcessingTables();

        progressTracker.updatePreviousWorkUnitsForChangedTables(currentChangedTables);

        synchronized (printLock) {
            clearPreviousOutput();
            printOverallProgress(progressTracker);
            int outputCount = printTableProgress(progressTracker, currentProcessingTables);
            lastLineCount = 1 + outputCount;
            outPrinter.flush();
        }
    }

    private void clearPreviousOutput() {
        if (!isFirstOutput) {
            for (int i = 0; i < lastLineCount; i++) {
                outPrinter.print("\033[F");
            }
            outPrinter.print("\033[J");
        } else {
            isFirstOutput = false;
        }
    }

    private void printOverallProgress(MigrationProgressTracker progressTracker) {
        if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR) {
            long totalWork = progressTracker.getTotalRecordUnits();
            if (totalWork > 0) {
                long completedWork = progressTracker.getCompletedWorkUnits();
                long percent = (totalWork > 0) ? (completedWork * 100 / totalWork) : 100;
                percent = Math.max(percent, 1);

                outPrinter.printf(
                        "Record Migration Progress: %d%% [%d / %d records]\n",
                        percent, completedWork, totalWork);
            }
        }
    }

    private int printTableProgress(
            MigrationProgressTracker progressTracker, Set<String> currentProcessingTables) {
        int outputCount = 0;

        if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR) {
            for (String tableName : progressTracker.getTableOrder()) {
                if (!currentProcessingTables.contains(tableName)) continue;

                TableProgressData data = progressTracker.getTableProgressData(tableName);
                if (data == null) continue;

                long totalTableWork = data.getTotalRows();
                if (totalTableWork > 0) {
                    long completedTableWork = data.getCompletedWorkUnits();
                    int index = data.getIndex() + 1;
                    long tablePercent = data.getWorkPercent();

                    String output =
                            String.format(
                                    "%s(%d/%d) | %d / %d %d%%\n",
                                    tableName,
                                    index,
                                    progressTracker.getTableOrderSize(),
                                    completedTableWork,
                                    totalTableWork,
                                    tablePercent);
                    outPrinter.print(output);
                    outputCount++;
                }
            }
        }

        return outputCount;
    }

    public void printFinalProgress(
            MigrationProgressTracker progressTracker,
            boolean hasError,
            MigrationFinishedEvent finalEvent) {
        printProgressIfChanged(progressTracker);

        synchronized (printLock) {
            if (hasError) {
                outPrinter.println("Some errors occurred during migration.");
                outPrinter.println("Please see the report for more.");
            }
            if (finalEvent != null) {
                outPrinter.println(finalEvent.toString());
            }
        }
    }
}
