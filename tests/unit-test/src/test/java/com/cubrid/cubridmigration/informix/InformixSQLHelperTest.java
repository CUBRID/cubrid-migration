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
package com.cubrid.cubridmigration.informix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("InformixSQLHelper")
class InformixSQLHelperTest {

    private static final InformixSQLHelper HELPER = InformixSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnsSameInstance() {
            assertThat(InformixSQLHelper.getInstance(null))
                    .isSameAs(InformixSQLHelper.getInstance("14.10"));
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("names are wrapped in backticks, which Informix does not use")
        @CsvSource({"t1, '`t1`'", "MiXeD, '`MiXeD`'"})
        void objectName_returnsBacktickedName(String objectName, String expected) {
            // DEFECT: Informix quotes identifiers with double quotes; the backticks are carried
            // over from the MySQL helper this one was copied from
            // - see InformixSQLHelper.getQuotedObjName()
            assertThat(HELPER.getQuotedObjName(objectName)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> a never-true subquery")
        @DisplayName("the statement is wrapped in an aliased subquery that returns no row")
        @CsvSource({
            "'select * from t',   'SELECT * FROM ( select * from t ) AS tartbl WHERE 1<>1'",

            // A trailing semicolon is removed before wrapping.
            "'select * from t;',  'SELECT * FROM ( select * from t ) AS tartbl WHERE 1<>1'",
            "'  select 1  ',      'SELECT * FROM ( select 1 ) AS tartbl WHERE 1<>1'",
        })
        void selectSQL_isWrappedInAlwaysFalseFilter(String sql, String expected) {
            assertThat(HELPER.getTestSelectSQL(sql)).isEqualTo(expected);
        }
    }
}
