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

import com.cubrid.cubridmigration.core.engine.IMigrationMonitor;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.event.CreateObjectEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportCSVEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportSQLsEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationFinishedEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationStartEvent;
import com.cubrid.cubridmigration.cubrid.CUBRIDTimeUtil;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CommandMigrationMonitor Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class CmdMigrationMonitor implements IMigrationMonitor, Runnable {

    private static final long PROGRESS_UPDATE_INTERVAL_MS = 100;

    private final MigrationProgressTracker progressTracker;
    private final ProgressDisplayManager displayManager;
    private final int monitorMode;
    private final PrintStream outPrinter = System.out;

    private final AtomicBoolean hasError = new AtomicBoolean(false);
    private volatile MigrationFinishedEvent finalEvent = null;
    private volatile boolean isNewLine = true;

    private final Object startLock = new Object();
    private volatile boolean stopRequested = false;
    private Thread progressThread;

    public CmdMigrationMonitor(MigrationConfiguration config, int monitorMode) {
        this.progressTracker = new MigrationProgressTracker();
        this.displayManager = new ProgressDisplayManager(monitorMode);
        this.monitorMode = monitorMode;

        progressTracker.initialize(config);
    }

    @Override
    public void finished() {
        requestStop();
    }

    @Override
    public void start() {
        synchronized (startLock) {
            if (progressThread != null && progressThread.isAlive()) {
                return;
            }
            progressThread = new Thread(this, "MigrationProgressPrinter");
            progressThread.setDaemon(true);
            progressThread.start();
        }
    }

    public void addEvent(MigrationEvent event) {
        if (finalEvent != null) return;

        if (event instanceof MigrationStartEvent) {
            outPrinter.println(event.toString());
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            displayManager.printFinalProgress(progressTracker, hasError.get(), finalEvent);
            requestStop();
            return;
        }

        boolean isError = false;

        if (event instanceof CreateObjectEvent) {
            CreateObjectEvent ev = (CreateObjectEvent) event;
            if (ev.isSuccess()) {
            } else {
                isError = true;
            }
        } else if (event instanceof ImportRecordsEvent) {
            ImportRecordsEvent ev = (ImportRecordsEvent) event;
            if (ev.isSuccess()) {
                progressTracker.addCompletedWorkUnits(ev.getRecordCount());
                progressTracker.updateTableProgress(
                        ev.getSourceTable().getOwner() + "." + ev.getSourceTable().getName(),
                        ev.getRecordCount());
            } else {
                isError = true;
            }
        } else if (event instanceof ImportSQLsEvent) {
            ImportSQLsEvent ev = (ImportSQLsEvent) event;
            progressTracker.addCompletedWorkUnits(ev.getSize());
            if (!ev.isSuccess()) isError = true;
        } else if (event instanceof ImportCSVEvent) {
            ImportCSVEvent ev = (ImportCSVEvent) event;
            progressTracker.addCompletedWorkUnits(ev.getSize());
            if (!ev.isSuccess()) isError = true;
        }

        if (isError) {
            hasError.set(true);
        }

        logEventIfNeeded(event);
    }

    public void requestStop() {
        stopRequested = true;
        if (progressThread != null) {
            progressThread.interrupt();
        }
    }

    @Override
    public void run() {
        while (!stopRequested) {
            displayManager.printProgressIfChanged(progressTracker);
            isNewLine = false;

            try {
                Thread.sleep(PROGRESS_UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
        displayManager.printProgressIfChanged(progressTracker);
        isNewLine = false;
    }

    private void logEventIfNeeded(MigrationEvent event) {
        if (event.getLevel() <= monitorMode) {
            synchronized (outPrinter) {
                if (!isNewLine) {
                    outPrinter.println();
                }
                outPrinter.println(
                        CUBRIDTimeUtil.defaultFormatMilin(event.getEventTime())
                                + " "
                                + event.toString());
                isNewLine = true;
            }
        }
    }
}
