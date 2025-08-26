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
