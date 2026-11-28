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
package com.cubrid.cubridmigration.core.engine.importer.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.engine.MigrationStatusManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The final data-file merge of a table needs two things - every export range finished, every
 * exported record imported - and either can become true last. These tests pin the decision itself
 * and, with a real {@link MigrationStatusManager}, the case the importer used to miss: the last
 * range to finish returns no rows, so no import batch is left to notice that the export is over.
 */
@DisplayName("FinalMergeGate")
class FinalMergeGateTest {

    private static final String OWNER = "owner";
    private static final String TABLE = "orders";
    private static final String KEY = OWNER + TABLE;

    @Nested
    @DisplayName("tryAcquire()")
    class TryAcquire {

        private final FinalMergeGate gate = new FinalMergeGate();

        @Test
        @DisplayName("export finished and counts equal -> the first caller gets the turn")
        void exportFinishedAndCountsEqual_returnsTrue() {
            assertThat(gate.tryAcquire(KEY, true, 3000, 3000)).isTrue();
        }

        @Test
        @DisplayName("a second caller for the same table does not get the turn again")
        void secondCallForSameTable_returnsFalse() {
            gate.tryAcquire(KEY, true, 3000, 3000);

            assertThat(gate.tryAcquire(KEY, true, 3000, 3000)).isFalse();
        }

        @Test
        @DisplayName("another table has its own turn")
        void otherTable_returnsTrue() {
            gate.tryAcquire(KEY, true, 3000, 3000);

            assertThat(gate.tryAcquire(OWNER + "customers", true, 10, 10)).isTrue();
        }

        @Test
        @DisplayName("export still running -> no turn, and the turn is not used up")
        void exportNotFinished_returnsFalseWithoutUsingTheTurn() {
            assertThat(gate.tryAcquire(KEY, false, 3000, 3000)).isFalse();

            assertThat(gate.tryAcquire(KEY, true, 3000, 3000)).isTrue();
        }

        @Test
        @DisplayName("batches still being imported -> no turn, and the turn is not used up")
        void importedCountBehind_returnsFalseWithoutUsingTheTurn() {
            assertThat(gate.tryAcquire(KEY, true, 3000, 2000)).isFalse();

            assertThat(gate.tryAcquire(KEY, true, 3000, 3000)).isTrue();
        }

        @Test
        @DisplayName("many threads racing for the same table: exactly one gets the turn")
        void concurrentCallers_returnsTrueForExactlyOne() throws InterruptedException {
            int threads = 16;
            AtomicInteger winners = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                new Thread(
                                () -> {
                                    try {
                                        start.await();
                                        if (gate.tryAcquire(KEY, true, 5, 5)) {
                                            winners.incrementAndGet();
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } finally {
                                        done.countDown();
                                    }
                                })
                        .start();
            }

            start.countDown();

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(winners.get()).isEqualTo(1);
        }

        @Test
        @DisplayName(
                "last range to finish is empty: merged once, from the finish notification, not"
                        + " from the batch callbacks")
        void lastRangeEmptyAfterAllImports_returnsTrueOnceFromTheFinishNotification() {
            MigrationStatusManager status = new MigrationStatusManager();
            AtomicInteger merges = new AtomicInteger();
            // What LoadFileImporter does from both events.
            Runnable tryMerge =
                    () -> {
                        if (gate.tryAcquire(
                                KEY,
                                status.getExpFlag(OWNER, TABLE),
                                status.getExpCount(OWNER, TABLE),
                                status.getImpCount(OWNER, TABLE))) {
                            merges.incrementAndGet();
                        }
                    };
            status.addTableExportFinishedListener((owner, source) -> tryMerge.run());
            status.registerExportRangeCount(OWNER, TABLE, 4);

            // Ranges 1-3 export one batch of 1000 rows each and finish; range 4 is still running.
            for (int range = 1; range <= 3; range++) {
                status.addExpCount(OWNER, TABLE, 1000);
                status.setExpFinished(OWNER, TABLE);
            }
            // Every batch is imported; each import callback checks, but the export is not over.
            for (int batch = 1; batch <= 3; batch++) {
                status.addImpCount(OWNER, TABLE, 1000);
                tryMerge.run();
            }
            assertThat(merges.get()).isZero();

            // Range 4 matched no rows, so it never produced a batch and no callback follows.
            status.setExpFinished(OWNER, TABLE);

            assertThat(merges.get()).isEqualTo(1);
        }

        @Test
        @DisplayName(
                "last batch imported after the export finished: merged once, from the batch"
                        + " callback")
        void lastBatchImportedAfterExportFinished_returnsTrueOnceFromTheBatchCallback() {
            MigrationStatusManager status = new MigrationStatusManager();
            AtomicInteger merges = new AtomicInteger();
            Runnable tryMerge =
                    () -> {
                        if (gate.tryAcquire(
                                KEY,
                                status.getExpFlag(OWNER, TABLE),
                                status.getExpCount(OWNER, TABLE),
                                status.getImpCount(OWNER, TABLE))) {
                            merges.incrementAndGet();
                        }
                    };
            status.addTableExportFinishedListener((owner, source) -> tryMerge.run());

            status.addExpCount(OWNER, TABLE, 1000);
            status.addImpCount(OWNER, TABLE, 500);
            // The export ends while half of the rows are still being imported: nothing to merge
            // yet.
            status.setExpFinished(OWNER, TABLE);
            assertThat(merges.get()).isZero();

            status.addImpCount(OWNER, TABLE, 500);
            tryMerge.run();

            assertThat(merges.get()).isEqualTo(1);
        }
    }
}
