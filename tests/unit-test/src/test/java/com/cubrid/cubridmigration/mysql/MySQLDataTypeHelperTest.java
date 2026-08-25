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
package com.cubrid.cubridmigration.mysql;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("MySQLDataTypeHelper")
class MySQLDataTypeHelperTest {

    private final MySQLDataTypeHelper helper = MySQLDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] {0}(p={1}, s={2}) -> \"{3}\"")
        @CsvSource({
            // DATA_TYPE_1: precision and scale are dropped.
            "tinyblob,                0,  2,  tinyblob",
            "tinytext,                0,  2,  tinytext",
            "blob,                    0,  2,  blob",
            "text,                    0,  2,  text",
            "mediumblob,              0,  2,  mediumblob",
            "mediumtext,              0,  2,  mediumtext",
            "longblob,                0,  2,  longblob",
            "longtext,                0,  2,  longtext",
            "time,                    0,  2,  time",
            "date,                    0,  2,  date",
            "timestamp,               0,  2,  timestamp",
            "datetime,                0,  2,  datetime",

            // DATA_TYPE_2: precision only.
            "char,                    10, 2,  char(10)",
            "varchar,                 10, 2,  varchar(10)",
            "tinyint,                 10, 2,  tinyint(10)",
            "smallint,                10, 2,  smallint(10)",
            "mediumint,               10, 2,  mediumint(10)",
            "int,                     10, 2,  int(10)",
            "bigint,                  10, 2,  bigint(10)",
            "bit,                     10, 2,  bit(10)",
            "binary,                  10, 2,  binary(10)",
            "varbinary,               10, 2,  varbinary(10)",
            "year,                    10, 2,  year(10)",

            // DATA_TYPE_3: precision and scale.
            "float,                   10, 2,  'float(10,2)'",
            "double,                  10, 2,  'double(10,2)'",
            "decimal,                 10, 2,  'decimal(10,2)'",

            // DATA_TYPE_4: precision and scale are dropped.
            "enum,                    10, 2,  enum",
            "set,                     10, 2,  set",

            // " unsigned" is split off and re-appended after the arguments.
            "int unsigned,            10, 2,  int(10) unsigned",
            "float unsigned,          10, 2,  'float(10,2) unsigned'",
            "bigint unsigned,         20, 2,  bigint(20) unsigned",

            // Unrecognized types are returned unchanged.
            "character(10),           10, 2,  character(10)",
            "unknowntype,             10, 2,  unknowntype",

            // DEFECT: the type lists hold lower-case names only, so an upper-case
            // type silently loses its precision - see MySQLDataTypeHelper.java:178
            "INT,                     10, 2,  INT",
            "BLOB,                    10, 2,  BLOB",
        })
        void variousTypes_returnShownDataType(
                String dataType, Integer precision, Integer scale, String expected) {
            assertThat(helper.getShownDataType(createColumn(dataType.trim(), precision, scale)))
                    .isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("null precision -> rendered as 0 because Column coalesces null")
        void nullPrecision_rendersZero() {
            assertThat(helper.getShownDataType(createColumn("int", null, null)))
                    .isEqualTo("int(0)");
        }

        @Test
        @DisplayName("null precision and scale -> both rendered as 0")
        void nullPrecisionAndScale_renderBothAsZero() {
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
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> helper.getShownDataType(createColumn(null, 10, 2)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseMainType()")
    class ParseMainType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            "'decimal(5,2)',           decimal",
            "enum,                     enum",
            "Integer(5),               Integer",
            "set(int),                 set",
            "varchar(200),             varchar",

            // " unsigned" survives, the argument list does not.
            "int unsigned,             int unsigned",
            "int(10) unsigned,         int unsigned",
            "'decimal(10,2) unsigned', decimal unsigned",
        })
        void typeWithArguments_returnMainType(String type, String expected) {
            assertThat(helper.parseMainType(type.trim())).isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyString_returnEmptyString() {
            assertThat(helper.parseMainType("")).isEmpty();
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void null_throwsNullPointerException() {
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
                    "Integer(5),                5",
                    "varchar(200),              200",
                    "'decimal(5,2)',            '5,2'",
                    "set(int),                  int",

                    // No argument list at all -> null.
                    "integer,                   null",

                    // DEFECT: the closing parenthesis is assumed to be the last
                    // character, so anything after it is mangled instead of ignored
                    // - see MySQLDataTypeHelper.java:321
                    "int(10) unsigned,          '10) unsigne'",
                    "'decimal(10,2) unsigned',  '10,2) unsigne'",
                })
        void typeWithArguments_returnArgumentPart(String type, String expected) {
            assertThat(helper.parseTypeRemain(type.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum value list -> the quoted values")
        void enumValueList_returnValueList() {
            assertThat(helper.parseTypeRemain("enum('a','b')")).isEqualTo("'a','b'");
        }

        @Test
        @DisplayName("empty argument list -> empty string")
        void emptyArgumentList_returnEmptyString() {
            assertThat(helper.parseTypeRemain("()")).isEmpty();
        }

        @Test
        @DisplayName("empty string -> null")
        void emptyString_returnNull() {
            assertThat(helper.parseTypeRemain("")).isNull();
        }

        @Test
        @DisplayName("unclosed parenthesis -> StringIndexOutOfBoundsException")
        void unclosedParenthesis_throwsStringIndexOutOfBoundsException() {
            // DEFECT: an unbalanced argument list is not rejected, it overflows the
            // substring range - see MySQLDataTypeHelper.java:321
            assertThatThrownBy(() -> helper.parseTypeRemain("char("))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void null_throwsNullPointerException() {
            assertThatThrownBy(() -> helper.parseTypeRemain(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parsePrecision()")
    class ParsePrecision {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "'decimal(5,2)',           5",
            "char(10),                 10",
            "varchar(200),             200",
            "'number(38,2)',           38",
            "char(0),                  0",

            // No argument list -> -1.
            "enum,                     -1",
            "integer,                  -1",

            // enum/set are -1 even when an argument list is present.
            "enum(int),                -1",
            "set(int),                 -1",
            "enum('a'),                -1",

            // The ") unsigne" residue of parseTypeRemain is patched back off.
            "int(10) unsigned,         10",
            "'decimal(10,2) unsigned', 10",
        })
        void typeWithArguments_returnPrecision(String type, int expected) {
            assertThat(helper.parsePrecision(type.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty argument list -> NumberFormatException")
        void emptyArgumentList_throwsNumberFormatException() {
            assertThatThrownBy(() -> helper.parsePrecision("()"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("non numeric argument -> NumberFormatException")
        void nonNumericArgument_throwsNumberFormatException() {
            assertThatThrownBy(() -> helper.parsePrecision("varchar(a)"))
                    .isInstanceOf(NumberFormatException.class);
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
                    "'decimal(10,0)',           0",
                    "'decimal(10,2) unsigned',  2",

                    // A single argument is a precision, not a scale -> null.
                    "char(10),                  null",
                    "varchar(200),              null",

                    // No argument list -> null.
                    "enum,                      null",
                    "integer,                   null",

                    // enum/set are null even when an argument list is present.
                    "enum(int),                 null",
                    "set(int),                  null",

                    // "10) unsigne" loses its residue and holds no comma -> null.
                    "int(10) unsigned,          null",
                })
        void typeWithArguments_returnScale(String type, Integer expected) {
            assertThat(helper.parseScale(type.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty argument list -> null")
        void emptyArgumentList_returnNull() {
            assertThat(helper.parseScale("()")).isNull();
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @Test
        @DisplayName("exactly one supported data type -> its jdbc type id")
        void singleSupportedType_returnJdbcDataTypeID() {
            Catalog catalog = createCatalog("INTEGER", dataType(Types.INTEGER));

            assertThat(helper.getJdbcDataTypeID(catalog, "INTEGER", null, null))
                    .isEqualTo(Types.INTEGER);
        }

        @Test
        @DisplayName("unknown data type -> IllegalArgumentException")
        void unknownDataType_throwsIllegalArgumentException() {
            Catalog catalog = createCatalog("INTEGER", dataType(Types.INTEGER));

            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "testnotype", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(testnotype)");
        }

        @Test
        @DisplayName("lookup key is case sensitive -> IllegalArgumentException")
        void lowercaseDataType_throwsIllegalArgumentException() {
            Catalog catalog = createCatalog("INTEGER", dataType(Types.INTEGER));

            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "integer", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(integer)");
        }

        @Test
        @DisplayName("empty catalog -> IllegalArgumentException")
        void emptyCatalog_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> helper.getJdbcDataTypeID(new Catalog(), "BLOB", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(BLOB)");
        }

        @Test
        @DisplayName("more than one supported data type -> IllegalArgumentException")
        void ambiguousSupportedTypes_throwsIllegalArgumentException() {
            Catalog catalog =
                    createCatalog("VARCHAR", dataType(Types.VARCHAR), dataType(Types.LONGVARCHAR));

            // DEFECT: the message has a doubled space after "Not supported"
            // - see MySQLDataTypeHelper.java:151
            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "VARCHAR", 200, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MySQL data type(VARCHAR: p=200, s=null)");
        }

        @Test
        @DisplayName("empty supported data type list -> IllegalArgumentException")
        void emptySupportedTypeList_throwsIllegalArgumentException() {
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put("BLOB", new ArrayList<DataType>());
            catalog.setSupportedDataType(supported);

            assertThatThrownBy(() -> helper.getJdbcDataTypeID(catalog, "BLOB", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MySQL data type(BLOB: p=null, s=null)");
        }

        private Catalog createCatalog(String key, DataType... dataTypes) {
            Catalog catalog = new Catalog();
            Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
            supported.put(key, Arrays.asList(dataTypes));
            catalog.setSupportedDataType(supported);
            return catalog;
        }

        private DataType dataType(int jdbcDataTypeID) {
            DataType dataType = new DataType();
            dataType.setJdbcDataTypeID(jdbcDataTypeID);
            return dataType;
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            // DATA_TYPE_5 members.
            "blob,       true",
            "tinyblob,   true",
            "mediumblob, true",
            "longblob,   true",
            "bit,        true",

            // Non binary types.
            "int,        false",
            "text,       false",
            "tinytext,   false",

            // binary and varbinary are not listed as binary types.
            "binary,     false",
            "varbinary,  false",

            // Only the bare main type matches, a full type string does not.
            "blob(10),   false",

            // DEFECT: List.indexOf against a lower-case-only DATA_TYPE_5 makes this
            // case sensitive, so upper-case blob types are not binary
            // - see MySQLDataTypeHelper.java:199
            "BLOB,       false",
            "BIT,        false",
            "Blob,       false",
        })
        void dataType_returnWhetherBinary(String dataType, boolean expected) {
            assertThat(helper.isBinary(dataType.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("null -> false")
        void null_returnFalse() {
            assertThat(helper.isBinary(null)).isFalse();
        }

        @Test
        @DisplayName("empty string -> false")
        void emptyString_returnFalse() {
            assertThat(helper.isBinary("")).isFalse();
        }
    }
}
