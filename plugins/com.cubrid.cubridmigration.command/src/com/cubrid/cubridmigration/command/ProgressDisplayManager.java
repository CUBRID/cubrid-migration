/*
 * Copyright (C) 2008 Search Solution Corporation.
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

import com.cubrid.cubridmigration.core.engine.event.MigrationEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationFinishedEvent;
import java.io.PrintStream;
import java.util.Set;

/**
 * Manages console output and progress display for migration
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class ProgressDisplayManager {

    private final PrintStream outPrinter = System.out;
    private final Object printLock = new Object();

    private volatile boolean isFirstOutput = true;
    private volatile int lastLineCount = 0;

    private final StringBuilder outputBuffer = new StringBuilder(256);

    public void printStartEvent(MigrationEvent event) {
        synchronized (printLock) {
            outPrinter.println(event.toString());
        }
    }

    public void printProgressIfChanged(MigrationProgressTracker progressTracker) {
        if (!progressTracker.hasChanges()) {
            return;
        }

        Set<String> currentChangedTables = progressTracker.getAndClearChangedTables();
        Set<String> currentProcessingTables = progressTracker.getCachedProcessingTables();

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
        long totalWork = progressTracker.getTotalWorkUnits();
        long completedWork = progressTracker.getCompletedWorkUnits();
        long percent = (totalWork > 0) ? (completedWork * 100 / totalWork) : 100;
        percent = Math.max(percent, 1);

        outPrinter.printf(
                "Migration Progress: %d%% [%,d / %,d]\n", percent, completedWork, totalWork);
    }

    private int printTableProgress(
            MigrationProgressTracker progressTracker, Set<String> currentProcessingTables) {
        int outputCount = 0;

        for (String tableName : progressTracker.getTableOrder()) {
            if (!currentProcessingTables.contains(tableName)) continue;

            TableProgressData data = progressTracker.getTableProgressData(tableName);
            if (data == null) continue;

            long totalTableWork = data.getTotalWorkUnits();
            long completedTableWork = data.getCompletedWorkUnits();
            int index = data.getIndex() + 1;
            long tablePercent = data.getWorkPercent();

            outputBuffer.setLength(0);
            outputBuffer
                    .append(tableName)
                    .append('(')
                    .append(index)
                    .append('/')
                    .append(progressTracker.getTableOrderSize())
                    .append(") | ")
                    .append(completedTableWork)
                    .append(' ')
                    .append('/')
                    .append(' ')
                    .append(totalTableWork)
                    .append(' ')
                    .append(tablePercent)
                    .append("%\n");

            outPrinter.print(outputBuffer.toString());
            outputCount++;
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
