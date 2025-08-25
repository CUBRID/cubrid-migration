package com.cubrid.cubridmigration.command;

import java.util.concurrent.atomic.AtomicReference;

public class TableProgressData {
    private final String tableName;
    private final int index;

    private volatile long totalRows;
    private volatile long currentRows;

    private volatile long completedWorkUnits;
    private volatile long previousWorkUnits;

    private final AtomicReference<TableStatus> status;

    public TableProgressData(String tableName, long totalRows, int index) {
        this.tableName = tableName;
        this.totalRows = totalRows;
        this.index = index;
        this.currentRows = 0L;
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

    public void updatePreviousWorkUnits() {
        this.previousWorkUnits = this.completedWorkUnits;
    }

    public long getWorkPercent() {
        return totalRows > 0 ? (completedWorkUnits * 100 / totalRows) : 0;
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

    public long getCompletedWorkUnits() {
        return completedWorkUnits;
    }

    public AtomicReference<TableStatus> getStatus() {
        return status;
    }
}
