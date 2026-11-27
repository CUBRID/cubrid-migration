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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.core.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * MigrationStatusManager: thread safe.
 *
 * @author Kevin Cao
 * @version 1.0 - 2013-6-27 created by Kevin Cao
 */
public class MigrationStatusManager {

    private long maxMemory;
    private long alertFreeMemory;
    private long warningFreeMemory;
    private int warningCommitCount;
    private int alertCommitCount;

    private final Map<String, DataMigrationStatus> dataMigrationStatus =
            new HashMap<String, DataMigrationStatus>();

    public static final int STATUS_CONTINUE = 0;
    public static final int STATUS_COMMIT = 1;
    public static final int STATUS_WAITING = 2;

    private long finishedImportTaskCount;
    private boolean hasOOMRisk;

    private final Object lockObj = new Object();
    private final Object lockObj2 = new Object();

    private long totalImportTaskCount;

    /**
     * add a source's exported record count
     *
     * @param owner of the object
     * @param source name
     * @param count of records
     */
    public void addExpCount(String owner, String source, long count) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            if (dms == null) {
                dms = new DataMigrationStatus();
                dms.setSource(src);
                dataMigrationStatus.put(src, dms);
            }
            dms.addTotalExpCount(count);
        }
    }

    /**
     * The importing threads processed record count, it it not the record count inserted
     * successfully. In the end, the imported count should be equal with the exported count
     *
     * @param owner of the object
     * @param source String
     * @param count processed count, including failed count
     */
    public void addImpCount(String owner, String source, long count) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            if (dms == null) {
                dms = new DataMigrationStatus();
                dms.setSource(src);
                dataMigrationStatus.put(src, dms);
            }
            dms.addTotalImpCount(count);
        }
    }

    /**
     * Get source exporting status
     *
     * @param owner of the object
     * @param source name
     * @return records count
     */
    public long getExpCount(String owner, String source) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            return dms == null ? 0 : dms.getTotalExpCount();
        }
    }

    /**
     * Get source's exporting finished status
     *
     * @param owner of the object
     * @param source name
     * @return true once every export range for this source has reported finished (a table that was
     *     never split into parallel ranges has exactly one range, so this behaves exactly as the
     *     old single-flag version did)
     */
    public boolean getExpFlag(String owner, String source) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            return dms != null && dms.isExportFinished();
        }
    }

    /**
     * Registers how many parallel export ranges/tasks a source was split into. Callers that split a
     * table via {@code TableSplitPlanner} must call this with the actual range count *before*
     * dispatching any of those range tasks, so that {@link #setExpFinished} only reports the source
     * as done once every range has finished - otherwise the first range to finish would mark the
     * whole source done while the others are still exporting. Sources that are not split (the
     * default, single-task-per-table behavior) don't need to call this at all.
     *
     * @param owner of the object
     * @param source name
     * @param totalRanges total number of export tasks/ranges this source was split into, &gt;= 1
     */
    public void registerExportRangeCount(String owner, String source, int totalRanges) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            if (dms == null) {
                dms = new DataMigrationStatus();
                dms.setSource(src);
                dataMigrationStatus.put(src, dms);
            }
            dms.setTotalExportRanges(totalRanges);
        }
    }

    /**
     * Retrieves the finished importing task count
     *
     * @return task count
     */
    public long getFinishedImportTaskCount() {
        synchronized (lockObj2) {
            return finishedImportTaskCount;
        }
    }

    /**
     * Get source importing status, including the records which are failed to be imported
     *
     * @param owner of the object
     * @param source name
     * @return record count
     */
    public long getImpCount(String owner, String source) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            return dms == null ? 0 : dms.getTotalImpCount();
        }
    }

    /**
     * Retrieves the total importing task count
     *
     * @return total count
     */
    public long getTotalImportTaskCount() {
        synchronized (lockObj2) {
            return totalImportTaskCount;
        }
    }

    /** Increase finished importing task count */
    public void increaseFinishedImportTaskCount() {
        synchronized (lockObj2) {
            finishedImportTaskCount++;
        }
    }

    /** Increase total importing task count. */
    public void increaseTotalImportTaskCount() {
        synchronized (lockObj2) {
            totalImportTaskCount++;
        }
    }

    /**
     * Tell the migration process whether to commit exported records now.
     *
     * @param expName String
     * @param currentCount int
     * @param commitCount int
     * @return 0:continue; 1:commit; 2:waiting.
     */
    public int isCommitNow(String expName, int currentCount, int commitCount) {
        // According to commit count settings
        if (!hasOOMRisk) {
            return currentCount >= commitCount ? STATUS_COMMIT : STATUS_CONTINUE;
        }
        Runtime rt = Runtime.getRuntime();
        if (rt.totalMemory() >= maxMemory) {
            if (rt.freeMemory() <= warningFreeMemory) {
                return currentCount >= warningCommitCount ? STATUS_COMMIT : STATUS_CONTINUE;
            } else if (rt.freeMemory() <= alertFreeMemory) {
                return currentCount >= alertCommitCount ? STATUS_COMMIT : STATUS_WAITING;
            }
        }
        return currentCount >= commitCount ? STATUS_COMMIT : STATUS_CONTINUE;
    }

    /**
     * Reports that one export range/task for this source has finished. A source that was never
     * registered via {@link #registerExportRangeCount} defaults to a single range, so the first
     * (and only) call marks it done immediately - matching the old one-task-per-table behavior. A
     * source split into N ranges only becomes "done" (per {@link #getExpFlag}) once this has been
     * called N times.
     *
     * @param owner of the object
     * @param source name
     */
    public void setExpFinished(String owner, String source) {
        synchronized (lockObj) {
            String src = (owner == null ? "" : owner) + "." + source;
            DataMigrationStatus dms = dataMigrationStatus.get(src);
            if (dms == null) {
                dms = new DataMigrationStatus();
                dms.setSource(src);
                dataMigrationStatus.put(src, dms);
            }
            dms.finishExportRange();
        }
    }

    public void setHasOOMRisk(boolean hasOOMRisk) {
        this.hasOOMRisk = hasOOMRisk;
    }

    public void setMaxMemory(long maxMemory) {
        this.maxMemory = maxMemory;
    }

    public void setAlertFreeMemory(long alertFreeMemory) {
        this.alertFreeMemory = alertFreeMemory;
    }

    public void setWarningFreeMemory(long warningFreeMemory) {
        this.warningFreeMemory = warningFreeMemory;
    }

    public void setWarningCommitCount(int warningCommitCount) {
        this.warningCommitCount = warningCommitCount;
    }

    public void setAlertCommitCount(int alertCommitCount) {
        this.alertCommitCount = alertCommitCount;
    }
}
