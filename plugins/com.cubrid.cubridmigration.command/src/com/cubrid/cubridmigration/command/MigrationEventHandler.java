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

import com.cubrid.cubridmigration.core.engine.event.CreateObjectEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportCSVEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportSQLsEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationFinishedEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationStartEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles migration events and updates progress accordingly
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class MigrationEventHandler {

    private final MigrationProgressTracker progressTracker;
    private final ProgressDisplayManager displayManager;
    private final AtomicBoolean hasError = new AtomicBoolean(false);

    private volatile MigrationFinishedEvent finalEvent = null;

    public MigrationEventHandler(
            MigrationProgressTracker progressTracker, ProgressDisplayManager displayManager) {
        this.progressTracker = progressTracker;
        this.displayManager = displayManager;
    }

    public void handleEvent(MigrationEvent event) {
        if (finalEvent != null) return;

        if (event instanceof MigrationStartEvent) {
            displayManager.printStartEvent(event);
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            return;
        }

        boolean isError = false;

        if (event instanceof CreateObjectEvent) {
            CreateObjectEvent ev = (CreateObjectEvent) event;
            if (ev.isSuccess()) {
                progressTracker.incrementCompletedWorkUnits();
            } else {
                isError = true;
            }
        } else if (event instanceof ImportRecordsEvent) {
            ImportRecordsEvent ev = (ImportRecordsEvent) event;
            if (ev.isSuccess()) {
                progressTracker.addCompletedWorkUnits(ev.getRecordCount());
                progressTracker.updateTableProgress(
                        ev.getSourceTable().getName(), ev.getRecordCount());
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
    }

    public boolean hasError() {
        return hasError.get();
    }

    public MigrationFinishedEvent getFinalEvent() {
        return finalEvent;
    }

    public boolean isFinished() {
        return finalEvent != null;
    }
}
