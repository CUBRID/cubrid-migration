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
package com.cubrid.cubridmigration.tibero;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TiberoSQLHelper")
class TiberoSQLHelperTest {

    private static final TiberoSQLHelper HELPER = TiberoSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @Test
        @DisplayName("SELECT * FROM T -> wrapped with WHERE 1<>1")
        void simpleSelect_wrapsWithWhereClause() {
            String sql = "SELECT * FROM EMP";

            String result = HELPER.getTestSelectSQL(sql);

            assertThat(result).isEqualTo("SELECT * FROM ( SELECT * FROM EMP ) WHERE 1<>1");
        }

        @Test
        @DisplayName("result contains original SQL")
        void result_containsOriginalSql() {
            String sql = "SELECT ID, NAME FROM ACCOUNTS WHERE STATUS = 'ACTIVE'";

            String result = HELPER.getTestSelectSQL(sql);

            assertThat(result).contains(sql);
            assertThat(result).startsWith("SELECT * FROM (");
            assertThat(result).endsWith("WHERE 1<>1");
        }

        @Test
        @DisplayName("result is a zero-row query")
        void result_hasZeroRowCondition() {
            String result = HELPER.getTestSelectSQL("SELECT 1 FROM DUAL");

            assertThat(result).contains("WHERE 1<>1");
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("normal table name -> double quotes")
        void tableName_wrapsInDoubleQoutes() {
            assertThat(HELPER.getQuotedObjName("MY_TABLE")).isEqualTo("\"MY_TABLE\"");
        }

        @Test
        @DisplayName("lowercase object name -> keep case")
        void lowercase_wrapsWithoutCaseChange() {
            assertThat(HELPER.getQuotedObjName("my_view")).isEqualTo("\"my_view\"");
        }

        @Test
        @DisplayName("reserved word -> double quotes")
        void reservedWord_wrapsInDoubleQuotes() {
            assertThat(HELPER.getQuotedObjName("SELECT")).isEqualTo("\"SELECT\"");
        }

        @Test
        @DisplayName("empty string -> two double quotes")
        void emptyString_returnsEmptyQuotes() {
            assertThat(HELPER.getQuotedObjName("")).isEqualTo("\"\"");
        }
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance")
        void singleton_returnsSameInstance() {
            assertThat(TiberoSQLHelper.getInstance(null))
                    .isSameAs(TiberoSQLHelper.getInstance("7.0"));
        }
    }
}
