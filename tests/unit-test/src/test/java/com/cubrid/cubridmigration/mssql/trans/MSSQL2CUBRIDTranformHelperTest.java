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
package com.cubrid.cubridmigration.mssql.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@DisplayName("MSSQL2CUBRIDTranformHelper")
class MSSQL2CUBRIDTranformHelperTest {

    private static final MSSQL2CUBRIDTranformHelper HELPER =
            new MSSQL2CUBRIDTranformHelper(
                    new MSSQLDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("getCloneView()")
    class GetCloneView {

        @Test
        @DisplayName("[a] -> \"a\", the brackets MSSQL quotes identifiers with")
        void bracketQuotedIdentifiers_becomeDoubleQuoted() {
            View source = new View();
            source.setName("V_ORDER");
            source.setQuerySpec("select [id], [name] from [order]");
            source.setColumns(new ArrayList<Column>());

            View target = HELPER.getCloneView(source, new MigrationConfiguration());

            assertThat(target.getQuerySpec()).isEqualTo("select \"id\", \"name\" from \"order\"");
        }
    }

    @Nested
    @DisplayName("getRealDataType()")
    class GetRealDataType {

        @Test
        @DisplayName("a user defined type -> the type it is really built on")
        void userDefinedType_returnsItsRealTypeName() {
            DataType userDefined = new DataType();
            userDefined.setTypeName("phone_number");
            userDefined.setRealTypeName("varchar");
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put("phone_number", Collections.singletonList(userDefined));

            assertThat(HELPER.getRealDataType(supported, "phone_number")).isEqualTo("varchar");
        }
    }

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("MSSQL's own parentheses and quotes are peeled off the value")
        @CsvSource(
                quoteCharacter = '"',
                nullValues = "null",
                value = {
                    // (NULL) in any spacing is no default at all.
                    "(NULL),     null",
                    "\" (NULL) \", null",

                    // A number arrives wrapped once or twice.
                    "(1),        1",
                    "((1)),      1",

                    // Quotes come off the same way.
                    "'1',        1",

                    // What is left has to format as the target type, or there is no default.
                    "((N)),      null",
                })
        void wrappedDefault_isPeeledToItsValue(String mssqlDefault, String expected) {
            assertThat(adjusted("numeric", 18, 0, mssqlDefault)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a unicode constant keeps only what is inside its quotes")
        @CsvSource(
                quoteCharacter = '"',
                value = {
                    "N'value',   value",
                    "N'',        ''",
                })
        void unicodeConstant_keepsOnlyItsContents(String mssqlDefault, String expected) {
            assertThat(adjusted("varchar", 10, null, mssqlDefault)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a string target gets its default back quoted, function calls included")
        void stringTarget_getsAQuotedLiteral() {
            assertThat(adjusted("varchar", 10, null, "('abc')")).isEqualTo("'abc'");
            // MSSQL never marks a default as an expression, so a function call is pinned here as
            // the literal it formats to.
            assertThat(adjusted("varchar", 20, null, "(getdate())")).isEqualTo("'getdate()'");
        }

        @Test
        @DisplayName("no default -> none on the target either")
        void noDefault_leavesTheTargetWithout() {
            // Column.setDefaultValue folds the literal "NULL" to null, so both arrive here as null.
            assertThat(adjusted("numeric", 18, 0, null)).isNull();
            assertThat(adjusted("numeric", 18, 0, "NULL")).isNull();
        }

        private String adjusted(
                String targetType, Integer precision, Integer scale, String mssqlDefault) {
            Column source = createColumn("numeric", 18, 0);
            source.setDefaultValue(mssqlDefault);
            Column target = createColumn(targetType, precision, scale);

            HELPER.adjustDefaultValue(source, target);

            return target.getDefaultValue();
        }
    }

    @Nested
    @DisplayName("convertValueToTargetDBValue()")
    @ResourceLock(Resources.TIME_ZONE)
    class ConvertValueToTargetDBValue {

        private TimeZone defaultZone;

        @BeforeEach
        void pinTimeZone() {
            defaultZone = TimeZone.getDefault();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        }

        @AfterEach
        void restoreTimeZone() {
            TimeZone.setDefault(defaultZone);
        }

        @Test
        @DisplayName("time -> TIME drops the fractional seconds CUBRID has no room for")
        void timeToTime_isCutToSeconds() {
            assertThat(converted("time", DataTypeConstant.CUBRID_DT_TIME, "12:34:56.1234567"))
                    .isEqualTo("12:34:56");
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("datetime2 keeps at most three fractional digits")
        @CsvSource(
                quoteCharacter = '"',
                value = {
                    "\"2013-11-08 11:31:00.1234567\", \"2013-11-08 11:31:00.123\"",

                    // Shorter than the cut, so nothing is dropped.
                    "\"2013-11-08 11:31:00\",          \"2013-11-08 11:31:00\"",
                })
        void datetime2_isCutToMilliseconds(String value, String expected) {
            assertThat(converted("datetime2", DataTypeConstant.CUBRID_DT_DATETIME, value))
                    .isEqualTo(expected);
            assertThat(converted("datetime2", DataTypeConstant.CUBRID_DT_TIMESTAMP, value))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("datetimeoffset is moved onto the target's own time zone")
        @CsvSource(
                quoteCharacter = '"',
                value = {
                    // Target is UTC, so each value moves back by its own offset.
                    "\"2013-11-08 11:31:00.0000000 +12:00\", \"2013-11-07 23:31:00.0\"",
                    "\"2013-11-08 11:31:00.0000000 -05:30\", \"2013-11-08 17:01:00.0\"",
                    "\"2013-11-08 11:31:00.0000000 +00:00\", \"2013-11-08 11:31:00.0\"",
                })
        void datetimeOffset_isShiftedToTheTargetZone(String value, String expected) {
            Object converted =
                    converted("datetimeoffset", DataTypeConstant.CUBRID_DT_DATETIME, value);

            assertThat(converted).isInstanceOf(Timestamp.class).hasToString(expected);
        }

        @Test
        @DisplayName("a type MSSQL has no rule for is left to the shared converter")
        void unhandledType_fallsThroughToTheBase() {
            assertThat(converted("varchar", DataTypeConstant.CUBRID_DT_VARCHAR, "plain"))
                    .isEqualTo("plain");
            // The rules are keyed on both sides, so the same source type with another target is
            // left alone as well.
            assertThat(converted("time", DataTypeConstant.CUBRID_DT_DATETIME, "12:34:56.1234567"))
                    .isEqualTo("12:34:56.1234567");
        }

        @Test
        @DisplayName("no value -> no value, whatever the types are")
        void nullValue_staysNull() {
            assertThat(converted("datetimeoffset", DataTypeConstant.CUBRID_DT_DATETIME, null))
                    .isNull();
        }

        private Object converted(String sourceType, int targetJdbcId, Object value) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setTargetFileTimeZone("UTC");
            Column target = createColumn("varchar", 100, null);
            target.setJdbcIDOfDataType(targetJdbcId);

            return HELPER.convertValueToTargetDBValue(
                    config,
                    new HashMap<String, Object>(),
                    new SourceColumnConfig(),
                    createColumn(sourceType, null, null),
                    target,
                    value);
        }
    }

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        @Test
        @DisplayName("a string is capped at the widest CUBRID accepts")
        void stringPrecision_isCappedAtTheCubridMaximum() {
            assertThat(adjusted("varchar", null, 100).getPrecision()).isEqualTo(100);
            assertThat(
                            adjusted("varchar", null, DataTypeConstant.CUBRID_MAXSIZE + 10)
                                    .getPrecision())
                    .isEqualTo(DataTypeConstant.CUBRID_MAXSIZE);
        }

        @Test
        @DisplayName("a numeric wider than 38 digits becomes a varchar with room for the sign")
        void overflowingNumeric_becomesVarchar() {
            Column adjusted = adjusted("numeric", null, 39);

            assertThat(adjusted.getDataType()).isEqualTo(DataTypeConstant.CUBRID_VARCHAR);
            assertThat(adjusted.getPrecision()).isEqualTo(41);
            // DEFECT: MSSQL2CUBRIDTranformHelper.adjustPrecision() files the new varchar under the
            // VARBIT type id. Oracle and Tibero set CUBRID_DT_VARCHAR on the same conversion.
            assertThat(adjusted.getJdbcIDOfDataType()).isEqualTo(DataTypeConstant.CUBRID_DT_VARBIT);
        }

        @Test
        @DisplayName("a numeric that still fits keeps its precision")
        void fittingNumeric_isLeftAlone() {
            assertThat(adjusted("numeric", null, 38).getPrecision()).isEqualTo(38);
        }

        @Test
        @DisplayName("binary precision counts bytes on MSSQL and bits on CUBRID")
        void binaryPrecision_isMultipliedByEight() {
            assertThat(adjusted("bit varying", null, 10).getPrecision()).isEqualTo(80);
        }

        @Test
        @DisplayName(
                "an element type decides string and numeric, but the column type decides"
                        + " binary")
        void collectionOfBinary_keepsItsPrecision() {
            assertThat(adjusted("varchar", "bit varying", 10).getPrecision()).isEqualTo(10);
        }

        private Column adjusted(String targetType, String subType, int precision) {
            Column target = createColumn(targetType, precision, 0);
            target.setSubDataType(subType);

            HELPER.adjustPrecision(
                    createColumn("source", precision, 0), target, new MigrationConfiguration());

            return target;
        }
    }
}
