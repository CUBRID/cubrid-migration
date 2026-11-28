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
 *   and/or other materials provided with the distribution
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression coverage for {@link SingleQueueExecutor}'s task-count limit.
 *
 * <p>The limit used to be enforced by a loop that slept 200 ms between checks <em>while holding the
 * executor's monitor</em>. Two things followed: every other submitter stood BLOCKED on the monitor
 * behind the one sleeper, and a freed slot was only noticed at the next 200 ms tick - so several
 * parallel export ranges feeding one table's import queue spent most of their time asleep while the
 * importers sat idle, capping throughput at roughly one queue-full per 200 ms.
 *
 * <p>The tests drive that through thread states and latches (a blocked submitter is either
 * TIMED_WAITING on the monitor or BLOCKED behind someone) rather than through elapsed time, except
 * where a generous bound is the point.
 */
@DisplayName("SingleQueueExecutor")
class SingleQueueExecutorTest {

    private static final long AWAIT_SECONDS = 5L;

    /** One pool thread: the task limit is then 1 + max(2, 0) = 3 outstanding tasks. */
    private static final int ONE_THREAD = 1;

    private static final int LIMIT_FOR_ONE_THREAD = 3;

    @Test
    @DisplayName("a freed slot is used at once, not at the next 200 ms poll")
    void execute_manyQuickTasksThroughSmallLimit_finishesFarFasterThanPolling()
            throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        // With the 200 ms poll, only about LIMIT_FOR_ONE_THREAD tasks got in per tick, so these
        // 300 would need ~300 / 3 * 0.2 s = 20 s just to be submitted. Waking on completion needs
        // milliseconds, so a bound several times below the old figure separates the two without
        // being timing-fragile.
        int tasks = 300;
        CountDownLatch done = new CountDownLatch(tasks);
        try {
            Thread submitter =
                    new Thread(
                            () -> {
                                for (int i = 0; i < tasks; i++) {
                                    executor.execute(done::countDown);
                                }
                            });
            submitter.start();
            submitter.join(AWAIT_SECONDS * 1000);

            assertThat(submitter.isAlive()).isFalse();
            assertThat(awaitLatch(done)).isTrue();
        } finally {
            executor.dispose();
        }
    }

    @Test
    @DisplayName("a submitter at the limit waits, then goes through as soon as one task finishes")
    void execute_queueAtLimit_submitterWaitsUntilATaskFinishes() throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch extraAccepted = new CountDownLatch(1);
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            Thread submitter = new Thread(() -> executor.execute(extraAccepted::countDown));
            submitter.start();

            // Over the limit: the submitter must be parked waiting, not already through.
            assertThat(awaitState(submitter, Thread.State.TIMED_WAITING)).isTrue();
            assertThat(extraAccepted.getCount()).isEqualTo(1);

            release.countDown();

            assertThat(awaitLatch(extraAccepted)).isTrue();
            submitter.join(AWAIT_SECONDS * 1000);
        } finally {
            release.countDown();
            executor.dispose();
        }
    }

    @Test
    @DisplayName("several waiting submitters all wait, none is left BLOCKED on the monitor")
    void execute_severalSubmittersAtLimit_noneBlockedOnTheMonitor() throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            Thread first = new Thread(() -> executor.execute(() -> {}));
            Thread second = new Thread(() -> executor.execute(() -> {}));
            first.start();
            second.start();

            // The old loop slept while holding the monitor, so the second submitter could only
            // ever be BLOCKED (never TIMED_WAITING) until the first got through.
            assertThat(awaitState(first, Thread.State.TIMED_WAITING)).isTrue();
            assertThat(awaitState(second, Thread.State.TIMED_WAITING)).isTrue();

            release.countDown();
            first.join(AWAIT_SECONDS * 1000);
            second.join(AWAIT_SECONDS * 1000);
            assertThat(first.isAlive()).isFalse();
            assertThat(second.isAlive()).isFalse();
        } finally {
            release.countDown();
            executor.dispose();
        }
    }

    @Test
    @DisplayName(
            "more waiting submitters than the limit all go through once the queued tasks finish")
    void execute_moreWaitingSubmittersThanLimit_allGoThroughOnceQueuedTasksFinish()
            throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        // e.g. four export threads feeding one shared import queue whose limit is only three.
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

            // The queued tasks are done, so nothing is outstanding any more and every waiter must
            // be let in. A waiter that was already counted while it waited would still see
            // `submitters` outstanding tasks - more than the limit - and wait forever.
            assertThat(awaitLatch(accepted)).isTrue();
        } finally {
            release.countDown();
            // Frees any submitter still parked, so a failing run cannot hang the build.
            executor.interrupt();
        }
    }

    @Test
    @DisplayName("a task rejected by the pool is not counted as outstanding")
    void execute_rejectedByPool_isNotCountedAsOutstanding() {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        executor.dispose();

        assertThatThrownBy(() -> executor.execute(() -> {}))
                .isInstanceOf(RejectedExecutionException.class);

        assertThat(executor.isBusy()).isFalse();
    }

    @Test
    @DisplayName(
            "waiting submitters whose tasks the pool rejects neither block each other nor leak"
                    + " the count")
    void execute_waitingSubmittersRejectedByPool_doNotBlockEachOtherOrLeakCount()
            throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        List<Thread> threads = new ArrayList<Thread>();
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            // Graceful: the three tasks above still finish, but anything new is rejected.
            executor.dispose();
            for (int i = 0; i < 2; i++) {
                Thread thread =
                        new Thread(
                                () -> {
                                    try {
                                        executor.execute(() -> {});
                                    } catch (RejectedExecutionException expected) {
                                        rejected.incrementAndGet();
                                    }
                                });
                threads.add(thread);
                thread.start();
            }
            for (Thread thread : threads) {
                assertThat(awaitState(thread, Thread.State.TIMED_WAITING)).isTrue();
            }

            release.countDown();

            // Nothing wakes the waiters when a submission fails, so each must get through on the
            // completion notifications alone and fail on its own, without holding up the other.
            for (Thread thread : threads) {
                thread.join(AWAIT_SECONDS * 1000);
                assertThat(thread.isAlive()).isFalse();
            }
            assertThat(rejected.get()).isEqualTo(2);
            // A rejected task was never counted, so once the three real ones finished nothing is
            // left outstanding.
            assertThat(awaitIdle(executor)).isTrue();
        } finally {
            release.countDown();
            executor.interrupt();
        }
    }

    @Test
    @DisplayName("a task that throws still frees its slot")
    void execute_taskThrows_stillFreesItsSlot() {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        int tasks = 20;
        CountDownLatch survivors = new CountDownLatch(1);
        try {
            // More failing tasks than the limit: if a throwing task never released its slot, the
            // submitter would wait forever once LIMIT_FOR_ONE_THREAD of them had failed.
            for (int i = 0; i < tasks; i++) {
                executor.execute(
                        () -> {
                            throw new IllegalStateException("expected by the test");
                        });
            }
            executor.execute(survivors::countDown);

            assertThat(awaitLatch(survivors)).isTrue();
        } finally {
            executor.dispose();
        }
    }

    @Test
    @DisplayName("interrupting wakes a submitter that is waiting for a slot")
    void interrupt_submitterWaitingForSlot_throwsInterrupted() throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            for (int i = 0; i < LIMIT_FOR_ONE_THREAD; i++) {
                executor.execute(() -> awaitLatchQuietly(release));
            }
            Thread submitter =
                    new Thread(
                            () -> {
                                try {
                                    executor.execute(() -> {});
                                } catch (RuntimeException ex) {
                                    thrown.set(ex);
                                }
                            });
            submitter.start();
            assertThat(awaitState(submitter, Thread.State.TIMED_WAITING)).isTrue();

            executor.interrupt();

            submitter.join(AWAIT_SECONDS * 1000);
            assertThat(submitter.isAlive()).isFalse();
            assertThat(thrown.get())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Interrupted.");
        } finally {
            release.countDown();
            executor.dispose();
        }
    }

    @Test
    @DisplayName("without a task limit, submitting never waits however many tasks are queued")
    void execute_limitDisabled_neverWaits() throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, false);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        try {
            Thread submitter =
                    new Thread(
                            () -> {
                                for (int i = 0; i < 50; i++) {
                                    executor.execute(() -> awaitLatchQuietly(release));
                                    accepted.incrementAndGet();
                                }
                            });
            submitter.start();
            submitter.join(AWAIT_SECONDS * 1000);

            // 50 blocked tasks on one pool thread, all accepted without the submitter ever waiting.
            assertThat(submitter.isAlive()).isFalse();
            assertThat(accepted.get()).isEqualTo(50);
        } finally {
            release.countDown();
            executor.dispose();
        }
    }

    @Test
    @DisplayName("busy while a task is outstanding, idle again once it has finished")
    void isBusy_taskOutstanding_trueThenFalseAfterItFinishes() throws InterruptedException {
        SingleQueueExecutor executor = new SingleQueueExecutor(ONE_THREAD, true);
        CountDownLatch release = new CountDownLatch(1);
        try {
            assertThat(executor.isBusy()).isFalse();
            executor.execute(() -> awaitLatchQuietly(release));
            assertThat(executor.isBusy()).isTrue();

            release.countDown();

            assertThat(awaitIdle(executor)).isTrue();
        } finally {
            release.countDown();
            executor.dispose();
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

    /**
     * Polls the thread's state (no sleeps in the code under test) until it reaches {@code state}.
     */
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

    private static boolean awaitIdle(SingleQueueExecutor executor) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!executor.isBusy()) {
                return true;
            }
            Thread.yield();
        }
        return !executor.isBusy();
    }
}
