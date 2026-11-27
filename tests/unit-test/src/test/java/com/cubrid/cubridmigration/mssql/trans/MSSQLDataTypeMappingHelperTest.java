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

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MSSQLDataTypeMappingHelper")
class MSSQLDataTypeMappingHelperTest {

    private static final MSSQLDataTypeMappingHelper HELPER = new MSSQLDataTypeMappingHelper();

    @Nested
    @DisplayName("MSSQL2CUBRID.xml loading")
    class XmlLoading {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every type CMT reads from this database has a mapping entry")
        @ValueSource(
                strings = {
                    // Whole numbers.
                    "bigint",
                    "bit1",
                    "int",
                    "smallint",
                    "tinyint",

                    // Exact and approximate numerics.
                    "decimal",
                    "float",
                    "money",
                    "numeric",
                    "real",
                    "smallmoney",

                    // Character data, including the large objects that migrate as text.
                    "char",
                    "nchar",
                    "ntext",
                    "nvarchar",
                    "sql_variant",
                    "sysname",
                    "text",
                    "uniqueidentifier",
                    "varchar",
                    "xml",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "binary",
                    "geography",
                    "geometry",
                    "hierarchyid",
                    "image",
                    "varbinary",

                    // Dates and times.
                    "date",
                    "datetime",
                    "datetime2",
                    "datetimeoffset",
                    "smalldatetime",
                    "time",
                    "timestamp",
                })
        void declaredSourceType_hasAnEntry(String sourceType) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("MSSQL2CUBRID.xml has no entry for '%s'", sourceType)
                    .containsKey(sourceType);
        }

        @Test
        @DisplayName("the table holds exactly these, so a new type cannot slip in unnoticed")
        void nothingElse_isDeclared() {
            assertThat(HELPER.getXmlConfigMap()).hasSize(34);
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
                    "bit1,                               short,          null,         null",
                    "int,                                int,            null,         null",
                    "smallint,                           short,          null,         null",
                    "tinyint,                            short,          null,         null",

                    // Exact and approximate numerics.
                    "decimal,                            numeric,        p,            s",
                    "float,                              float,          null,         null",
                    "money,                              numeric,        19,           4",
                    "numeric,                            numeric,        p,            s",
                    "real,                               float,          null,         null",
                    "smallmoney,                         numeric,        10,           4",

                    // Character data, including the large objects that migrate as text.
                    "char,                               char,           n,            null",
                    "nchar,                              char,           n,            null",
                    "ntext,                              varchar,        1073741823,   null",
                    "nvarchar,                           varchar,        n,            null",
                    "sql_variant,                        varchar,        8000,         null",
                    "sysname,                            varchar,        128,          null",
                    "text,                               varchar,        1073741823,   null",
                    "uniqueidentifier,                   varchar,        36,           null",
                    "varchar,                            varchar,        n,            null",
                    "xml,                                varchar,        1073741823,   null",

                    // Binary data, counted in bytes on the source and in bits on CUBRID.
                    "binary,                             bit,            n,            null",
                    "geography,                          bit varying,    1073741823,   null",
                    "geometry,                           bit varying,    1073741823,   null",
                    "hierarchyid,                        bit varying,    7136,         null",
                    "image,                              bit varying,    1073741823,   null",
                    "varbinary,                          bit varying,    n,            null",

                    // Dates and times.
                    "date,                               date,           null,         null",
                    "datetime,                           datetime,       null,         null",
                    "datetime2,                          datetime,       null,         null",
                    "datetimeoffset,                     varchar,        34,           null",
                    "smalldatetime,                      datetime,       null,         null",
                    "time,                               time,           null,         null",
                    "timestamp,                          bit varying,    64,           null",
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
