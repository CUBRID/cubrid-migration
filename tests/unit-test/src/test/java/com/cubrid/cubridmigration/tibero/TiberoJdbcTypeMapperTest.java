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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Types;
import java.util.stream.Stream;

@DisplayName("TiberoJdbcTypeMapper")
class TiberoJdbcTypeMapperTest {

    @Nested
    @DisplayName("getFixedJdbcTypeId()")
    class GetFixedJdbcTypeId {

        @Test
        @DisplayName("BINARY_FLOAT -> Types.FLOAT")
        void binaryFloat_returnsFloat() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("BINARY_FLOAT"))
                    .isEqualTo(Types.FLOAT);
        }

        @Test
        @DisplayName("BINARY_DOUBLE -> Types.DOUBLE")
        void binaryDouble_returnsDouble() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("BINARY_DOUBLE"))
                    .isEqualTo(Types.DOUBLE);
        }

        @Test
        @DisplayName("ROWID -> Types.VARCHAR")
        void rowid_returnsVarchar() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("ROWID")).isEqualTo(Types.VARCHAR);
        }

        @Test
        @DisplayName("TIMESTAMP WITH TIME ZONE -> Types.TIMESTAMP_WITH_TIMEZONE")
        void timestampWithTimeZone_returnsTimestamp() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("TIMESTAMP WITH TIME ZONE"))
                    .isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        }

        @Test
        @DisplayName("TIMESTAMP WITH LOCAL TIME ZONE -> Types.TIMESTAMP")
        void timestampWithLocalTimeZone_returnsTimestamp() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("TIMESTAMP WITH LOCAL TIME ZONE"))
                    .isEqualTo(Types.TIMESTAMP);
        }

        @Test
        @DisplayName("INTERVAL DAY TO SECOND -> Types.OTHER")
        void intervalDayToSecond_returnsOther() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("INTERVAL DAY TO SECOND"))
                    .isEqualTo(Types.OTHER);
        }

        @Test
        @DisplayName("INTERVAL YEAR TO MONTH -> Types.OTHER")
        void intervalYearToMonth_returnsOther() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("INTERVAL YEAR TO MONTH"))
                    .isEqualTo(Types.OTHER);
        }

        @Test
        @DisplayName("XMLTYPE -> Types.SQLXML")
        void xmlType_returnsSqlXml() {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId("XMLTYPE")).isEqualTo(Types.SQLXML);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null for non-fixed type")
        @DisplayName("no fixed mapping for VARCHAR2/NUMBER/CLOB/BLOB/DATE/TIMESTAMP")
        @ValueSource(strings = {"VARCHAR2", "NUMBER", "CLOB", "BLOB", "DATE", "TIMESTAMP"})
        void nonFixedTypes_returnsNull(String dataType) {
            assertThat(TiberoJdbcTypeMapper.getFixedJdbcTypeId(dataType)).isNull();
        }
    }

    @Nested
    @DisplayName("getNumberType")
    class GetNumberType {

        @Test
        @DisplayName("precision=null, scale=null -> NUMERIC")
        void nullPrecisionNullScale_returnsNumeric() {
            assertThat(TiberoJdbcTypeMapper.getNumberType(null, null)).isEqualTo(Types.NUMERIC);
        }

        @Test
        @DisplayName("precision=null, scale=0 -> BIGINT")
        void nullPrecisionScale0_returnsBigint() {
            assertThat(TiberoJdbcTypeMapper.getNumberType(null, 0)).isEqualTo(Types.BIGINT);
        }

        @ParameterizedTest(name = "[{index}] NUMBER({0},{1}) -> JDBC {2}")
        @DisplayName("precision -> integer width; scale or precision > 38 -> NUMERIC")
        @MethodSource(
                "com.cubrid.cubridmigration.tibero.TiberoJdbcTypeMapperTest#precisionScaleToJdbcType")
        void precisionScale_mapsToCorrectJdbcType(
                Integer precision, Integer scale, int expectedJdbcType) {
            assertThat(TiberoJdbcTypeMapper.getNumberType(precision, scale))
                    .isEqualTo(expectedJdbcType);
        }
    }

    static Stream<Arguments> precisionScaleToJdbcType() {
        return Stream.of(
                // precision1 -> BIT
                Arguments.of(1, 0, Types.BIT),
                Arguments.of(1, null, Types.BIT),

                // precision=3 -> TINYINT
                Arguments.of(3, 0, Types.TINYINT),
                Arguments.of(3, null, Types.TINYINT),

                // precision=5 -> SMALLINT
                Arguments.of(5, 0, Types.SMALLINT),
                Arguments.of(5, null, Types.SMALLINT),

                // precision 2, 4, 6~10 -> INTEGER
                Arguments.of(2, 0, Types.INTEGER),
                Arguments.of(4, 0, Types.INTEGER),
                Arguments.of(6, 0, Types.INTEGER),
                Arguments.of(9, 0, Types.INTEGER),
                Arguments.of(10, 0, Types.INTEGER),

                // precision 11~38 -> BIGINT
                Arguments.of(11, 0, Types.BIGINT),
                Arguments.of(18, 0, Types.BIGINT),
                Arguments.of(38, 0, Types.BIGINT),

                // precision > 38 -> NUMERIC
                Arguments.of(39, 0, Types.NUMERIC),

                // scale set -> NUMERIC
                Arguments.of(10, 2, Types.NUMERIC),
                Arguments.of(38, 15, Types.NUMERIC));
    }
}
