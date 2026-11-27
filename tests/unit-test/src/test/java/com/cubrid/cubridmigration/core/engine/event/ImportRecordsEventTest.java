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
package com.cubrid.cubridmigration.core.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ImportRecordsEvent")
class ImportRecordsEventTest {

    private static SourceTableConfig sourceTable() {
        SourceTableConfig table = new SourceTableConfig();
        table.setName("test");
        table.setTarget("target");
        return table;
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("a count reads as the records imported from the source into the target")
        void count_readsTheSourceAndTheTarget() {
            assertThat(new ImportRecordsEvent(sourceTable(), 100))
                    .hasToString(
                            "Imported 100 records from [test] to table [target] successfully.");
        }

        @Test
        @DisplayName("a failure reads as unsuccessfully with the error message")
        void failure_readsTheErrorMessage() {
            ImportRecordsEvent event =
                    new ImportRecordsEvent(
                            sourceTable(), 100, new NormalMigrationException("test error"), null);

            assertThat(event)
                    .hasToString(
                            "Imported 100 records from [test] to table [target] unsuccessfully."
                                    + " Error:test error");
        }

        @Test
        @DisplayName("no records read as nothing to import into the target")
        void zeroRecords_readAsNothingToImport() {
            assertThat(new ImportRecordsEvent(sourceTable(), 0))
                    .hasToString("No record of table [target] to be imported.");
        }

        @Test
        @DisplayName("a failure with no records reads the same, without its error")
        void failureWithZeroRecords_losesTheError() {
            // DEFECT: the zero count check comes before the success check, so a failed import of
            // no records reads as nothing to import and its error never shows
            // - see ImportRecordsEvent.toString()
            ImportRecordsEvent event =
                    new ImportRecordsEvent(
                            sourceTable(), 0, new NormalMigrationException("test error"), null);

            assertThat(event).hasToString("No record of table [target] to be imported.");
        }

        @Test
        @DisplayName("a failure without an error throws NullPointerException")
        void failureWithoutError_throwsNullPointerException() {
            ImportRecordsEvent event = new ImportRecordsEvent(sourceTable(), 100, null, null);

            assertThatThrownBy(event::toString).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getLevel()")
    class GetLevel {

        @Test
        @DisplayName("a success event is level 2 and a failure event level 1")
        void failure_isLevelOne() {
            ImportRecordsEvent failed =
                    new ImportRecordsEvent(
                            sourceTable(), 100, new NormalMigrationException("test error"), null);

            assertThat(new ImportRecordsEvent(sourceTable(), 100).getLevel()).isEqualTo(2);
            assertThat(failed.getLevel()).isEqualTo(1);
        }
    }
}
