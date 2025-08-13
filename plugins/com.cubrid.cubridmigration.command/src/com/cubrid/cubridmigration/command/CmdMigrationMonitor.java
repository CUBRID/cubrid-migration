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
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CommandMigrationMonitor Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class CmdMigrationMonitor implements IMigrationMonitor {
    private final AtomicLong totalWorkUnits = new AtomicLong(0);
    private final AtomicLong completedWorkUnits = new AtomicLong(0);
    private final AtomicLong lastPrintedProgressPercent = new AtomicLong(0);
    private volatile MigrationFinishedEvent finalEvent = null;
    private final AtomicBoolean hasError = new AtomicBoolean(false);
    private final int monitorMode;
    private PrintStream outPrinter = System.out;

    private final Map<String, Long> tableTotalRows = new ConcurrentHashMap<>();
    private final Map<String, Long> tableCurrentRows = new ConcurrentHashMap<>();
    private final Map<String, Long> tablePreviousRows = new ConcurrentHashMap<>();
    private final Map<String, Long> tableTotalWorkUnits = new ConcurrentHashMap<>();
    private final Map<String, Long> tableCompletedWorkUnits = new ConcurrentHashMap<>();
    private final Map<String, Long> tablePreviousWorkUnits = new ConcurrentHashMap<>();
    private final List<String> tableOrder = new ArrayList<>();
    private final Map<String, Integer> tableIndexMap = new ConcurrentHashMap<>();

    public CmdMigrationMonitor(MigrationConfiguration config, int monitorMode) {
        this.monitorMode = monitorMode;
        initializeMonitor(config);
    }

    private void initializeMonitor(MigrationConfiguration config) {
        if (config.sourceIsOnline() || config.sourceIsXMLDump()) {
            processSourceTables(config.getExpEntryTableCfg(), true, config);
            processSourceTables(config.getExpSQLCfg(), false, config);
            totalWorkUnits.addAndGet(config.getExpObjCount());
        } else if (config.sourceIsSQL()) {
            for (String ss : config.getSqlFiles()) {
                totalWorkUnits.addAndGet(new File(ss).length());
            }
        } else if (config.sourceIsCSV()) {
            for (SourceCSVConfig scc : config.getCSVConfigs()) {
                totalWorkUnits.addAndGet(new File(scc.getName()).length());
            }
        }
    }

    private void processSourceTables(
            Collection<?> tables, boolean isEntryTable, MigrationConfiguration config) {
        for (Object obj : tables) {
            String tableName;
            String owner = null;
            boolean createPK = false;

            if (isEntryTable) {
                SourceEntryTableConfig tbl = (SourceEntryTableConfig) obj;
                tableName = tbl.getName();
                owner = tbl.getOwner();
                createPK = tbl.isCreatePK();
            } else {
                SourceSQLTableConfig tbl = (SourceSQLTableConfig) obj;
                tableName = tbl.getName();
                owner = tbl.getOwner();
            }

            Table table = config.getSrcTableSchema(owner, tableName);
            long rowCount = (table == null) ? 0L : table.getTableRowCount();

            addTotalWorkUnits(isEntryTable, createPK, table, rowCount);

            int index = tableOrder.size();
            tableOrder.add(tableName);
            tableIndexMap.put(tableName, index);

            initializeTableProgress(tableName, rowCount);

            long tableWorkUnits = calculateTableWorkUnits(isEntryTable, createPK, table, rowCount);
            tableTotalWorkUnits.put(tableName, tableWorkUnits);
            tableCompletedWorkUnits.put(tableName, 0L);
            tablePreviousWorkUnits.put(tableName, -1L);
        }
    }

    private void addTotalWorkUnits(
            boolean isEntryTable, boolean createPK, Table table, long rowCount) {
        if (isEntryTable && createPK && table != null && table.getPk() != null) {
            totalWorkUnits.incrementAndGet();
        }
        totalWorkUnits.addAndGet(rowCount);
    }

    private void initializeTableProgress(String tableName, long rowCount) {
        tableTotalRows.put(tableName, rowCount);
        tableCurrentRows.put(tableName, 0L);
        tablePreviousRows.put(tableName, -1L);
    }

    private long calculateTableWorkUnits(
            boolean isEntryTable, boolean createPK, Table table, long rowCount) {
        long workUnits = rowCount;
        if (isEntryTable && createPK && table != null && table.getPk() != null) {
            workUnits += 1;
        }
        return workUnits;
    }

    /** Print finished message. */
    public void finished() {}

    /** Print started message. */
    public void start() {}

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
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            outPrinter.print("\rProgress:100%");
            outPrinter.println();
            if (hasError.get()) {
                outPrinter.println("Some errors occurred during migration.");
                outPrinter.println("Please see the report for more.");
            }
            outPrinter.println(event.toString());
            return;
        }

        boolean isError = false;
        if (event instanceof CreateObjectEvent) {
            CreateObjectEvent ev = (CreateObjectEvent) event;
            if (ev.isSuccess()) {
                completedWorkUnits.incrementAndGet();
            } else {
                isError = true;
            }
        } else if (event instanceof ImportRecordsEvent) {
            final ImportRecordsEvent importRecordsEvent = (ImportRecordsEvent) event;
            if (importRecordsEvent.isSuccess()) {
                completedWorkUnits.addAndGet(importRecordsEvent.getRecordCount());
            } else {
                isError = true;
            }
        } else if (event instanceof ImportSQLsEvent) {
            ImportSQLsEvent ev = (ImportSQLsEvent) event;
            completedWorkUnits.addAndGet(ev.getSize());
            if (!ev.isSuccess()) {
                isError = true;
            }
        } else if (event instanceof ImportCSVEvent) {
            ImportCSVEvent ev = (ImportCSVEvent) event;
            completedWorkUnits.addAndGet(ev.getSize());
            if (!ev.isSuccess()) {
                isError = true;
            }
        }

        if (isError) {
            hasError.set(true);
        }

        boolean isNewLine = false;
        if (event.getLevel() <= monitorMode) {
            outPrinter.println(event.toString());
            isNewLine = true;
        }

        if (monitorMode <= MigrationConfiguration.RPT_LEVEL_ERROR && (totalWorkUnits.get() > 0)) {
            long tmpPro = completedWorkUnits.get() * 100 / totalWorkUnits.get();
            tmpPro = tmpPro == 0 ? 1 : tmpPro;
            lastPrintedProgressPercent.set(tmpPro);
            if (!isNewLine) {
                outPrinter.print('\r');
            }
            outPrinter.print("Progress:" + tmpPro + "%");
        }
    }
}
