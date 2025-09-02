/*
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
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MigrationProgressTracker {

    private final AtomicLong totalRecordUnits = new AtomicLong(0);
    private final AtomicLong completedRecordUnits = new AtomicLong(0);

    private final Set<String> processingTables = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> tableOrder = new ConcurrentLinkedQueue<>();
    private final Map<String, TableProgressData> tableProgressMap = new ConcurrentHashMap<>();
    private final AtomicBoolean hasChanges = new AtomicBoolean(false);
    private final AtomicInteger tableOrderSize = new AtomicInteger(0);

    public void initialize(MigrationConfiguration config) {
        if (config.sourceIsOnline() || config.sourceIsXMLDump()) {
            processSourceTables(config.getExpEntryTableCfg(), config);
            processSourceTables(config.getExpSQLCfg(), config);
        } else if (config.sourceIsSQL()) {
            for (String ss : config.getSqlFiles()) {
                totalRecordUnits.addAndGet(new File(ss).length());
            }
        } else if (config.sourceIsCSV()) {
            for (SourceCSVConfig scc : config.getCSVConfigs()) {
                totalRecordUnits.addAndGet(new File(scc.getName()).length());
            }
        }
    }

    private void processSourceTables(
            Collection<? extends SourceTableConfig> tables, MigrationConfiguration config) {

        for (SourceTableConfig tbl : tables) {
            String tableName = tbl.getName();
            String owner = tbl.getOwner();

            Table table = config.getSrcTableSchema(owner, tableName);
            long rowCount = (table == null) ? 0L : table.getTableRowCount();

            totalRecordUnits.addAndGet(rowCount);

            int index = tableOrderSize.getAndIncrement();
            String ownerTableName = owner + "." + tableName;
            tableOrder.add(ownerTableName);

            initializeTableProgress(ownerTableName, rowCount, index);
        }
    }

    private void initializeTableProgress(String ownerTableName, long rowCount, int index) {
        TableProgressData data = new TableProgressData(rowCount, index);
        tableProgressMap.put(ownerTableName, data);
    }

    public void updateTableProgress(String ownerTableName, long increment) {
        TableProgressData data = tableProgressMap.get(ownerTableName);
        if (data != null) {
            data.addCompletedWorkUnits(increment);

            long newCurrent = data.getCompletedWorkUnits();
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
                if (newStatus == TableStatus.PROCESSING) {
                    processingTables.add(ownerTableName);
                } else {
                    processingTables.remove(ownerTableName);
                }
            }

            hasChanges.set(true);
        }
    }

    public void addCompletedWorkUnits(long increment) {
        completedRecordUnits.addAndGet(increment);
    }

    private TableStatus determineTableStatus(long current, long total) {
        if (current == 0) return TableStatus.PENDING;
        else if (current >= total) return TableStatus.COMPLETED;
        else return TableStatus.PROCESSING;
    }

    public Set<String> getProcessingTables() {
        return new HashSet<>(processingTables);
    }

    public long getTotalRecordUnits() {
        return totalRecordUnits.get();
    }

    public long getCompletedWorkUnits() {
        return completedRecordUnits.get();
    }

    public boolean hasChanges() {
        return hasChanges.get();
    }

    public void resetChanges() {
        hasChanges.set(false);
    }

    public ConcurrentLinkedQueue<String> getTableOrder() {
        return tableOrder;
    }

    public int getTableOrderSize() {
        return tableOrderSize.get();
    }

    public TableProgressData getTableProgressData(String ownerTableName) {
        return tableProgressMap.get(ownerTableName);
    }
}
