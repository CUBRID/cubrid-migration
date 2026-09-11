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
package com.cubrid.cubridmigration.tibero.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.mapping.model.MapItem;
import com.cubrid.cubridmigration.core.mapping.model.MapObject;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

@DisplayName("Tibero2CUBRIDTransformHelper")
class Tibero2CUBRIDTransformHelperTest {

    private static final Tibero2CUBRIDTransformHelper HELPER =
            new Tibero2CUBRIDTransformHelper(
                    new TiberoDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @Test
        @DisplayName("null default -> no change")
        void nullDefault_noChange() {
            Column src = createColumn("DATE");
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {3}")
        @DisplayName("datetime column Tibero date function -> CUBRID function")
        @CsvSource({
            "DATE,      SYSDATE,      DATETIME, SYS_DATETIME",
            "DATE,      SYSTIME,      DATETIME, SYS_TIME",
            "TIME,      CURRENT_TIME, TIME,     CURRENT_TIME",
            "TIME,      SYSTIME,      TIME,     SYS_TIME",
            "TIMESTAMP, SYSTIMESTAMP, DATETIME, SYSTIMESTAMP",
            "DATE,      CURRENT_DATE, DATETIME, CURRENT_DATETIME",
            "TIMESTAMP, CURRENT_TIMESTAMP, DATETIME, CURRENT_TIMESTAMP",
            "TIMESTAMP, LOCALTIMESTAMP, DATETIME, CURRENT_DATETIME",
            "TIMESTAMP WITH TIME ZONE, SYSTIMESTAMP, DATETIMETZ, SYSTIMESTAMP",
            "TIMESTAMP WITH TIME ZONE, CURRENT_TIMESTAMP, DATETIMETZ, CURRENT_TIMESTAMP",
            "TIMESTAMP WITH LOCAL TIME ZONE, SYSTIMESTAMP, DATETIMELTZ, SYSTIMESTAMP",
            "TIMESTAMP WITH LOCAL TIME ZONE, CURRENT_TIMESTAMP, DATETIMELTZ, CURRENT_TIMESTAMP",
        })
        void datetimeColumn_dateTimeFunction_converted(
                String srcType, String tiberoFn, String cubType, String cubridFn) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {3}")
        @DisplayName("datetime column date function is converted case-insensitively")
        @CsvSource({
            "DATE, sysdate, DATETIME, SYS_DATETIME",
            "TIME, current_time, TIME, CURRENT_TIME",
        })
        void datetimeColumn_dateTimeFunctionConvertedCaseInsensitively(
                String srcType, String tiberoFn, String cubType, String cubridFn) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(cubridFn);
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} remains as is")
        @DisplayName("datetime column recognized date function without mapping remains unchanged")
        @CsvSource({
            "TIME, CURRENT_TIMESTAMP, TIME, CURRENT_TIMESTAMP",
            "TIME, LOCALTIMESTAMP, TIME, LOCALTIMESTAMP",
        })
        void datetimeColumn_recognizedDateFunctionWithoutMapping_remainsUnchanged(
                String srcType, String tiberoFn, String cubType, String expected) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn(cubType);

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} column | {1} -> {2}")
        @DisplayName("date function conversion depends on source column type")
        @CsvSource({
            "DATE,      SYSDATE, true",
            "TIMESTAMP, SYSDATE, true",
            "VARCHAR2,  SYSDATE, false",
        })
        void dateFunctionConversion_dependsOnSourceColumnType(
                String srcType, String tiberoFn, boolean converted) {
            Column src = createColumn(srcType);
            src.setDefaultValue(tiberoFn);
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            if (converted) {
                assertThat(cub.getDefaultValue()).isEqualTo("SYS_DATETIME");
            } else {
                assertThat(cub.getDefaultValue()).isNull();
            }
            assertThat(cub.isDefaultIsExpression()).isEqualTo(converted);
        }

        @ParameterizedTest(name = "[{index}] expression \"{0}\" -> isExpression=true")
        @DisplayName("expression default -> set isDefaultIsExpression=true")
        @ValueSource(
                strings = {
                    "(SELECT 1 FROM DUAL)",
                    "TO_CHAR(SYSDATE,'YYYY-MM-DD')",
                    "CAST('2024-01-01' AS DATE)",
                })
        void expressionDefault_markedAsExpression(String expr) {
            Column src = createColumn("VARCHAR2");
            src.setDefaultValue(expr);
            Column cub = createColumn("VARCHAR");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] DATETIME column | TO_DATE/TO_TIMESTAMP conversion")
        @DisplayName("DATETIME type converts TO_DATE/TO_TIMESTAMP to TO_DATETIME")
        @MethodSource(
                "com.cubrid.cubridmigration.tibero.trans.Tibero2CUBRIDTransformHelperTest#toDateConversionCases")
        void dateTimeColumn_toDateConverted(String input, String expected) {
            Column src = createColumn("DATE");
            src.setDefaultValue(input);
            Column cub = createColumn("DATETIME");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] DATETIMETZ column | {0}")
        @DisplayName("DATETIMETZ type wraps TO_DATE/TO_TIMESTAMP with FROM_TZ")
        @MethodSource(
                "com.cubrid.cubridmigration.tibero.trans.Tibero2CUBRIDTransformHelperTest#toDateTimeTzConversionCases")
        void dateTimeTzColumn_toDateConverted(String input, String expected) {
            Column src = createColumn("TIMESTAMP WITH TIME ZONE");
            src.setDefaultValue(input);
            Column cub = createColumn("DATETIMETZ");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isEqualTo(expected);
        }

        @Test
        @DisplayName(
                "TO_TIMESTAMP_TZ remains unchanged without a documented CUBRID parser equivalent")
        void toTimestampTz_remainsUnchanged() {
            Column src = createColumn("TIMESTAMP WITH TIME ZONE");
            src.setDefaultValue("to_timestamp_tz('2024-01-01 +09:00','YYYY-MM-DD TZH:TZM')");
            Column cub = createColumn("DATETIMETZ");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue())
                    .isEqualTo("TO_TIMESTAMP_TZ('2024-01-01 +09:00','YYYY-MM-DD TZH:TZM')");
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("non-date literal default -> cub stays unchanged")
        void nonDateLiteralDefault_cubridColumnUnchanged() {
            Column src = createColumn("NUMBER");
            src.setDefaultValue("42");
            Column cub = createColumn("INT");

            HELPER.adjustDefaultValue(src, cub);

            assertThat(cub.getDefaultValue()).isNull();
            assertThat(cub.isDefaultIsExpression()).isFalse();
        }
    }

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        private final MigrationConfiguration CONFIG = new MigrationConfiguration();

        @Test
        @DisplayName("strict numeric scale<0 -> precision grows and scale resets to zero")
        void negativeScale_numericPrecisionAdjusted() {
            Column src = createColumn("NUMBER", 10, -2);
            Column cub = createColumn("numeric", 10, -2);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getPrecision()).isEqualTo(12);
            assertThat(cub.getScale()).isZero();
            assertThat(cub.getDataType()).isEqualTo("numeric");
        }

        @Test
        @DisplayName("strict numeric scale>precision -> precision follows scale")
        void scaleGreaterThanPrecision_precisionAdjustedToScale() {
            Column src = createColumn("NUMBER", 5, 8);
            Column cub = createColumn("numeric", 5, 8);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getPrecision()).isEqualTo(8);
            assertThat(cub.getScale()).isEqualTo(8);
        }

        @Test
        @DisplayName("strict numeric overflow -> converted to varchar")
        void overflowNumeric_convertedToVarchar() {
            Column src = createColumn("NUMBER", 10, 45);
            Column cub = createColumn("numeric", 10, 45);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getDataTypeInstance()).isNotNull();
            assertThat(cub.getDataTypeInstance().getName())
                    .isEqualTo(DataTypeConstant.CUBRID_VARCHAR);
            assertThat(cub.getDataTypeInstance().getPrecision()).isEqualTo(48);
            assertThat(cub.getJdbcIDOfDataType()).isEqualTo(DataTypeConstant.CUBRID_DT_VARCHAR);
        }

        @Test
        @DisplayName("RAW to bit varying -> precision is scaled by bytes to bits")
        void rawBinaryType_precisionScaledToBits() {
            Column src = createColumn("RAW", 8, null);
            Column cub = createColumn("bit varying", 8, null);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getPrecision()).isEqualTo(64);
        }

        @Test
        @DisplayName("BLOB to bit varying -> precision stays as configured")
        void blobToBitVarying_precisionRemainsUnchanged() {
            Column src = createColumn("BLOB", null, null);
            Column cub = createColumn("bit varying", 100, null);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getPrecision()).isEqualTo(100);
        }

        @Test
        @DisplayName("LONG RAW to bit varying -> precision stays as configured")
        void longRawToBitVarying_precisionRemainsUnchanged() {
            Column src = createColumn("LONG RAW", null, null);
            Column cub = createColumn("bit varying", 100, null);

            HELPER.adjustPrecision(src, cub, CONFIG);

            assertThat(cub.getPrecision()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("getCUBRIDColumn()")
    class GetCUBRIDColumn {

        @Test
        @DisplayName("string default removes comments, wraps quotes, and preserves source comment")
        void stringDefault_commentRemovedAndWrappedWithQuotes() {
            Column src = createColumn("NAME", "VARCHAR", 20, null);
            src.setDefaultValue("abc /* block */ -- line");
            src.setComment("source comment");

            Column cub = HELPER.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDefaultValue()).isEqualTo("'abc'");
            assertThat(cub.getComment()).isEqualTo("source comment");
            assertThat(cub.getName()).isEqualTo("name");
        }

        @Test
        @DisplayName("expression default starting with parenthesis -> no extra quotes")
        void expressionDefault_notWrappedWithQuotes() {
            Column src = createColumn("NAME", "VARCHAR", 20, null);
            src.setDefaultValue("(USER)");

            Column cub = HELPER.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDefaultValue()).isEqualTo("(USER)");
        }

        @Test
        @DisplayName("date timezone function mapped to varchar stays unquoted")
        void dateTimezoneFunctionMappedToVarchar_staysUnquoted() {
            Tibero2CUBRIDTransformHelper helper = createDateToVarcharHelper("64");
            Column src = createColumn("TZ_COL", "DATE", null, null);
            src.setDefaultValue("DBTIMEZONE");

            Column cub = helper.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDataType()).isEqualTo("varchar");
            assertThat(cub.getPrecision()).isEqualTo(64);
            assertThat(cub.getDefaultValue()).isEqualTo("DBTIMEZONE");
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("session timezone function mapped to varchar stays unquoted")
        void sessionTimezoneFunctionMappedToVarchar_staysUnquoted() {
            Tibero2CUBRIDTransformHelper helper = createDateToVarcharHelper("64");
            Column src = createColumn("TZ_COL", "DATE", null, null);
            src.setDefaultValue("SESSIONTIMEZONE");

            Column cub = helper.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDataType()).isEqualTo("varchar");
            assertThat(cub.getPrecision()).isEqualTo(64);
            assertThat(cub.getDefaultValue()).isEqualTo("SESSIONTIMEZONE");
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("varchar default DBTIMEZONE stays unquoted")
        void varcharDefaultDbTimezone_staysUnquoted() {
            Column src = createColumn("TZ_COL", "VARCHAR2", 64, null);
            src.setDefaultValue("DBTIMEZONE");

            Column cub = HELPER.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDataType()).isEqualTo("varchar");
            assertThat(cub.getPrecision()).isEqualTo(64);
            assertThat(cub.getDefaultValue()).isEqualTo("DBTIMEZONE");
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("varchar default SESSIONTIMEZONE stays unquoted")
        void varcharDefaultSessionTimezone_staysUnquoted() {
            Column src = createColumn("TZ_COL", "VARCHAR2", 64, null);
            src.setDefaultValue("SESSIONTIMEZONE");

            Column cub = HELPER.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDataType()).isEqualTo("varchar");
            assertThat(cub.getPrecision()).isEqualTo(64);
            assertThat(cub.getDefaultValue()).isEqualTo("SESSIONTIMEZONE");
            assertThat(cub.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("NUMBER without declared precision/scale uses default numeric(38,15) mapping")
        void numberWithoutDeclaredPrecision_usesDefaultNumeric3815() {
            Column src = createColumn("COL", "NUMBER", 0, 0);

            Column cub = HELPER.getCUBRIDColumn(src, new MigrationConfiguration());

            assertThat(cub.getDataType()).isEqualTo("numeric");
            assertThat(cub.getPrecision()).isEqualTo(38);
            assertThat(cub.getScale()).isEqualTo(15);
            assertThat(cub.getShownDataType()).isEqualTo("numeric(38,15)");
        }
    }

    @Nested
    @DisplayName("validateChar()")
    class ValidateChar {

        @Test
        @DisplayName("string target shorter than charset-adjusted precision -> no enough length")
        void stringTargetShorterThanNeeded_returnsNoEnoughLength() {
            MigrationConfiguration config = configWithCharsetFactor(3);
            Column src = createColumn("VARCHAR2", 4, null);
            Column cub = createColumn("varchar", 11, null);

            VerifyInfo result = HELPER.validateChar(src, cub, config);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
            assertThat(result.getMessage()).contains("12");
        }

        @Test
        @DisplayName("string target long enough -> match")
        void stringTargetLongEnough_returnsMatch() {
            MigrationConfiguration config = configWithCharsetFactor(3);
            Column src = createColumn("VARCHAR2", 4, null);
            Column cub = createColumn("varchar", 12, null);

            VerifyInfo result = HELPER.validateChar(src, cub, config);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("nstring target shorter than source precision -> no enough length")
        void nstringTargetShorterThanSource_returnsNoEnoughLength() {
            MigrationConfiguration config = new MigrationConfiguration();
            Column src = createColumn("NVARCHAR2", 6, null);
            Column cub = createColumn("nchar", 5, null);

            VerifyInfo result = HELPER.validateChar(src, cub, config);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
            assertThat(result.getMessage()).contains("6");
        }

        @Test
        @DisplayName("nstring target long enough -> match")
        void nstringTargetLongEnough_returnsMatch() {
            MigrationConfiguration config = new MigrationConfiguration();
            Column src = createColumn("NVARCHAR2", 6, null);
            Column cub = createColumn("nchar", 6, null);

            VerifyInfo result = HELPER.validateChar(src, cub, config);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        private MigrationConfiguration configWithCharsetFactor(final int charsetFactor) {
            return new MigrationConfiguration() {
                @Override
                public Integer getCharsetFactor() {
                    return charsetFactor;
                }
            };
        }
    }

    @Nested
    @DisplayName("validateNumericToVarchar()")
    class ValidateNumericToVarchar {

        private final MigrationConfiguration CONFIG = new MigrationConfiguration();

        @Test
        @DisplayName("negative scale required precision not met -> no enough length")
        void negativeScalePrecisionTooSmall_returnsNoEnoughLength() {
            Column src = createColumn("NUMBER", 10, -2);
            Column cub = createColumn("varchar", 12, null);

            VerifyInfo result = HELPER.validateNumericToVarchar(src, cub, CONFIG);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
            assertThat(result.getMessage()).contains("13");
        }

        @Test
        @DisplayName("large scale overflow precision met -> match")
        void scaleGreaterThanPrecisionAnd38_returnsMatch() {
            Column src = createColumn("NUMBER", 10, 45);
            Column cub = createColumn("varchar", 48, null);

            VerifyInfo result = HELPER.validateNumericToVarchar(src, cub, CONFIG);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("normal numeric to varchar precision too small -> no enough length")
        void normalRangePrecisionTooSmall_returnsNoEnoughLength() {
            Column src = createColumn("NUMBER", 10, 2);
            Column cub = createColumn("varchar", 11, null);

            VerifyInfo result = HELPER.validateNumericToVarchar(src, cub, CONFIG);

            assertThat(result.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }
    }

    @Nested
    @DisplayName("getColumnView()")
    class GetColumnView {

        private final MigrationConfiguration config = new MigrationConfiguration();

        @Test
        @DisplayName("removes uppercase WITH READ ONLY")
        void uppercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP WITH READ ONLY");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
            assertThat(result.getQuerySpec()).contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("removes lowercase with read only")
        void lowercase_withReadOnly_removed() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT * FROM EMP with read only");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec()).doesNotContainIgnoringCase("with read only");
        }

        @Test
        @DisplayName("view without WITH READ ONLY -> keep querySpec")
        void noWithReadOnly_querySpecUnchanged() {
            View view = new View();
            view.setName("V_EMP");
            view.setQuerySpec("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");

            View result = HELPER.getCloneView(view, config);

            assertThat(result.getQuerySpec())
                    .isEqualTo("SELECT ID, NAME FROM EMP WHERE ACTIVE = 1");
        }
    }

    @Nested
    @DisplayName("getToCUBRIDPartitionDDL()")
    class GetToCUBRIDPartitionDDL {

        private Table tableWithPartitionInfo(PartitionInfo info) {
            Table table = new Table();
            table.setPartitionInfo(info);
            return table;
        }

        private PartitionInfo partitionInfo(
                String method,
                int partitionCount,
                String columnName,
                PartitionTable... partitions) {
            PartitionInfo info = new PartitionInfo();
            info.setPartitionMethod(method);
            info.setPartitionCount(partitionCount);
            info.setPartitionColumnCount(columnName == null ? 0 : 1);

            if (columnName != null) {
                Column column = new Column();
                column.setName(columnName);
                info.setPartitionColumns(List.of(column));
            }

            info.setPartitions(List.of(partitions));
            return info;
        }

        private PartitionTable partition(String name, String desc) {
            PartitionTable partition = new PartitionTable();
            partition.setPartitionName(name);
            partition.setPartitionDesc(desc);
            return partition;
        }

        @Test
        @DisplayName("table=null -> null")
        void nullTable_returnsNull() {
            assertThat(HELPER.getToCUBRIDPartitionDDL(null)).isNull();
        }

        @Test
        @DisplayName("partitionInfo=null -> null")
        void nullPartitionInfo_returnsNull() {
            assertThat(HELPER.getToCUBRIDPartitionDDL(new Table())).isNull();
        }

        @Test
        @DisplayName("partition columns missing -> null")
        void zeroPartitionColumns_returnsNull() {
            PartitionInfo info = new PartitionInfo();
            info.setPartitionColumnCount(0);
            info.setPartitionCount(2);

            assertThat(HELPER.getToCUBRIDPartitionDDL(tableWithPartitionInfo(info))).isNull();
        }

        @Test
        @DisplayName("HASH partition -> PARTITION BY HASH(COL) PARTITION N")
        void hashPartition_generatesHashDDL() {
            Table table = tableWithPartitionInfo(partitionInfo("HASH", 4, "ID"));

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("HASH");
            assertThat(ddl).contains("(ID)");
            assertThat(ddl).contains("PARTITIONS 4");
        }

        @Test
        @DisplayName("RANGE partition -> handles VALUES LESS THAN and MAXVALUE")
        void rangePartition_generatesRangeDDL() {
            Table table =
                    tableWithPartitionInfo(
                            partitionInfo(
                                    "RANGE",
                                    2,
                                    "SALARY",
                                    partition("P_LOW", "1000"),
                                    partition("P_HIGH", "MAXVALUE")));

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("PARTITION BY");
            assertThat(ddl).contains("RANGE");
            assertThat(ddl).contains("PARTITION P_LOW VALUES LESS THAN (1000)");
            assertThat(ddl).contains("PARTITION P_HIGH VALUES LESS THAN MAXVALUE");
        }

        @Test
        @DisplayName("LIST partition -> VALUES IN format")
        void listPartition_generatesListDDL() {
            Table table =
                    tableWithPartitionInfo(
                            partitionInfo(
                                    "LIST", 1, "REGION", partition("P_SEOUL", "'SEOUL','BUSAN'")));

            String ddl = HELPER.getToCUBRIDPartitionDDL(table);

            assertThat(ddl).contains("LIST");
            assertThat(ddl).contains("PARTITION P_SEOUL VALUES IN ('SEOUL','BUSAN')");
        }

        @Test
        @DisplayName("LIST DEFAULT partition -> keeps DEFAULT literal")
        void listDefaultPartition_generatesDDL() {
            Table table =
                    tableWithPartitionInfo(
                            partitionInfo("LIST", 1, "REGION", partition("P_OTHER", "DEFAULT")));

            assertThat(HELPER.getToCUBRIDPartitionDDL(table))
                    .contains("PARTITION P_OTHER VALUES IN (DEFAULT)");
        }

        @Test
        @DisplayName("unknown partition method -> null")
        void unknownPartitionMethod_returnsNull() {
            Table table = tableWithPartitionInfo(partitionInfo("UNKNOWN", 1, "ID"));

            assertThat(HELPER.getToCUBRIDPartitionDDL(table)).isNull();
        }
    }

    static Stream<Arguments> toDateConversionCases() {
        return Stream.of(
                Arguments.of(
                        "TO_DATE('2024-01-01','YYYY-MM-DD')",
                        "TO_DATETIME('2024-01-01','YYYY-MM-DD')"),
                Arguments.of(
                        "TO_TIMESTAMP('2024-01-01','YYYY-MM-DD')",
                        "TO_DATETIME('2024-01-01','YYYY-MM-DD')"));
    }

    static Stream<Arguments> toDateTimeTzConversionCases() {
        return Stream.of(
                Arguments.of(
                        "TO_DATE('2024-01-01','YYYY-MM-DD')",
                        "FROM_TZ(TO_DATETIME('2024-01-01','YYYY-MM-DD'), SESSIONTIMEZONE())"),
                Arguments.of(
                        "TO_TIMESTAMP('2024-01-01','YYYY-MM-DD')",
                        "FROM_TZ(TO_DATETIME('2024-01-01','YYYY-MM-DD'), SESSIONTIMEZONE())"));
    }

    private static Tibero2CUBRIDTransformHelper createDateToVarcharHelper(String precision) {
        TiberoDataTypeMappingHelper mappingHelper = new TiberoDataTypeMappingHelper();

        MapObject source = new MapObject();
        source.setDatatype("DATE");
        source.setPrecision("");
        source.setScale("");

        MapObject target = new MapObject();
        target.setDatatype("varchar");
        target.setPrecision(precision);
        target.setScale("");

        MapItem item = new MapItem(mappingHelper, source, target);
        mappingHelper.getPreferenceConfigMap().put("DATE", item);

        return new Tibero2CUBRIDTransformHelper(
                mappingHelper, ToCUBRIDDataConverterFacade.getIntance());
    }
}
