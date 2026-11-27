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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.core.engine.task.exp;

import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.RecordExportedListener;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ExportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.StartExpTableEvent;
import com.cubrid.cubridmigration.core.engine.task.ExportTask;

import java.util.List;

/**
 * JDBCExportRecordTask Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-8 created by Kevin Cao
 */
public class XMLRecordExportTask extends ExportTask {

    protected final MigrationContext mrManager;

    public XMLRecordExportTask(MigrationContext mrManager) {
        this.mrManager = mrManager;
    }

    /** Execute export operation */
    protected void executeExportTask() {
        exporter.exportAllRecords(
                new RecordExportedListener() {

                    @Override
                    public void processRecords(SourceEntryTableConfig stc, List<Record> records) {
                        if (stc == null || !stc.isMigrateData()) {
                            return;
                        }
                        mrManager
                                .getStatusMgr()
                                .addExpCount(stc.getOwner(), stc.getName(), records.size());
                        eventHandler.handleEvent(new ExportRecordsEvent(stc, records.size()));
                        importTaskExecutor = mrManager.getImportRecordExecutor();
                        importTaskExecutor.execute(
                                (Runnable) taskFactory.createImportRecordsTask(stc, records));
                    }

                    @Override
                    public void startExportTable(SourceEntryTableConfig stc) {
                        eventHandler.handleEvent(new StartExpTableEvent(stc));
                    }

                    @Override
                    public void endExportTable(SourceEntryTableConfig stc) {
                        mrManager.getStatusMgr().setExpFinished(stc.getOwner(), stc.getName());
                    }

                    @Override
                    public void processRecords(String sourceTableName, List<Record> records) {
                        processRecords(
                                mrManager.getConfig().getExpEntryTableCfg(null, sourceTableName),
                                records);
                    }

                    @Override
                    public void startExportTable(String tableName) {
                        startExportTable(
                                mrManager.getConfig().getExpEntryTableCfg(null, tableName));
                    }

                    @Override
                    public void endExportTable(String tableName) {
                        mrManager.getStatusMgr().setExpFinished("", tableName);
                    }
                });
    }
}
