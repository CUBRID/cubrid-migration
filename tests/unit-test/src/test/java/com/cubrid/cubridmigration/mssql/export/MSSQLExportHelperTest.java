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
package com.cubrid.cubridmigration.mssql.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MSSQLExportHelper")
class MSSQLExportHelperTest {

    private static final MSSQLExportHelper HELPER = new MSSQLExportHelper();

    @Nested
    @DisplayName("getPagedSelectSQL()")
    class GetPagedSelectSQL {

        @Test
        @DisplayName("paging placeholders are filled in from the page arguments")
        void sqlWithPagingParameters_returnsSubstitutedSQL() {
            String sql =
                    "select * from t where rn between #pageStartPosition# and #pageEndPosition#"
                            + " order by column_one, column_two";

            assertThat(HELPER.getPagedSelectSQL(sql, 1000, 2000, null))
                    .isEqualTo(
                            "select * from t where rn between 2000 and 2999 order by column_one,"
                                    + " column_two");
        }

        @Test
        @DisplayName("a placeholder near the end -> StringIndexOutOfBoundsException")
        void placeholderNearTheEnd_throwsStringIndexOutOfBoundsException() {
            // DEFECT: every '#' is read as parameterName.length() characters without checking
            // what is left, and the longest name is 19 characters, so any '#' with fewer than
            // that remaining overruns the string
            // - see SQLHelper.replacePageQueryParameterToValue()
            String sql = "select * from t where rn <= #pageEndPosition#";

            assertThatThrownBy(() -> HELPER.getPagedSelectSQL(sql, 1000, 2000, null))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("a statement with no placeholder is handed back unchanged")
        void sqlWithoutPagingParameters_returnsTheStatementUnchanged() {
            assertThat(HELPER.getPagedSelectSQL("select * from t", 1000, 0, null))
                    .isEqualTo("select * from t");
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("quoting is delegated to the MSSQL SQL helper")
        void objectName_returnsBracketedName() {
            assertThat(HELPER.getQuotedObjName("t1")).isEqualTo("[t1]");
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("the helper reports its own dialect")
        void helper_returnsMssqlDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.MSSQL);
        }
    }
}
