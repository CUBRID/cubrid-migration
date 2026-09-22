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

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("OracleDataTypeMappingHelper")
class OracleDataTypeMappingHelperTest {

    private static final OracleDataTypeMappingHelper HELPER = new OracleDataTypeMappingHelper();

    @Nested
    @DisplayName("Oracle2CUBRID.xml loading")
    class XmlLoading {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every type CMT reads from this database has a mapping entry")
        @ValueSource(
                strings = {
                    // Whole numbers.
                    "INTEGER",

                    // Exact and approximate numerics.
                    "BINARY_DOUBLE",
                    "BINARY_FLOAT",
                    "DECIMAL",
                    "FLOAT",
                    "NUMBER__",
                    "NUMBER_p_s",
                    "REAL",

                    // Character data, including the large objects that migrate as text.
                    "CHAR",
                    "CLOB",
                    "LONG",
                    "NCHAR",
                    "NCLOB",
                    "NVARCHAR2",
                    "ROWID",
                    "UROWID",
                    "VARCHAR2",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "BFILE",
                    "BLOB",
                    "LONG RAW",
                    "RAW",

                    // Dates and times.
                    "DATE",
                    "INTERVAL DAY TO SECOND",
                    "INTERVAL YEAR TO MONTH",
                    "TIMESTAMP",
                    "TIMESTAMP WITH LOCAL TIME ZONE",
                    "TIMESTAMP WITH TIME ZONE",

                    // Collections and the structured types.
                    "ARRAY",
                })
        void declaredSourceType_hasAnEntry(String sourceType) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("Oracle2CUBRID.xml has no entry for '%s'", sourceType)
                    .containsKey(sourceType);
        }

        @Test
        @DisplayName("the table holds exactly these, so a new type cannot slip in unnoticed")
        void nothingElse_isDeclared() {
            assertThat(HELPER.getXmlConfigMap()).hasSize(28);
        }
    }

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @ParameterizedTest(name = "[{index}] NUMBER({0}) -> {1}")
        @DisplayName("a NUMBER is keyed on whether it declared a width")
        @CsvSource(
                nullValues = "null",
                value = {
                    "10,   NUMBER_p_s",
                    "p,    NUMBER_p_s",

                    // No width declared, so the mapping's own default applies.
                    "null, NUMBER__",
                    "0,    NUMBER__",
                })
        void number_isKeyedOnItsDeclaredWidth(String precision, String expected) {
            assertThat(HELPER.getMapKey("NUMBER", precision, "2")).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("an interval drops the field widths it was declared with")
        @CsvSource({
            "INTERVAL DAY(2) TO SECOND(6), INTERVAL DAY TO SECOND",
            "INTERVAL YEAR(2) TO MONTH,    INTERVAL YEAR TO MONTH",
        })
        void interval_dropsItsFieldWidths(String dataType, String expected) {
            assertThat(HELPER.getMapKey(dataType, null, null)).isEqualTo(expected);
        }

        @Test
        @DisplayName("every other type is keyed by its own name")
        void otherType_isKeyedByItsOwnName() {
            assertThat(HELPER.getMapKey("VARCHAR2", null, null)).isEqualTo("VARCHAR2");
        }
    }

    @Nested
    @DisplayName("getTargetFromPreference()")
    class GetTargetFromPreference {

        @ParameterizedTest(name = "[{index}] {0} -> {1}({2},{3})")
        @DisplayName("each source type migrates to the first CUBRID type its entry offers")
        @CsvSource(
                nullValues = "null",
                value = {
                    // Whole numbers.
                    "INTEGER,                            int,            null,         null",

                    // Exact and approximate numerics.
                    "BINARY_DOUBLE,                      double,         null,         null",
                    "BINARY_FLOAT,                       float,          null,         null",
                    "DECIMAL,                            numeric,        p,            s",
                    "FLOAT,                              double,         null,         null",
                    "NUMBER__,                           numeric,        38,           15",
                    "NUMBER_p_s,                         numeric,        p,            s",
                    "REAL,                               float,          null,         null",

                    // Character data, including the large objects that migrate as text.
                    "CHAR,                               char,           n,            null",
                    "CLOB,                               varchar,        1073741823,   null",
                    "LONG,                               varchar,        1073741823,   null",
                    "NCHAR,                              char,           n,            null",
                    "NCLOB,                              varchar,        1073741823,   null",
                    "NVARCHAR2,                          varchar,        n,            null",
                    "ROWID,                              varchar,        64,           null",
                    "UROWID,                             varchar,        4000,         null",
                    "VARCHAR2,                           varchar,        n,            null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "BFILE,                              blob,           null,         null",
                    "BLOB,                               bit varying,    1073741823,   null",
                    "LONG RAW,                           bit varying,    1073741823,   null",
                    "RAW,                                bit varying,    n,            null",

                    // Dates and times.
                    "DATE,                               datetime,       null,         null",
                    "INTERVAL DAY TO SECOND,             varchar,        255,          null",
                    "INTERVAL YEAR TO MONTH,             varchar,        255,          null",
                    "TIMESTAMP,                          timestamp,      null,         null",
                    "TIMESTAMP WITH LOCAL TIME ZONE,     datetime,       null,         null",
                    "TIMESTAMP WITH TIME ZONE,           varchar,        100,          null",

                    // Collections and the structured types.
                    "ARRAY,                              list(varchar),  255,          null",
                })
        void sourceType_takesTheFirstTargetItsEntryOffers(
                String sourceType, String targetType, String precision, String scale) {
            MapObject target = HELPER.getTargetFromPreference(sourceType, 10, 2);

            assertThat(target)
                    .extracting(
                            MapObject::getDatatype, MapObject::getPrecision, MapObject::getScale)
                    .containsExactly(targetType, precision, scale);
        }
    }
}
