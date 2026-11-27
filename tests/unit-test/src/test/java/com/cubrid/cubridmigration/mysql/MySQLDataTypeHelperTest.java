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
package com.cubrid.cubridmigration.mysql;

import static com.cubrid.cubridmigration.testutil.TestCatalogFactory.createCatalog;
import static com.cubrid.cubridmigration.testutil.TestCatalogFactory.createDataType;
import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.sql.Types;

@DisplayName("MySQLDataTypeHelper")
class MySQLDataTypeHelperTest {

    private static final MySQLDataTypeHelper HELPER = MySQLDataTypeHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnsSameInstance() {
            assertThat(MySQLDataTypeHelper.getInstance(null))
                    .isSameAs(MySQLDataTypeHelper.getInstance("5.7"))
                    .isSameAs(MySQLDataTypeHelper.getInstance("8.0"));
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("MySQL helper -> DatabaseType.MYSQL")
        void mySqlHelper_returnsMySqlDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.MYSQL);
            assertThat(HELPER.getDBType().getName()).isEqualTo("MYSQL");
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @Test
        @DisplayName("exactly one supported data type -> its jdbc type id")
        void singleSupportedType_returnsJdbcDataTypeID() {
            Catalog catalog = createCatalog("INTEGER", Types.INTEGER);

            assertThat(HELPER.getJdbcDataTypeID(catalog, "INTEGER", null, null))
                    .isEqualTo(Types.INTEGER);
        }

        @Test
        @DisplayName("unknown data type -> IllegalArgumentException")
        void unknownDataType_throwsIllegalArgumentException() {
            Catalog catalog = createCatalog("INTEGER", Types.INTEGER);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "testnotype", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(testnotype)");
        }

        @Test
        @DisplayName("lookup key is case sensitive -> IllegalArgumentException")
        void lowercaseDataType_throwsIllegalArgumentException() {
            Catalog catalog = createCatalog("INTEGER", Types.INTEGER);

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "integer", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(integer)");
        }

        @Test
        @DisplayName("empty catalog -> IllegalArgumentException")
        void emptyCatalog_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(new Catalog(), "BLOB", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported MySQL data type(BLOB)");
        }

        @Test
        @DisplayName("more than one supported data type -> IllegalArgumentException")
        void ambiguousSupportedTypes_throwsIllegalArgumentException() {
            Catalog catalog =
                    createCatalog(
                            "VARCHAR",
                            createDataType("VARCHAR", Types.VARCHAR),
                            createDataType("VARCHAR", Types.LONGVARCHAR));

            // DEFECT: the message has a doubled space after "Not supported"
            // - see MySQLDataTypeHelper.getJdbcDataTypeID()
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "VARCHAR", 200, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MySQL data type(VARCHAR: p=200, s=null)");
        }

        @Test
        @DisplayName("empty supported data type list -> IllegalArgumentException")
        void emptySupportedTypeList_throwsIllegalArgumentException() {
            Catalog catalog = createCatalog("BLOB");

            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(catalog, "BLOB", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Not supported  MySQL data type(BLOB: p=null, s=null)");
        }
    }

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] {0}(p={1}, s={2}) -> \"{3}\"")
        @DisplayName("each DATA_TYPE list decides whether precision and scale are shown")
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
            "character(10),           10, 2,  character(10)",
            "unknowntype,             10, 2,  unknowntype",

            // DEFECT: the type lists hold lower-case names only, so an upper-case
            // type silently loses its precision - see MySQLDataTypeHelper.getShownDataType()
            "INT,                     10, 2,  INT",
            "BLOB,                    10, 2,  BLOB",
        })
        void variousTypes_returnsShownDataType(
                String dataType, Integer precision, Integer scale, String expected) {
            assertThat(HELPER.getShownDataType(createColumn(dataType, precision, scale)))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("null precision -> rendered as 0 because Column coalesces null")
        void nullPrecision_rendersZero() {
            assertThat(HELPER.getShownDataType(createColumn("int", null, null)))
                    .isEqualTo("int(0)");
        }

        @Test
        @DisplayName("null precision and scale -> both rendered as 0")
        void nullPrecisionAndScale_rendersBothAsZero() {
            assertThat(HELPER.getShownDataType(createColumn("decimal", null, null)))
                    .isEqualTo("decimal(0,0)");
        }

        @Test
        @DisplayName("empty data type -> empty string")
        void emptyDataType_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("", 10, 2))).isEmpty();
        }

        @Test
        @DisplayName("null data type -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.getShownDataType(createColumn(null, 10, 2)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("DATA_TYPE_5 members only, matched exactly and case sensitively")
        @CsvSource({
            // DATA_TYPE_5 members.
            "blob,       true",
            "tinyblob,   true",
            "mediumblob, true",
            "longblob,   true",
            "bit,        true",
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
            // - see MySQLDataTypeHelper.isBinary()
            "BLOB,       false",
            "BIT,        false",
            "Blob,       false",
        })
        void dataType_returnsWhetherBinary(String dataType, boolean expected) {
            assertThat(HELPER.isBinary(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not binary")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isBinary(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isCollection()")
    class IsCollection {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("only set counts, case insensitively and with any argument list")
        @CsvSource({
            "set,        true",
            "set(int),   true",

            // checkType() lower-cases the type and drops the argument list, so unlike
            // isBinary() this predicate is case insensitive.
            "SET,        true",
            "Set,        true",
            "'SET(a,b)', true",

            // enum is DATA_TYPE_4 too, but only "set" is a collection.
            "enum,       false",
            "int,        false",
            "setof,      false",

            // The CUBRID collection types are not MySQL types, the model holds "set" only.
            "multiset,   false",
            "sequence,   false",
        })
        void dataType_returnsWhetherCollection(String dataType, boolean expected) {
            assertThat(HELPER.isCollection(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not a collection")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isCollection(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("parseMainType()")
    class ParseMainType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("the argument list is dropped but a trailing unsigned survives")
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
        void typeWithArguments_returnsMainType(String type, String expected) {
            assertThat(HELPER.parseMainType(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyDataType_returnsEmptyString() {
            assertThat(HELPER.parseMainType("")).isEmpty();
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.parseMainType(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parsePrecision()")
    class ParsePrecision {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the first argument, -1 for enum/set and for a bare type")
        @CsvSource({
            "'decimal(5,2)',           5",
            "char(10),                 10",
            "varchar(200),             200",
            "'number(38,2)',           38",
            "char(0),                  0",
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
        void typeWithArguments_returnsPrecision(String type, int expected) {
            assertThat(HELPER.parsePrecision(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty argument list -> NumberFormatException")
        void emptyArgumentList_throwsNumberFormatException() {
            assertThatThrownBy(() -> HELPER.parsePrecision("()"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("non numeric argument -> NumberFormatException")
        void nonNumericArgument_throwsNumberFormatException() {
            assertThatThrownBy(() -> HELPER.parsePrecision("varchar(a)"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("upper case enum -> NumberFormatException")
        void uppercaseEnum_throwsNumberFormatException() {
            // DEFECT: the enum/set guard compares against the lower-case names only, so an
            // upper-case ENUM/SET reaches Integer.parseInt() instead of returning -1
            // - see MySQLDataTypeHelper.parsePrecision()
            assertThatThrownBy(() -> HELPER.parsePrecision("ENUM(a)"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.parsePrecision(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseScale()")
    class ParseScale {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the second argument, null when there is only one")
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
                    "enum,                      null",
                    "integer,                   null",

                    // enum/set are null even when an argument list is present.
                    "enum(int),                 null",
                    "set(int),                  null",

                    // "10) unsigne" loses its residue and holds no comma -> null.
                    "int(10) unsigned,          null",
                })
        void typeWithArguments_returnsScale(String type, Integer expected) {
            assertThat(HELPER.parseScale(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("empty argument list -> null")
        void emptyArgumentList_returnsNull() {
            assertThat(HELPER.parseScale("()")).isNull();
        }

        @Test
        @DisplayName("upper case enum -> NumberFormatException")
        void uppercaseEnum_throwsNumberFormatException() {
            // DEFECT: the enum/set guard compares against the lower-case names only, so an
            // upper-case ENUM/SET reaches Integer.parseInt() instead of returning null
            // - see MySQLDataTypeHelper.parseScale()
            assertThatThrownBy(() -> HELPER.parseScale("ENUM(a,b)"))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.parseScale(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("parseTypeRemain()")
    class ParseTypeRemain {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the text between the parentheses, null when there is none")
        @CsvSource(
                nullValues = "null",
                value = {
                    "Integer(5),                5",
                    "varchar(200),              200",
                    "'decimal(5,2)',            '5,2'",
                    "set(int),                  int",
                    "integer,                   null",

                    // DEFECT: the closing parenthesis is assumed to be the last
                    // character, so anything after it is mangled instead of ignored
                    // - see MySQLDataTypeHelper.parseTypeRemain()
                    "int(10) unsigned,          '10) unsigne'",
                    "'decimal(10,2) unsigned',  '10,2) unsigne'",
                })
        void typeWithArguments_returnsArgumentPart(String type, String expected) {
            assertThat(HELPER.parseTypeRemain(type)).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum value list -> the quoted values")
        void enumValueList_returnsValueList() {
            assertThat(HELPER.parseTypeRemain("enum('a','b')")).isEqualTo("'a','b'");
        }

        @Test
        @DisplayName("empty argument list -> empty string")
        void emptyArgumentList_returnsEmptyString() {
            assertThat(HELPER.parseTypeRemain("()")).isEmpty();
        }

        @Test
        @DisplayName("unclosed parenthesis -> StringIndexOutOfBoundsException")
        void unclosedParenthesis_throwsStringIndexOutOfBoundsException() {
            // DEFECT: an unbalanced argument list is not rejected, it overflows the
            // substring range - see MySQLDataTypeHelper.parseTypeRemain()
            assertThatThrownBy(() -> HELPER.parseTypeRemain("char("))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("empty string -> null")
        void emptyDataType_returnsNull() {
            assertThat(HELPER.parseTypeRemain("")).isNull();
        }

        @Test
        @DisplayName("null -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.parseTypeRemain(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isYear()")
    class IsYear {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("year with or without a precision, case insensitively")
        @CsvSource({
            // No production code calls isYear(), the rows pin the public contract only.
            "year,       true",
            "year(4),    true",
            "YEAR,       true",
            "Year,       true",
            "int,        false",
            "date,       false",
            "datetime,   false",
            "years,      false",
        })
        void dataType_returnsWhetherYear(String dataType, boolean expected) {
            assertThat(HELPER.isYear(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not year")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isYear(dataType)).isFalse();
        }
    }
}
