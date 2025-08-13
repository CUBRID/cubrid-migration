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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CommandMigrationMonitor Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class CmdMigrationMonitor implements IMigrationMonitor, Runnable {
    private final AtomicLong totalWorkUnits = new AtomicLong(0);
    private final AtomicLong completedWorkUnits = new AtomicLong(0);

    private final AtomicBoolean processingTablesChanged = new AtomicBoolean(false);
    private final AtomicBoolean hasError = new AtomicBoolean(false);

    private volatile MigrationFinishedEvent finalEvent = null;
    private volatile boolean stopRequested = false;

    private final Object printLock = new Object();
    private final Object startLock = new Object();
    private final Map<String, Long> tableTotalRows = new ConcurrentHashMap<>();
    private final Map<String, Long> tableCurrentRows = new ConcurrentHashMap<>();
    private final Map<String, Long> tablePreviousRows = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<TableStatus>> tableStatus = new ConcurrentHashMap<>();
    private final Set<String> processingTables = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> tableOrder = new ConcurrentLinkedQueue<>();

    private final Map<String, Long> tableTotalWorkUnits = new ConcurrentHashMap<>();
    private final Map<String, Long> tableCompletedWorkUnits = new ConcurrentHashMap<>();
    private final Map<String, Long> tablePreviousWorkUnits = new ConcurrentHashMap<>();

    private final Map<String, Integer> tableIndexMap = new ConcurrentHashMap<>();

    private final Set<String> changedTables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stateChanged = new AtomicBoolean(false);
    private final Set<String> cachedProcessingTables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cacheValid = new AtomicBoolean(false);

    // 🔥 ConcurrentLinkedQueue.size() 최적화 - O(1) 성능
    private final AtomicInteger tableOrderSize = new AtomicInteger(0);

    private final int monitorMode;
    private final PrintStream outPrinter = System.out;

    private final AtomicBoolean firstProgressOutput = new AtomicBoolean(true);
    private final AtomicInteger lastProgressLineCount = new AtomicInteger(0);

    private static final long PROGRESS_UPDATE_INTERVAL_MS = 100;

    private Thread progressThread;

    public enum TableStatus {
        PENDING,
        PROCESSING,
        COMPLETED
    }

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

            int index = tableOrderSize.get();
            tableOrderSize.incrementAndGet();
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
        tableStatus.put(tableName, new AtomicReference<>(TableStatus.PENDING));
    }

    private long calculateTableWorkUnits(
            boolean isEntryTable, boolean createPK, Table table, long rowCount) {
        long workUnits = rowCount;
        if (isEntryTable && createPK && table != null && table.getPk() != null) {
            workUnits += 1;
        }
        return workUnits;
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

    public void updateTableProgress(String tableName, long increment) {
        long newCurrent = tableCurrentRows.merge(tableName, increment, Long::sum);
        long total = tableTotalRows.getOrDefault(tableName, 0L);

        tableCompletedWorkUnits.merge(tableName, increment, Long::sum);

        changedTables.add(tableName);
        stateChanged.set(true);

        TableStatus newStatus = determineTableStatus(newCurrent, total);

        AtomicReference<TableStatus> statusRef = tableStatus.get(tableName);
        if (statusRef != null) {
            TableStatus oldStatus;
            do {
                oldStatus = statusRef.get();
                if (oldStatus == newStatus) {
                    return;
                }
            } while (!statusRef.compareAndSet(oldStatus, newStatus));

            updateProcessingTables(tableName, oldStatus, newStatus);
            stateChanged.set(true);
        }
    }

    public void updateTableObjectProgress(String tableName, long increment) {
        tableCompletedWorkUnits.merge(tableName, increment, Long::sum);

        changedTables.add(tableName);
        stateChanged.set(true);
    }

    private TableStatus determineTableStatus(long current, long total) {
        if (current == 0) return TableStatus.PENDING;
        else if (current >= total) return TableStatus.COMPLETED;
        else return TableStatus.PROCESSING;
    }

    private void updateProcessingTables(
            String tableName, TableStatus oldStatus, TableStatus newStatus) {
        if (newStatus == TableStatus.PROCESSING) {
            processingTables.add(tableName);
        } else {
            processingTables.remove(tableName);
        }

        cacheValid.set(false);
    }

    private Set<String> getCachedProcessingTables() {
        if (!cacheValid.get()) {
            cachedProcessingTables.clear();
            cachedProcessingTables.addAll(processingTables);
            cacheValid.set(true);
        }
        return cachedProcessingTables;
    }

    private Set<String> getAndClearChangedTables() {
        if (!stateChanged.compareAndSet(true, false)) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>(changedTables);
        changedTables.clear();
        return result;
    }

    private int calculateProgressLines() {
        return getCachedProcessingTables().size();
    }

    public void addEvent(MigrationEvent event) {
        if (finalEvent != null) return;

        if (event instanceof MigrationStartEvent) {
            synchronized (printLock) {
                outPrinter.println(event.toString());
            }
            return;
        }

        if (event instanceof MigrationFinishedEvent) {
            finalEvent = (MigrationFinishedEvent) event;
            printFinalProgress();
            requestStop();
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
            ImportRecordsEvent ev = (ImportRecordsEvent) event;
            if (ev.isSuccess()) {
                completedWorkUnits.addAndGet(ev.getRecordCount());
                updateTableProgress(ev.getSourceTable().getName(), ev.getRecordCount());
            } else {
                isError = true;
            }
        } else if (event instanceof ImportSQLsEvent) {
            ImportSQLsEvent ev = (ImportSQLsEvent) event;
            completedWorkUnits.addAndGet(ev.getSize());
            if (!ev.isSuccess()) isError = true;
        } else if (event instanceof ImportCSVEvent) {
            ImportCSVEvent ev = (ImportCSVEvent) event;
            completedWorkUnits.addAndGet(ev.getSize());
            if (!ev.isSuccess()) isError = true;
        }

        if (isError) {
            hasError.set(true);
        }
    }

    public void requestStop() {
        stopRequested = true;
        if (progressThread != null) {
            progressThread.interrupt();
        }
    }

    private void printProgressIfChanged() {

        if (!stateChanged.get()) {
            return;
        }

        Set<String> currentChangedTables = getAndClearChangedTables();

        Set<String> currentProcessingTables = getCachedProcessingTables();

        for (String tableName : currentChangedTables) {
            long completedWork = tableCompletedWorkUnits.getOrDefault(tableName, 0L);
            tablePreviousWorkUnits.put(tableName, completedWork);
        }

        synchronized (printLock) {
            if (!firstProgressOutput.get()) {
                for (int i = 0; i < lastProgressLineCount.get(); i++) {
                    outPrinter.print("\033[F");
                }
                outPrinter.print("\033[J");
            } else {
                firstProgressOutput.set(false);
            }

            long totalWork = totalWorkUnits.get();
            long completedWork = completedWorkUnits.get();
            long percent = (totalWork > 0) ? (completedWork * 100 / totalWork) : 100;
            percent = Math.max(percent, 1);

            outPrinter.printf(
                    "Migration Progress: %d%% [%,d / %,d]\n", percent, completedWork, totalWork);

            int outputCount = 0;
            for (String tableName : tableOrder) {
                if (!currentProcessingTables.contains(tableName)) continue;

                long totalTableWork = tableTotalWorkUnits.getOrDefault(tableName, 0L);
                long completedTableWork = tableCompletedWorkUnits.getOrDefault(tableName, 0L);

                Integer indexObj = tableIndexMap.get(tableName);
                int index = (indexObj != null) ? indexObj + 1 : 1;

                long tablePercent =
                        (totalTableWork > 0) ? (completedTableWork * 100 / totalTableWork) : 0;

                outPrinter.printf(
                        "%s(%d/%d) | %,d/%,d %d%%\n",
                        tableName,
                        index,
                        tableOrderSize.get(),
                        completedTableWork,
                        totalTableWork,
                        tablePercent);
                outputCount++;
            }

            lastProgressLineCount.set(1 + outputCount);
            outPrinter.flush();
        }
    }

    private void printFinalProgress() {
        printProgressIfChanged();

        synchronized (printLock) {
            if (hasError.get()) {
                outPrinter.println("Some errors occurred during migration.");
                outPrinter.println("Please see the report for more.");
            }
            if (finalEvent != null) {
                outPrinter.println(finalEvent.toString());
            }
        }
    }

    @Override
    public void run() {
        while (!stopRequested) {
            printProgressIfChanged();
            try {
                Thread.sleep(PROGRESS_UPDATE_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
        printProgressIfChanged();
    }
}
