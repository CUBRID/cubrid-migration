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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.core.mapping.model.MapItem;
import com.cubrid.cubridmigration.core.mapping.model.MapObject;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

@DisplayName("TiberoDataTypeMappingHelper")
public class TiberoDataTypeMappingHelperTest {

    private static final TiberoDataTypeMappingHelper HELPER = new TiberoDataTypeMappingHelper();

    @Nested
    @DisplayName("Tibero2CUBRID.xml loading")
    class XmlLoading {

        @Test
        @DisplayName("XML load success -> xmlConfigMap is not empty")
        void xmlLoaded_configMapIsNotEmpty() {
            Map<String, MapItem> configMap = HELPER.getXmlConfigMap();

            assertThat(configMap).isNotEmpty();
        }

        @Test
        @DisplayName("NUMBER without precision mapping exists")
        void numberWithoutPrecision_keyExists() {
            assertThat(HELPER.getXmlConfigMap()).containsKey("NUMBER");
        }

        @Test
        @DisplayName("NUMBER with precision/scale mapping exists")
        void numberWithPrecisionScale_keyExists() {
            assertThat(HELPER.getXmlConfigMap()).containsKey("NUMBER_p_s");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" key exists in xmlConfigMap")
        @CsvSource({
            "BINARY_FLOAT",
            "BINARY_DOUBLE",
            "BLOB",
            "CHAR",
            "CLOB",
            "DATE",
            "FLOAT",
            "INTEGER",
            "INTERVAL DAY TO SECOND",
            "INTERVAL YEAR TO MONTH",
            "JSON",
            "LONG",
            "LONG RAW",
            "NCHAR",
            "NCLOB",
            "NVARCHAR",
            "NVARCHAR2",
            "RAW",
            "ROWID",
            "TIME",
            "TIMESTAMP",
            "TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP WITH TIME ZONE",
            "VARCHAR",
            "VARCHAR2",
            "XMLTYPE",
        })
        void expectedKeys_existInXmlConfig(String expectedKey) {
            assertThat(HELPER.getXmlConfigMap())
                    .as("Tiber2CUBRID.xml has no key '%s'", expectedKey)
                    .containsKey(expectedKey);
        }
    }

    @Nested
    @DisplayName("getMapKey()")
    class GetMapKey {

        @Nested
        @DisplayName("NUMBER type")
        class NumberType {

            @Test
            @DisplayName("NUMBER, precision=null -> \"NUMBER\"")
            void nullPrecision_returnsNumber() {
                assertThat(HELPER.getMapKey("NUMBER", null, null)).isEqualTo("NUMBER");
            }

            @Test
            @DisplayName("NUMBER, empty precision -> \"NUMBER\"")
            void emptyPrecision_returnsNumber() {
                assertThat(HELPER.getMapKey("NUMBER", "", "")).isEqualTo("NUMBER");
            }

            @Test
            @DisplayName("NUMBER, precision=\"10\" -> \"NUMBER_p_s\"")
            void numericPrecision_returnNumberPs() {
                assertThat(HELPER.getMapKey("NUMBER", "10", "2"))
                        .isEqualTo(
                                "NUMBER"
                                        + AbstractDataTypeMappingHelper.MAP_KEY_SEPARATOR
                                        + "p"
                                        + AbstractDataTypeMappingHelper.MAP_KEY_SEPARATOR
                                        + "s");
            }

            @Test
            @DisplayName("NUMBER, precision=\"0\" -> \"NUMBER\"")
            void zeroPrecision_returnsNumber() {
                assertThat(HELPER.getMapKey("NUMBER", "0", "0")).isEqualTo("NUMBER");
            }

            @Test
            @DisplayName("NUMBER, precision=\"p\" -> \"NUMBER_p_s\"")
            void templatePrecision_returnsNumberPs() {
                assertThat(HELPER.getMapKey("NUMBER", "p", "s")).isEqualTo("NUMBER_p_s");
            }

            @Test
            @DisplayName("NUMBER, precision=\"P\" -> \"NUMBER_p_s\"")
            void templatePrecisionUppercase_returnsNumberPs() {
                assertThat(HELPER.getMapKey("NUMBER", "P", "S")).isEqualTo("NUMBER_p_s");
            }
        }

        @Nested
        @DisplayName("non-NUMBER types")
        class NonNumberTypes {

            @ParameterizedTest(name = "[{index}] getMapKey(\"{0}\", ...) → \"{1}\"")
            @CsvSource({
                "VARCHAR2,                      VARCHAR2",
                "VARCHAR,                       VARCHAR",
                "CHAR,                          CHAR",
                "NCHAR,                         NCHAR",
                "TIMESTAMP,                     TIMESTAMP",
                // TIMESTAMP(6) -> getTiberoDataTypeKey -> "TIMESTAMP"
                "TIMESTAMP(6),                  TIMESTAMP",
                // INTERVAL DAY TO SECOND without precision -> keep as is
                "INTERVAL DAY TO SECOND,        INTERVAL DAY TO SECOND",
                // INTERVAL DAY(5) TO SECOND(9) -> normalized
                "INTERVAL DAY(5) TO SECOND(9),  INTERVAL DAY TO SECOND",
                "INTERVAL YEAR TO MONTH,        INTERVAL YEAR TO MONTH",
                "INTERVAL YEAR(4) TO MONTH,     INTERVAL YEAR TO MONTH",
                "JSON,                          JSON",
                "XMLTYPE,                       XMLTYPE",
                "CLOB,                          CLOB",
                "BLOB,                          BLOB",
            })
            void nonNumberTypes_returnsNormalizedKey(String inputType, String expectedKey) {
                assertThat(HELPER.getMapKey(inputType.trim(), null, null))
                        .isEqualTo(expectedKey.trim());
            }

            @Test
            @DisplayName("lowercase input -> uppercase")
            void lowercase_returnsUppercaseKey() {
                assertThat(HELPER.getMapKey("varchar", "100", null)).isEqualTo("VARCHAR");
            }

            @Test
            @DisplayName("null type -> empty string")
            void nullType_returnsEmptyKey() {
                assertThat(HELPER.getMapKey(null, null, null)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("available target list")
    class AvailableTargetList {

        @Test
        @DisplayName("BINARY_DOUBLE has double int bigint targets")
        void binaryDouble_hasExpectedTargets() {
            assertAvailableTargets(
                    "BINARY_DOUBLE",
                    null,
                    null,
                    tuple("double", null, null),
                    tuple("int", null, null),
                    tuple("bigint", null, null));
        }

        @Test
        @DisplayName("BINARY_FLOAT has float double int bigint targets")
        void binaryFloat_hasExpectedTargets() {
            assertAvailableTargets(
                    "BINARY_FLOAT",
                    null,
                    null,
                    tuple("float", null, null),
                    tuple("double", null, null),
                    tuple("int", null, null),
                    tuple("bigint", null, null));
        }

        @Test
        @DisplayName("BLOB has bit varying and blob targets")
        void blob_hasExpectedTargets() {
            assertAvailableTargets(
                    "BLOB",
                    null,
                    null,
                    tuple("bit varying", "1073741823", null),
                    tuple("blob", null, null));
        }

        @Test
        @DisplayName("CHAR has char and varchar targets")
        void char_hasExpectedTargets() {
            assertAvailableTargets(
                    "CHAR", "10", null, tuple("char", "n", null), tuple("varchar", "n", null));
        }

        @Test
        @DisplayName("CLOB has varchar and clob targets")
        void clob_hasExpectedTargets() {
            assertAvailableTargets(
                    "CLOB",
                    null,
                    null,
                    tuple("varchar", "1073741823", null),
                    tuple("clob", null, null));
        }

        @Test
        @DisplayName("DATE has datetime and varchar targets")
        void date_hasExpectedTargets() {
            assertAvailableTargets(
                    "DATE",
                    null,
                    null,
                    tuple("datetime", null, null),
                    tuple("varchar", "19", null));
        }

        @Test
        @DisplayName("FLOAT has numeric and double targets")
        void float_hasExpectedTargets() {
            assertAvailableTargets(
                    "FLOAT", null, null, tuple("numeric", null, null), tuple("double", null, null));
        }

        @Test
        @DisplayName("INTEGER has numeric and int targets")
        void integer_hasExpectedTargets() {
            assertAvailableTargets(
                    "INTEGER", null, null, tuple("numeric", null, null), tuple("int", null, null));
        }

        @Test
        @DisplayName("INTERVAL DAY TO SECOND has varchar target")
        void intervalDayToSecond_hasExpectedTargets() {
            assertAvailableTargets(
                    "INTERVAL DAY TO SECOND", null, null, tuple("varchar", "64", null));
        }

        @Test
        @DisplayName("INTERVAL YEAR TO MONTH has varchar target")
        void intervalYearToMonth_hasExpectedTargets() {
            assertAvailableTargets(
                    "INTERVAL YEAR TO MONTH", null, null, tuple("varchar", "16", null));
        }

        @Test
        @DisplayName("JSON has json and blob targets")
        void json_hasExpectedTargets() {
            assertAvailableTargets(
                    "JSON", null, null, tuple("json", null, null), tuple("blob", null, null));
        }

        @Test
        @DisplayName("LONG has varchar and clob targets")
        void long_hasExpectedTargets() {
            assertAvailableTargets(
                    "LONG",
                    null,
                    null,
                    tuple("varchar", "1073741823", null),
                    tuple("clob", null, null));
        }

        @Test
        @DisplayName("LONG RAW has bit varying and blob targets")
        void longRaw_hasExpectedTargets() {
            assertAvailableTargets(
                    "LONG RAW",
                    null,
                    null,
                    tuple("bit varying", "1073741823", null),
                    tuple("blob", null, null));
        }

        @Test
        @DisplayName("NCHAR has char and varchar targets")
        void nchar_hasExpectedTargets() {
            assertAvailableTargets(
                    "NCHAR", "10", null, tuple("char", "n", null), tuple("varchar", "n", null));
        }

        @Test
        @DisplayName("NCLOB has varchar and clob targets")
        void nclob_hasExpectedTargets() {
            assertAvailableTargets(
                    "NCLOB",
                    null,
                    null,
                    tuple("varchar", "1073741823", null),
                    tuple("clob", null, null));
        }

        @Test
        @DisplayName("NUMBER has numeric int bigint varchar targets")
        void number_hasExpectedTargets() {
            assertAvailableTargets(
                    "NUMBER",
                    null,
                    null,
                    tuple("numeric", "38", "15"),
                    tuple("int", null, null),
                    tuple("bigint", null, null),
                    tuple("varchar", "133", null));
        }

        @Test
        @DisplayName("NUMBER with precision and scale has numeric int bigint varchar targets")
        void numberWithPrecisionScale_hasExpectedTargets() {
            assertAvailableTargets(
                    "NUMBER",
                    "10",
                    "2",
                    tuple("numeric", "p", "s"),
                    tuple("int", null, null),
                    tuple("bigint", null, null),
                    tuple("varchar", "133", null));
        }

        @Test
        @DisplayName("RAW has bit varying and blob targets")
        void raw_hasExpectedTargets() {
            assertAvailableTargets(
                    "RAW", "10", null, tuple("bit varying", "n", null), tuple("blob", null, null));
        }

        @Test
        @DisplayName("ROWID has varchar target")
        void rowid_hasExpectedTargets() {
            assertAvailableTargets("ROWID", null, null, tuple("varchar", "32", null));
        }

        @Test
        @DisplayName("TIME has time and varchar targets")
        void time_hasExpectedTargets() {
            assertAvailableTargets(
                    "TIME", null, null, tuple("time", null, null), tuple("varchar", "18", null));
        }

        @Test
        @DisplayName("TIMESTAMP has datetime and varchar targets")
        void timestamp_hasExpectedTargets() {
            assertAvailableTargets(
                    "TIMESTAMP",
                    null,
                    null,
                    tuple("datetime", null, null),
                    tuple("varchar", "29", null));
        }

        @Test
        @DisplayName("TIMESTAMP WITH LOCAL TIME ZONE has datetimeltz and varchar targets")
        void timestampWithLocalTimeZone_hasExpectedTargets() {
            assertAvailableTargets(
                    "TIMESTAMP WITH LOCAL TIME ZONE",
                    null,
                    null,
                    tuple("datetimeltz", null, null),
                    tuple("varchar", "29", null));
        }

        @Test
        @DisplayName("TIMESTAMP WITH TIME ZONE has datetimetz and varchar targets")
        void timestampWithTimeZone_hasExpectedTargets() {
            assertAvailableTargets(
                    "TIMESTAMP WITH TIME ZONE",
                    null,
                    null,
                    tuple("datetimetz", null, null),
                    tuple("varchar", "128", null));
        }

        @Test
        @DisplayName("XMLTYPE has varchar and clob targets")
        void xmltype_hasExpectedTargets() {
            assertAvailableTargets(
                    "XMLTYPE",
                    null,
                    null,
                    tuple("varchar", "1073741823", null),
                    tuple("clob", null, null));
        }

        private void assertAvailableTargets(
                String dataType, String precision, String scale, Tuple... expectedTargets) {
            MapItem mapItem = HELPER.getXmlConfigMapItem(dataType, precision, scale);

            assertThat(mapItem).isNotNull();
            assertThat(mapItem.getAvailableTargetList())
                    .extracting(
                            MapObject::getDatatype, MapObject::getPrecision, MapObject::getScale)
                    .containsExactly(expectedTargets);
        }
    }
}
