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
package com.cubrid.cubridmigration.cubrid;

import static com.cubrid.cubridmigration.testutil.TestColumnFactory.createColumn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.cubrid.exception.UnSupportCUBRIDDataTypeException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CUBRIDDataTypeHelper")
class CUBRIDDataTypeHelperTest {

    private static final CUBRIDDataTypeHelper HELPER = CUBRIDDataTypeHelper.getInstance(null);

    /** Fills in the JDBC type ids that the shown and DDL rendering read off the column. */
    private Column columnOf(String spelling) {
        Column column = createColumn(spelling);
        HELPER.setColumnDataType(spelling, column);
        return column;
    }

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("version argument is ignored -> same singleton")
        void anyVersion_returnsSameInstance() {
            assertThat(CUBRIDDataTypeHelper.getInstance(null))
                    .isSameAs(CUBRIDDataTypeHelper.getInstance("11.2"));
        }
    }

    @Nested
    @DisplayName("getCUBRIDDataTypeID()")
    class GetCUBRIDDataTypeID {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("each standard type name maps to its CUBRID data type id")
        @CsvSource({
            "smallint,      5",
            "int,           4",
            "bigint,        -5",
            "numeric,       2",
            "float,         7",
            "double,        8",
            "monetary,      30008",
            "char,          1",
            "varchar,       12",
            // nchar/nvarchar are synonyms of char/varchar, they have no id of their own.
            "nchar,         1",
            "nvarchar,      12",
            "date,          91",
            "time,          92",
            "timestamp,     93",
            "datetime,      30093",
            "bit,           -2",
            "bit varying,   -3",
            "set,           31111",
            "multiset,      41111",
            "sequence,      51111",
            "list,          51111",
            "object,        32000",
            "glo,           32004",
            "blob,          2004",
            "clob,          2005",
            "enum,          61111",
            "json,          71111",
            "timestamptz,   36",
            "timestampltz,  37",
            "datetimetz,    38",
            "datetimeltz,   39",
        })
        void standardName_returnsDataTypeId(String dataType, int expected) {
            assertThat(HELPER.getCUBRIDDataTypeID(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("synonyms, mixed case and an argument list all resolve alike")
        @CsvSource({
            "SMALLINT,                  5",
            "short,                     5",
            "INT,                       4",
            "BIGINT,                    -5",
            "biginteger,                -5",
            "'NUMERIC(38,2)',           2",
            "'decimal(38,2)',           2",
            "'dec(38,2)',               2",
            "FLOAT,                     7",
            "real,                      7",
            "DOUBLE,                    8",
            "MONETARY,                  30008",
            "CHAR(1),                   1",
            "CHARacter(1),              1",
            "VARCHAR(10),               12",
            "character varying(10),     12",
            "NCHAR(10),                 1",
            "NVARCHAR(100),             12",
            "DATE,                      91",
            "TIME,                      92",
            "TIMESTAMP,                 93",
            "DATETIME,                  30093",
            "BIT(10),                   -2",
            "VARBIT(11),                -3",
            "SET(int),                  31111",
            "MULTISET(varchar(2)),      41111",
            "'SEQUENCE(numeric(38,2))', 51111",
            "list_of(int),              51111",
            "OBJECT,                    32000",
            "BLOB,                      2004",
            "CLOB,                      2005",
            "JSON,                      71111",
        })
        void synonymOrDecoratedName_returnsDataTypeId(String dataType, int expected) {
            assertThat(HELPER.getCUBRIDDataTypeID(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum with elements -> enum id")
        void enumWithElements_returnsEnumId() {
            assertThat(HELPER.getCUBRIDDataTypeID("ENUM('a','A','b')")).isEqualTo(61111);
        }

        @Test
        @DisplayName("string -> UnSupportCUBRIDDataTypeException")
        void stringSpelling_throwsUnSupportCUBRIDDataTypeException() {
            // DEFECT: getDataTypeSymbol() expands "string" to varchar(1073741823) first, but
            // getCUBRIDDataTypeID() does not, so the two disagree on the same spelling - see
            // CUBRIDDataTypeHelper.getCUBRIDDataTypeID()
            assertThatThrownBy(() -> HELPER.getCUBRIDDataTypeID("string"))
                    .isInstanceOf(UnSupportCUBRIDDataTypeException.class)
                    .hasMessage("Unsupported CUBRID data type:string");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> UnSupportCUBRIDDataTypeException")
        @DisplayName("an unknown, null or empty name has no id")
        @NullAndEmptySource
        @ValueSource(strings = {"unknowntype"})
        void unknownNullOrEmpty_throwsUnSupportCUBRIDDataTypeException(String dataType) {
            assertThatThrownBy(() -> HELPER.getCUBRIDDataTypeID(dataType))
                    .isInstanceOf(UnSupportCUBRIDDataTypeException.class);
        }
    }

    @Nested
    @DisplayName("getDataTypeByteSize()")
    class GetDataTypeByteSize {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1} bytes")
        @DisplayName("each type id carries a fixed width, LOB and JSON count as zero")
        @CsvSource({
            "smallint,          2",
            "integer,           4",
            "bigint,            8",
            "float,             4",
            "double,            8",
            "monetary,          12",
            "'numeric(38,2)',   16",
            "date,              4",
            "time,              4",
            "timestamp,         4",
            "datetime,          8",
            "timestamptz,       8",
            "timestampltz,      8",
            "datetimetz,        12",
            "datetimeltz,       12",
            "enum('a'),         4",
            "object,            256",
            "set_of(int),       256",
            "multiset_of(int),  256",
            "sequence_of(int),  256",

            // The LOB and JSON payloads live outside the row, so they add nothing to it.
            "blob,              0",
            "clob,              0",
            "json,              0",
        })
        void fixedWidthType_returnsRegisteredWidth(String spelling, long expected) {
            assertThat(HELPER.getDataTypeByteSize(columnOf(spelling))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1} bytes")
        @DisplayName("the char, varchar and bit families report their precision instead")
        @CsvSource({
            "char(10),          10",
            "varchar(255),      255",
            "NCHAR(10),         10",
            "bit varying(64),   64",
        })
        void variableWidthType_returnsItsPrecision(String spelling, long expected) {
            assertThat(HELPER.getDataTypeByteSize(columnOf(spelling))).isEqualTo(expected);
        }

        @Test
        @DisplayName("bit(1024) -> 1024, the declared bit count is returned as a byte count")
        void bitType_returnsBitCountAsByteCount() {
            // DEFECT: bit(n) declares n bits, so the size is ceil(n / 8) bytes, but the
            // precision is returned unchanged and overstates the row width eightfold
            // - see CUBRIDDataTypeHelper.getDataTypeByteSize()
            assertThat(HELPER.getDataTypeByteSize(columnOf("bit(1024)"))).isEqualTo(1024L);
        }

        @Test
        @DisplayName("column whose JDBC type id was never set -> 0")
        void columnWithoutJdbcTypeId_returnsZero() {
            assertThat(HELPER.getDataTypeByteSize(createColumn("varchar", 10, null))).isZero();
        }
    }

    @Nested
    @DisplayName("getDBType()")
    class GetDBType {

        @Test
        @DisplayName("CUBRID helper -> DatabaseType.CUBRID")
        void cubridHelper_returnsCubridDatabaseType() {
            assertThat(HELPER.getDBType()).isSameAs(DatabaseType.CUBRID);
            assertThat(HELPER.getDBType().getName()).isEqualTo("CUBRID");
        }
    }

    @Nested
    @DisplayName("getDDLDataType()")
    class GetDDLDataType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("synonyms collapse the same way they do in the shown form")
        @CsvSource({
            "char(1),                       char(1)",
            "STRING,                        varchar(1073741823)",
            "NCHAR(1),                      char(1)",
            "varbit(30),                    bit varying(30)",
            "'dec(15,3)',                   'numeric(15,3)'",
            "integer,                       int",
            "smallint,                      short",
            "'set_of(numeric(15,3))',       'set(numeric(15,3))'",
            "sequence_of(char(10)),         list(char(10))",
            "object,                        object",
            "json,                          json",
        })
        void spelling_returnsStandardDdlDataType(String spelling, String expected) {
            assertThat(HELPER.getDDLDataType(columnOf(spelling))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> same as getShownDataType()")
        @DisplayName("no live type distinguishes the DDL form from the shown form")
        @ValueSource(
                strings = {
                    "char(1)",
                    "STRING",
                    "NCHAR(1)",
                    "varbit(30)",
                    "numeric(15,3)",
                    "integer",
                    "set_of(int)",
                    "ENUM('a','b')",
                    "object",
                    "json"
                })
        void spelling_rendersTheSameAsShownDataType(String spelling) {
            // Every live DataTypeSymbol is registered with the same string as its shown and its
            // inner name, so the flag that separates the two renderings has no effect
            Column column = columnOf(spelling);

            assertThat(HELPER.getDDLDataType(column)).isEqualTo(HELPER.getShownDataType(column));
        }

        @Test
        @DisplayName("column whose JDBC type id was never set -> empty string")
        void columnWithoutJdbcTypeId_returnsEmptyString() {
            assertThat(HELPER.getDDLDataType(createColumn("varchar", 10, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("getJdbcDataTypeID()")
    class GetJdbcDataTypeID {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the catalog, precision and scale arguments are never read")
        @CsvSource({
            "integer,           4",
            "'numeric(38,2)',   2",
            "varchar(10),       12",
            "set_of(int),       31111",
        })
        void dataType_returnsIdIgnoringCatalogPrecisionAndScale(String dataType, int expected) {
            assertThat(HELPER.getJdbcDataTypeID(null, dataType, null, null)).isEqualTo(expected);
            assertThat(HELPER.getJdbcDataTypeID(new Catalog(), dataType, 99, 9))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("unknown data type -> UnSupportCUBRIDDataTypeException")
        void unknownDataType_throwsUnSupportCubridDataTypeException() {
            assertThatThrownBy(() -> HELPER.getJdbcDataTypeID(null, "geometry", 10, 0))
                    .isInstanceOf(UnSupportCUBRIDDataTypeException.class)
                    .hasMessage("Unsupported CUBRID data type:geometry");
        }
    }

    @Nested
    @DisplayName("getPrecision()")
    class GetPrecision {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the precision of the instance, through a collection element type too")
        @CsvSource(
                nullValues = "null",
                value = {
                    "numeric(10),               10",
                    "varchar(10),               10",
                    // Collections delegate to their element type.
                    "'SET(numeric(10,3))',      10",
                    "SET(varchar(10)),          10",
                    "MULTISET(char(5)),         5",
                    // "string" is expanded to varchar(1073741823) before parsing.
                    "STRING,                    1073741823",
                    "string,                    1073741823",
                    // No precision part.
                    "int,                       null",
                    "SET(int),                  null",
                    "null,                      null",
                })
        void dataTypeInstance_returnsPrecisionOrNull(String dataType, Integer expected) {
            assertThat(HELPER.getPrecision(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("non numeric precision -> NumberFormatException")
        void nonNumericPrecision_throwsNumberFormatException() {
            assertThatThrownBy(() -> HELPER.getPrecision("varchar(a)"))
                    .isInstanceOf(NumberFormatException.class)
                    .hasMessageContaining("\"a\"");
        }
    }

    @Nested
    @DisplayName("getRemain()")
    class GetRemain {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the text inside the parentheses, lower cased")
        @CsvSource(
                nullValues = "null",
                value = {
                    "MULTISET(int),             int",
                    "MULTISET(varchar(10)),     varchar(10)",
                    "'MULTISET(numeric(10,2))', 'numeric(10,2)'",
                    // The result is always lower cased.
                    "'MULTISET(NUMERIC(10,2))', 'numeric(10,2)'",
                    "MULTISET(VARCHAR(10)),     varchar(10)",
                    // A scalar precision comes back as the raw digits.
                    "character(10),             10",
                    "set(integer),              integer",
                    // No parentheses at all.
                    "int,                       null",
                    "null,                      null",
                })
        void dataTypeInstance_returnsLowerCasedInnerPart(String dataType, String expected) {
            assertThat(HELPER.getRemain(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum elements are lower cased by the public overload")
        void enumElements_areLowerCased() {
            // The private overload has a keepCase flag for ENUM; the public one does not, so the
            // element case is lost.
            assertThat(HELPER.getRemain("ENUM('a','A')")).isEqualTo("'a','a'");
        }

        @Test
        @DisplayName("STRING -> null instead of the expanded precision")
        void stringSpelling_returnsNull() {
            // DEFECT: "string" is expanded to varchar(1073741823), but the substring is still
            // bounded by the original argument's length, so it overflows and the exception is
            // swallowed - see CUBRIDDataTypeHelper.getRemain()
            assertThat(HELPER.getRemain("STRING")).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("an unbalanced instance is truncated instead of rejected")
        @CsvSource({
            "'char(10',                 1",
            "'char(10,',                10",
            "'varchar(20',              2",
            "'varchar(20,',             20",
            "'numeric(10,2',            '10,'",
            "'set(int',                 in",
            "'set(int,',                int",
        })
        void unbalancedParenthesis_returnsTruncatedRemain(String dataType, String expected) {
            // DEFECT: the closing parenthesis is assumed to be the last character, so an
            // unbalanced instance yields a silently truncated string instead of null - see
            // CUBRIDDataTypeHelper.getRemain()
            assertThat(HELPER.getRemain(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getScale()")
    class GetScale {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the scale of the instance, through a collection element type too")
        @CsvSource(
                nullValues = "null",
                value = {
                    "'numeric(10,3)',           3",
                    // Collections delegate to their element type, recursively.
                    "'SET(numeric(10,3))',      3",
                    "'MULTISET(numeric(10,2))', 2",
                    "'LIST(numeric(7,4))',      4",
                    "'SET(SET(numeric(10,3)))', 3",
                    // No scale part.
                    "varchar(200),              null",
                    "numeric(38),               null",
                    "int,                       null",
                    "SET(int),                  null",
                    "STRING,                    null",
                    "null,                      null",
                })
        void dataTypeInstance_returnsScaleOrNull(String dataType, Integer expected) {
            assertThat(HELPER.getScale(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("non numeric scale -> NumberFormatException")
        void nonNumericScale_throwsNumberFormatException() {
            assertThatThrownBy(() -> HELPER.getScale("numeric(10,a)"))
                    .isInstanceOf(NumberFormatException.class)
                    .hasMessageContaining("\"a\"");
        }
    }

    @Nested
    @DisplayName("getShownDataType()")
    class GetShownDataType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("every synonym collapses onto its standard CUBRID spelling")
        @CsvSource({
            // LOB types carry no precision.
            "CLOB,                          clob",
            "BLOB,                          blob",

            // char / varchar synonyms.
            "character(1),                  char(1)",
            "char(1),                       char(1)",
            "character varying(1073741823), varchar(1073741823)",
            "STRING,                        varchar(1073741823)",
            "character varying(30),         varchar(30)",
            "VARCHAR(30),                   varchar(30)",

            // national character synonyms collapse onto char / varchar.
            "national character(1),         char(1)",
            "NCHAR(1),                      char(1)",
            "national character varying(4), varchar(4)",
            "VARNCHAR(4),                   varchar(4)",

            // bit types.
            "bit(10),                       bit(10)",
            "bit varying(30),               bit varying(30)",
            "varbit(30),                    bit varying(30)",

            // numeric synonyms.
            "'numeric(15,0)',               'numeric(15,0)'",
            "'dec(15,0)',                   'numeric(15,0)'",
            "'decimal(15,0)',               'numeric(15,0)'",

            // numeric types that never take a precision.
            "integer,                       int",
            "smallint,                      short",
            "bigint,                        bigint",
            "monetary,                      monetary",
            "float,                         float",
            "real,                          float",
            "double,                        double",
            "double precision,              double",

            // date / time types.
            "date,                          date",
            "time,                          time",
            "timestamp,                     timestamp",
            "datetime,                      datetime",

            // collections render the element type inside the parentheses.
            "'set_of(numeric(15,0))',       'set(numeric(15,0))'",
            "multiset_of(string),           multiset(varchar(1073741823))",
            "sequence_of(char(10)),         list(char(10))",
            "'sequence_of(numeric(10,2))',  'list(numeric(10,2))'",
            "sequence_of(datetime),         list(datetime)",
            "SEQUENCE_OF(DATETIME),         list(datetime)",

            // remaining scalar types.
            "object,                        object",
            "json,                          json",
        })
        void spelling_returnsStandardShownDataType(String spelling, String expected) {
            assertThat(HELPER.getShownDataType(columnOf(spelling))).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum -> elements are kept verbatim, including their case")
        void enumSpelling_keepsElementsVerbatim() {
            assertThat(HELPER.getShownDataType(columnOf("ENUM('1','2','a','A')")))
                    .isEqualTo("enum('1','2','a','A')");
        }

        @Test
        @DisplayName("numeric without precision -> maximum precision and zero scale")
        void numericWithoutPrecision_usesMaximumPrecisionAndZeroScale() {
            assertThat(HELPER.getShownDataType(columnOf("numeric"))).isEqualTo("numeric(38,0)");
        }

        @Test
        @DisplayName("numeric(38) -> scale defaults to 0")
        void numericWithoutScale_defaultsScaleToZero() {
            assertThat(HELPER.getShownDataType(columnOf("numeric(38)"))).isEqualTo("numeric(38,0)");
        }

        @Test
        @DisplayName("char without precision -> char(0)")
        void charWithoutPrecision_rendersZeroPrecision() {
            // DEFECT: Column.getPrecision() substitutes 0 for a missing precision, so a bare char
            // renders as char(0), which CUBRID rejects as DDL - see
            // CUBRIDDataTypeHelper.innerGetShownDataTypeType()
            assertThat(HELPER.getShownDataType(columnOf("char"))).isEqualTo("char(0)");
        }

        @Test
        @DisplayName("column whose JDBC type id was never set -> empty string")
        void columnWithoutJdbcTypeId_returnsEmptyString() {
            assertThat(HELPER.getShownDataType(createColumn("varchar", 10, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("getStdMainDataType()")
    class GetStdMainDataType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("every synonym folds onto its standard main type name")
        @CsvSource({
            // Case folding only.
            "CLOB,                          clob",

            // char / varchar synonyms.
            "character(1),                  char",
            "char(1),                       char",
            "character varying(30),         varchar",
            "char varying(200),             varchar",
            "STRING,                        varchar",
            "string,                        varchar",

            // national character synonyms collapse onto char / varchar.
            "national character(1),         char",
            "NCHAR(1),                      char",
            "national character varying(4), varchar",
            "VARNCHAR(4),                   varchar",
            "nchar varying(10),             varchar",

            // bit types.
            "bit varying(30),               bit varying",
            "varbit(30),                    bit varying",

            // numeric synonyms.
            "'dec(15,0)',                   numeric",
            "'decimal(15,0)',               numeric",
            "integer,                       int",
            "smallint,                      short",
            "biginteger,                    bigint",
            "real,                          float",
            "double precision,              double",

            // collection synonyms; "sequence" family is shown as "list".
            "'set_of(numeric(15,0))',       set",
            "multiset_of(string),           multiset",
            "sequence_of(char(10)),         list",
            "SEQUENCE_OF(DATETIME),         list",
            "list_of(int),                  list",
        })
        void synonym_returnsStandardMainDataType(String spelling, String expected) {
            assertThat(HELPER.getStdMainDataType(spelling)).isEqualTo(expected);
        }

        @Test
        @DisplayName("enum with elements -> enum")
        void enumSpelling_returnsEnum() {
            assertThat(HELPER.getStdMainDataType("ENUM('1','2','a','A')")).isEqualTo("enum");
        }

        @Test
        @DisplayName("unsupported type -> UnSupportCUBRIDDataTypeException")
        void unsupportedType_throwsUnSupportCUBRIDDataTypeException() {
            assertThatThrownBy(() -> HELPER.getStdMainDataType("unknowntype"))
                    .isInstanceOf(UnSupportCUBRIDDataTypeException.class)
                    .hasMessage("Unsupported CUBRID data type:unknowntype");
        }
    }

    @Nested
    @DisplayName("isBinary()")
    class IsBinary {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the bit family is binary, the LOB types are not")
        @CsvSource({
            "bit,                   true",
            "bit varying,           true",
            "varbit,                true",
            "BIT(10),               true",
            "'bit varying(30)',     true",

            // The LOB types hold bytes as well, but the shared model lists neither of them.
            "blob,                  false",
            "clob,                  false",
            "char(10),              false",
            "integer,               false",
        })
        void dataType_returnsWhetherBinary(String dataType, boolean expected) {
            assertThat(HELPER.isBinary(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("binary and varbinary come from the shared model, not from CUBRID")
        @ValueSource(strings = {"binary", "varbinary"})
        void typesCubridDoesNotDefine_returnsTrue(String dataType) {
            // DBDataTypeHelper.BINARY_TYPES is shared with every other dialect
            assertThat(HELPER.isBinary(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not binary")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isBinary(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isObjectType()")
    class IsObjectType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the whole name must be object, case insensitively")
        @CsvSource({
            "object,        true",
            "OBJECT,        true",
            "Object,        true",

            // Unlike every other predicate here this one skips checkType(), so an argument
            // list is not stripped off before the comparison.
            "object(x),     false",
            "integer,       false",
        })
        void dataType_returnsWhetherObjectType(String dataType, boolean expected) {
            assertThat(HELPER.isObjectType(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not the object type")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isObjectType(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isStrictNumeric()")
    class IsStrictNumeric {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("the fixed point family plus monetary, not the other numbers")
        @CsvSource({
            "numeric,           true",
            "decimal,           true",
            "dec,               true",
            "'NUMERIC(38,2)',   true",

            // monetary is in the model although the method comment names only numeric/decimal/dec.
            "monetary,          true",
            "integer,           false",
            "bigint,            false",
            "float,             false",
            "double,            false",
            "char(10),          false",
        })
        void dataType_returnsWhetherStrictNumeric(String dataType, boolean expected) {
            assertThat(HELPER.isStrictNumeric(dataType)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null or empty -> false")
        @DisplayName("null and empty are not strict numeric")
        @NullAndEmptySource
        void nullOrEmptyDataType_returnsFalse(String dataType) {
            assertThat(HELPER.isStrictNumeric(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isSupportAutoIncr()")
    class IsSupportAutoIncr {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("the integer family can carry an auto increment")
        @ValueSource(strings = {"int", "integer", "short", "smallint", "bigint", "INT", "BIGINT"})
        void integerFamily_returnsTrue(String dataType) {
            assertThat(HELPER.isSupportAutoIncr(dataType, null, null)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("mediumint and tinyint come from the shared model, not from CUBRID")
        @ValueSource(strings = {"mediumint", "tinyint"})
        void typesCubridDoesNotDefine_returnsTrue(String dataType) {
            // DBDataTypeHelper.AUTOINC_TYPES is shared with every other dialect
            assertThat(HELPER.isSupportAutoIncr(dataType, null, null)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("everything outside the integer family cannot")
        @ValueSource(strings = {"char", "varchar", "float", "double", "monetary", "date", "blob"})
        void nonIntegerFamily_returnsFalse(String dataType) {
            assertThat(HELPER.isSupportAutoIncr(dataType, null, null)).isFalse();
        }

        @ParameterizedTest(name = "[{index}] numeric with scale {0} -> {1}")
        @DisplayName("numeric qualifies only when its scale is exactly 0")
        @CsvSource(
                nullValues = "null",
                value = {"0, true", "1, false", "2, false", "null, false"})
        void numericScale_decidesAutoIncrSupport(Integer scale, boolean expected) {
            assertThat(HELPER.isSupportAutoIncr("numeric", null, scale)).isEqualTo(expected);
        }

        @Test
        @DisplayName("default value present -> false whatever the data type")
        void presentDefaultValue_returnsFalse() {
            assertThat(HELPER.isSupportAutoIncr("integer", "0", null)).isFalse();
            assertThat(HELPER.isSupportAutoIncr("numeric", "0", 0)).isFalse();
        }

        @Test
        @DisplayName("empty default value -> counted as no default at all")
        void emptyDefaultValue_returnsTrue() {
            assertThat(HELPER.isSupportAutoIncr("integer", "", null)).isTrue();
        }

        @Test
        @DisplayName("null data type -> NullPointerException")
        void nullDataType_throwsNullPointerException() {
            assertThatThrownBy(() -> HELPER.isSupportAutoIncr(null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isValidDatatype()")
    class IsValidDatatype {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("well formed instances inside the precision and scale bounds pass")
        @ValueSource(
                strings = {
                    "smallint",
                    "VARCHAR(10)",
                    // Upper bound of the char/varchar/bit precision.
                    "varchar(1073741823)",
                    "char(1073741823)",
                    "bit(1073741823)",
                    "bit varying(1073741823)",
                    // numeric requires precision <= 38 and 0 <= scale <= precision.
                    "numeric(38,2)",
                    "numeric(38,38)",
                    "numeric(0,0)",
                    // A zero precision is accepted for char.
                    "char(0)",
                    // enum short circuits before any precision check.
                    "enum",
                    "ENUM('a','b')",
                    // Collections are validated through their element type.
                    "set(int)",
                    "set(varchar(10))",
                    "multiset_of(string)",
                    "sequence_of(int)",
                    "json",
                    "object",
                    "glo",
                    "timestamptz"
                })
        void supportedDataTypeInstance_returnsTrue(String dataType) {
            assertThat(HELPER.isValidDatatype(dataType)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("bad parentheses, out-of-range bounds and unknown types are rejected")
        @ValueSource(
                strings = {
                    // Unbalanced parentheses.
                    "VARCHAR(2",
                    "VARCHAR2(2",
                    "char(10))",
                    "sequence_of(char(10)",
                    // VARCHAR2 is an Oracle spelling, not a CUBRID synonym.
                    "VARCHAR2(10)",
                    // Non numeric precision.
                    "VARCHAR(a)",
                    // A precision on a type that takes none.
                    "int(10)",
                    // char/varchar/bit require a precision, numeric requires precision and scale.
                    "char",
                    "numeric",
                    "numeric(38)",
                    // One over the maximum precision.
                    "varchar(1073741824)",
                    "char(1073741824)",
                    "bit(1073741824)",
                    // numeric precision over 38, or a scale larger than the precision.
                    "numeric(39,2)",
                    "numeric(2,5)",
                    "numeric(37,38)",
                    // Unknown types, also as a collection element type.
                    "unknowntype",
                    "set(unknowntype)",
                    "set(numeric(39,2))"
                })
        void unsupportedDataTypeInstance_returnsFalse(String dataType) {
            assertThat(HELPER.isValidDatatype(dataType)).isFalse();
        }

        @Test
        @DisplayName("list_of(int) -> false although list_of is a known synonym")
        void listOfCollection_returnsFalse() {
            // DEFECT: "list_of" is a registered synonym of the sequence type, but isCollection()
            // does not list it, so the whole instance is validated as a scalar - see
            // CUBRIDDataTypeHelper.isCollection()
            assertThat(HELPER.isValidDatatype("list_of(int)")).isFalse();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("null, empty and blank are all rejected")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void nullEmptyOrBlank_returnsFalse(String dataType) {
            assertThat(HELPER.isValidDatatype(dataType)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidValue()")
    class IsValidValue {

        @ParameterizedTest(name = "[{index}] {0} default \"{1}\" -> {2}")
        @DisplayName("each type family checks its default value its own way")
        @CsvSource({
            // Character types are length checked against their precision.
            "char(1),       a,                       true",
            "char(1),       aa,                      false",
            "varchar(2),    aa,                      true",
            "varchar(2),    aaa,                     false",

            // Integer types are parsed as int.
            "int,           1,                       true",
            "int,           a,                       false",
            "integer,       1,                       true",
            "integer,       b,                       false",
            "integer,       99999999999999999,       false",
            "short,         1,                       true",
            "short,         b,                       false",
            "smallint,      1,                       true",
            "smallint,      a,                       false",

            // long is parsed as long, bigint as an unbounded BigInteger.
            "long,          111,                     true",
            "long,          ff,                      false",
            "bigint,        111,                     true",
            "bigint,        ddd,                     false",
            "bigint,        99999999999999999999,    true",

            // Date and time types are parsed with a lenient SimpleDateFormat.
            "date,          2013-01-01,              true",
            "date,          fasdf,                   false",
            "time,          01:01:01.001,            true",
            "time,          fasdfasdf,               false",
            "datetime,      2013-01-01 01:01:01.001, true",
            "datetime,      ttttt,                   false",
            "timestamp,     2013-01-01 01:01:01,     true",
            "timestamp,     ffff,                    false",

            // Types with no validation rule at all.
            "clob,          anything,                true",
            "json,          '{}',                    true",
        })
        void defaultValue_validatedAgainstDataType(
                String dataType, String value, boolean expected) {
            assertThat(HELPER.isValidValue(dataType, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] bit(8) default \"{0}\" -> true")
        @DisplayName("b'' and x'' literals are accepted in either case")
        @ValueSource(
                strings = {
                    "b'00000001'",
                    "B'00000001'",
                    "x'FF'",
                    "X'ff'",
                    "0xFF",
                    "0Xff",
                    "0b00000001",
                    "0B00000001"
                })
        void wellFormedBitLiteral_returnsTrue(String value) {
            assertThat(HELPER.isValidValue("bit(8)", value)).isTrue();
        }

        @Test
        @DisplayName("bit varying accepts the same literals as bit")
        void bitVaryingLiteral_returnsTrue() {
            assertThat(HELPER.isValidValue("bit varying(16)", "b'00000001'")).isTrue();
        }

        @ParameterizedTest(name = "[{index}] bit(8) default \"{0}\" -> true")
        @DisplayName("a malformed bit literal falls through and is accepted too")
        @ValueSource(strings = {"b'0a000001'", "0b00000002", "zzz"})
        void malformedBitLiteral_returnsTrue(String value) {
            // DEFECT: when no BIT_VALUE_PATTERN matches, the bit branch falls through to the
            // final "return true", so any garbage is accepted for a bit column. The legacy test
            // pinned this with the double negative assertFalse(!isValidValue(...)) - see
            // CUBRIDDataTypeHelper.isValidValue()
            assertThat(HELPER.isValidValue("bit(8)", value)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} default \"{1}\" -> {2}")
        @DisplayName("a decimal point or a sign makes a legal numeric default invalid")
        @CsvSource({
            "'numeric(10,2)', 123,  true",
            "'numeric(10,2)', 12.5, false",
            "'numeric(10,2)', -5,   false",
            "'numeric(10,2)', abc,  false",
            "monetary,        123,  true",
            "double,          1.5,  false",
            "float,           1.5,  false",
        })
        void numericDefaultValue_acceptsDigitsOnly(
                String dataType, String value, boolean expected) {
            // DEFECT: StringUtils.isNumeric() rejects a decimal point and a sign, so legal
            // defaults such as 12.5 or -5 are reported invalid - see
            // CUBRIDDataTypeHelper.isValidValue()
            assertThat(HELPER.isValidValue(dataType, value)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] datetime default \"{0}\" -> true")
        @DisplayName("the datetime functions CMT knows are accepted as defaults")
        @ValueSource(strings = {"SYS_DATETIME", "sys_datetime", "CURRENT_TIMESTAMP", "now()"})
        void knownDateFunction_returnsTrue(String value) {
            assertThat(HELPER.isValidValue("datetime", value)).isTrue();
        }

        @Test
        @DisplayName("unknown function -> false")
        void unknownDateFunction_returnsFalse() {
            assertThat(HELPER.isValidValue("datetime", "unknown_func()")).isFalse();
        }

        @Test
        @DisplayName("character type without a precision -> NumberFormatException")
        void characterTypeWithoutPrecision_throwsNumberFormatException() {
            // DEFECT: the precision is read with a blind substring, so a precision-less
            // character type parses the type name itself as a number
            // - see CUBRIDDataTypeHelper.isValidValue()
            assertThatThrownBy(() -> HELPER.isValidValue("varchar", "x"))
                    .isInstanceOf(NumberFormatException.class)
                    .hasMessageContaining("\"varcha\"");
        }

        @ParameterizedTest(name = "[{index}] null or empty default -> true")
        @DisplayName("no default value at all is always valid")
        @NullAndEmptySource
        void nullOrEmptyValue_returnsTrue(String value) {
            assertThat(HELPER.isValidValue("char(1)", value)).isTrue();
        }
    }

    @Nested
    @DisplayName("parseDTInstance()")
    class ParseDTInstance {

        @Test
        @DisplayName("int -> name only")
        void scalarWithoutPrecision_setsNameOnly() {
            DataTypeInstance dti = HELPER.parseDTInstance("int");

            assertThat(dti.getName()).isEqualTo("int");
            assertThat(dti.getPrecision()).isNull();
            assertThat(dti.getScale()).isNull();
            assertThat(dti.getElments()).isNull();
            assertThat(dti.getSubType()).isNull();
        }

        @Test
        @DisplayName("varchar(100) -> precision only")
        void scalarWithPrecision_setsPrecision() {
            DataTypeInstance dti = HELPER.parseDTInstance("varchar(100)");

            assertThat(dti.getName()).isEqualTo("varchar");
            assertThat(dti.getPrecision()).isEqualTo(100);
            assertThat(dti.getScale()).isNull();
            assertThat(dti.getSubType()).isNull();
        }

        @Test
        @DisplayName("numeric(38,2) -> precision and scale")
        void scalarWithPrecisionAndScale_setsBoth() {
            DataTypeInstance dti = HELPER.parseDTInstance("numeric(38,2)");

            assertThat(dti.getName()).isEqualTo("numeric");
            assertThat(dti.getPrecision()).isEqualTo(38);
            assertThat(dti.getScale()).isEqualTo(2);
            assertThat(dti.getSubType()).isNull();
        }

        @Test
        @DisplayName("numeric(38) -> precision only, no scale")
        void numericWithoutScale_leavesScaleNull() {
            DataTypeInstance dti = HELPER.parseDTInstance("numeric(38)");

            assertThat(dti.getName()).isEqualTo("numeric");
            assertThat(dti.getPrecision()).isEqualTo(38);
            assertThat(dti.getScale()).isNull();
        }

        @Test
        @DisplayName("enum -> elements verbatim, no precision")
        void enumInstance_setsElements() {
            DataTypeInstance dti = HELPER.parseDTInstance("enum('1','2','4','3','A')");

            assertThat(dti.getName()).isEqualTo("enum");
            assertThat(dti.getElments()).isEqualTo("'1','2','4','3','A'");
            assertThat(dti.getPrecision()).isNull();
            assertThat(dti.getScale()).isNull();
            assertThat(dti.getSubType()).isNull();
        }

        @Test
        @DisplayName("set(int) -> element type in the sub type")
        void collectionOfScalar_setsSubType() {
            DataTypeInstance dti = HELPER.parseDTInstance("set(int)");

            assertThat(dti.getName()).isEqualTo("set");
            assertThat(dti.getPrecision()).isNull();
            assertThat(dti.getScale()).isNull();
            assertThat(dti.getElments()).isNull();
            assertThat(dti.getSubType().getName()).isEqualTo("int");
            assertThat(dti.getSubType().getPrecision()).isNull();
            assertThat(dti.getSubType().getScale()).isNull();
            assertThat(dti.getSubType().getSubType()).isNull();
        }

        @Test
        @DisplayName("set(varchar(100)) -> element precision stays on the sub type")
        void collectionOfSizedScalar_keepsPrecisionOnSubType() {
            DataTypeInstance dti = HELPER.parseDTInstance("set(varchar(100))");

            assertThat(dti.getName()).isEqualTo("set");
            assertThat(dti.getPrecision()).isNull();
            assertThat(dti.getSubType().getName()).isEqualTo("varchar");
            assertThat(dti.getSubType().getPrecision()).isEqualTo(100);
            assertThat(dti.getSubType().getScale()).isNull();
        }

        @Test
        @DisplayName("set(numeric(38,2)) -> element precision and scale on the sub type")
        void collectionOfNumeric_keepsPrecisionAndScaleOnSubType() {
            DataTypeInstance dti = HELPER.parseDTInstance("set(numeric(38,2))");

            assertThat(dti.getName()).isEqualTo("set");
            assertThat(dti.getSubType().getName()).isEqualTo("numeric");
            assertThat(dti.getSubType().getPrecision()).isEqualTo(38);
            assertThat(dti.getSubType().getScale()).isEqualTo(2);
        }

        @Test
        @DisplayName("SET(NUMERIC(38,2)) -> names keep their original case")
        void upperCaseInstance_keepsNameCase() {
            DataTypeInstance dti = HELPER.parseDTInstance("SET(NUMERIC(38,2))");

            assertThat(dti.getName()).isEqualTo("SET");
            assertThat(dti.getSubType().getName()).isEqualTo("NUMERIC");
            assertThat(dti.getSubType().getPrecision()).isEqualTo(38);
            assertThat(dti.getSubType().getScale()).isEqualTo(2);
        }

        @Test
        @DisplayName("SET(STRING) -> element expanded to varchar with the maximum precision")
        void collectionOfString_expandsElementToMaximumVarchar() {
            DataTypeInstance dti = HELPER.parseDTInstance("SET(STRING)");

            assertThat(dti.getName()).isEqualTo("SET");
            assertThat(dti.getSubType().getName()).isEqualTo("varchar");
            assertThat(dti.getSubType().getPrecision()).isEqualTo(1073741823);
            assertThat(dti.getSubType().getScale()).isNull();
            assertThat(dti.getSubType().getSubType()).isNull();
        }

        @Test
        @DisplayName("STRING -> varchar with the maximum precision")
        void stringInstance_expandsToMaximumVarchar() {
            DataTypeInstance dti = HELPER.parseDTInstance("STRING");

            assertThat(dti.getName()).isEqualTo("varchar");
            assertThat(dti.getPrecision()).isEqualTo(1073741823);
            assertThat(dti.getSubType()).isNull();
        }

        @Test
        @DisplayName("non numeric precision -> IllegalArgumentException")
        void nonNumericPrecision_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.parseDTInstance("varchar(abc)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid data type:varchar(abc)");
        }

        @ParameterizedTest(name = "[{index}] null or empty -> IllegalArgumentException")
        @DisplayName("null and empty are rejected before parsing starts")
        @NullAndEmptySource
        void nullOrEmpty_throwsIllegalArgumentException(String fullDataType) {
            assertThatThrownBy(() -> HELPER.parseDTInstance(fullDataType))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type can't be empty.");
        }
    }

    @Nested
    @DisplayName("setColumnDataType()")
    class SetColumnDataType {

        @Test
        @DisplayName("collection spelling -> main type and element type are both stored")
        void collectionSpelling_storesMainAndElementType() {
            Column column = columnOf("set_of(numeric(15,3))");

            assertThat(column.getDataType()).isEqualTo("set");
            assertThat(column.getSubDataType()).isEqualTo("numeric");
            assertThat(column.getPrecision()).isEqualTo(15);
            assertThat(column.getScale()).isEqualTo(3);
            assertThat(column.getJdbcIDOfDataType()).isEqualTo(31111);
            assertThat(column.getJdbcIDOfSubDataType()).isEqualTo(2);
        }

        @Test
        @DisplayName("STRING -> varchar with the maximum precision")
        void stringSpelling_expandsToMaximumVarchar() {
            Column column = columnOf("STRING");

            assertThat(column.getDataType()).isEqualTo("varchar");
            assertThat(column.getPrecision()).isEqualTo(1073741823);
            assertThat(column.getSubDataType()).isNull();
            assertThat(column.getJdbcIDOfDataType()).isEqualTo(12);
        }

        @Test
        @DisplayName("enum -> elements are stored apart from the type name")
        void enumSpelling_storesElements() {
            Column column = columnOf("ENUM('1','2','a','A')");

            assertThat(column.getDataType()).isEqualTo("enum");
            assertThat(column.getEnumElements()).isEqualTo("'1','2','a','A'");
            assertThat(column.getJdbcIDOfDataType()).isEqualTo(61111);
        }

        @Test
        @DisplayName("list_of(int) -> IllegalArgumentException")
        void listOfSpelling_throwsIllegalArgumentException() {
            Column column = createColumn("list_of(int)");

            // DEFECT: "list_of" is a registered synonym of the sequence type, but isCollection()
            // does not list it, so the element type is never parsed and the parse fails - see
            // CUBRIDDataTypeHelper.isCollection()
            assertThatThrownBy(() -> HELPER.setColumnDataType("list_of(int)", column))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid data type:list_of(int)");
        }

        @Test
        @DisplayName("unsupported type -> UnSupportCUBRIDDataTypeException")
        void unsupportedSpelling_throwsUnSupportCUBRIDDataTypeException() {
            Column column = createColumn("unknowntype");

            assertThatThrownBy(() -> HELPER.setColumnDataType("unknowntype", column))
                    .isInstanceOf(UnSupportCUBRIDDataTypeException.class)
                    .hasMessage("Unsupported CUBRID data type:unknowntype");
        }
    }
}
