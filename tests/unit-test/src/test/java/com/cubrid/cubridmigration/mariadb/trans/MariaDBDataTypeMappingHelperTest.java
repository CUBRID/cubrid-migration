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
package com.cubrid.cubridmigration.mariadb.trans;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MariaDBDataTypeMappingHelper")
class MariaDBDataTypeMappingHelperTest {

    private static final MariaDBDataTypeMappingHelper HELPER = new MariaDBDataTypeMappingHelper();

    @Nested
    @DisplayName("MariaDB2CUBRID.xml loading")
    class XmlLoading {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every type CMT reads from this database has a mapping entry")
        @ValueSource(
                strings = {
                    // Whole numbers.
                    "bigint",
                    "bigint unsigned",
                    "bit1",
                    "bool",
                    "boolean",
                    "int",
                    "int unsigned",
                    "mediumint",
                    "mediumint unsigned",
                    "smallint",
                    "smallint unsigned",
                    "tinyint",
                    "tinyint unsigned",

                    // Exact and approximate numerics.
                    "decimal",
                    "decimal unsigned",
                    "double",
                    "double unsigned",
                    "float",
                    "float unsigned",
                    "numeric",

                    // Character data, including the large objects that migrate as text.
                    "char",
                    "longtext",
                    "mediumtext",
                    "text",
                    "tinytext",
                    "varchar",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "binary",
                    "bit",
                    "blob",
                    "longblob",
                    "mediumblob",
                    "tinyblob",
                    "varbinary",

                    // Dates and times.
                    "date",
                    "datetime",
                    "time",
                    "timestamp",
                    "year",

                    // Collections and the structured types.
                    "enum",
                    "set",
                })
        void declaredSourceType_hasAnEntry(String sourceType) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("MariaDB2CUBRID.xml has no entry for '%s'", sourceType)
                    .containsKey(sourceType);
        }

        @Test
        @DisplayName("the table holds exactly these, so a new type cannot slip in unnoticed")
        void nothingElse_isDeclared() {
            assertThat(HELPER.getXmlConfigMap()).hasSize(40);
        }
    }

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @ParameterizedTest(name = "[{index}] {0}({1}) -> {2}")
        @DisplayName("a one-bit column has its own key, so it can map to a number instead")
        @CsvSource({
            "bit,     1,  bit1",
            "bit,     2,  bit",
            "bit,     64, bit",
        })
        void bitColumn_isKeyedOnItsWidth(String dataType, String precision, String expected) {
            assertThat(HELPER.getMapKey(dataType, precision, "0")).isEqualTo(expected);
        }

        @Test
        @DisplayName("every other type is keyed by its own name, lowercased")
        void otherType_isKeyedByItsLowercasedName() {
            assertThat(HELPER.getMapKey("VARCHAR", "10", "0")).isEqualTo("varchar");
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
                    "bigint unsigned,                    numeric,        20,           null",
                    "bit1,                               short,          null,         null",
                    "bool,                               short,          null,         null",
                    "boolean,                            short,          null,         null",
                    "int,                                int,            null,         null",
                    "int unsigned,                       bigint,         null,         null",
                    "mediumint,                          int,            null,         null",
                    "mediumint unsigned,                 int,            null,         null",
                    "smallint,                           short,          null,         null",
                    "smallint unsigned,                  int,            null,         null",
                    "tinyint,                            short,          null,         null",
                    "tinyint unsigned,                   short,          null,         null",

                    // Exact and approximate numerics.
                    "decimal,                            numeric,        p,            s",
                    "decimal unsigned,                   numeric,        p,            s",
                    "double,                             double,         null,         null",
                    "double unsigned,                    double,         null,         null",
                    "float,                              float,          null,         null",
                    "float unsigned,                     float,          null,         null",
                    "numeric,                            numeric,        p,            s",

                    // Character data, including the large objects that migrate as text.
                    "char,                               char,           n,            null",
                    "longtext,                           varchar,        1073741823,   null",
                    "mediumtext,                         varchar,        16277215,     null",
                    "text,                               varchar,        65535,        null",
                    "tinytext,                           varchar,        255,          null",
                    "varchar,                            varchar,        n,            null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "binary,                             bit,            n,            null",
                    "bit,                                bit,            n,            null",
                    "blob,                               bit varying,    524280,       null",
                    "longblob,                           bit varying,    1073741823,   null",
                    "mediumblob,                         bit varying,    1073741823,   null",
                    "tinyblob,                           bit varying,    2040,         null",
                    "varbinary,                          bit varying,    n,            null",

                    // Dates and times.
                    "date,                               date,           null,         null",
                    "datetime,                           datetime,       null,         null",
                    "time,                               time,           null,         null",
                    "timestamp,                          timestamp,      null,         null",
                    "year,                               char,           4,            null",

                    // Collections and the structured types.
                    "enum,                               enum,           null,         null",
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
