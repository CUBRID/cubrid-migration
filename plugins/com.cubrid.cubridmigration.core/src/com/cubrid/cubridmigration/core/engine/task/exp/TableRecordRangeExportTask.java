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
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ExportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.StartExpTableEvent;
import com.cubrid.cubridmigration.core.engine.exporter.IMigrationExporter;
import com.cubrid.cubridmigration.core.engine.exporter.impl.JDBCExporter;
import com.cubrid.cubridmigration.core.engine.task.ITableKeyedTask;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

import java.util.List;

/**
 * One parallel range worker for a table {@code TableSplitPlanner} decided to split: reads only the
 * rows matching {@link #rangeCondition} instead of the whole table.
 *
 * <p>This is a separate class rather than a modification of {@link TableRecordExportTask} so the
 * existing, far more common one-task-per-table path is untouched and carries zero risk from this
 * feature. Multiple instances of this task (one per range) are scheduled for the same table; the
 * scheduler is responsible for calling {@code MigrationStatusManager#registerExportRangeCount} with
 * the true number of ranges *before* dispatching any of them, so that the table is only reported
 * done (see {@code endExportTable} below) once every range has finished - see
 * MigrationStatusManager's javadoc for why a plain per-task flag would be wrong here.
 *
 * <p>Requires the configured exporter to be a {@link JDBCExporter} (or subclass), since the range
 * predicate is applied via {@link JDBCExporter#exportTableRecords(SourceTableConfig, String,
 * RecordExportedListener)}. Range-based export is not currently wired for {@code
 * MYSQLDumpXMLExporter}-based (offline dump) sources.
 *
 * @author Kevin Cao
 */
public class TableRecordRangeExportTask extends TableRecordExportTask {

    private final String rangeCondition;

    /**
     * @param mrManager migration context
     * @param table the (unsplit) table config; the range restriction is applied on top of it, not
     *     by cloning it
     * @param rangeCondition a standalone boolean SQL predicate for this range (e.g. from {@code
     *     TableSplitPlanner#buildRangeCondition}), ANDed onto the table's own condition
     */
    public TableRecordRangeExportTask(
            MigrationContext mrManager, SourceTableConfig table, String rangeCondition) {
        super(mrManager, table);
        this.rangeCondition = rangeCondition;
    }

    /** Export this range of the source table's records */
    @Override
    protected void executeExportTask() {
        IMigrationExporter exp = exporter;
        if (!(exp instanceof JDBCExporter)) {
            throw new IllegalStateException(
                    "Range-based export requires a JDBCExporter, but got: "
                            + (exp == null ? "null" : exp.getClass().getName()));
        }

        ((JDBCExporter) exp)
                .exportTableRecords(
                        sourceTable,
                        rangeCondition,
                        new RecordExportedListener() {
                            public void processRecords(
                                    String sourceTableName, List<Record> records) {
                                eventHandler.handleEvent(
                                        new ExportRecordsEvent(sourceTable, records.size()));
                                ImportTask task =
                                        taskFactory.createImportRecordsTask(sourceTable, records);

                                importTaskExecutor = mrManager.getImportRecordExecutor();
                                importTaskExecutor.execute((Runnable) task);
                                mrManager
                                        .getStatusMgr()
                                        .addExpCount(
                                                sourceTable.getOwner(),
                                                sourceTable.getName(),
                                                records.size());
                            }

                            public void startExportTable(String tableName) {
                                eventHandler.handleEvent(new StartExpTableEvent(sourceTable));
                            }

                            public void endExportTable(String tableName) {
                                mrManager
                                        .getStatusMgr()
                                        .setExpFinished(
                                                sourceTable.getOwner(), sourceTable.getName());
                                if (mrManager
                                        .getStatusMgr()
                                        .getExpFlag(
                                                sourceTable.getOwner(), sourceTable.getName())) {
                                    mrManager
                                            .getImportRecordExecutor()
                                            .releaseKey(
                                                    ITableKeyedTask.tableKey(
                                                            sourceTable.getOwner(),
                                                            sourceTable.getName()));
                                }
                            }
                        });
    }
}
