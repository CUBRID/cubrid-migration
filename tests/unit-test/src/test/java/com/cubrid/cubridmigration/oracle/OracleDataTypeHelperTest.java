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
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
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
package com.cubrid.cubridmigration.oracle;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createCharColumn;
import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("OracleDataTypeHelper")
class OracleDataTypeHelperTest {

    private static final OracleDataTypeHelper HELPER = OracleDataTypeHelper.getInstance(null);

    private static Catalog catalogWithSupportedType(String key, int jdbcTypeId) {
        DataType dataType = new DataType();
        dataType.setTypeName(key);
        dataType.setJdbcDataTypeID(jdbcTypeId);
        return catalogWithSupportedTypes(key, Arrays.asList(dataType));
    }

    private static Catalog catalogWithSupportedTypes(String key, List<DataType> dataTypes) {
        Catalog catalog = new Catalog();
        Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
        supported.put(key, dataTypes);
        catalog.setSupportedDataType(supported);
        return catalog;
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("version is ignored -> same singleton instance")
        void anyVersion_returnsSameInstance() {
            assertThat(OracleDataTypeHelper.getInstance(null))
                    .isSameAs(OracleDataTypeHelper.getInstance("11g"));
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("returns DatabaseType.ORACLE")
        void returnsOracle() {
            assertThat(HELPER.getDBType()).isEqualTo(DatabaseType.ORACLE);
        }
    }

    @Nested
    @DisplayName("getOracleDataTypeKey()")
    class GetOracleDataTypeKey {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            // Precision is stripped from the TIMESTAMP family.
            "TIMESTAMP(38),                       TIMESTAMP",
            "TIMESTAMP(6),                        TIMESTAMP",
            "TIMESTAMP(),                         TIMESTAMP",
            "TIMESTAMP(38) WITH TIME ZONE,        TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(6) WITH TIME ZONE,         TIMESTAMP WITH TIME ZONE",
            "TIMESTAMP(38) WITH LOCAL TIME ZONE,  TIMESTAMP WITH LOCAL TIME ZONE",
            "TIMESTAMP(3) WITH LOCAL TIME ZONE,   TIMESTAMP WITH LOCAL TIME ZONE",

            // INTERVAL types collapse onto their catalog key.
            "INTERVAL DAY(2) TO SECOND(10),       INTERVALDS",
            "INTERVAL YEAR(2012) TO MONTH,        INTERVALYM",

            // No pattern matches: the input is returned untouched.
            "NUMBER,                              NUMBER",
            "VARCHAR2,                            VARCHAR2",
            "TIMESTAMP,                           TIMESTAMP",
            "TIMESTAMP WITH TIME ZONE,            TIMESTAMP WITH TIME ZONE",

            // Only the fully parenthesized spellings are normalized.
            "INTERVAL DAY TO SECOND,              INTERVAL DAY TO SECOND",
            "INTERVAL DAY(2) TO SECOND,           INTERVAL DAY(2) TO SECOND",
            "TIMESTAMP( 6 ),                      TIMESTAMP( 6 )",
            "timestamp(6),                        timestamp(6)",
        })
        void variousTypes_returnLookupKey(String input, String expected) {
            assertThat(OracleDataTypeHelper.getOracleDataTypeKey(input.trim()))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyString_returnsEmptyString() {
            assertThat(OracleDataTypeHelper.getOracleDataTypeKey("")).isEmpty();
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            // DEFECT: null is dereferenced instead of being rejected or normalized, unlike the
            // sibling TiberoDataTypeHelper.getTiberoDataTypeKey() which returns ""
            // - see OracleDataTypeHelper.java:120
            assertThatThrownBy(() -> OracleDataTypeHelper.getOracleDataTypeKey(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @ParameterizedTest(name = "[{index}] NUMBER(p={0}, s={1}) -> jdbc type {2}")
        @CsvSource(
                nullValues = "null",
                value = {
                    // precision, scale, expected java.sql.Types id
                    "null,        null,   2", // NUMERIC
                    "null,        0,     -5", // BIGINT
                    "null,        2,      2", // NUMERIC: only scale 0 short-circuits to BIGINT
                    "1,           0,     -7", // BIT
                    "1,           null,  -7", // BIT
                    "1,           1,      2", // NUMERIC: a non-zero scale ignores the precision
                    "3,           0,     -6", // TINYINT
                    "3,           null,  -6", // TINYINT
                    "5,           0,      5", // SMALLINT
                    "5,           null,   5", // SMALLINT
                    // DEFECT: the exact-match chain makes the mapping non-monotonic - NUMBER(4)
                    // widens to INTEGER while the larger NUMBER(5) narrows to SMALLINT
                    // - see OracleDataTypeHelper.java:102
                    "2,           0,      4", // INTEGER
                    "4,           0,      4", // INTEGER
                    "6,           0,      4", // INTEGER
                    "10,          0,      4", // INTEGER
                    "10,          null,   4", // INTEGER
                    "11,          0,     -5", // BIGINT
                    "37,          0,     -5", // BIGINT
                    "38,          0,     -5", // BIGINT
                    "38,          null,  -5", // BIGINT
                    "39,          0,      2", // NUMERIC: above the 38 digit integer range
                    "38,          2,      2", // NUMERIC
                    "10,          2,      2", // NUMERIC
                    // precision 0 counts as a real precision here, while getShownDataType()
                    // renders a bare NUMBER for the very same column.
                    "0,           0,      4", // INTEGER
                    "0,           null,   4", // INTEGER
                })
        void number_returnsJdbcTypeIdFromPrecisionAndScale(
                Integer precision, Integer scale, int expectedJdbcTypeId) {
            assertThat(HELPER.getJdbcDataTypeID(null, "NUMBER", precision, scale))
                    .isEqualTo(expectedJdbcTypeId);
        }

        @Test
        @DisplayName("DATE -> Types.DATE without a catalog")
        void date_returnsDate() {
            assertThat(HELPER.getJdbcDataTypeID(null, "DATE", null, null)).isEqualTo(Types.DATE);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> Types.CHAR without a catalog")
        @ValueSource(strings = {"NCHAR", "NVARCHAR2"})
        void nationalCharTypes_returnChar(String dataType) {
            // DEFECT: NVARCHAR2 is variable length but is mapped to the fixed-length Types.CHAR,
            // exactly like NCHAR - see OracleDataTypeHelper.java:162
            assertThat(HELPER.getJdbcDataTypeID(null, dataType, 100, null)).isEqualTo(Types.CHAR);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> Types.CLOB without a catalog")
        @ValueSource(strings = {"NCLOB", "LONG"})
        void characterLobTypes_returnClob(String dataType) {
            assertThat(HELPER.getJdbcDataTypeID(null, dataType, null, null)).isEqualTo(Types.CLOB);
        }

        @Test
        @DisplayName("BINARY_FLOAT -> Types.FLOAT without a catalog")
        void binaryFloat_returnsFloat() {
            assertThat(HELPER.getJdbcDataTypeID(null, "BINARY_FLOAT", 4000, null))
                    .isEqualTo(Types.FLOAT);
        }

        @Test
        @DisplayName("BINARY_DOUBLE -> Types.DOUBLE without a catalog")
        void binaryDouble_returnsDouble() {
            assertThat(HELPER.getJdbcDataTypeID(null, "BINARY_DOUBLE", null, null))
                    .isEqualTo(Types.DOUBLE);
        }

        @Test
        @DisplayName("INTEGER -> Types.INTEGER without a catalog")
        void integer_returnsInteger() {
            assertThat(HELPER.getJdbcDataTypeID(null, "INTEGER", null, null))
                    .isEqualTo(Types.INTEGER);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null (unmigratable type)")
        @ValueSource(strings = {"BFILE", "ROWID", "UROWID"})
        void unmigratableTypes_returnNull(String dataType) {
            assertThat(HELPER.getJdbcDataTypeID(null, dataType, null, null)).isNull();
        }

        @Test
        @DisplayName("VARCHAR2 -> jdbc type from the catalog's supported data types")
        void catalogBackedType_returnsJdbcTypeIdFromCatalog() {
            Catalog catalog = catalogWithSupportedType("VARCHAR2", Types.VARCHAR);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "VARCHAR2", 4000, null))
                    .isEqualTo(Types.VARCHAR);
        }

        @Test
        @DisplayName("TIMESTAMP(6) -> resolved under the normalized TIMESTAMP key")
        void normalizedType_usesNormalizedLookupKey() {
            Catalog catalog = catalogWithSupportedType("TIMESTAMP", Types.TIMESTAMP);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "TIMESTAMP(6)", null, 6))
                    .isEqualTo(Types.TIMESTAMP);
        }

        @Test
        @DisplayName("INTERVAL DAY(2) TO SECOND(6) -> resolved under the INTERVALDS key")
        void intervalType_usesNormalizedLookupKey() {
            Catalog catalog = catalogWithSupportedType("INTERVALDS", Types.OTHER);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "INTERVAL DAY(2) TO SECOND(6)", 2, 6))
                    .isEqualTo(Types.OTHER);
        }

        @Test
        @DisplayName("type missing from the catalog -> IllegalArgumentException")
        void missingSupportedType_throwsIllegalArgumentException() {
            assertThatThrownBy(
                            () -> HELPER.getJdbcDataTypeID(new Catalog(), "VARCHAR2", 4000, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Oracle data type(VARCHAR2)");
        }

        @Test
        @DisplayName("ambiguous catalog entry -> IllegalArgumentException naming precision/scale")
        void ambiguousSupportedType_throwsIllegalArgumentException() {
            DataType first = new DataType();
            first.setTypeName("CHAR");
            first.setJdbcDataTypeID(Types.CHAR);
            DataType second = new DataType();
            second.setTypeName("CHAR");
            second.setJdbcDataTypeID(Types.VARCHAR);
            Catalog catalog = catalogWithSupportedTypes("CHAR", Arrays.asList(first, second));

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "CHAR", 10, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Oracle data type(CHAR: p=10, s=null)");
        }

        @Test
        @DisplayName("lowercase number -> not recognized, falls through to the catalog lookup")
        void lowercaseNumber_isNotRecognized() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), "number", 38, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported Oracle data type(number)");
        }

        @Test
        @DisplayName("catalog-backed type with a null catalog -> NullPointerException")
        void nullCatalogForCatalogBackedType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(null, "VARCHAR2", 4000, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null data type -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] {0}(p={1}, s={2}) -> \"{3}\"")
        @CsvSource(
                nullValues = "null",
                value = {
                    // dataType,    precision, scale, expected shown data type
                    "VARCHAR2,      4000,      null,  VARCHAR2(4000)",
                    "CHAR,          128,       null,  CHAR(128)",
                    "NCHAR,         100,       null,  NCHAR(100)",
                    "NVARCHAR2,     100,       null,  NVARCHAR2(100)",
                    "RAW,           2000,      null,  RAW(2000)",

                    // FLOAT(126) is the Oracle default, so the precision is hidden.
                    "FLOAT,         126,       null,  FLOAT",
                    "FLOAT,         63,        null,  FLOAT(63)",

                    // Precision 0 and null both mean "unspecified" for NUMBER.
                    "NUMBER,        null,      null,  NUMBER",
                    "NUMBER,        0,         null,  NUMBER",
                    "NUMBER,        null,      2,     NUMBER",
                    // Column.getScale() turns a null scale into 0, so the scale is always shown.
                    "NUMBER,        38,        null,  'NUMBER(38,0)'",
                    "NUMBER,        38,        2,     'NUMBER(38,2)'",

                    // Datetime and interval types are rebuilt into their Oracle spelling.
                    "TIMESTAMP,     null,      6,     TIMESTAMP(6)",
                    "TIMESTAMPTZ,   null,      6,     TIMESTAMP(6) WITH TIME ZONE",
                    "TIMESTAMPLTZ,  null,      6,     TIMESTAMP(6) WITH LOCAL TIME ZONE",
                    "INTERVALDS,    2,         6,     INTERVAL DAY(2) TO SECOND(6)",
                    "INTERVALYM,    4,         null,  INTERVAL YEAR(4) TO MONTH",

                    // Everything else is returned as is.
                    "CLOB,          null,      null,  CLOB",
                    "BLOB,          null,      null,  BLOB",
                    "DATE,          null,      null,  DATE",
                    "LONG RAW,      null,      null,  LONG RAW",
                    "BINARY_FLOAT,  null,      null,  BINARY_FLOAT",
                })
        void column_returnsShownDataType(
                String dataType, Integer precision, Integer scale, String expected) {
            assertThat(HELPER.getShownDataType(createColumn(dataType.trim(), precision, scale)))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("charUsed C -> length is qualified with CHAR")
        void charUsedChar_appendsCharQualifier() {
            assertThat(HELPER.getShownDataType(createCharColumn("VARCHAR2", 100, "C")))
                    .isEqualTo("VARCHAR2(100 CHAR)");
            assertThat(HELPER.getShownDataType(createCharColumn("CHAR", 10, "C")))
                    .isEqualTo("CHAR(10 CHAR)");
        }

        @Test
        @DisplayName("charUsed B -> plain byte length")
        void charUsedByte_keepsPlainLength() {
            assertThat(HELPER.getShownDataType(createCharColumn("VARCHAR2", 100, "B")))
                    .isEqualTo("VARCHAR2(100)");
        }

        @Test
        @DisplayName("charUsed C on a national type -> qualifier is ignored")
        void charUsedCharOnNationalType_isIgnored() {
            assertThat(HELPER.getShownDataType(createCharColumn("NVARCHAR2", 100, "C")))
                    .isEqualTo("NVARCHAR2(100)");
        }

        @ParameterizedTest(name = "[{index}] {0} without precision -> \"{1}\"")
        @CsvSource({
            // DEFECT: Column.getPrecision()/getScale() coerce an unset value to 0, so an
            // unspecified length is rendered as a literal 0 and produces invalid Oracle DDL
            // - see OracleDataTypeHelper.java:215
            "VARCHAR2,   VARCHAR2(0)",
            "FLOAT,      FLOAT(0)",
            "TIMESTAMP,  TIMESTAMP(0)",
        })
        void unsetPrecision_rendersZero(String dataType, String expected) {
            assertThat(HELPER.getShownDataType(createColumn(dataType.trim())))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("lowercase data type -> only the string branch is case insensitive")
        void lowercaseDataType_isHandledInconsistently() {
            assertThat(HELPER.getShownDataType(createColumn("varchar2", 100, null)))
                    .isEqualTo("varchar2(100)");
            // DEFECT: NUMBER/RAW/FLOAT/TIMESTAMP are compared case sensitively while the
            // isString() check is not, so a lowercase number column silently loses its
            // precision and scale - see OracleDataTypeHelper.java:227
            assertThat(HELPER.getShownDataType(createColumn("number", 38, 2))).isEqualTo("number");
        }

        @Test
        @DisplayName("null data type -> null")
        void nullDataType_returnsNull() {
            assertThat(HELPER.getShownDataType(createColumn(null, 10, 2))).isNull();
        }

        @Test
        @DisplayName("empty data type -> empty string")
        void emptyDataType_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("", 10, 2))).isEmpty();
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @ValueSource(strings = {"blob", "BLOB"})
        void blob_returnsTrue(String dataType) {
            assertThat(HELPER.isBinary(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"CLOB", "RAW", "LONG RAW"})
        void nonBlobTypes_returnFalse(String dataType) {
            assertThat(HELPER.isBinary(dataType)).isFalse();
        }

        @Test
        @DisplayName("null -> false")
        void nullDataType_returnsFalse() {
            assertThat(HELPER.isBinary(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @Test
        @DisplayName("Oracle has no collection types -> always false")
        void anyDataType_returnsFalse() {
            assertThat(HELPER.isCollection("VARCHAR2")).isFalse();
            assertThat(HELPER.isCollection(null)).isFalse();
        }
    }
}
