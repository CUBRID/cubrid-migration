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
package com.cubrid.cubridmigration.cubrid.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CUBRIDExportHelper")
class CUBRIDExportHelperTest {

    private static final CUBRIDExportHelper HELPER = new CUBRIDExportHelper();

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("names are bracketed, not double quoted")
        void objectName_returnsBracketedName() {
            assertThat(HELPER.getQuotedObjName("t1")).isEqualTo("[t1]");
        }
    }

    @Nested
    @DisplayName("getPagedSelectSQL()")
    class GetPagedSelectSQL {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("the row filter matches the clause the statement already ends with")
        @CsvSource({
            // A bare select takes ROWNUM.
            "'select * from code'," + " 'select * from code WHERE ROWNUM  BETWEEN 1001 AND 2000'",

            // ORDER BY and GROUP BY have their own numbering functions.
            "'select * from code order by f1',"
                    + " 'select * from code order by f1 FOR ORDERBY_NUM()  BETWEEN 1001 AND 2000'",
            "'select * from code group by f1', 'select * from code group by f1 HAVING "
                    + " GROUPBY_NUM()  BETWEEN 1001 AND 2000'",

            // An existing HAVING or WHERE is extended with AND instead of replaced.
            "'select * from code group by f1 having f1=1', 'select * from code group by f1 having"
                    + " f1=1 AND  GROUPBY_NUM()  BETWEEN 1001 AND 2000'",
            "'select * from code where 1=1 ',"
                    + " 'select * from code where 1=1 AND ROWNUM  BETWEEN 1001 AND 2000'",
        })
        void selectSQL_returnsPagedSQL(String sql, String expected) {
            assertThat(HELPER.getPagedSelectSQL(sql, 1000, 1000, null)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getSelectCountSQL()")
    class GetSelectCountSQL {

        @Test
        @DisplayName("an owner becomes a bracketed prefix on the table name")
        void configWithOwner_prefixesTheTable() {
            assertThat(HELPER.getSelectCountSQL(config("o1", "where f1=1")))
                    .isEqualTo("SELECT COUNT(1)  FROM [o1].[t1] where f1=1");
        }

        @Test
        @DisplayName("a blank owner leaves the table name bare")
        void configWithoutOwner_omitsThePrefix() {
            assertThat(HELPER.getSelectCountSQL(config("", "f1=1")))
                    .isEqualTo("SELECT COUNT(1)  FROM [t1] WHERE  f1=1");
        }

        @Test
        @DisplayName("a condition already opening on WHERE is not given a second one")
        void conditionStartingWithWhere_isAppendedAsIs() {
            assertThat(HELPER.getSelectCountSQL(config("o1", "WHERE f1=1")))
                    .isEqualTo("SELECT COUNT(1)  FROM [o1].[t1] WHERE f1=1");
        }

        @Test
        @DisplayName("a trailing semicolon is stripped off the condition")
        void conditionEndingWithSemicolon_dropsIt() {
            assertThat(HELPER.getSelectCountSQL(config("o1", "where f1=1;")))
                    .isEqualTo("SELECT COUNT(1)  FROM [o1].[t1] where f1=1");
        }

        @Test
        @DisplayName("no condition at all -> plain count")
        void configWithoutCondition_returnsPlainCount() {
            assertThat(HELPER.getSelectCountSQL(config("o1", null)))
                    .isEqualTo("SELECT COUNT(1)  FROM [o1].[t1]");
        }

        private SourceEntryTableConfig config(String owner, String condition) {
            SourceEntryTableConfig setc = new SourceEntryTableConfig();
            setc.setName("t1");
            setc.setTarget("t1");
            setc.setOwner(owner);
            setc.setCondition(condition);
            return setc;
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("the helper reports its own dialect")
        void helper_returnsCubridDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.CUBRID);
        }
    }
}
