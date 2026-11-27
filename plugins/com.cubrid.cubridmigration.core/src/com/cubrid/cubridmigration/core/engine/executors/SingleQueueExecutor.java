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
package com.cubrid.cubridmigration.core.engine.executors;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * SingleQueueExecutor: tasks will be pushed into a single queue waiting for dispatching
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-4 created by Kevin Cao
 */
public class SingleQueueExecutor implements IRunnableExecutor {

    protected final ThreadPoolExecutor executor;

    protected int poolSize;

    protected final boolean limitTaskCount;

    /** Tasks accepted so far. Guarded by {@link #lockObj}. */
    protected long taskCount;

    /**
     * Tasks that have finished running (normally or not). Counted by this class instead of read
     * from {@link ThreadPoolExecutor#getCompletedTaskCount()} so that a finishing task can wake a
     * blocked submitter at the exact moment its slot frees up. Guarded by {@link #lockObj}.
     */
    protected long completedCount;

    /**
     * Monitor for the counters above. A submitter that must wait for a free slot waits ON this
     * monitor (which releases it), so other submitters are never stuck behind a sleeping one.
     */
    protected Object lockObj = new Object();

    /**
     * Safety re-check interval while waiting for a free slot. A finishing task normally wakes the
     * waiter immediately; this only bounds how long a wake-up could ever be missed.
     */
    private static final long SLOT_RECHECK_MILLIS = 200L;

    protected volatile boolean interrupted;

    public SingleQueueExecutor(int threadSize, boolean limitTaskCount) {
        poolSize = threadSize + Math.max(2, threadSize / 2);
        this.limitTaskCount = limitTaskCount;
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadSize);
    }

    /** Release threads pool. */
    public void dispose() {
        executor.shutdown();
    }

    /**
     * Add task to scheduler.
     *
     * @param tk is the migration task to be executed.
     */
    public void execute(Runnable tk) {
        synchronized (lockObj) {
            taskCount++;
            boolean submitted = false;
            try {
                while (limitTaskCount && tooManyTasks()) {
                    if (interrupted) {
                        throw new RuntimeException("Interrupted.");
                    }
                    awaitFreeSlot();
                }
                if (interrupted) {
                    throw new RuntimeException("Interrupted.");
                }
                executor.execute(countOnCompletion(tk));
                submitted = true;
            } finally {
                if (!submitted) {
                    // The task never reached the pool, so it must not count as outstanding
                    // forever (it would otherwise keep isBusy() true and eat a queue slot).
                    taskCount--;
                    lockObj.notifyAll();
                }
            }
        }
    }

    /**
     * Waits until a task finishes (or {@link #SLOT_RECHECK_MILLIS} passes) so the caller can
     * re-check {@link #tooManyTasks()}. Must be called holding {@link #lockObj}; the wait releases
     * it, which is the point: the old sleep-while-holding-the-monitor loop made every other
     * submitter queue up on the monitor behind one sleeping thread, and the wake-up only came on
     * the next 200 ms poll even though importers had long since drained the queue.
     */
    protected final void awaitFreeSlot() {
        try {
            lockObj.wait(SLOT_RECHECK_MILLIS);
        } catch (InterruptedException ex) {
            // Same as the previous ThreadUtils.threadSleep(.., null): swallowed, the caller's loop
            // re-checks the interrupted flag set by interrupt().
        }
    }

    /**
     * Wraps {@code tk} so that finishing it - normally or by exception - is counted and wakes any
     * submitter waiting for a free slot.
     *
     * @param tk the task to run
     * @return the task to hand to the pool
     */
    protected final Runnable countOnCompletion(final Runnable tk) {
        return new Runnable() {
            public void run() {
                try {
                    tk.run();
                } finally {
                    synchronized (lockObj) {
                        completedCount++;
                        lockObj.notifyAll();
                    }
                }
            }
        };
    }

    /**
     * True if there are too many tasks in queue.
     *
     * @return True if there are too many tasks in queue.
     */
    protected boolean tooManyTasks() {
        return (taskCount - completedCount) > poolSize;
    }

    /** Interrupted */
    public void interrupt() {
        interrupted = true;
        executor.shutdownNow();
        synchronized (lockObj) {
            // Wake submitters waiting for a slot so they see the flag now instead of at the next
            // recheck; tasks dropped by shutdownNow() never complete, so nothing else would.
            lockObj.notifyAll();
        }
    }

    /**
     * The executor is busy
     *
     * @return true if has tasks not executed
     */
    public boolean isBusy() {
        if (interrupted) {
            return false;
        }
        synchronized (lockObj) {
            return (taskCount - completedCount) != 0;
        }
    }
}
