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
package com.cubrid.cubridmigration.core.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.engine.event.MigrationErrorEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@DisplayName("ThreadUtils")
class ThreadUtilsTest {

    private final List<MigrationEvent> events = new ArrayList<>();

    private final IMigrationEventHandler handler =
            new IMigrationEventHandler() {
                public void handleEvent(MigrationEvent event) {
                    events.add(event);
                }

                public void dispose() {}
            };

    @AfterEach
    void clearInterruptStatus() {
        Thread.interrupted();
    }

    @Nested
    @DisplayName("threadSleep()")
    class ThreadSleep {

        @Test
        @DisplayName("an interrupted sleep reaches the handler as a fatal MigrationErrorEvent")
        void interruptedSleep_reachesTheHandler() {
            Thread.currentThread().interrupt();

            ThreadUtils.threadSleep(10_000, handler);

            assertThat(events)
                    .singleElement()
                    .isInstanceOfSatisfying(
                            MigrationErrorEvent.class,
                            event -> {
                                assertThat(event.getError())
                                        .isInstanceOf(InterruptedException.class);
                                assertThat(event.isFatalError()).isTrue();
                            });
        }

        @Test
        @DisplayName("an interrupted sleep returns with the interrupt status cleared")
        void interruptedSleep_clearsTheInterruptStatus() {
            // DEFECT: the InterruptedException is caught without restoring the status, so without
            // a handler, as most callers pass, the interrupt is lost without a trace
            // - see ThreadUtils.threadSleep()
            Thread.currentThread().interrupt();

            ThreadUtils.threadSleep(10_000, null);

            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        }

        @Test
        @DisplayName("a negative time throws IllegalArgumentException past the handler")
        void negativeTime_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> ThreadUtils.threadSleep(-1, handler))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(events).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleException()")
    class HandleException {

        @Test
        @DisplayName("the exception reaches the handler wrapped in a MigrationErrorEvent")
        void exception_reachesTheHandler() {
            InterruptedException exception = new InterruptedException();

            ThreadUtils.handleException(handler, exception);

            assertThat(events)
                    .singleElement()
                    .isInstanceOfSatisfying(
                            MigrationErrorEvent.class,
                            event -> assertThat(event.getError()).isSameAs(exception));
        }

        @Test
        @DisplayName("without a handler nothing happens")
        void noHandler_doesNothing() {
            assertThatCode(() -> ThreadUtils.handleException(null, null))
                    .doesNotThrowAnyException();
        }
    }
}
