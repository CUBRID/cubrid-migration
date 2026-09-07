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
package com.cubrid.cubridmigration.mariadb;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("MariaDBDataTypeHelper")
class MariaDBDataTypeHelperTest {

    private final MariaDBDataTypeHelper helper = MariaDBDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnSameInstance() {
            assertThat(MariaDBDataTypeHelper.getInstance(null))
                    .isSameAs(MariaDBDataTypeHelper.getInstance("10.6"));
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("MariaDB helper -> DatabaseType.MARIADB")
        void mariaDbHelper_returnMariaDbDatabaseType() {
            assertThat(helper.getDBType()).isSameAs(DatabaseType.MARIADB);
            assertThat(helper.getDBType().getName()).isEqualTo("MARIADB");
        }
    }

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] {0}(p={1},s={2}) -> \"{3}\"")
        @CsvSource({
            // DATA_TYPE_1: precision and scale are never shown.
            "tinyblob,      0,  2,  tinyblob",
            "tinytext,      0,  2,  tinytext",
            "blob,          0,  2,  blob",
            "text,          0,  2,  text",
            "mediumblob,    0,  2,  mediumblob",
            "mediumtext,    0,  2,  mediumtext",
            "longblob,      0,  2,  longblob",
            "longtext,      0,  2,  longtext",
            "time,          0,  2,  time",
            "date,          0,  2,  date",
            "timestamp,     0,  2,  timestamp",
            "datetime,      0,  2,  datetime",

            // DATA_TYPE_2: precision only.
            "char,          10, 2,  char(10)",
            "varchar,       10, 2,  varchar(10)",
            "tinyint,       10, 2,  tinyint(10)",
            "smallint,      10, 2,  smallint(10)",
            "mediumint,     10, 2,  mediumint(10)",
            "int,           10, 2,  int(10)",
            "bigint,        10, 2,  bigint(10)",
            "bit,           10, 2,  bit(10)",
            "binary,        10, 2,  binary(10)",
            "varbinary,     10, 2,  varbinary(10)",
            "year,          10, 2,  year(10)",

            // DATA_TYPE_3: precision and scale.
            "float,         10, 2,  'float(10,2)'",
            "double,        10, 2,  'double(10,2)'",
            "decimal,       10, 2,  'decimal(10,2)'",

            // DATA_TYPE_4: element list is not part of the shown type.
            "enum,          10, 2,  enum",
            "set,           10, 2,  set",
        })
        void knownTypes_returnShownDataType(
                String dataType, Integer precision, Integer scale, String expected) {
            assertThat(helper.getShownDataType(createColumn(dataType, precision, scale)))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}(p=10,s=2) -> \"{1}\"")
        @CsvSource({
            "'float unsigned',      'float(10,2) unsigned'",
            "'decimal unsigned',    'decimal(10,2) unsigned'",
            "'int unsigned',        'int(10) unsigned'",
            "'bigint unsigned',     'bigint(10) unsigned'",
            "'blob unsigned',       'blob unsigned'",
            "'enum unsigned',       'enum unsigned'",
        })
        void unsignedTypes_keepUnsignedSuffixAfterPrecision(String dataType, String expected) {
            assertThat(helper.getShownDataType(createColumn(dataType, 10, 2))).isEqualTo(expected);
        }

        @Test
        @DisplayName("unknown type -> returned unchanged")
        void unknownType_returnTypeAsIs() {
            assertThat(helper.getShownDataType(createColumn("unknowntype", 10, 2)))
                    .isEqualTo("unknowntype");
        }

        @Test
        @DisplayName("type that already carries precision -> returned unchanged")
        void typeWithPrecisionInName_returnTypeAsIs() {
            assertThat(helper.getShownDataType(createColumn("character(10)", 10, 2)))
                    .isEqualTo("character(10)");
            assertThat(helper.getShownDataType(createColumn("int(10)", 10, 2)))
                    .isEqualTo("int(10)");
        }

        @Test
        @DisplayName("uppercase CHAR -> precision dropped")
        void uppercaseType_losePrecision() {
            // DEFECT: the type lists are matched case sensitively, so an uppercase data type
            // silently falls through to the "unknown type" branch and loses its precision
            // - see MariaDBDataTypeHelper.getShownDataType()
            assertThat(helper.getShownDataType(createColumn("CHAR", 10, 2))).isEqualTo("CHAR");
        }

        @Test
        @DisplayName("null precision and scale -> rendered as 0")
        void nullPrecisionAndScale_renderZero() {
            // Column.getPrecision()/getScale() substitute 0 for null, so the helper never sees null
            // - see Column.getPrecision()
            assertThat(helper.getShownDataType(createColumn("char", null, null)))
                    .isEqualTo("char(0)");
            assertThat(helper.getShownDataType(createColumn("decimal", null, null)))
                    .isEqualTo("decimal(0,0)");
        }

        @Test
        @DisplayName("empty data type -> empty string")
        void emptyDataType_returnEmptyString() {
            assertThat(helper.getShownDataType(createColumn("", 10, 2))).isEmpty();
        }

        @Test
        @DisplayName("null data type -> NullPointerException")
        void nullDataType_throwNullPointerException() {
            assertThatThrownBy(() -> helper.getShownDataType(createColumn(null, 10, 2)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseMainType()")
    class ParseMainType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            "'decimal(5,2)',            decimal",
            "char(10),                  char",
            "enum,                      enum",
            "enum(int),                 enum",
            "set(int),                  set",
            "integer,                   integer",

            // " unsigned" is split off first and appended to the main type.
            "'int unsigned',            'int unsigned'",
            "'int(10) unsigned',        'int unsigned'",
            "'decimal(10,2) unsigned',  'decimal unsigned'",
        })
        void variousTypes_returnMainType(String type, String expected) {
            assertThat(helper.parseMainType(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyString_returnEmptyString() {
            assertThat(helper.parseMainType("")).isEmpty();
        }

        @Test
        @DisplayName("leading parenthesis -> empty string")
        void leadingParenthesis_returnEmptyString() {
            assertThat(helper.parseMainType("(10)")).isEmpty();
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullType_throwNullPointerException() {
            assertThatThrownBy(() -> helper.parseMainType(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseTypeRemain()")
    class ParseTypeRemain {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "Integer(5),        5",
                    "char(10),          10",
                    "'decimal(5,2)',    '5,2'",
                    "enum(int),         int",
                    "set(int),          int",
                    "integer,           null",
                    "enum,              null",
                })
        void variousTypes_returnRemainPart(String type, String expected) {
            assertThat(helper.parseTypeRemain(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty string -> null")
        void emptyString_returnNull() {
            assertThat(helper.parseTypeRemain("")).isNull();
        }

        @Test
        @DisplayName("empty parenthesis -> empty string")
        void emptyParenthesis_returnEmptyString() {
            assertThat(helper.parseTypeRemain("char()")).isEmpty();
        }

        @Test
        @DisplayName("unsigned type -> truncated remain part")
        void unsignedType_returnTruncatedRemainPart() {
            // DEFECT: the remain part is cut at length()-1 assuming ')' is the last character, so
            // everything after the closing parenthesis leaks in minus its last character
            // - see MariaDBDataTypeHelper.parseTypeRemain()
            assertThat(helper.parseTypeRemain("int(10) unsigned")).isEqualTo("10) unsigne");
            assertThat(helper.parseTypeRemain("decimal(10,2) unsigned")).isEqualTo("10,2) unsigne");
        }

        @Test
        @DisplayName("unclosed parenthesis -> StringIndexOutOfBoundsException")
        void unclosedParenthesis_throwStringIndexOutOfBoundsException() {
            // DEFECT: substring(index + 1, length() - 1) inverts its bounds when '(' is the
            // last character, so an unclosed type crashes instead of returning null
            // - see MariaDBDataTypeHelper.parseTypeRemain()
            assertThatThrownBy(() -> helper.parseTypeRemain("char("))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("quoted enum elements -> the element list with the quotes kept")
        void quotedEnumElements_returnElementListWithQuotes() {
            assertThat(helper.parseTypeRemain("enum('a','b')")).isEqualTo("'a','b'");
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullType_throwNullPointerException() {
            assertThatThrownBy(() -> helper.parseTypeRemain(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parsePrecision()")
    class ParsePrecision {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "'decimal(5,2)',            5",
            "char(10),                  10",
            "varchar(200),              200",
            "'number(38,2)',            38",
            "char(0),                   0",
            "'double(255,30)',          255",

            // The ") unsigne" left over by parseTypeRemain() is stripped again here.
            "'int(10) unsigned',        10",
            "'decimal(10,2) unsigned',  10",
            "integer,                   -1",

            // enum/set are always -1 even when they carry an element list.
            "enum,                      -1",
            "set,                       -1",
            "enum(int),                 -1",
            "set(int),                  -1",
        })
        void variousTypes_returnPrecision(String type, int expected) {
            assertThat(helper.parsePrecision(type)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> NumberFormatException")
        @ValueSource(strings = {"char()", "char(abc)"})
        void nonNumericRemainPart_throwNumberFormatException(String type) {
            assertThatThrownBy(() -> helper.parsePrecision(type))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("uppercase ENUM -> NumberFormatException")
        void uppercaseEnum_throwNumberFormatException() {
            // DEFECT: the enum/set guard compares against lowercase literals only, so an uppercase
            // ENUM/SET reaches Integer.parseInt() and blows up instead of returning -1
            // - see MariaDBDataTypeHelper.parsePrecision()
            assertThatThrownBy(() -> helper.parsePrecision("ENUM(a)"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullType_throwNullPointerException() {
            assertThatThrownBy(() -> helper.parsePrecision(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseScale()")
    class ParseScale {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "'decimal(5,2)',            2",
                    "'number(38,2)',            2",
                    "'double(10,0)',            0",
                    "'decimal(10,2) unsigned',  2",
                    "char(10),                  null",
                    "varchar(200),              null",
                    "integer,                   null",

                    // parseTypeRemain() leaves "10) unsigne", which has no comma -> null.
                    "'int(10) unsigned',        null",

                    // enum/set are always null even when they carry an element list.
                    "enum,                      null",
                    "set,                       null",
                    "enum(int),                 null",
                    "set(int),                  null",
                })
        void variousTypes_returnScale(String type, Integer expected) {
            assertThat(helper.parseScale(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("uppercase ENUM -> NumberFormatException")
        void uppercaseEnum_throwNumberFormatException() {
            // DEFECT: the enum/set guard compares against lowercase literals only, so an uppercase
            // ENUM/SET reaches Integer.parseInt() and blows up instead of returning null
            // - see MariaDBDataTypeHelper.parseScale()
            assertThatThrownBy(() -> helper.parseScale("ENUM(a,b)"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullType_throwNullPointerException() {
            assertThatThrownBy(() -> helper.parseScale(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @ValueSource(strings = {"blob", "tinyblob", "mediumblob", "longblob", "bit"})
        void blobAndBitTypes_returnTrue(String dataType) {
            assertThat(helper.isBinary(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"int", "text", "tinytext", "varchar"})
        void nonBinaryTypes_returnFalse(String dataType) {
            assertThat(helper.isBinary(dataType)).isFalse();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"binary", "varbinary"})
        void byteStringTypes_returnFalse(String dataType) {
            // DEFECT: binary/varbinary hold raw bytes and the base class lists them in
            // BINARY_TYPES, but DATA_TYPE_5 omits them so isBinary() reports false
            // - see MariaDBDataTypeHelper.DATA_TYPE_5
            assertThat(helper.isBinary(dataType)).isFalse();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"BLOB", "Blob", "blob(10)", "bit(1)", " blob"})
        void unnormalizedBinaryTypes_returnFalse(String dataType) {
            // DEFECT: isBinary() does an exact list lookup instead of the checkType()
            // normalization used by isCollection()/isYear(), so case and precision defeat it
            // - see MariaDBDataTypeHelper.isBinary()
            assertThat(helper.isBinary(dataType)).isFalse();
        }

        @Test
        @DisplayName("empty string -> false")
        void emptyString_returnFalse() {
            assertThat(helper.isBinary("")).isFalse();
        }

        @Test
        @DisplayName("null -> false")
        void nullDataType_returnFalse() {
            assertThat(helper.isBinary(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @ValueSource(strings = {"set", "SET", "Set", "set(int)", "SET('a','b')"})
        void setTypes_returnTrue(String dataType) {
            assertThat(helper.isCollection(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"enum", "int", "setof", ""})
        void nonSetTypes_returnFalse(String dataType) {
            assertThat(helper.isCollection(dataType)).isFalse();
        }

        @Test
        @DisplayName("null -> false")
        void nullDataType_returnFalse() {
            assertThat(helper.isCollection(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isYear()")
    class IsYear {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @ValueSource(strings = {"year", "YEAR", "year(4)"})
        void yearTypes_returnTrue(String dataType) {
            assertThat(helper.isYear(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @ValueSource(strings = {"int", "date", "datetime", ""})
        void nonYearTypes_returnFalse(String dataType) {
            assertThat(helper.isYear(dataType)).isFalse();
        }

        @Test
        @DisplayName("null -> false")
        void nullDataType_returnFalse() {
            assertThat(helper.isYear(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @Test
        @DisplayName("single supported type -> its jdbc type id")
        void singleSupportedType_returnJdbcTypeId() {
            Catalog catalog = createCatalogWith("INTEGER", Types.INTEGER);

            assertThat(helper.getJdbcDataTypeID(catalog, "INTEGER", null, null))
                    .isEqualTo(Types.INTEGER);
        }

        @Test
        @DisplayName("precision and scale are ignored when the type is unambiguous")
        void unambiguousType_ignorePrecisionAndScale() {
            Catalog catalog = createCatalogWith("VARCHAR", Types.VARCHAR);

            assertThat(helper.getJdbcDataTypeID(catalog, "VARCHAR", 200, null))
                    .isEqualTo(Types.VARCHAR);
        }

        @Test
        @DisplayName("lookup key is case sensitive -> IllegalArgumentException")
        void differentCaseKey_throwIllegalArgumentException() {
            Catalog catalog = createCatalogWith("INTEGER", Types.INTEGER);

            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "integer", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MariaDB data type(integer)");
        }

        @Test
        @DisplayName("unknown type -> IllegalArgumentException")
        void unknownType_throwIllegalArgumentException() {
            assertThatThrownBy(
                            () -> helper.getJdbcDataTypeID(new Catalog(), "testnotype", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MariaDB data type(testnotype)");
        }

        @Test
        @DisplayName("null type -> IllegalArgumentException")
        void nullType_throwIllegalArgumentException() {
            assertThatThrownBy(() -> helper.getJdbcDataTypeID(new Catalog(), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MariaDB data type(null)");
        }

        @Test
        @DisplayName("several candidate types -> IllegalArgumentException with precision and scale")
        void ambiguousType_throwIllegalArgumentException() {
            Catalog catalog =
                    createCatalogWith(
                            "INT",
                            Arrays.asList(
                                    createDataType("INT", Types.INTEGER),
                                    createDataType("INT", Types.BIGINT)));

            // DEFECT: the message contains a double space after "Not supported"
            // - see MariaDBDataTypeHelper.getJdbcDataTypeID()
            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "INT", 10, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MariaDB data type(INT: p=10, s=0)");
        }

        @Test
        @DisplayName("empty candidate list -> IllegalArgumentException of the ambiguous branch")
        void emptyCandidateList_throwIllegalArgumentException() {
            Catalog catalog = createCatalogWith("VARCHAR", new ArrayList<DataType>());

            // DEFECT: an empty candidate list is not the ambiguous case, but the size == 1 check
            // sends it to the ambiguous message anyway
            // - see MariaDBDataTypeHelper.getJdbcDataTypeID()
            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "VARCHAR", 200, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MariaDB data type(VARCHAR: p=200, s=null)");
        }

        private Catalog createCatalogWith(String key, int jdbcTypeId) {
            return createCatalogWith(key, Arrays.asList(createDataType(key, jdbcTypeId)));
        }

        private Catalog createCatalogWith(String key, List<DataType> dataTypes) {
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put(key, dataTypes);
            catalog.setSupportedDataType(supported);
            return catalog;
        }

        private DataType createDataType(String typeName, int jdbcTypeId) {
            DataType dataType = new DataType();
            dataType.setTypeName(typeName);
            dataType.setJdbcDataTypeID(jdbcTypeId);
            return dataType;
        }
    }
}
