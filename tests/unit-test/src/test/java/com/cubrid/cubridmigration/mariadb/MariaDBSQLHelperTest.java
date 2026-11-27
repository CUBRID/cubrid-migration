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
package com.cubrid.cubridmigration.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MariaDBSQLHelper")
class MariaDBSQLHelperTest {

    private static final MariaDBSQLHelper HELPER = MariaDBSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnsSameInstance() {
            assertThat(MariaDBSQLHelper.getInstance(null))
                    .isSameAs(MariaDBSQLHelper.getInstance("8.0"));
        }
    }

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("an existing limit clause is stripped before LIMIT 1 is appended")
        @CsvSource({
            "'select * from t',              'select * from t LIMIT 1'",

            // A trailing semicolon goes first, then the limit clause.
            "'select * from t;',             'select * from t LIMIT 1'",
            "'select * from t limit 100',    'select * from t LIMIT 1'",
            "'select * from t LIMIT 10, 20', 'select * from t LIMIT 1'",
            // Surrounding whitespace is trimmed off the input.
            "'  select * from t  ',          'select * from t LIMIT 1'",
        })
        void selectSQL_returnsSingleRowProbe(String sql, String expected) {
            assertThat(HELPER.getTestSelectSQL(sql)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an OFFSET clause loses only its keyword, so the row count is left behind")
        void limitWithOffset_leavesTheOffsetValueInTheSQL() {
            // DEFECT: LIMIT_PATTEN_1 runs first and its trailing \\D* consumes the word OFFSET
            // but not its digits, so LIMIT_PATTEN_2 never matches and the probe SQL is malformed
            // - see MariaDBSQLHelper.getTestSelectSQL()
            assertThat(HELPER.getTestSelectSQL("select * from t limit 10 offset 5"))
                    .isEqualTo("select * from t5 LIMIT 1");
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("names are wrapped in backticks verbatim")
        @CsvSource({
            "t1,      '`t1`'",
            "MiXeD,   '`MiXeD`'",
            "'',      '``'",
        })
        void objectName_returnsBacktickedName(String objectName, String expected) {
            assertThat(HELPER.getQuotedObjName(objectName)).isEqualTo(expected);
        }
    }
}
