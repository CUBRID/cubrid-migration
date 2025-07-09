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

import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.IMigrationMonitor;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.event.CreateObjectEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportCSVEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportSQLsEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationFinishedEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationStartEvent;
import com.cubrid.cubridmigration.cubrid.CUBRIDTimeUtil;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CommandMigrationMonitor Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class CmdMigrationMonitor implements IMigrationMonitor {
    private long totalProgress = 0;
    private long currentProgress = 0;
    private long progress = 0;
    private MigrationFinishedEvent finalEvent = null;
    // private int circle = 0;
    private boolean hasError;
    private final int monitorMode;
    private PrintStream outPrinter = System.out;
    private final Map<String, Long> tableTotalRows = new LinkedHashMap<>();
    private final Map<String, Long> tableCurrentRows = new LinkedHashMap<>();
    private final List<String> tableOrder = new ArrayList<>();
    private boolean tablesInitialized = false;
    private long lastPrintedPercent = -1;
    private long[] lastPrintedTableProgress;

    public CmdMigrationMonitor(MigrationConfiguration config, int monitorMode) {
        if (config.sourceIsOnline() || config.sourceIsXMLDump()) {
            List<SourceEntryTableConfig> tables = config.getExpEntryTableCfg();
            for (SourceEntryTableConfig tbl : tables) {
                Table table = config.getSrcTableSchema(tbl.getOwner(), tbl.getName());
                if (tbl.isCreatePK() && table.getPk() != null) {
                    totalProgress++;
                }
                totalProgress = totalProgress + table.getTableRowCount();
            }
            List<SourceSQLTableConfig> sqlTables = config.getExpSQLCfg();
            for (SourceSQLTableConfig tbl : sqlTables) {
                Table table = config.getSrcTableSchema(tbl.getOwner(), tbl.getName());
                totalProgress = totalProgress + (table == null ? 0 : table.getTableRowCount());
            }
            totalProgress = totalProgress + config.getExpObjCount();
        } else if (config.sourceIsSQL()) {
            for (String ss : config.getSqlFiles()) {
                totalProgress = totalProgress + new File(ss).length();
            }
        } else if (config.sourceIsCSV()) {
            for (SourceCSVConfig scc : config.getCSVConfigs()) {
                totalProgress = totalProgress + new File(scc.getName()).length();
            }
        }
        this.monitorMode = monitorMode;
        hasError = false;
    }

    /** Print finished message. */
    public void finished() {}

    /** Print started message. */
    public void start() {

        for (int i = 0; i < tableOrder.size() + 1; i++) {
            outPrinter.println();
        }

        tablesInitialized = true;
    }

    private void printLiveProgressBlock() {
        if (tableOrder.isEmpty()) return;

        long totalRecords = 0;
        long currentRecords = 0;
        for (String tableName : tableOrder) {
            totalRecords += tableTotalRows.getOrDefault(tableName, 0L);
            currentRecords += tableCurrentRows.getOrDefault(tableName, 0L);
        }

        long percent = (totalRecords > 0) ? (currentRecords * 100 / totalRecords) : 100;
        percent = Math.max(percent, 1);

        outPrinter.print("\033[" + (tableOrder.size() + 1) + "A");
        outPrinter.print(
                "\r\033[KProgress: "
                        + percent
                        + "% ["
                        + currentRecords
                        + " / "
                        + totalRecords
                        + "]\n");

        if (percent != lastPrintedPercent) {
            outPrinter.print("\033[" + (tableOrder.size() + 1) + "A");
            outPrinter.print(
                    "\r\033[KProgress: "
                            + percent
                            + "% ["
                            + currentRecords
                            + " / "
                            + totalRecords
                            + "]\n");
            lastPrintedPercent = percent;
        }

        for (int i = 0; i < tableOrder.size(); i++) {
            String tableName = tableOrder.get(i);
            Long totalRows = tableTotalRows.get(tableName);
            Long currentRows = tableCurrentRows.get(tableName);
            long tableProgress = (totalRows > 0) ? (currentRows * 100 / totalRows) : 0;

            if (tableProgress != lastPrintedTableProgress[i]) {
                outPrinter.print("\r\033[K");
                outPrinter.println(
                        tableName
                                + " ("
                                + (i + 1)
                                + "/"
                                + tableOrder.size()
                                + "): "
                                + tableProgress
                                + "% ["
                                + currentRows
                                + " / "
                                + totalRows
                                + "]");
                lastPrintedTableProgress[i] = tableProgress;
            }
        }
    }

    /**
     * Print event message.
     *
     * @param event MigrationEvent
     */
    public void addEvent(MigrationEvent event) {
        if (finalEvent != null) {
            return;
        }

        if (event instanceof MigrationStartEvent) {
            outPrinter.println(event.toString());
            start();
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            printLiveProgressBlock();
            outPrinter.print("\rProgress:100%");
            outPrinter.println();
            if (hasError) {
                outPrinter.println("Some errors occurred during migration.");
                outPrinter.println("Please see the report for more.");
            }
            outPrinter.println(event.toString());
            return;
        }

        boolean isError = false;
        boolean progressUpdated = false;
        if (event instanceof CreateObjectEvent) {
            CreateObjectEvent ev = (CreateObjectEvent) event;
            if (ev.isSuccess()) {
                currentProgress++;
            } else {
                isError = true;
            }
        } else if (event instanceof ImportRecordsEvent) {
            final ImportRecordsEvent importRecordsEvent = (ImportRecordsEvent) event;
            if (importRecordsEvent.isSuccess()) {
                currentProgress = currentProgress + importRecordsEvent.getRecordCount();
                String tblName = importRecordsEvent.getSourceTable().getName();
                if (tableCurrentRows.containsKey(tblName)) {
                    long current = tableCurrentRows.get(tblName);
                    tableCurrentRows.put(tblName, current + importRecordsEvent.getRecordCount());
                    progressUpdated = true;
                }
            } else {
                isError = true;
            }
        } else if (event instanceof ImportSQLsEvent) {
            ImportSQLsEvent ev = (ImportSQLsEvent) event;
            currentProgress = currentProgress + ev.getSize();
            if (!ev.isSuccess()) {
                isError = true;
            }
        } else if (event instanceof ImportCSVEvent) {
            ImportCSVEvent ev = (ImportCSVEvent) event;
            currentProgress = currentProgress + ev.getSize();
            if (!ev.isSuccess()) {
                isError = true;
            }
        }
        hasError = isError;
        boolean isNewLine = false;
        if (event.getLevel() <= monitorMode) {
            outPrinter.println(
                    CUBRIDTimeUtil.defaultFormatMilin(event.getEventTime())
                            + " "
                            + event.toString());
            isNewLine = true;
        }
        if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR && (totalProgress > 0)) {
            // print progress
            long tmpPro = currentProgress * 100 / totalProgress;
            tmpPro = tmpPro == 0 ? 1 : tmpPro;
            if (tmpPro != progress) {
                progress = tmpPro;
                progressUpdated = true;
            }
        }
        if (progressUpdated) {
            printLiveProgressBlock();
        }
    }
}
