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
package com.cubrid.cubridmigration.cubrid.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

@DisplayName("CUBRID2CUBRIDTranformHelper")
class CUBRID2CUBRIDTranformHelperTest {

    private static final CUBRID2CUBRIDTranformHelper HELPER =
            new CUBRID2CUBRIDTranformHelper(new CUBRIDDataTypeMappingHelper());

    private static final CUBRIDDataTypeHelper DATA_TYPE_HELPER =
            CUBRIDDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        @Test
        @DisplayName("both sides are CUBRID, so nothing is adjusted")
        void anyColumn_isLeftAlone() {
            Column target = createColumn("varchar", 999, 7);

            HELPER.adjustPrecision(createColumn("char", 1, 1), target, config());

            assertThat(target.getPrecision()).isEqualTo(999);
            assertThat(target.getScale()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("validateCollection()")
    class ValidateCollection {

        @Test
        @DisplayName("the same collection kind -> its element types decide")
        void sameCollectionKind_isDecidedByItsElements() {
            assertThat(
                            validated(
                                    collection("set", "integer", null),
                                    collection("set", "integer", null)))
                    .isEqualTo(VerifyInfo.TYPE_MATCH);
            assertThat(validated(collection("set", "varchar", 10), collection("set", "varchar", 3)))
                    .isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("a different collection kind is refused without looking at the elements")
        void differentCollectionKind_isRefused() {
            assertThat(
                            validated(
                                    collection("sequence", "integer", null),
                                    collection("set", "integer", null)))
                    .isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        private int validated(Column source, Column target) {
            return HELPER.validateCollection(source, target, config()).getResult();
        }

        private Column collection(String dataType, String elementType, Integer precision) {
            Column column = createColumn(dataType, precision, null);
            column.setSubDataType(elementType);
            return column;
        }
    }

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("CUBRID's own collection kinds, and anything spelled like them")
        @ValueSource(strings = {"set", "multiset", "sequence", "SET_OF"})
        void collectionType_returnsTrue(String dataType) {
            assertThat(HELPER.isCollection(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("list is not one of them, despite being a collection in SQL")
        @ValueSource(strings = {"list", "varchar"})
        void otherType_returnsFalse(String dataType) {
            assertThat(HELPER.isCollection(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("validateChar()")
    class ValidateChar {

        @Test
        @DisplayName("both sides share a charset, so the precisions are compared as they stand")
        void targetAtLeastAsWide_returnsMatch() {
            assertThat(validated(createColumn("char", 10, null), createColumn("char", 10, null)))
                    .isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        @Test
        @DisplayName("a narrower target -> no enough length")
        void narrowerTarget_returnsNoEnoughLength() {
            assertThat(validated(createColumn("char", 10, null), createColumn("char", 3, null)))
                    .isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("only the precision is looked at, never the target's type")
        void nonStringTarget_isJudgedOnPrecisionAlone() {
            assertThat(validated(createColumn("char", 10, null), createColumn("int", 30, null)))
                    .isEqualTo(VerifyInfo.TYPE_MATCH);
        }

        private int validated(Column source, Column target) {
            return HELPER.validateChar(source, target, config()).getResult();
        }
    }

    @Nested
    @DisplayName("getCUBRIDColumn()")
    class GetCUBRIDColumn {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("a CUBRID column keeps its own type, lowercased")
        @CsvSource(
                nullValues = "null",
                value = {
                    "char,      20,   null,  char,      20,  0",
                    "numeric,   10,   5,     numeric,   10,  5",
                    "numeric,   38,   5,     numeric,   38,  5",
                    "bit,       1,    null,  bit,       1,   0",
                    "bit,       3,    null,  bit,       3,   0",

                    // A type that carries no precision drops the one it was given.
                    "int,       3,    null,  int,       0,   0",

                    // The name is lowercased on the way through.
                    "DATETIME,  3,    null,  datetime,  0,   0",
                    "TIMESTAMP, 3,    null,  timestamp, 0,   0",
                    "TIME,      3,    null,  time,      0,   0",
                })
        void cubridType_isCarriedOver(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetType,
                Integer targetPrecision,
                Integer targetScale) {
            Column target =
                    HELPER.getCUBRIDColumn(
                            createColumn(sourceType, sourcePrecision, sourceScale), config());

            assertThat(target.getDataType()).isEqualTo(targetType);
            assertThat(target.getPrecision()).isEqualTo(targetPrecision);
            assertThat(target.getScale()).isEqualTo(targetScale);
        }

        @Test
        @DisplayName("clob -> varchar, wide enough to hold what the LOB held")
        void clob_becomesVarchar() {
            // A LOB does not replicate in an HA setup, so engineers ask for CUBRID to CUBRID
            // migrations that land the contents in a plain column instead. The mapping table puts
            // varchar ahead of clob for that reason; blob takes bit varying the same way.
            Column target = HELPER.getCUBRIDColumn(createColumn("clob", 3, null), config());

            assertThat(target.getDataType()).isEqualTo("varchar");
            assertThat(target.getPrecision()).isEqualTo(1073741823);
        }
    }

    @Nested
    @DisplayName("verifyColumnDataType()")
    class VerifyColumnDataType {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4},{5})")
        @DisplayName("a CUBRID type converts to itself and to its synonyms")
        @CsvSource(
                nullValues = "null",
                value = {
                    "bit,         1,    null, bit,               1,    null",
                    "int,         null, null, int,               null, null",
                    "smallint,    null, null, smallint,          null, null",
                    "bigint,      null, null, bigint,            null, null",
                    "float,       null, null, float,             null, null",
                    "double,      null, null, double,            null, null",
                    "date,        null, null, date,              null, null",
                    "datetime,    null, null, datetime,          null, null",
                    "timestamp,   null, null, timestamp,         null, null",
                    "time,        null, null, time,              null, null",
                    "blob,        null, null, blob,              null, null",
                    "bit varying, 3,    null, bit varying,       3,    null",

                    // The target is named the long way round and still resolves.
                    "int,         null, null, integer,           null, null",
                    "char,        10,   null, character,         10,   null",
                    "varchar,     3,    null, character varying, 5,    null",

                    // Exact numerics match when the target has room for both parts.
                    "decimal,     10,   2,    numeric,           10,   2",
                    "numeric,     10,   2,    numeric,           10,   2",
                    "decimal,     2,    null, decimal,           10,   2",
                    "decimal,     2,    null, decimal,           15,   10",

                    // A wider target is fine.
                    "char,        10,   null, character,         30,   null",
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
                    // A target declared without a precision has none, not the source's.
                    "bit,     1,  null, bit,               null, null",
                    "varchar, 3,  null, character varying, 2,    null",

                    // Widening the scale eats into the integer digits.
                    "decimal, 10, 2,    numeric,           15,   10",
                    "numeric, 10, 2,    numeric,           15,   10",

                    // The mapping asks for the widest bit varying there is.
                    "blob,    null, null, bit varying,     65535, null",
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

        @Test
        @DisplayName("a target the mapping does not offer -> no match")
        void unmappedTarget_returnsNoMatch() {
            assertThat(verified("bit", 1, null, "smallint", null, null))
                    .isEqualTo(VerifyInfo.TYPE_NO_MATCH);
        }

        /**
         * The target is built the way CUBRID itself describes a column, so a synonym resolves to
         * its standard name and a type declared without a precision keeps none.
         */
        private int verified(
                String sourceType,
                Integer sourcePrecision,
                Integer sourceScale,
                String targetShownType,
                Integer targetPrecision,
                Integer targetScale) {
            Column target = new Column();
            DATA_TYPE_HELPER.setColumnDataType(targetShownType, target);
            if (targetPrecision != null) {
                target.setPrecision(targetPrecision);
            }
            if (targetScale != null) {
                target.setScale(targetScale);
            }

            return HELPER.verifyColumnDataType(
                            createColumn(sourceType, sourcePrecision, sourceScale),
                            target,
                            config())
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
