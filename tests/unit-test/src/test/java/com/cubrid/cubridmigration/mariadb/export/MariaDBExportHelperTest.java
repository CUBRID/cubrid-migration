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
package com.cubrid.cubridmigration.mariadb.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MariaDBExportHelper")
class MariaDBExportHelperTest {

    private static final MariaDBExportHelper HELPER = new MariaDBExportHelper();

    @Nested
    @DisplayName("matchMariaDBLimit()")
    class MatchMariaDBLimit {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("a row count, a comma pair or an OFFSET clause all count as a limit")
        @CsvSource({
            "' LIMIT 10 ',            true",
            "' LIMIT 10 , 12',        true",
            "' LIMIT 10 OFFSET 12',   true",

            // Two bare numbers are not MySQL limit syntax.
            "' LIMIT 10 12',          false",
            "' LIMIT ',               false",
            "' where 1=1',            false",
            "'',                      false",
        })
        void limitPart_returnsWhetherItIsALimit(String limitPart, boolean expected) {
            assertThat(HELPER.matchMariaDBLimit(limitPart)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("replaceWithMariaDBLimit0()")
    class ReplaceWithMariaDBLimit0 {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("a recognised limit collapses to LIMIT 0, anything else is untouched")
        @CsvSource({
            "' LIMIT 10 ss',           ' LIMIT 0 ss'",
            "' LIMIT 10 , 12 dd',      ' LIMIT 0 dd'",
            "' LIMIT 10 OFFSET 12',    ' LIMIT 0 '",

            // Not a limit, so it is handed back unchanged.
            "' LIMIT 10 12',           ' LIMIT 10 12'",
            "' LIMIT ',                ' LIMIT '",
        })
        void limitPart_returnsZeroLimit(String limitPart, String expected) {
            assertThat(HELPER.replaceWithMariaDBLimit0(limitPart)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("the helper reports its own dialect")
        void helper_returnsMariaDBDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.MARIADB);
        }
    }
}
