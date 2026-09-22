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
package com.cubrid.cubridmigration.oracle.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@DisplayName("Oracle2CUBRIDTranformHelper")
class Oracle2CUBRIDTranformHelperTest {

    private static final Oracle2CUBRIDTranformHelper HELPER =
            new Oracle2CUBRIDTranformHelper(
                    new OracleDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("the types that migrate as text are exempt from every adjustment")
        @ValueSource(
                strings = {
                    // The four spellings the guard names: both row id types and both intervals.
                    "UROWID",
                    "ROWID",
                    "INTERVAL YEAR(2) TO MONTH",
                    "INTERVAL DAY(2) TO SECOND(6)",
                })
        void textLikeSource_isLeftAlone(String sourceType) {
            // These always map to a varchar, which no adjustment touches anyway. Pairing them with
            // a numeric target is what makes the exemption itself visible: NUMBER at this width
            // becomes a varchar, and these do not.
            Column target = adjusted(sourceType, "numeric", 39, 0);

            assertThat(target.getDataType()).isEqualTo("numeric");
            assertThat(target.getPrecision()).isEqualTo(39);
        }

        @Test
        @DisplayName("a negative scale is folded into the precision and reset to zero")
        void negativeScale_movesIntoThePrecision() {
            Column target = adjusted("NUMBER", "numeric", 10, -3);

            assertThat(target.getPrecision()).isEqualTo(13);
            assertThat(target.getScale()).isEqualTo(0);
        }

        @Test
        @DisplayName("a scale wider than the precision pulls the precision up to it")
        void scaleWiderThanPrecision_raisesThePrecision() {
            Column target = adjusted("NUMBER", "numeric", 5, 9);

            assertThat(target.getPrecision()).isEqualTo(9);
            assertThat(target.getScale()).isEqualTo(9);
        }

        @Test
        @DisplayName("38 digits still fit a numeric")
        void numericAtTheLimit_staysNumeric() {
            Column target = adjusted("NUMBER", "numeric", 38, 0);

            assertThat(target.getDataType()).isEqualTo("numeric");
            assertThat(target.getPrecision()).isEqualTo(38);
        }

        @ParameterizedTest(name = "[{index}] numeric({0},{1}) -> varchar({2})")
        @DisplayName("past 38 digits it becomes a varchar wide enough for the punctuation")
        @CsvSource({
            // Whole numbers need room for the sign.
            "39, 0,  40",

            // With a fraction, the point comes too.
            "40, 5,  42",

            // All fraction, so a leading zero is needed as well.
            "40, 40, 43",
        })
        void overflowingNumeric_becomesVarchar(int precision, int scale, int expectedPrecision) {
            Column target = adjusted("NUMBER", "numeric", precision, scale);

            assertThat(target.getDataType()).isEqualTo(DataTypeConstant.CUBRID_VARCHAR);
            assertThat(target.getPrecision()).isEqualTo(expectedPrecision);
            assertThat(target.getJdbcIDOfDataType()).isEqualTo(DataTypeConstant.CUBRID_DT_VARCHAR);
        }

        @ParameterizedTest(name = "[{index}] {0} bytes -> {1} bits")
        @DisplayName("binary precision counts bytes on Oracle and bits on CUBRID")
        @CsvSource({
            "2000,      16000",
            "200000000, 1073741823",
        })
        void binaryTarget_isMultipliedByEightAndCapped(int precision, int expected) {
            assertThat(adjusted("RAW", "bit varying", precision, 0).getPrecision())
                    .isEqualTo(expected);
        }

        private Column adjusted(String sourceType, String targetType, int precision, int scale) {
            Column target = createColumn(targetType, precision, scale);

            HELPER.adjustPrecision(createColumn(sourceType, precision, scale), target, config());

            return target;
        }
    }

    @Nested
    @DisplayName("getCloneView()")
    class GetCloneView {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("WITH READ ONLY has no CUBRID equivalent and is dropped, in any casing")
        @ValueSource(
                strings = {
                    "SELECT * FROM EMP WITH READ ONLY",
                    "SELECT * FROM EMP with read only",
                })
        void withReadOnly_isRemoved(String querySpec) {
            assertThat(cloned(querySpec))
                    .doesNotContainIgnoringCase("with read only")
                    .contains("SELECT * FROM EMP");
        }

        @Test
        @DisplayName("any other query is carried over as it stands")
        void otherQuery_isUnchanged() {
            assertThat(cloned("SELECT ID FROM EMP")).isEqualTo("SELECT ID FROM EMP");
        }

        private String cloned(String querySpec) {
            View source = new View();
            source.setName("V_EMP");
            source.setQuerySpec(querySpec);
            source.setColumns(new ArrayList<Column>());

            return HELPER.getCloneView(source, config()).getQuerySpec();
        }
    }

    @Nested
    @DisplayName("getCUBRIDColumn()")
    class GetCUBRIDColumn {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("each Oracle type lands on its CUBRID counterpart")
        @CsvSource({
            "VARCHAR2,     4000, 0, varchar,     4000,       0",
            "NVARCHAR2,    2000, 0, varchar,     2000,       0",
            "CHAR,         10,   0, char,        10,         0",
            "NUMBER,       10,   2, numeric,     10,         2",
            "INTEGER,      0,    0, int,         0,          0",
            "DATE,         0,    0, datetime,    0,          0",
            "TIMESTAMP,    0,    0, timestamp,   0,          0",
            "BINARY_FLOAT, 0,    0, float,       0,          0",

            // A NUMBER with nothing declared takes the mapping's own precision and scale.
            "NUMBER,       0,    0, numeric,     38,         15",

            // Large objects and text take the widest varchar there is.
            "CLOB,         0,    0, varchar,     1073741823, 0",
            "LONG,         0,    0, varchar,     1073741823, 0",
            "BLOB,         0,    0, bit varying, 1073741823, 0",

            // Binary precision is counted in bits.
            "RAW,          100,  0, bit varying, 800,        0",

            // A row id is text of a fixed width.
            "ROWID,        0,    0, varchar,     64,         0",
        })
        void oracleType_becomesItsCubridCounterpart(
                String sourceType,
                int precision,
                int scale,
                String expectedType,
                int expectedPrecision,
                int expectedScale) {
            Column target =
                    HELPER.getCUBRIDColumn(createColumn(sourceType, precision, scale), config());

            assertThat(target.getDataType()).isEqualTo(expectedType);
            assertThat(target.getPrecision()).isEqualTo(expectedPrecision);
            assertThat(target.getScale()).isEqualTo(expectedScale);
        }

        @Test
        @DisplayName("a string default is quoted on the way out")
        void stringDefault_isQuoted() {
            Column source = createColumn("VARCHAR2", 10, 0);
            source.setDefaultValue("abc");

            assertThat(HELPER.getCUBRIDColumn(source, config()).getDefaultValue())
                    .isEqualTo("'abc'");
        }
    }

    @Nested
    @DisplayName("validateChar()")
    class ValidateChar {

        @Test
        @DisplayName("a string target has to hold the source width times the charset factor")
        void stringTarget_isMeasuredAgainstTheCharsetFactor() {
            assertThat(validated(3, "CHAR", 4, "varchar", 11))
                    .isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
            assertThat(validated(3, "CHAR", 4, "varchar", 12)).isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("an nstring target counts characters, so the factor does not apply")
        void nstringTarget_isMeasuredAgainstTheSourceWidth() {
            assertThat(validated(1, "NCHAR", 10, "nchar", 9))
                    .isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
            assertThat(validated(1, "NCHAR", 10, "nchar", 10)).isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("a target that holds no text at all -> no match")
        void nonTextTarget_returnsNoMatch() {
            assertThat(validated(1, "CHAR", 10, "int", 100)).isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        private int validated(
                final int charsetFactor,
                String sourceType,
                int sourcePrecision,
                String targetType,
                int targetPrecision) {
            MigrationConfiguration config =
                    new MigrationConfiguration() {
                        @Override
                        public Integer getCharsetFactor() {
                            return charsetFactor;
                        }
                    };

            return HELPER.validateChar(
                            createColumn(sourceType, sourcePrecision, null),
                            createColumn(targetType, targetPrecision, null),
                            config)
                    .getResult();
        }
    }

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("an Oracle date function becomes the CUBRID one that means the same")
        @CsvSource({
            "SYSDATE,      sys_datetime",
            "CURRENT_DATE, current_datetime",

            // The lookup is case insensitive.
            "sysdate,      sys_datetime",
        })
        void renamedDateFunction_isTranslated(String oracleDefault, String expected) {
            assertThat(adjusted("DATE", "datetime", oracleDefault).getDefaultValue())
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a date function CUBRID spells the same way is carried over untouched")
        @ValueSource(strings = {"SYSTIMESTAMP", "CURRENT_TIMESTAMP"})
        void sharedDateFunction_isCarriedOver(String oracleDefault) {
            assertThat(adjusted("TIMESTAMP", "datetime", oracleDefault).getDefaultValue())
                    .isEqualTo(oracleDefault);
        }

        @Test
        @DisplayName("TO_DATE into a datetime becomes TO_DATETIME, and is marked an expression")
        void toDateIntoDatetime_becomesToDatetime() {
            Column target = adjusted("DATE", "datetime", "TO_DATE('2024-01-01','YYYY-MM-DD')");

            assertThat(target.getDefaultValue())
                    .isEqualTo("to_datetime('2024-01-01','yyyy-mm-dd')");
            assertThat(target.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("TO_DATE into a date keeps its name, since CUBRID has that function too")
        void toDateIntoDate_isCarriedOver() {
            Column target = adjusted("DATE", "date", "TO_DATE('2024-01-01','YYYY-MM-DD')");

            assertThat(target.getDefaultValue()).isEqualTo("TO_DATE('2024-01-01','YYYY-MM-DD')");
            assertThat(target.isDefaultIsExpression()).isTrue();
        }

        @Test
        @DisplayName("a date Oracle itself could not store falls back to the configured date")
        void unparsableDate_fallsBackToTheConfiguredValue() {
            assertThat(adjusted("DATE", "datetime", "0000-00-00").getDefaultValue())
                    .isEqualTo("0001-01-01");
        }

        @Test
        @DisplayName("a timestamp that will not parse falls back to the configured timestamp")
        void unparsableTimestamp_fallsBackToTheConfiguredValue() {
            assertThat(adjusted("TIMESTAMP", "datetime", "not a timestamp").getDefaultValue())
                    .isEqualTo("1970-01-02 01:00:00");
        }

        @Test
        @DisplayName("no default -> the target keeps whatever it had")
        void noDefault_leavesTheTargetAlone() {
            assertThat(adjusted("VARCHAR2", "varchar", null).getDefaultValue()).isNull();
        }

        private Column adjusted(String sourceType, String targetType, String oracleDefault) {
            Column source = createColumn(sourceType, null, null);
            source.setDefaultValue(oracleDefault);
            Column target = createColumn(targetType, null, null);

            HELPER.adjustDefaultValue(source, target);

            return target;
        }
    }

    @Nested
    @DisplayName("validateNumericToVarchar()")
    class ValidateNumericToVarchar {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> varchar({3})")
        @DisplayName("the varchar has to hold every digit the number can carry")
        @CsvSource({
            // A negative scale spells out the trailing zeros, plus the sign.
            "NUMBER,  10, -3, 13, false",
            "NUMBER,  10, -3, 14, true",

            // All fraction: the digits, the point, the sign and a leading zero.
            "NUMBER,  10, 40, 42, false",
            "NUMBER,  10, 40, 43, true",

            // The ordinary case: the digits, the point and the sign.
            "NUMBER,  10, 2,  11, false",
            "NUMBER,  10, 2,  12, true",

            // INTEGER has no declared width, so it asks for a fixed minimum instead.
            "INTEGER, 10, 0,  126, false",
            "INTEGER, 10, 0,  127, true",
        })
        void targetWidth_decidesTheVerdict(
                String sourceType,
                int sourcePrecision,
                int sourceScale,
                int targetPrecision,
                boolean fits) {
            int result =
                    HELPER.validateNumericToVarchar(
                                    createColumn(sourceType, sourcePrecision, sourceScale),
                                    createColumn("varchar", targetPrecision, null),
                                    config())
                            .getResult();

            assertThat(result)
                    .isEqualTo(fits ? VerifyInfo.TYPE_MATCH : VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }
    }

    @Nested
    @DisplayName("getToCUBRIDPartitionDDL()")
    class GetToCUBRIDPartitionDDL {

        @Test
        @DisplayName("HASH -> the column and how many partitions to spread it over")
        void hashPartition_countsThePartitions() {
            assertThat(ddlOf(partitionInfo("HASH", 4, "ID")))
                    .isEqualTo("PARTITION BY  HASH (ID)  PARTITIONS 4");
        }

        @Test
        @DisplayName("RANGE -> one VALUES LESS THAN per partition, MAXVALUE unbracketed")
        void rangePartition_listsItsBounds() {
            String ddl =
                    ddlOf(
                            partitionInfo(
                                    "RANGE",
                                    2,
                                    "SALARY",
                                    partition("P_LOW", "1000"),
                                    partition("P_HIGH", "MAXVALUE")));

            assertThat(ddl)
                    .contains("PARTITION BY  RANGE (SALARY)")
                    .contains("PARTITION P_LOW VALUES LESS THAN (1000)")
                    .contains("PARTITION P_HIGH VALUES LESS THAN MAXVALUE");
        }

        @Test
        @DisplayName("LIST -> one VALUES IN per partition")
        void listPartition_listsItsValues() {
            String ddl =
                    ddlOf(
                            partitionInfo(
                                    "LIST", 1, "REGION", partition("P_SEOUL", "'SEOUL','BUSAN'")));

            assertThat(ddl)
                    .contains("PARTITION BY  LIST (REGION)")
                    .contains("PARTITION P_SEOUL VALUES IN ('SEOUL','BUSAN')");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("what cannot be translated is handed on as the source wrote it")
        @CsvSource({"UNKNOWN, ID", "RANGE, null"})
        void untranslatablePartition_keepsTheSourceDdl(String method, String columnName) {
            PartitionInfo info =
                    partitionInfo(
                            method,
                            1,
                            "null".equals(columnName) ? null : columnName,
                            partition("P1", "1"));

            assertThat(ddlOf(info)).isEqualTo("ORIGINAL DDL");
        }

        @Test
        @DisplayName("nothing to translate -> null")
        void noPartitionInfo_returnsNull() {
            assertThat(HELPER.getToCUBRIDPartitionDDL(null)).isNull();
            assertThat(HELPER.getToCUBRIDPartitionDDL(new Table())).isNull();
        }

        private String ddlOf(PartitionInfo info) {
            Table table = new Table();
            table.setPartitionInfo(info);

            return HELPER.getToCUBRIDPartitionDDL(table);
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
            info.setDDL("ORIGINAL DDL");

            Column column = new Column();
            column.setName(columnName);
            info.setPartitionColumns(columnName == null ? List.of() : List.of(column));
            info.setPartitions(List.of(partitions));
            return info;
        }

        private PartitionTable partition(String name, String description) {
            PartitionTable partition = new PartitionTable();
            partition.setPartitionName(name);
            partition.setPartitionDesc(description);
            return partition;
        }
    }

    @Nested
    @DisplayName("verifyColumnDataType()")
    class VerifyColumnDataType {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4})")
        @DisplayName("a NUMBER migrating as text needs room for every digit it can carry")
        @CsvSource({
            "NUMBER, 38, 0,  varchar, 39, true",
            "NUMBER, 38, 0,  varchar, 38, false",

            // A fraction brings the point along.
            "NUMBER, 38, 2,  varchar, 40, true",

            // A negative scale spells out the trailing zeros.
            "NUMBER, 38, -2, varchar, 41, true",

            // More fraction than digits: the point and a leading zero both need room.
            "NUMBER, 38, 40, varchar, 43, true",
            "NUMBER, 38, 40, varchar, 42, false",
        })
        void numberIntoText_needsRoomForEveryDigit(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetType,
                Integer targetPrecision,
                boolean fits) {
            assertThat(
                            verified(
                                    sourceType,
                                    sourcePrecision,
                                    sourceScale,
                                    targetType,
                                    targetPrecision))
                    .isEqualTo(fits ? VerifyInfo.TYPE_MATCH : VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @ParameterizedTest(name = "[{index}] VARCHAR2({0}) -> varchar({1})")
        @DisplayName("text needs the source width times the charset factor")
        @CsvSource({
            "10, 30, true",
            "10, 29, false",
        })
        void textIntoText_needsTheCharsetFactorApplied(
                int sourcePrecision, int targetPrecision, boolean fits) {
            assertThat(verified("VARCHAR2", sourcePrecision, 0, "varchar", targetPrecision))
                    .isEqualTo(fits ? VerifyInfo.TYPE_MATCH : VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("a target the mapping does not offer -> no match")
        void unmappedTarget_returnsNoMatch() {
            assertThat(verified("NUMBER", 38, 40, "blob", 0)).isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        /**
         * The target is built the way CUBRID itself describes a column, and the charset factor is
         * three, as it is for the UTF-8 databases these rules were written against.
         */
        private int verified(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetShownType,
                Integer targetPrecision) {
            Column target = new Column();
            CUBRIDDataTypeHelper.getInstance(null).setColumnDataType(targetShownType, target);
            target.setPrecision(targetPrecision);
            target.setScale(0);

            MigrationConfiguration config =
                    new MigrationConfiguration() {
                        @Override
                        public Integer getCharsetFactor() {
                            return 3;
                        }
                    };

            return HELPER.verifyColumnDataType(
                            createColumn(sourceType, sourcePrecision, sourceScale), target, config)
                    .getResult();
        }
    }

    /** getCUBRIDColumn reads the source catalog, so the configuration has to carry one. */
    private static MigrationConfiguration config() {
        MigrationConfiguration config = new MigrationConfiguration();
        Catalog catalog = new Catalog();
        catalog.setSupportedDataType(new HashMap<>());
        config.setSrcCatalog(catalog, false);
        return config;
    }
}
