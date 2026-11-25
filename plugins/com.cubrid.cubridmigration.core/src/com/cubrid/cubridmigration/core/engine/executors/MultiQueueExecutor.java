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
package com.cubrid.cubridmigration.core.engine.executors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.engine.task.ITableKeyedTask;

/**
 * MultiQueueExecutor will execute the tasks in a thread pool which only has one thread.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-4 created by Kevin Cao
 */
public class MultiQueueExecutor implements IRunnableExecutor {

    private static final Logger LOG = LogUtil.getLogger(MultiQueueExecutor.class);

    /** Live queues by key. Guarded by {@link #lockObj}. */
    private final Map<Object, IRunnableExecutor> executors = new HashMap<Object, IRunnableExecutor>();

    /**
     * Queues already released by {@link #releaseKey} (no more tasks will be submitted) that may
     * still hold queued or running tasks. They stay here until drained so {@link #isBusy()} keeps
     * reporting busy and {@link #interrupt()} can still stop them. Guarded by {@link #lockObj}.
     */
    private final List<IRunnableExecutor> drainingExecutors = new ArrayList<IRunnableExecutor>();

    private volatile boolean interrupted;

    private final boolean limitTaskCount;

    private long totalTask = 0;

    private final Object lockObj = new Object();

    private int executorPoolSize;

    public MultiQueueExecutor(int poolSize, boolean limitTaskCount) {
        this.limitTaskCount = limitTaskCount;
        this.executorPoolSize = poolSize;
    }

    /**
     * Add task to scheduler.
     *
     * @param tk is the migration task to be executed.
     */
    public void execute(Runnable tk) {
        IRunnableExecutor es = getExecutor(tk);
        if (interrupted) {
            throw new RuntimeException("Interrupted");
        }
        totalTask++;
        es.execute((Runnable) tk);
    }

    /**
     * Retrieves the executor that owns this task's work.
     *
     * <p>A {@link ITableKeyedTask} (e.g. a record-import task) is keyed by its table, so every
     * export range/thread feeding the same table shares one {@code importThreadCount}-sized queue
     * instead of each claiming its own - otherwise a split table's import parallelism silently
     * scales with its split degree instead of staying at the configured value. Anything else falls
     * back to the original per-calling-thread key.
     *
     * @param tk the task about to be submitted
     * @return IRunnableExecutor
     */
    private IRunnableExecutor getExecutor(Runnable tk) {
        synchronized (lockObj) {
            final Object key =
                    (tk instanceof ITableKeyedTask && ((ITableKeyedTask) tk).getTableKey() != null)
                            ? ((ITableKeyedTask) tk).getTableKey()
                            : Thread.currentThread().getId();
            IRunnableExecutor es = executors.get(key);
            if (es == null) {
                es = new SingleQueueExecutor(executorPoolSize, limitTaskCount);
                executors.put(key, es);
                // One line per NEW queue only (not per task), so counting these lines for a given
                // key in the log directly measures how many import queues that key ended up with.
                // For a table key this should stay 1 no matter how many split ranges feed it; for
                // a plain thread-id fallback key, it stays 1 per distinct calling thread.
                LOG.info(
                        "[DEBUG-IMPORTQUEUE] New import queue #"
                                + executors.size()
                                + " created for key="
                                + key
                                + " (size="
                                + executorPoolSize
                                + ")");
            }
            return es;
        }
    }

    /**
     * Releases and disposes the queue for one key once its producer(s) are known to be finished -
     * e.g. once {@code MigrationStatusManager#getExpFlag} confirms a table's export has fully
     * completed (all ranges done), so a table-keyed queue (see {@link ITableKeyedTask}) doesn't sit
     * alive, idle, for the rest of the migration; without this, keying by table instead of by
     * calling thread trades the split-degree thread multiplication this class was fixed for a
     * table-count-proportional one, since nothing else ever removes a map entry before {@link
     * #dispose()}/{@link #interrupt()} at whole-migration teardown.
     *
     * <p>Uses a plain {@link IRunnableExecutor#dispose()} (graceful shutdown), not {@link
     * #interrupt()}: any batch already queued or running under this key at the moment of the call
     * still finishes normally - the caller is only expected to call this once it already knows
     * nothing will ever be submitted under this key again, not that the key is idle right now.
     *
     * <p>The released queue moves into {@link #drainingExecutors} in the same critical section that
     * removes it from the live map, so there is never a moment where it is in neither and {@link
     * #isBusy()} could wrongly report idle while its tasks are still pending.
     *
     * @param key the same key {@link #getExecutor} would have resolved for that producer
     */
    @Override
    public void releaseKey(Object key) {
        final IRunnableExecutor es;
        final int stillOpen;
        synchronized (lockObj) {
            es = executors.remove(key);
            if (es != null) {
                drainingExecutors.add(es);
            }
            stillOpen = executors.size();
        }
        if (es != null) {
            es.dispose();
            LOG.info(
                    "[DEBUG-IMPORTQUEUE] Released import queue for key="
                            + key
                            + " ("
                            + stillOpen
                            + " still open)");
        }
    }

    /** Release thread pools */
    public void dispose() {
        final List<IRunnableExecutor> live;
        synchronized (lockObj) {
            live = new ArrayList<IRunnableExecutor>(executors.values());
        }
        // Draining queues were already disposed by releaseKey.
        for (IRunnableExecutor et : live) {
            et.dispose();
        }
    }

    /** Release thread pools, including queues that were released but are still draining */
    public void interrupt() {
        interrupted = true;
        final List<IRunnableExecutor> all;
        synchronized (lockObj) {
            all = new ArrayList<IRunnableExecutor>(executors.values());
            all.addAll(drainingExecutors);
        }
        for (IRunnableExecutor et : all) {
            et.interrupt();
        }
    }

    /**
     * Test-only: how many distinct keyed queues are currently live, for verifying {@link
     * #getExecutor}/{@link #releaseKey}'s bookkeeping without depending on timing.
     *
     * @return the number of keys with a live queue right now
     */
    int queueCount() {
        synchronized (lockObj) {
            return executors.size();
        }
    }

    /**
     * Test-only: how many released queues are still waiting to drain.
     *
     * @return the number of released queues not yet observed idle
     */
    int drainingCount() {
        synchronized (lockObj) {
            return drainingExecutors.size();
        }
    }

    /**
     * @return true if any live or released-but-still-draining queue has pending work.
     */
    public boolean isBusy() {
        if (interrupted) {
            return false;
        }
        synchronized (lockObj) {
            drainingExecutors.removeIf(executor -> !executor.isBusy());
            if (!drainingExecutors.isEmpty()) {
                return true;
            }
            for (IRunnableExecutor et : executors.values()) {
                if (et.isBusy()) {
                    return true;
                }
            }
            return false;
        }
    }
}
