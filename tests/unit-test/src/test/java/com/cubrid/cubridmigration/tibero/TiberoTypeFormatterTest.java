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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static com.cubrid.cubridmigration.testutil.TestColumnFactory.*;

@DisplayName("TiberoTypeFormatter")
public class TiberoTypeFormatterTest {

    private static final TiberoDataTypeHelper HELPER = TiberoDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("NUMBER format")
    class NumberFormat {

        @Test
        @DisplayName("NUMBER (precision=null) -> \"NUMBER\"")
        void numberNoPrecision_returnsNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER"), HELPER))
                    .isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("NUMBER (precision=0) -> \"NUMBER\"")
        void numberPrecision0_returnNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 0, null), HELPER))
                    .isEqualTo("NUMBER");
        }

        @Test
        @DisplayName("NUMBER(10) -> \"NUMBER(10,0)\"")
        void numberPrecisionOnly_returnsNumberWithPrecision() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 10, null), HELPER))
                    .isEqualTo("NUMBER(10,0)");
        }

        @Test
        @DisplayName("NUMBER(10,2) → \"NUMBER(10,2)\"")
        void numberPrecisionAndScale_returnsNumberWithBoth() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 10, 2), HELPER))
                    .isEqualTo("NUMBER(10,2)");
        }

        @Test
        @DisplayName("NUMBER(38,15) -> \"NUMBER(38,15)\"")
        void numberMaxPrecision_returnsFormattedNumber() {
            assertThat(TiberoTypeFormatter.format(createColumn("NUMBER", 38, 15), HELPER))
                    .isEqualTo("NUMBER(38,15)");
        }
    }

    @Nested
    @DisplayName("string type format")
    class StringTypeFormat {

        @ParameterizedTest(name = "[{index}] {0}{1} -> \"{0}({1})\"")
        @CsvSource({
            "VARCHAR, 100",
            "VARCHAR, 4000",
            "VARCHAR2, 100",
            "VARCHAR2, 4000",
            "CHAR,     10",
            "NCHAR,    10",
            "NVARCHAR2,200",
        })
        void stringTypesWithPrecision_returnsTypeWithPrecision(String dataType, int precision) {
            assertThat(TiberoTypeFormatter.format(createColumn(dataType, precision, null), HELPER))
                    .isEqualTo(dataType + "(" + precision + ")");
        }

        @Test
        @DisplayName("VARCHAR with precision=null -> \"VARCHAR\"")
        void varcharNoPrecision_returnTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("VARCHAR"), HELPER))
                    .isEqualTo("VARCHAR");
        }

        @Test
        @DisplayName("VARCHAR with precision=0 -> \"VARCHAR\"")
        void varcharPrecision0_returnsTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("VARCHAR", 0, null), HELPER))
                    .isEqualTo("VARCHAR");
        }

        @Test
        @DisplayName("CHAR(10 CHAR) when charUsed='C")
        void charWithCharSemantics_appendsCharSuffix() {
            assertThat(TiberoTypeFormatter.format(createCharColumn("CHAR", 10, "C"), HELPER))
                    .isEqualTo("CHAR(10 CHAR)");
        }

        @Test
        @DisplayName("CHAR(10) when charUsed='B'")
        void charWithByteSemantics_noCHARSuffix() {
            assertThat(TiberoTypeFormatter.format(createCharColumn("CHAR", 10, "B"), HELPER))
                    .isEqualTo("CHAR(10)");
        }
    }

    @Nested
    @DisplayName("RAW format")
    class RawTypeFormat {

        @Test
        @DisplayName("RAW(100) -> \"RAW(100)\"")
        void rawWithPrecision_returnWithPrecision() {
            assertThat(TiberoTypeFormatter.format(createColumn("RAW", 100, null), HELPER))
                    .isEqualTo("RAW(100)");
        }

        @Test
        @DisplayName("RAW (precision=null) → \"RAW\"")
        void rawNoPrecision_returnsTypeOnly() {
            assertThat(TiberoTypeFormatter.format(createColumn("RAW"), HELPER))
                    .isEqualTo("RAW");
        }
    }

    @Nested
    @DisplayName("pass-through types")
    class PassThroughTypes {

        @ParameterizedTest(name = "[{index}] {0} → {0}")
        @CsvSource({
            "TIMESTAMP",
            "DATE",
            "CLOB",
            "BLOB",
            "XMLTYPE",
            "JSON",
            "ROWID",
            "INTERVAL DAY TO SECOND",
            "INTERVAL YEAR TO MONTH",
            "TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP WITH LOCAL TIME ZONE",
            "BINARY_FLOAT",
            "BINARY_DOUBLE",
        })
        void passThroughTypes_returnedUnchanged(String dataType) {
            assertThat(TiberoTypeFormatter.format(createColumn(dataType), HELPER))
                    .isEqualTo(dataType);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("dataType=null -> empty string")
        void nullDataType_returnsEmptyString() {
            assertThat(TiberoTypeFormatter.format(createColumn(null), HELPER))
                    .isEmpty();
        }
    }
}
