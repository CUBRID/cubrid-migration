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
package com.cubrid.cubridmigration.core.engine.task;

/**
 * A {@link Runnable} task that belongs to one source table, so a per-key executor (see {@code
 * MultiQueueExecutor}) can group all of a table's work onto one shared queue instead of handing out
 * a new one per calling thread.
 *
 * <p>Without this, {@code MultiQueueExecutor} keys its queues by {@code
 * Thread.currentThread().getId()} - fine when one table's export was always exactly one thread, but
 * once {@code TableSplitPlanner} lets a table's export run as N parallel range threads, each range
 * thread is a distinct caller and silently claims its own {@code importThreadCount}-sized queue, so
 * one split table ends up with {@code N * importThreadCount} import threads instead of the
 * configured {@code importThreadCount}.
 *
 * @author Kevin Cao
 */
public interface ITableKeyedTask {

    /**
     * Retrieves a stable per-table key (e.g. {@code owner + "." + name}) shared by every task that
     * imports records for the same table, regardless of which export range/thread produced them.
     *
     * @return the table key, or {@code null} if this task isn't tied to one table (falls back to
     *     the executor's default per-calling-thread keying)
     */
    String getTableKey();

    /**
     * Builds the same key {@link #getTableKey()} implementations use, so that whoever knows a
     * table's export has fully finished (see {@code MigrationStatusManager#getExpFlag}) can release
     * its import queue (see {@code MultiQueueExecutor#releaseKey}) using the exact key that queue
     * was created under - the release is a no-op if the two ever compute the key differently.
     *
     * <p>{@code owner} is {@code null} for every table whenever the source doesn't support per-user
     * schemas (see {@code DatabaseType#isSupportMultiSchema} - e.g. CUBRID before 11.2, per {@code
     * MigrationConfiguration#resetSchemaInfo}, which explicitly nulls it out in that case) -
     * guarded the same way {@code DBExportHelper#getQualifiedTableName} guards a blank owner, so
     * the key degrades to just {@code name} instead of the literal string {@code "null.NAME"}.
     *
     * @param owner table owner, may be {@code null} or {@code ""} for sources without one
     * @param name table name
     * @return the table key
     */
    static String tableKey(String owner, String name) {
        return (owner == null || owner.isEmpty()) ? name : owner + "." + name;
    }
}
