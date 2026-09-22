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
package com.cubrid.cubridmigration.mysql.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("MySQL2CUBRIDTranformHelper")
class MySQL2CUBRIDTranformHelperTest {

    private static final MySQL2CUBRIDTranformHelper HELPER =
            new MySQL2CUBRIDTranformHelper(
                    new MySQLDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        @ParameterizedTest(name = "[{index}] {0} -> {1}({2}) = {3}")
        @DisplayName("a text column is capped at the widest CUBRID accepts")
        @CsvSource({
            "varchar, varchar, 100,        100",
            "text,    varchar, 65535,      65535",
            "varchar, varchar, 1073741824, 1073741823",
        })
        void textColumn_isCappedAtTheCubridMaximum(
                String sourceType, String targetType, int precision, int expected) {
            assertThat(adjusted(sourceType, targetType, null, precision)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a collection is measured by its element type")
        void collection_isMeasuredByItsElementType() {
            assertThat(adjusted("set", "set", "varchar", 255)).isEqualTo(255);
        }

        @ParameterizedTest(name = "[{index}] numeric({0}) -> {1}")
        @DisplayName("an exact numeric is capped at 38 digits")
        @CsvSource({
            "20, 20", "50, 38",
        })
        void exactNumeric_isCappedAt38(int precision, int expected) {
            assertThat(adjusted("decimal", "numeric", null, precision)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] bit({0}) -> {1} bits")
        @DisplayName("a bit column is rounded up to whole bytes")
        @CsvSource({
            "1, 8", "8, 8", "9, 16",
        })
        void bitColumn_isRoundedUpToWholeBytes(int precision, int expected) {
            assertThat(adjusted("bit", "bit varying", null, precision)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} bytes -> {1} bits")
        @DisplayName("any other binary counts bytes on the source and bits on CUBRID")
        @CsvSource({
            "100,       800",
            "200000000, 1073741823",
        })
        void otherBinary_isMultipliedByEightAndCapped(int precision, int expected) {
            assertThat(adjusted("blob", "bit varying", null, precision)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a numeric source into text is exempt, cap included")
        void numericSourceIntoText_isLeftAlone() {
            assertThat(adjusted("int", "varchar", null, 11)).isEqualTo(11);
            // Past the cap is where the exemption shows: a text source would be cut here.
            assertThat(adjusted("decimal", "varchar", null, DataTypeConstant.CUBRID_MAXSIZE + 1))
                    .isEqualTo(DataTypeConstant.CUBRID_MAXSIZE + 1);
        }

        private int adjusted(
                String sourceType, String targetType, String elementType, int precision) {
            Column target = createColumn(targetType, precision, 0);
            target.setSubDataType(elementType);

            HELPER.adjustPrecision(createColumn(sourceType, precision, 0), target, config());

            return target.getPrecision();
        }
    }

    @Nested
    @DisplayName("getCloneView()")
    class GetCloneView {

        @Test
        @DisplayName("the view gets a CUBRID CREATE VIEW statement of its own")
        void clonedView_getsItsOwnCreateStatement() {
            View source = new View();
            source.setName("V_ORDER");
            source.setQuerySpec("select `id` from `order`");
            source.setColumns(new ArrayList<Column>());

            View target = HELPER.getCloneView(source, config());

            assertThat(target.getQuerySpec()).isEqualTo("select \"id\" from \"order\"");
            assertThat(target.getDDL())
                    .isEqualTo("CREATE VIEW \"V_ORDER\" AS select \"id\" from \"order\"");
        }
    }

    @Nested
    @DisplayName("getRealDataType()")
    class GetRealDataType {

        @Test
        @DisplayName("SET UNSIGNED is the same SET, with a modifier that means nothing here")
        void setUnsigned_isJustSet() {
            assertThat(HELPER.getRealDataType(supportedTypes(), "SET UNSIGNED")).isEqualTo("SET");
        }

        @Test
        @DisplayName("any other type is its own real type")
        void otherType_isItsOwnRealType() {
            assertThat(HELPER.getRealDataType(supportedTypes(), "mytype")).isEqualTo("mytype");
        }

        private Map<String, List<DataType>> supportedTypes() {
            DataType userDefined = new DataType();
            userDefined.setTypeName("mytype");
            userDefined.setRealTypeName("varchar");
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put("mytype", Collections.singletonList(userDefined));
            return supported;
        }
    }

    @Nested
    @DisplayName("adjustDefaultValue()")
    class AdjustDefaultValue {

        @ParameterizedTest(name = "[{index}] {0} | {1} -> {2}")
        @DisplayName("each date function is renamed to the CUBRID one that means the same")
        @CsvSource({
            "DATE,      CURRENT_DATE,      CURRENT_DATE",
            "DATE,      CURDATE,           CURRENT_DATE",
            "TIME,      CURRENT_TIME,      CURRENT_TIME",
            "TIME,      CURTIME,           CURRENT_TIME",
            "TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP",
            "TIMESTAMP, NOW,               CURRENT_TIMESTAMP",

            // The table is read before the type is, so the column type does not gate it.
            "VARCHAR,   NOW,               CURRENT_TIMESTAMP",

            // The names are matched without regard to case.
            "DATE,      curdate,           CURRENT_DATE",
        })
        void dateFunction_isRenamed(String sourceType, String sourceDefault, String expected) {
            assertThat(adjusted(sourceType, sourceDefault)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} | {1} -> {2}")
        @DisplayName("a value CUBRID cannot store falls back to the configured replacement")
        @CsvSource({
            "TIMESTAMP, 0000-00-00 00:00:00, 1970-01-02 01:00:00",

            // A datetime carries milliseconds, so its replacement does too.
            "DATETIME,  0000-00-00 00:00:00, 1970-01-02 01:00:00.000",

            // This dialect's time range is wider than a clock's.
            "TIME,      -838:59:59,          00:00:00",
            "DATE,      0000-00-00,          0001-01-01",
        })
        void unstorableValue_fallsBackToTheConfiguredReplacement(
                String sourceType, String sourceDefault, String expected) {
            assertThat(adjusted(sourceType, sourceDefault)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} | {1}")
        @DisplayName("anything CUBRID can store is left for the shared code to format")
        @CsvSource(
                nullValues = "null",
                value = {
                    "TIMESTAMP, 2024-01-01 00:00:00",
                    "VARCHAR,   abc",
                    "VARCHAR,   null",
                })
        void storableValue_isLeftAlone(String sourceType, String sourceDefault) {
            assertThat(adjusted(sourceType, sourceDefault)).isNull();
        }

        private String adjusted(String sourceType, String sourceDefault) {
            Column source = createColumn(sourceType, null, null);
            source.setDefaultValue(sourceDefault);
            Column target = createColumn("varchar", 100, null);

            HELPER.adjustDefaultValue(source, target);

            return target.getDefaultValue();
        }
    }

    @Nested
    @DisplayName("validateCollection()")
    class ValidateCollection {

        @Test
        @DisplayName("the target the mapping would have produced -> match")
        void mappedTarget_returnsMatch() {
            Column source = createColumn("set", 255, 0);

            assertThat(validated(source, HELPER.getCUBRIDColumn(source, config())))
                    .isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("the right element type but too narrow -> no enough length")
        void narrowerTarget_returnsNoEnoughLength() {
            Column source = createColumn("set", 255, 0);
            Column target = HELPER.getCUBRIDColumn(source, config());
            target.setPrecision(1);

            assertThat(validated(source, target)).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("anything that is not that collection -> no match")
        void otherTarget_returnsNoMatch() {
            Column target = createColumn("varchar", 255, 0);
            target.setJdbcIDOfDataType(DataTypeConstant.CUBRID_DT_VARCHAR);

            assertThat(validated(createColumn("set", 255, 0), target))
                    .isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        private int validated(Column source, Column target) {
            return HELPER.validateCollection(source, target, config()).getResult();
        }
    }

    @Nested
    @DisplayName("convertValueToTargetDBValue()")
    class ConvertValueToTargetDBValue {

        @Test
        @DisplayName("a NUL byte is swapped for the configured replacement, which CUBRID accepts")
        void nulByte_isReplaced() {
            assertThat(converted("a\0b")).isEqualTo("a b");
        }

        @Test
        @DisplayName("a value that is not text skips the swap and goes to the shared converter")
        void nonTextValue_isHandedOn() {
            assertThat(converted(Integer.valueOf(7))).isEqualTo("7");
        }

        private Object converted(Object value) {
            Column target = createColumn("varchar", 100, null);
            target.setJdbcIDOfDataType(DataTypeConstant.CUBRID_DT_VARCHAR);

            return HELPER.convertValueToTargetDBValue(
                    config(),
                    new HashMap<String, Object>(),
                    new SourceColumnConfig(),
                    createColumn("varchar", 100, null),
                    target,
                    value);
        }
    }

    @Nested
    @DisplayName("getFitTargetFormatSQL()")
    class GetFitTargetFormatSQL {

        @Test
        @DisplayName("`a` -> \"a\", the backticks this dialect quotes identifiers with")
        void backquotedIdentifiers_becomeDoubleQuoted() {
            assertThat(HELPER.getFitTargetFormatSQL("select `a` from `t`"))
                    .isEqualTo("select \"a\" from \"t\"");
        }

        @Test
        @DisplayName("the storage engine clause has no CUBRID equivalent and is dropped")
        void engineClause_isRemoved() {
            assertThat(HELPER.getFitTargetFormatSQL("create table t (a int) ENGINE = InnoDB"))
                    .isEqualTo("create table t (a int)");
        }

        @Test
        @DisplayName("no DDL -> an empty statement rather than null")
        void noDdl_returnsEmpty() {
            assertThat(HELPER.getFitTargetFormatSQL(null)).isEmpty();
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

    @Nested
    @DisplayName("verifyColumnDataType()")
    class VerifyColumnDataType {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("a source type converts to every CUBRID type its mapping offers")
        @CsvSource(
                nullValues = "null",
                value = {
                    // Whole numbers.
                    "tinyint           , null, null, smallint            , null      , null",
                    "tinyint           , null, null, int                 , null      , null",
                    "tinyint           , null, null, numeric             , 3         , 0",
                    "tinyint           , null, null, varchar             , 4         , null",
                    "tinyint unsigned  , null, null, smallint            , null      , null",
                    "tinyint unsigned  , null, null, int                 , null      , null",
                    "tinyint unsigned  , null, null, numeric             , 3         , 0",
                    "tinyint unsigned  , null, null, varchar             , 3         , null",
                    "bool              , null, null, smallint            , null      , null",
                    "boolean           , 1   , null, smallint            , null      , null",
                    "int               , null, null, int                 , null      , null",
                    "int               , null, null, bigint              , null      , null",
                    "int               , null, null, numeric             , 10        , null",
                    "int               , null, null, varchar             , 11        , null",
                    "smallint          , null, null, smallint            , null      , null",
                    "smallint          , null, null, int                 , null      , null",
                    "smallint          , null, null, numeric             , 5         , null",
                    "smallint          , null, null, varchar             , 6         , null",
                    "smallint unsigned , null, null, int                 , null      , null",
                    "smallint unsigned , null, null, numeric             , 5         , null",
                    "smallint unsigned , null, null, varchar             , 5         , null",
                    "mediumint         , null, null, integer             , null      , null",
                    "mediumint         , null, null, numeric             , 7         , null",
                    "mediumint         , null, null, varchar             , 8         , null",
                    "mediumint unsigned, null, null, integer             , null      , null",
                    "mediumint unsigned, null, null, numeric             , 8         , null",
                    "mediumint unsigned, null, null, varchar             , 8         , null",
                    "int               , null, null, integer             , null      , null",
                    "int unsigned      , null, null, bigint              , null      , null",
                    "int unsigned      , null, null, numeric             , 10        , null",
                    "int unsigned      , null, null, varchar             , 10        , null",
                    "bigint            , null, null, bigint              , null      , null",
                    "bigint            , null, null, numeric             , 19        , null",
                    "bigint            , null, null, varchar             , 20        , null",
                    "bigint unsigned   , null, null, numeric             , 20        , null",
                    "bigint unsigned   , null, null, varchar             , 20        , null",

                    // Exact and approximate numerics.
                    "float             , null, null, float               , null      , null",
                    "float             , null, null, double              , null      , null",
                    "float unsigned    , null, null, float               , null      , null",
                    "float unsigned    , null, null, double              , null      , null",
                    "double            , null, null, double              , null      , null",
                    "double unsigned   , null, null, double              , null      , null",
                    "decimal           , 10  , 2   , numeric             , 10        , 2",
                    "decimal           , 2   , 1   , varchar             , 4         , null",
                    "decimal           , 2   , null, varchar             , 3         , null",
                    "decimal unsigned  , 10  , 2   , decimal             , 10        , 2",
                    "decimal unsigned  , 2   , 1   , varchar             , 3         , null",
                    "numeric           , 10  , 2   , numeric             , 10        , 2",
                    "numeric           , 2   , 1   , varchar             , 4         , null",
                    "numeric           , 2   , null, varchar             , 3         , null",

                    // Character data, including the large objects that migrate as text.
                    "year              , null, null, character           , 4         , null",
                    "char              , 10  , null, character           , 30        , null",
                    "char              , 10  , null, varchar             , 30        , null",
                    "varchar           , 3   , null, character varying   , 9         , null",
                    "tinytext          , null, null, character varying   , 255       , null",
                    "text              , null, null, character varying   , 65535     , null",
                    "mediumtext        , null, null, character varying   , 16277215  , null",
                    "longtext          , null, null, character varying   , 1073741823, null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "bit               , 1   , null, smallint            , null      , null",
                    "bit               , 5   , null, bit                 , 5         , null",
                    "tinyblob          , null, null, bit varying         , 2040      , null",
                    "tinyblob          , null, null, blob                , null      , null",
                    "blob              , null, null, bit varying         , 524280    , null",
                    "blob              , null, null, blob                , null      , null",
                    "mediumblob        , null, null, bit varying         , 1073741823, null",
                    "mediumblob        , null, null, blob                , null      , null",
                    "longblob          , null, null, bit varying         , 1073741823, null",
                    "longblob          , null, null, blob                , null      , null",
                    "binary            , 3   , null, bit                 , 3         , null",
                    "varbinary         , 3   , null, bit varying         , 3         , null",

                    // Dates and times.
                    "date              , null, null, date                , null      , null",
                    "datetime          , null, null, datetime            , null      , null",
                    "timestamp         , null, null, timestamp           , null      , null",
                    "time              , null, null, time                , null      , null",

                    // Collections and the structured types.
                    "enum              , null, null, character varying   , 255       , null",
                    "set               , null, null, set(varchar(255))   , null      , null",
                    "set               , null, null, set_of(varchar(255)), null      , null",
                })
        void convertibleTypes_returnMatch(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetType,
                Integer targetPrecision,
                Integer targetScale) {
            assertThat(
                            verified(
                                    sourceType,
                                    sourcePrecision,
                                    sourceScale,
                                    targetType,
                                    targetPrecision,
                                    targetScale))
                    .isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("a target too small for the source -> no enough length")
        @CsvSource(
                nullValues = "null",
                value = {
                    // Whole numbers.
                    "int             , null, null, numeric          , 9 , null",
                    "int             , null, null, varchar          , 10, null",

                    // Exact and approximate numerics.
                    "decimal         , 10  , 2   , numeric          , 15, 10",
                    "decimal unsigned, 10  , 2   , decimal          , 15, 10",
                    "decimal unsigned, 2   , 1   , varchar          , 2 , null",
                    "decimal         , 2   , null, varchar          , 2 , null",
                    "numeric         , 10  , 2   , numeric          , 15, 10",
                    "numeric         , 2   , 1   , varchar          , 3 , null",

                    // Character data, including the large objects that migrate as text.
                    "char            , 10  , null, character        , 10, null",
                    "varchar         , 3   , null, character varying, 5 , null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "bit             , 5   , null, bit              , 4 , null",
                })
        void targetTooSmall_returnsNoEnoughLength(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetType,
                Integer targetPrecision,
                Integer targetScale) {
            assertThat(
                            verified(
                                    sourceType,
                                    sourcePrecision,
                                    sourceScale,
                                    targetType,
                                    targetPrecision,
                                    targetScale))
                    .isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("a target the mapping does not offer -> no match")
        @CsvSource(
                nullValues = "null",
                value = {
                    "bit         , 1   , null, bit     , null, null",
                    "bit         , 5   , null, smallint, null, null",
                    "int unsigned, null, null, int     , null, null",
                })
        void unmappedTarget_returnsNoMatch(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetType,
                Integer targetPrecision,
                Integer targetScale) {
            assertThat(
                            verified(
                                    sourceType,
                                    sourcePrecision,
                                    sourceScale,
                                    targetType,
                                    targetPrecision,
                                    targetScale))
                    .isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        /**
         * The target is built the way CUBRID itself describes a column, so a synonym resolves to
         * its standard name and a type declared without a precision keeps none. The charset factor
         * is three, as it is for the UTF-8 databases these rules were written against.
         */
        private int verified(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetShownType,
                Integer targetPrecision,
                Integer targetScale) {
            Column target = new Column();
            CUBRIDDataTypeHelper.getInstance(null).setColumnDataType(targetShownType, target);
            if (targetPrecision != null) {
                target.setPrecision(targetPrecision);
            }
            if (targetScale != null) {
                target.setScale(targetScale);
            }

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
}
