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

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("InformixDataTypeMappingHelper")
class InformixDataTypeMappingHelperTest {

    private static final InformixDataTypeMappingHelper HELPER = new InformixDataTypeMappingHelper();

    @Nested
    @DisplayName("INFORMIX2CUBRID.xml loading")
    class XmlLoading {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every type CMT reads from this database has a mapping entry")
        @ValueSource(
                strings = {
                    // Whole numbers.
                    "bigint",
                    "bigserial",
                    "boolean",
                    "int8",
                    "integer",
                    "serial",
                    "serial8",
                    "smallint",

                    // Exact and approximate numerics.
                    "decimal",
                    "float",
                    "money",
                    "smallfloat",

                    // Character data, including the large objects that migrate as text.
                    "char",
                    "clob",
                    "lvarchar",
                    "nchar",
                    "nvarchar",
                    "text",
                    "varchar",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "blob",
                    "byte",

                    // Dates and times.
                    "date",
                    "datetime",
                    "interval",

                    // Collections and the structured types.
                    "bson",
                    "json",
                    "list",
                    "multiset",
                    "set",
                })
        void declaredSourceType_hasAnEntry(String sourceType) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("INFORMIX2CUBRID.xml has no entry for '%s'", sourceType)
                    .containsKey(sourceType);
        }

        @Test
        @DisplayName("the table holds exactly these, so a new type cannot slip in unnoticed")
        void nothingElse_isDeclared() {
            assertThat(HELPER.getXmlConfigMap()).hasSize(29);
        }
    }

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the families Informix spells out in full collapse onto one key")
        @CsvSource({
            "datetime year to second, datetime",
            "interval day to second,  interval",
            "sendreceive,             set",
        })
        void spelledOutFamily_collapsesOntoOneKey(String dataType, String expected) {
            assertThat(HELPER.getMapKey(dataType, null, null)).isEqualTo(expected);
        }

        @Test
        @DisplayName("every other type is keyed by its own name, case and all")
        void otherType_isKeyedByItsOwnName() {
            assertThat(HELPER.getMapKey("lvarchar", null, null)).isEqualTo("lvarchar");
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
                    "bigint,                             bigint,         null,         null",
                    "bigserial,                          bigint,         null,         null",
                    "boolean,                            int,            null,         null",
                    "int8,                               bigint,         null,         null",
                    "integer,                            int,            null,         null",
                    "serial,                             int,            null,         null",
                    "serial8,                            bigint,         null,         null",
                    "smallint,                           short,          null,         null",

                    // Exact and approximate numerics.
                    "decimal,                            numeric,        p,            s",
                    "float,                              double,         null,         null",
                    "money,                              numeric,        p,            s",
                    "smallfloat,                         float,          null,         null",

                    // Character data, including the large objects that migrate as text.
                    "char,                               char,           n,            null",
                    "clob,                               varchar,        1073741823,   null",
                    "lvarchar,                           varchar,        n,            null",
                    "nchar,                              char,           n,            null",
                    "nvarchar,                           varchar,        n,            null",
                    "text,                               varchar,        1073741823,   null",
                    "varchar,                            varchar,        n,            null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "blob,                               bit varying,    1073741823,   null",
                    "byte,                               bit varying,    n,            null",

                    // Dates and times.
                    "date,                               date,           null,         null",
                    "datetime,                           datetime,       null,         null",
                    "interval,                           varchar,        255,          null",

                    // Collections and the structured types.
                    "bson,                               json,           n,            null",
                    "json,                               json,           n,            null",
                    "list,                               list(varchar),  255,          null",
                    "multiset,                           multiset(varchar),  255,          null",
                    "set,                                set(varchar),   255,          null",
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
