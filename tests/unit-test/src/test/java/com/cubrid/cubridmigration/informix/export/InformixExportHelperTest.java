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
package com.cubrid.cubridmigration.informix.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InformixExportHelper")
class InformixExportHelperTest {

    private static final InformixExportHelper HELPER = new InformixExportHelper();

    @Nested
    @DisplayName("getPagedSelectSQL()")
    class GetPagedSelectSQL {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> unchanged")
        @DisplayName("the statement comes back unpaged whatever the page arguments are")
        @ValueSource(strings = {"select * from t", "select * from t where 1=1"})
        void selectSQL_returnsTheStatementUnchanged(String sql) {
            // DEFECT: the body is a "TODO Auto-generated method stub" returning its input, so an
            // Informix source is read in one statement, as with Oracle
            // - see InformixExportHelper.getPagedSelectSQL()
            assertThat(HELPER.getPagedSelectSQL(sql, 1000, 1000, null)).isEqualTo(sql);
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @Test
        @DisplayName("the name is returned unquoted")
        void objectName_returnsTheNameUnchanged() {
            // DEFECT: the delegation to the Informix SQL helper is commented out behind a
            // "TODO Auto-generated method stub", so nothing is quoted
            // - see InformixExportHelper.getQuotedObjName()
            assertThat(HELPER.getQuotedObjName("t1")).isEqualTo("t1");
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("the helper reports its own dialect")
        void helper_returnsInformixDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.INFORMIX);
        }
    }
}
