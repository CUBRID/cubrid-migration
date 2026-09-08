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
package com.cubrid.cubridmigration.tibero.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TiberoExportHelper")
class TiberoExportHelperTest {

    private static final TiberoExportHelper HELPER = new TiberoExportHelper();

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("returns DatabaseType.TIBERO")
        void returnsTypeTibero() {
            assertThat(HELPER.getDBType()).isEqualTo(DatabaseType.TIBERO);
        }
    }

    @Nested
    @DisplayName("getPagedSelectSQL")
    class GetPagedSelectSQL {

        @Test
        @DisplayName("first page -> ROWNUM <= 100 and CMT_ROWNUM > 0")
        void firstPage_correctRownumBounds() {
            String sql = "SELECT * FROM EMP";
            String result = HELPER.getPagedSelectSQL(sql, 100, 0, null);

            assertThat(result)
                    .startsWith("SELECT * FROM (SELECT CMT_PAGED_.*, ROWNUM CMT_ROWNUM FROM (");
            assertThat(result).contains("SELECT * FROM EMP");
            assertThat(result).contains(") CMT_PAGED_ WHERE ROWNUM <= 100");
            assertThat(result).contains(") WHERE CMT_ROWNUM > 0");
        }

        @Test
        @DisplayName("second page -> ROWNUM <= 200 and CMT_ROWNUM > 100")
        void secondPage_correctRownumBounds() {
            String result = HELPER.getPagedSelectSQL("SLECT * FROM EMP", 100, 100, null);

            assertThat(result).contains("ROWNUM <= 200");
            assertThat(result).contains("CMT_ROWNUM > 100");
        }

        @ParameterizedTest(name = "[{index}] rows={0}, exported={1} -> endRow={2}, start={3}")
        @DisplayName("checks endRow for rows/exportedRecords")
        @CsvSource({
            "50,   0,   50,  0",
            "50,  50,  100, 50",
            "200,  0,  200,  0",
            "1,    0,    1,  0",
        })
        void rownumBounds_variousCombinations(
                long rows, long exported, long expectedEnd, long expectedStart) {
            String result = HELPER.getPagedSelectSQL("SELECT 1 FROM DUAL", rows, exported, null);

            assertThat(result).contains("ROWNUM <= " + expectedEnd);
            assertThat(result).contains("CMT_ROWNUM > " + expectedStart);
        }

        @Test
        @DisplayName("trims sql")
        void sql_trimmed() {
            String result = HELPER.getPagedSelectSQL("  SELECT * FROM EMP  ", 10, 0, null);

            assertThat(result).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("works with null pk")
        void nullPk_works() {
            String result = HELPER.getPagedSelectSQL("SELECT * FROM T", 10, 0, null);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
