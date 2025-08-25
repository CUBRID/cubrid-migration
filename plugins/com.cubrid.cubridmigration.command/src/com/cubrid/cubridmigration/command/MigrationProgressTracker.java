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

public class MigrationProgressTracker {

    private final AtomicLong totalRecordUnits = new AtomicLong(0);
    private final AtomicLong completedRecordUnits = new AtomicLong(0);

    private final Set<String> processingTables = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> tableOrder = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> tableIndexMap = new ConcurrentHashMap<>();
    private final Map<String, TableProgressData> tableProgressMap = new ConcurrentHashMap<>();
    private final Set<String> changedTables = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean hasChanges = new AtomicBoolean(false);
    private final AtomicInteger tableOrderSize = new AtomicInteger(0);

    public void initialize(MigrationConfiguration config) {
        if (config.sourceIsOnline() || config.sourceIsXMLDump()) {
            processSourceTables(config.getExpEntryTableCfg(), true, config);
            processSourceTables(config.getExpSQLCfg(), false, config);
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
            Collection<?> tables, boolean isEntryTable, MigrationConfiguration config) {

        for (Object obj : tables) {
            String tableName;
            String owner = null;
            if (isEntryTable) {
                SourceEntryTableConfig tbl = (SourceEntryTableConfig) obj;
                tableName = tbl.getName();
                owner = tbl.getOwner();
            } else {
                SourceSQLTableConfig tbl = (SourceSQLTableConfig) obj;
                tableName = tbl.getName();
                owner = tbl.getOwner();
            }

            Table table = config.getSrcTableSchema(owner, tableName);
            long rowCount = (table == null) ? 0L : table.getTableRowCount();

            totalRecordUnits.addAndGet(rowCount);

            int index = tableOrderSize.getAndIncrement();
            tableOrder.add(tableName);
            tableIndexMap.put(tableName, index);

            initializeTableProgress(tableName, rowCount, index);
        }
    }

    private void initializeTableProgress(String tableName, long rowCount, int index) {
        TableProgressData data = new TableProgressData(tableName, rowCount, index);
        tableProgressMap.put(tableName, data);
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
                if (newStatus == TableStatus.PROCESSING) {
                    processingTables.add(tableName);
                } else {
                    processingTables.remove(tableName);
                }
            }

            changedTables.add(tableName);
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

    public Set<String> getAndClearChangedTables() {
        if (!hasChanges.compareAndSet(true, false)) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>(changedTables);
        changedTables.clear();
        return result;
    }

    public void updatePreviousWorkUnitsForChangedTables(Set<String> changedTables) {
        for (String tableName : changedTables) {
            TableProgressData data = tableProgressMap.get(tableName);
            if (data != null) {
                data.updatePreviousWorkUnits();
            }
        }
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
