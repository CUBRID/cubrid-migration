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
package com.cubrid.cubridmigration.mssql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("MSSQLSQLHelper")
class MSSQLSQLHelperTest {

    private static final MSSQLSQLHelper HELPER = MSSQLSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnsSameInstance() {
            assertThat(MSSQLSQLHelper.getInstance(null)).isSameAs(MSSQLSQLHelper.getInstance("16"));
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("names are bracketed verbatim")
        @CsvSource({"t1, [t1]", "MiXeD, [MiXeD]", "'', []"})
        void objectName_returnsBracketedName(String objectName, String expected) {
            assertThat(HELPER.getQuotedObjName(objectName)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getUnquotedObjName()")
    class GetUnquotedObjName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("every bracket is stripped, wherever it sits")
        @CsvSource({
            "[t1],        t1",
            "t1,          t1",

            // The brackets are removed as characters, not as a matched pair.
            "'[t1',       t1",
            "'a[b]c',     abc",
        })
        void objectName_returnsNameWithoutBrackets(String objectName, String expected) {
            assertThat(HELPER.getUnquotedObjName(objectName)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @Test
        @DisplayName("no ORDER BY -> the statement is wrapped in a SELECT TOP 1")
        void withoutOrderBy_wrapsInSelectTop1() {
            assertThat(HELPER.getTestSelectSQL("select * from t"))
                    .isEqualTo("SELECT TOP 1 * FROM (select * from t) tartbl");
        }

        @Test
        @DisplayName("ORDER BY without a TOP -> TOP 1 is injected into the existing SELECT")
        void orderByWithoutTop_injectsSelectTop1() {
            assertThat(HELPER.getTestSelectSQL("select * from t order by c1"))
                    .isEqualTo("SELECT TOP 1 * from t order by c1");
        }

        @Test
        @DisplayName("ORDER BY with a TOP already present -> handed back untouched")
        void orderByWithTop_returnsTheStatementUnchanged() {
            String sql = "select top 10 * from t order by c1";

            assertThat(HELPER.getTestSelectSQL(sql)).isEqualTo(sql);
        }

        @Test
        @DisplayName("a paging placeholder is filled in and the TOP rewrite is skipped")
        void sqlWithPagingParameter_returnsSubstitutedSQL() {
            assertThat(HELPER.getTestSelectSQL("select * from t where rn <= #pageSize#"))
                    .isEqualTo("select * from t where rn <= 1");
        }
    }
}
