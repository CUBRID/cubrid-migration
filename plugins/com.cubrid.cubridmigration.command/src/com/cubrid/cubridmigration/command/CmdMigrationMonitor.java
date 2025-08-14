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
    private final AtomicBoolean hasError = new AtomicBoolean(false);

    private volatile MigrationFinishedEvent finalEvent = null;
    private volatile boolean stopRequested = false;

    private final Object printLock = new Object();
    private final Object startLock = new Object();
    private final Set<String> processingTables = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> tableOrder = new ConcurrentLinkedQueue<>();

    private final Map<String, Integer> tableIndexMap = new ConcurrentHashMap<>();

    private final Set<String> changedTables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean hasChanges = new AtomicBoolean(false);
    private final Set<String> cachedProcessingTables = ConcurrentHashMap.newKeySet();
    private volatile boolean cacheNeedsUpdate = false;

    private final AtomicInteger tableOrderSize = new AtomicInteger(0);

    private final int monitorMode;
    private final PrintStream outPrinter = System.out;

    private volatile boolean isFirstOutput = true;
    private volatile int lastLineCount = 0;

    private final Set<String> reusableResultSet = new HashSet<>();
    private final StringBuilder outputBuffer = new StringBuilder(256);
    private volatile boolean hasPendingCacheUpdates = false;

    private final Map<String, TableProgressData> tableProgressMap = new ConcurrentHashMap<>();

    private static final long PROGRESS_UPDATE_INTERVAL_MS = 100;

    private Thread progressThread;

    public enum TableStatus {
        PENDING,
        PROCESSING,
        COMPLETED
    }

    static class TableProgressData {
        private final String tableName;
        private final int index;

        private volatile long totalRows;
        private volatile long currentRows;
        private volatile long previousRows;

        private volatile long totalWorkUnits;
        private volatile long completedWorkUnits;
        private volatile long previousWorkUnits;

        private final AtomicReference<TableStatus> status;

        TableProgressData(String tableName, long totalRows, long totalWorkUnits, int index) {
            this.tableName = tableName;
            this.totalRows = totalRows;
            this.totalWorkUnits = totalWorkUnits;
            this.index = index;
            this.currentRows = 0L;
            this.previousRows = -1L;
            this.completedWorkUnits = 0L;
            this.previousWorkUnits = -1L;
            this.status = new AtomicReference<>(TableStatus.PENDING);
        }

        public void addCurrentRows(long increment) {
            this.currentRows += increment;
        }

        public void addCompletedWorkUnits(long increment) {
            this.completedWorkUnits += increment;
        }

        public void updatePreviousRows() {
            this.previousRows = this.currentRows;
        }

        public void updatePreviousWorkUnits() {
            this.previousWorkUnits = this.completedWorkUnits;
        }

        public long getRowPercent() {
            return totalRows > 0 ? (currentRows * 100 / totalRows) : 0;
        }

        public long getWorkPercent() {
            return totalWorkUnits > 0 ? (completedWorkUnits * 100 / totalWorkUnits) : 0;
        }

        public String getTableName() {
            return tableName;
        }

        public int getIndex() {
            return index;
        }

        public long getTotalRows() {
            return totalRows;
        }

        public long getCurrentRows() {
            return currentRows;
        }

        public long getPreviousRows() {
            return previousRows;
        }

        public long getTotalWorkUnits() {
            return totalWorkUnits;
        }

        public long getCompletedWorkUnits() {
            return completedWorkUnits;
        }

        public long getPreviousWorkUnits() {
            return previousWorkUnits;
        }

        public AtomicReference<TableStatus> getStatus() {
            return status;
        }
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

            synchronized (tableOrder) {
                int index = tableOrderSize.get();
                tableOrder.add(tableName);
                tableOrderSize.incrementAndGet();
                tableIndexMap.put(tableName, index);
            }

            initializeTableProgress(tableName, rowCount);
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
        int index = tableIndexMap.get(tableName);
        long workUnits = calculateTableWorkUnits(true, true, null, rowCount); // 기본값 사용
        TableProgressData data = new TableProgressData(tableName, rowCount, workUnits, index);
        tableProgressMap.put(tableName, data);
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
        TableProgressData data = tableProgressMap.get(tableName);
        if (data != null) {
            data.addCurrentRows(increment);
            data.addCompletedWorkUnits(increment);

            long newCurrent = data.getCurrentRows();
            long total = data.getTotalRows();
            TableStatus newStatus = determineTableStatus(newCurrent, total);

            AtomicReference<TableStatus> statusRef = data.getStatus();
            TableStatus oldStatus;
            do {
                oldStatus = statusRef.get();
                if (oldStatus == newStatus) {
                    break;
                }
            } while (!statusRef.compareAndSet(oldStatus, newStatus));

            if (oldStatus != newStatus) {
                updateProcessingTables(tableName, oldStatus, newStatus);
            }

            changedTables.add(tableName);
            hasChanges.set(true);
        }
    }

    public void updateTableObjectProgress(String tableName, long increment) {
        TableProgressData data = tableProgressMap.get(tableName);
        if (data != null) {
            data.addCompletedWorkUnits(increment);
            changedTables.add(tableName);
            hasChanges.set(true);
        }
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

        if (!hasPendingCacheUpdates) {
            hasPendingCacheUpdates = true;
            cacheNeedsUpdate = true;
        }
    }

    private Set<String> getCachedProcessingTables() {
        if (cacheNeedsUpdate) {
            synchronized (this) {
                if (cacheNeedsUpdate) {
                    cachedProcessingTables.clear();
                    cachedProcessingTables.addAll(processingTables);
                    cacheNeedsUpdate = false;
                    hasPendingCacheUpdates = false;
                }
            }
        }
        return cachedProcessingTables;
    }

    private Set<String> getAndClearChangedTables() {
        if (!hasChanges.compareAndSet(true, false)) {
            return Collections.emptySet();
        }

        synchronized (reusableResultSet) {
            reusableResultSet.clear();
            reusableResultSet.addAll(changedTables);
            changedTables.clear();
            return new HashSet<>(reusableResultSet);
        }
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

        if (!hasChanges.get()) {
            return;
        }

        Set<String> currentChangedTables = getAndClearChangedTables();

        Set<String> currentProcessingTables = getCachedProcessingTables();

        for (String tableName : currentChangedTables) {
            TableProgressData data = tableProgressMap.get(tableName);
            if (data != null) {
                data.updatePreviousWorkUnits();
            }
        }

        synchronized (printLock) {
            if (!isFirstOutput) {
                for (int i = 0; i < lastLineCount; i++) {
                    outPrinter.print("\033[F");
                }
                outPrinter.print("\033[J");
            } else {
                isFirstOutput = false;
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

                TableProgressData data = tableProgressMap.get(tableName);
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
                        .append(tableOrderSize.get())
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

            lastLineCount = 1 + outputCount;
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
