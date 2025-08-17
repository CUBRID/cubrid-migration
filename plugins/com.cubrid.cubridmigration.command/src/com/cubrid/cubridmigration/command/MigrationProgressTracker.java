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
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import java.io.File;
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
 * Tracks migration progress for all tables and work units
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-2-2 created by Kevin Cao
 */
public class MigrationProgressTracker {

    private final AtomicLong totalWorkUnits = new AtomicLong(0);
    private final AtomicLong completedWorkUnits = new AtomicLong(0);

    private final Set<String> processingTables = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> tableOrder = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> tableIndexMap = new ConcurrentHashMap<>();
    private final Map<String, TableProgressData> tableProgressMap = new ConcurrentHashMap<>();

    private final Set<String> changedTables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean hasChanges = new AtomicBoolean(false);
    private final Set<String> cachedProcessingTables = ConcurrentHashMap.newKeySet();
    private volatile boolean cacheNeedsUpdate = false;
    private volatile boolean hasPendingCacheUpdates = false;

    private final AtomicInteger tableOrderSize = new AtomicInteger(0);

    private final Set<String> reusableResultSet = new HashSet<>();

    public void initialize(MigrationConfiguration config) {
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

            int index = tableOrderSize.getAndIncrement();
            tableOrder.add(tableName);
            tableIndexMap.put(tableName, index);

            initializeTableProgress(tableName, rowCount, isEntryTable, createPK, table);
        }
    }

    private void addTotalWorkUnits(
            boolean isEntryTable, boolean createPK, Table table, long rowCount) {
        if (isEntryTable && createPK && table != null && table.getPk() != null) {
            totalWorkUnits.incrementAndGet();
        }
        totalWorkUnits.addAndGet(rowCount);
    }

    private void initializeTableProgress(
            String tableName, long rowCount, boolean isEntryTable, boolean createPK, Table table) {
        int index = tableIndexMap.get(tableName);
        long workUnits = calculateTableWorkUnits(isEntryTable, createPK, table, rowCount);
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

    public void addCompletedWorkUnits(long increment) {
        completedWorkUnits.addAndGet(increment);
    }

    public void incrementCompletedWorkUnits() {
        completedWorkUnits.incrementAndGet();
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

    public Set<String> getCachedProcessingTables() {
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

    public Set<String> getAndClearChangedTables() {
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

    public void updatePreviousWorkUnitsForChangedTables(Set<String> changedTables) {
        for (String tableName : changedTables) {
            TableProgressData data = tableProgressMap.get(tableName);
            if (data != null) {
                data.updatePreviousWorkUnits();
            }
        }
    }

    public long getTotalWorkUnits() {
        return totalWorkUnits.get();
    }

    public long getCompletedWorkUnits() {
        return completedWorkUnits.get();
    }

    public boolean hasChanges() {
        return hasChanges.get();
    }

    public ConcurrentLinkedQueue<String> getTableOrder() {
        return tableOrder;
    }

    public int getTableOrderSize() {
        return tableOrderSize.get();
    }

    public TableProgressData getTableProgressData(String tableName) {
        return tableProgressMap.get(tableName);
    }
}
