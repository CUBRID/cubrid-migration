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

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CUBRIDDataTypeMappingHelper")
class CUBRIDDataTypeMappingHelperTest {

    private static final CUBRIDDataTypeMappingHelper HELPER = new CUBRIDDataTypeMappingHelper();

    @Nested
    @DisplayName("CUBRID2CUBRID.xml loading")
    class XmlLoading {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every type CMT reads from this database has a mapping entry")
        @ValueSource(
                strings = {
                    // Whole numbers.
                    "bigint",
                    "int",
                    "short",

                    // Exact and approximate numerics.
                    "double",
                    "float",
                    "monetary",
                    "numeric",

                    // Character data, including the large objects that migrate as text.
                    "char",
                    "clob",
                    "varchar",

                    // Binary data, counted in bytes on the source and in bits on CUBRID. blob
                    // and clob lead with a non-LOB target on purpose: a LOB does not replicate in
                    // an HA setup, so engineers ask for the contents to land in a plain column.
                    "bit",
                    "bit varying",
                    "blob",
                    "glo",

                    // Dates and times.
                    "date",
                    "datetime",
                    "datetimeltz",
                    "datetimetz",
                    "time",
                    "timestamp",
                    "timestampltz",
                    "timestamptz",

                    // Collections and the structured types.
                    "enum",
                    "json",
                    "list",
                    "multiset",
                    "set",
                })
        void declaredSourceType_hasAnEntry(String sourceType) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("CUBRID2CUBRID.xml has no entry for '%s'", sourceType)
                    .containsKey(sourceType);
        }

        @Test
        @DisplayName("the table holds exactly these, so a new type cannot slip in unnoticed")
        void nothingElse_isDeclared() {
            assertThat(HELPER.getXmlConfigMap()).hasSize(27);
        }
    }

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a synonym is keyed by the standard name CUBRID gives it")
        @CsvSource({
            "character varying, varchar",
            "SET,               set",
            "int,               int",
        })
        void synonym_isKeyedByItsStandardName(String dataType, String expected) {
            assertThat(HELPER.getMapKey(dataType, null, null)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a collection is keyed together with its element type")
        void collection_isKeyedWithItsElementType() {
            assertThat(HELPER.getMapKey("set_of(integer)", null, null)).isEqualTo("set(int)");
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
                    "int,                                int,            null,         null",
                    "short,                              short,          null,         null",

                    // Exact and approximate numerics.
                    "double,                             double,         null,         null",
                    "float,                              float,          null,         null",
                    "monetary,                           numeric,        38,           2",
                    "numeric,                            numeric,        p,            s",

                    // Character data, including the large objects that migrate as text.
                    "char,                               char,           n,            null",
                    "clob,                               varchar,        1073741823,   null",
                    "varchar,                            varchar,        n,            null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "bit,                                bit,            n,            null",
                    "bit varying,                        bit varying,    n,            null",
                    "blob,                               bit varying,    1073741823,   null",
                    "glo,                                bit varying,    1073741823,   null",

                    // Dates and times.
                    "date,                               date,           null,         null",
                    "datetime,                           datetime,       null,         null",
                    "datetimeltz,                        datetimeltz,    null,         null",
                    "datetimetz,                         datetimetz,     null,         null",
                    "time,                               time,           null,         null",
                    "timestamp,                          timestamp,      null,         null",
                    "timestampltz,                       timestampltz,   null,         null",
                    "timestamptz,                        timestamptz,    null,         null",

                    // Collections and the structured types.
                    "enum,                               enum,           null,         null",
                    "json,                               json,           null,         null",
                    "list,                               list,           null,         null",
                    "multiset,                           multiset,       null,         null",
                    "set,                                set,            null,         null",
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
