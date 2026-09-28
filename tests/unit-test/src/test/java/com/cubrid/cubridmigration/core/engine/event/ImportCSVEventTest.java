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

import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImportCSVEvent")
class ImportCSVEventTest {

    private static SourceCSVConfig csv() {
        SourceCSVConfig csv = new SourceCSVConfig();
        csv.setName("a.csv");
        csv.setTarget("target");
        return csv;
    }

    @Test
    @DisplayName("a count reads as the rows imported into the target")
    void toString_readsTheCountAndTheTarget() {
        assertThat(new ImportCSVEvent(csv(), 4, 10))
                .hasToString("Imported 4 row(s) into [target] successfully.");
    }

    @Test
    @DisplayName("a failure reads as unsuccessfully with the error and its class name")
    void toString_readsTheErrorWithItsClassName() {
        ImportCSVEvent failed =
                new ImportCSVEvent(csv(), 4, 10, new NormalMigrationException("bad"), "e.sql");

        assertThat(failed)
                .hasToString(
                        "Imported 4 row(s) into [target] unsuccessfully. Error:"
                                + "com.cubrid.cubridmigration.core.engine.exception"
                                + ".NormalMigrationException: bad");
        assertThat(new ImportCSVEvent(csv(), 4, 10, null, null))
                .hasToString("Imported 4 row(s) into [target] unsuccessfully. Error:null");
    }

    @Test
    @DisplayName("no rows read as nothing imported in the config object itself, error or not")
    void toString_readsTheConfigObjectForZero() {
        // DEFECT: SourceCSVConfig does not override toString(), so the message shows its class
        // name and hash code instead of the file, and a failure loses its error
        // - see ImportCSVEvent.toString()
        SourceCSVConfig csv = csv();
        String expected =
                "No data imported in [com.cubrid.cubridmigration.core.engine.config"
                        + ".SourceCSVConfig@"
                        + Integer.toHexString(csv.hashCode())
                        + "].";

        assertThat(new ImportCSVEvent(csv, 0, 10)).hasToString(expected);
        assertThat(new ImportCSVEvent(csv, 0, 10, new NormalMigrationException("bad"), "e.sql"))
                .hasToString(expected);
    }
}
