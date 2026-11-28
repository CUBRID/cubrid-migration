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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Same task-count limit as {@link SingleQueueExecutor}, for which this class overrides {@code
 * execute()}: a task must be counted only once it is really submitted, or several waiting
 * submitters can keep the count above the limit by themselves and block every later submission.
 */
@DisplayName("AutoCountSingleQueueExecutor")
class AutoCountSingleQueueExecutorTest {

    private static final long AWAIT_SECONDS = 5L;

    /** One pool thread: the task limit is then 1 + max(2, 0) = 3 outstanding tasks. */
    private static final int ONE_THREAD = 1;

    private static final int LIMIT_FOR_ONE_THREAD = 3;

    @Test
    @DisplayName(
            "more waiting submitters than the limit all go through once the queued tasks finish")
    void execute_moreWaitingSubmittersThanLimit_allGoThroughOnceQueuedTasksFinish()
            throws InterruptedException {
        AutoCountSingleQueueExecutor executor = new AutoCountSingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        int submitters = LIMIT_FOR_ONE_THREAD + 1;
        CountDownLatch accepted = new CountDownLatch(submitters);
        List<Thread> threads = new ArrayList<Thread>();
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            for (int i = 0; i < submitters; i++) {
                Thread thread = new Thread(() -> executor.execute(accepted::countDown));
                threads.add(thread);
                thread.start();
            }
            for (Thread thread : threads) {
                assertThat(awaitState(thread, Thread.State.TIMED_WAITING)).isTrue();
            }

            release.countDown();

            assertThat(awaitLatch(accepted)).isTrue();
        } finally {
            release.countDown();
            // Frees any submitter still parked, so a failing run cannot hang the build.
            executor.interrupt();
        }
    }

    @Test
    @DisplayName("a submitter at the limit waits, then goes through as soon as one task finishes")
    void execute_queueAtLimit_submitterWaitsUntilATaskFinishes() throws InterruptedException {
        AutoCountSingleQueueExecutor executor = new AutoCountSingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch extraAccepted = new CountDownLatch(1);
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            Thread submitter = new Thread(() -> executor.execute(extraAccepted::countDown));
            submitter.start();

            // Exactly LIMIT_FOR_ONE_THREAD tasks fit: the next submitter must wait, not get in.
            assertThat(awaitState(submitter, Thread.State.TIMED_WAITING)).isTrue();
            assertThat(extraAccepted.getCount()).isEqualTo(1);

            release.countDown();

            assertThat(awaitLatch(extraAccepted)).isTrue();
        } finally {
            release.countDown();
            executor.interrupt();
        }
    }

    private static boolean awaitLatch(CountDownLatch latch) {
        try {
            return latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void awaitLatchQuietly(CountDownLatch latch) {
        awaitLatch(latch);
    }

    /** Polls the thread state (no sleeps in the code under test) until it reaches {@code state}. */
    private static boolean awaitState(Thread thread, Thread.State state) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == state) {
                return true;
            }
            Thread.yield();
        }
        return thread.getState() == state;
    }
}
