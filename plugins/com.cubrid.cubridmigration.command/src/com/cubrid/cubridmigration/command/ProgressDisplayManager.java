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

        Set<String> currentProcessingTables = progressTracker.getProcessingTables();

        synchronized (printLock) {
            clearPreviousOutput();

            if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR) {
                printOverallProgress(progressTracker);
                int outputCount = printTableProgress(progressTracker, currentProcessingTables);
                lastLineCount = 1 + outputCount;
            }

            outPrinter.flush();

            progressTracker.resetChanges();
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

    private int printTableProgress(
            MigrationProgressTracker progressTracker, Set<String> currentProcessingTables) {
        int outputCount = 0;

        for (String ownerTableName : progressTracker.getTableOrder()) {
            if (!currentProcessingTables.contains(ownerTableName)) continue;

            TableProgressData data = progressTracker.getTableProgressData(ownerTableName);
            if (data == null) continue;

            long totalTableWork = data.getTotalRows();
            if (totalTableWork > 0) {
                long completedTableWork = data.getCompletedWorkUnits();
                int index = data.getIndex() + 1;
                long tablePercent = data.getWorkPercent();

                String output =
                        String.format(
                                "%s(%d/%d) | %d / %d %d%%\n",
                                ownerTableName,
                                index,
                                progressTracker.getTableOrderSize(),
                                completedTableWork,
                                totalTableWork,
                                tablePercent);
                outPrinter.print(output);
                outputCount++;
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
