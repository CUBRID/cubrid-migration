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
    private long totalWorkUnits = 0;
    private long completedWorkUnits = 0;
    private long lastPrintedProgressPercent = 0;
    private MigrationFinishedEvent finalEvent = null;
    private boolean hasError;
    private final int monitorMode;
    private PrintStream outPrinter = System.out;
    private final Map<String, Long> tableTotalRows = new LinkedHashMap<>();
    private final Map<String, Long> tableCurrentRows = new LinkedHashMap<>();
    private final Map<String, Long> tablePreviousRows = new LinkedHashMap<>();
    private final List<String> tableOrder = new ArrayList<>();
    private boolean tablesInitialized = false;
    private static final String CONSOLE_CURSOR_UP_FORMAT = "\033[%dA";
    private static final String CLEAR_LINE = "\r\033[K";

    public CmdMigrationMonitor(MigrationConfiguration config, int monitorMode) {
        this.monitorMode = monitorMode;
        hasError = false;

        if (config.sourceIsOnline() || config.sourceIsXMLDump()) {
            for (SourceEntryTableConfig tbl : config.getExpEntryTableCfg()) {
                Table table = config.getSrcTableSchema(tbl.getOwner(), tbl.getName());
                if (tbl.isCreatePK() && table.getPk() != null) {
                    totalWorkUnits++;
                }
                long rowCount = table.getTableRowCount();
                totalWorkUnits += rowCount;

                String name = tbl.getName();
                tableOrder.add(name);
                tableTotalRows.put(name, rowCount);
                tableCurrentRows.put(name, 0L);
                tablePreviousRows.put(name, -1L);
            }

            for (SourceSQLTableConfig tbl : config.getExpSQLCfg()) {
                Table table = config.getSrcTableSchema(tbl.getOwner(), tbl.getName());
                long rowCount = table == null ? 0 : table.getTableRowCount();
                totalWorkUnits += rowCount;

                String name = tbl.getName();
                tableOrder.add(name);
                tableTotalRows.put(name, rowCount);
                tableCurrentRows.put(name, 0L);
                tablePreviousRows.put(name, -1L);
            }

            totalWorkUnits += config.getExpObjCount();
        } else if (config.sourceIsSQL()) {
            for (String ss : config.getSqlFiles()) {
                totalWorkUnits += new File(ss).length();
            }
        } else if (config.sourceIsCSV()) {
            for (SourceCSVConfig scc : config.getCSVConfigs()) {
                totalWorkUnits += new File(scc.getName()).length();
            }
        }
    }

    /** Print finished message. */
    public void finished() {}

    /** Print started message. */
    public void start() {}

    public void prepareTableProgressOutput() {
        if (tablesInitialized || tableOrder.isEmpty()) {
            return;
        }

        for (int i = 0; i < tableOrder.size() + 1; i++) {
            outPrinter.println();
        }

        tablesInitialized = true;
    }

    private void printSelectiveProgressUpdate() {
        if (tableOrder.isEmpty()) return;

        long totalRecords = 0;
        long currentRecords = 0;
        for (String tableName : tableOrder) {
            totalRecords += tableTotalRows.getOrDefault(tableName, 0L);
            currentRecords += tableCurrentRows.getOrDefault(tableName, 0L);
        }

        long percent = (totalRecords > 0) ? (currentRecords * 100 / totalRecords) : 100;
        percent = Math.max(percent, 1);

        outPrinter.print(String.format(CONSOLE_CURSOR_UP_FORMAT, tableOrder.size() + 1));
        outPrinter.print(
                String.format(
                        "%sProgress: %d%% [%d / %d]%n",
                        CLEAR_LINE, percent, currentRecords, totalRecords));

        for (int i = 0; i < tableOrder.size(); i++) {
            String tableName = tableOrder.get(i);
            Long totalRows = tableTotalRows.get(tableName);
            Long currentRows = tableCurrentRows.get(tableName);
            Long previousRows = tablePreviousRows.get(tableName);
            
            if (!currentRows.equals(previousRows)) {
                outPrinter.print(CLEAR_LINE);
                long tableProgress = (totalRows > 0) ? (currentRows * 100 / totalRows) : 0;
                outPrinter.println(String.format(
                    "%s (%d/%d): %d%% [%d / %d]",
                    tableName, i + 1, tableOrder.size(),
                    tableProgress, currentRows, totalRows));
                tablePreviousRows.put(tableName, currentRows);
            } else {
                // 줄은 유지하되 출력은 생략
                outPrinter.print("\033[1B"); 
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
            prepareTableProgressOutput();
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            printSelectiveProgressUpdate();
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
                completedWorkUnits++;
            } else {
                isError = true;
            }
        } else if (event instanceof ImportRecordsEvent) {
            final ImportRecordsEvent importRecordsEvent = (ImportRecordsEvent) event;
            if (importRecordsEvent.isSuccess()) {
                completedWorkUnits = completedWorkUnits + importRecordsEvent.getRecordCount();
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
            completedWorkUnits = completedWorkUnits + ev.getSize();
            if (!ev.isSuccess()) {
                isError = true;
            }
        } else if (event instanceof ImportCSVEvent) {
            ImportCSVEvent ev = (ImportCSVEvent) event;
            completedWorkUnits = completedWorkUnits + ev.getSize();
            if (!ev.isSuccess()) {
                isError = true;
            }
        }
        hasError = isError;
        if (event.getLevel() <= monitorMode) {
            outPrinter.println(
                    CUBRIDTimeUtil.defaultFormatMilin(event.getEventTime())
                            + " "
                            + event.toString());
        }
        if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR && (totalWorkUnits > 0)) {
            long tmpPro = completedWorkUnits * 100 / totalWorkUnits;
            tmpPro = tmpPro == 0 ? 1 : tmpPro;
            if (tmpPro != lastPrintedProgressPercent) {
                lastPrintedProgressPercent = tmpPro;
                progressUpdated = true;
            }
        }
        if (progressUpdated) {
        	printSelectiveProgressUpdate();
        }
    }
}
