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
package com.cubrid.cubridmigration.informix.trans;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;

@DisplayName("Informix2CUBRIDTransformHelper")
class Informix2CUBRIDTransformHelperTest {

    private static final Informix2CUBRIDTransformHelper HELPER =
            new Informix2CUBRIDTransformHelper(
                    new InformixDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance());

    @Nested
    @DisplayName("adjustPrecision()")
    class AdjustPrecision {

        @ParameterizedTest(name = "[{index}] decimal -> numeric({0},{1}) = ({2},{3})")
        @DisplayName("a decimal is cut down to what CUBRID's numeric holds")
        @CsvSource({
            "50, 5,  38, 5",
            "10, 50, 10, 38",
            "50, 50, 38, 38",

            // Already at the limit, so nothing moves.
            "38, 38, 38, 38",

            // Under the limit and left alone.
            "10, 2,  10, 2",
        })
        void decimalSource_isCappedAt38(
                int precision, int scale, int expectedPrecision, int expectedScale) {
            Column target = adjusted("decimal", "numeric", precision, scale);

            assertThat(target.getPrecision()).isEqualTo(expectedPrecision);
            assertThat(target.getScale()).isEqualTo(expectedScale);
        }

        @Test
        @DisplayName("the cap is keyed on the exact spelling Informix reports")
        void differentlySpelledDecimal_isLeftAlone() {
            assertThat(adjusted("DECIMAL", "numeric", 50, 5).getPrecision()).isEqualTo(50);
        }

        @Test
        @DisplayName("another source type keeps whatever the mapping gave it")
        void otherSource_isLeftAlone() {
            assertThat(adjusted("money", "numeric", 50, 5).getPrecision()).isEqualTo(50);
        }

        @ParameterizedTest(name = "[{index}] bit varying({0}) -> {1}")
        @DisplayName("bit varying is cut down to the widest CUBRID holds")
        @CsvSource({
            "1073741824, 1073741823",
            "100,        100",
        })
        void binaryTarget_isCappedAtTheCubridMaximum(int precision, int expected) {
            assertThat(adjusted("byte", "bit varying", precision, 0).getPrecision())
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("a decimal into bit varying is cut by the decimal rule first")
        void decimalIntoBinary_isCappedAt38() {
            Column target =
                    adjusted("decimal", "bit varying", DataTypeConstant.CUBRID_MAXSIZE + 1, 50);

            assertThat(target.getPrecision()).isEqualTo(38);
            assertThat(target.getScale()).isEqualTo(38);
        }

        private Column adjusted(String sourceType, String targetType, int precision, int scale) {
            Column target = createColumn(targetType, precision, scale);

            HELPER.adjustPrecision(createColumn(sourceType, precision, scale), target, config());

            return target;
        }
    }

    @Nested
    @DisplayName("getCUBRIDColumn()")
    class GetCUBRIDColumn {

        @ParameterizedTest(name = "[{index}] {0}({1},{2}) -> {3}({4})")
        @DisplayName(
                "each Informix type lands on its CUBRID counterpart, capped where it has to be")
        @CsvSource({
            // The caps adjustPrecision applies, seen from the outside.
            "decimal,  50,         5, numeric,     38",
            "byte,     2000000000, 0, bit varying, 1073741823",

            // Types that pass straight through.
            "integer,  10,         0, int,         0",
            "char,     20,         0, char,        20",
            "lvarchar, 500,        0, varchar,     500",

            // A large object takes the widest varchar there is.
            "text,     100,        0, varchar,     1073741823",
        })
        void informixType_becomesItsCubridCounterpart(
                String sourceType,
                int precision,
                int scale,
                String expectedType,
                int expectedPrecision) {
            Column target =
                    HELPER.getCUBRIDColumn(createColumn(sourceType, precision, scale), config());

            assertThat(target.getDataType()).isEqualTo(expectedType);
            assertThat(target.getPrecision()).isEqualTo(expectedPrecision);
        }

        @Test
        @DisplayName("datetime year to second -> datetime, qualifier and all")
        void qualifiedDatetime_becomesDatetime() {
            Column target =
                    HELPER.getCUBRIDColumn(createColumn("datetime year to second", 0, 0), config());

            assertThat(target.getDataType()).isEqualTo("datetime");
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
