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
package com.cubrid.cubridmigration.core.datatype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("DBDataTypeHelper")
class DBDataTypeHelperTest {

    private static final DBDataTypeHelper HELPER = new BaseHelper();

    private static final DBDataTypeHelper COLLECTION_HELPER = new CollectionAwareHelper();

    @Nested
    @DisplayName("getMainDataType()")
    class GetMainDataType {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @CsvSource({
            "int,                 int",
            "varchar(200),        varchar",
            "'numeric(10,2)',     numeric",
            "set_of(bit),         set_of",
            "VARCHAR(10),         VARCHAR",
            "varchar(200,         varchar",
        })
        void dataTypeWithArguments_returnsNameBeforeParenthesis(String dataType, String expected) {
            assertThat(HELPER.getMainDataType(dataType.trim())).isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("null -> empty string")
        void nullDataType_returnsEmptyString() {
            assertThat(HELPER.getMainDataType(null)).isEmpty();
        }

        @Test
        @DisplayName("empty string -> empty string")
        void emptyDataType_returnsEmptyString() {
            assertThat(HELPER.getMainDataType("")).isEmpty();
        }

        @Test
        @DisplayName("blank string -> empty string")
        void blankDataType_returnsEmptyString() {
            assertThat(HELPER.getMainDataType("   ")).isEmpty();
        }

        @Test
        @DisplayName("surrounding whitespace -> trimmed name")
        void surroundingWhitespace_returnsTrimmedName() {
            assertThat(HELPER.getMainDataType("  varchar (200)")).isEqualTo("varchar");
        }

        @Test
        @DisplayName("leading parenthesis -> empty string")
        void leadingParenthesis_returnsEmptyString() {
            assertThat(HELPER.getMainDataType("(200)")).isEmpty();
        }
    }

    @Nested
    @DisplayName("isChar()")
    class IsChar {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "char,            true",
                    "char(1),         true",
                    "CHAR,            true",
                    "character,       true",
                    "character(1),    true",
                    "CHARACTER,       true",
                    "varchar,         false",
                    "char varying,    false",
                    "nchar,           false",
                    "ncharacter,      false",
                    "null,            false",
                    "'',              false",
                })
        void variousDataTypes_classifyCharFamily(String dataType, boolean expected) {
            assertThat(HELPER.isChar(dataType)).isEqualTo(expected);
        }

        @Test
        @DisplayName("surrounding whitespace -> true")
        void surroundingWhitespace_returnsTrue() {
            assertThat(HELPER.isChar(" char ")).isTrue();
        }
    }

    @Nested
    @DisplayName("isVarchar()")
    class IsVarchar {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "varchar,               true",
                    "varchar(100),          true",
                    "varchar2,              true",
                    "VARCHAR2(4000),        true",
                    "string,                true",
                    "character varying,     true",
                    "char varying,          true",
                    "char,                  false",
                    "nvarchar,              false",
                    "nvarchar2,             false",
                    "null,                  false",
                    "'',                    false",
                })
        void variousDataTypes_classifyVarcharFamily(String dataType, boolean expected) {
            assertThat(HELPER.isVarchar(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isString()")
    class IsString {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "char,           true",
                    "CHAR(10),       true",
                    "varchar,        true",
                    "string,         true",
                    "nchar,          false",
                    "nvarchar,       false",
                    "blob,           false",
                    "int,            false",
                    "null,           false",
                    "'',             false",
                })
        void variousDataTypes_classifyCharAndVarcharOnly(String dataType, boolean expected) {
            assertThat(HELPER.isString(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isNChar()")
    class IsNChar {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "nchar,                  true",
                    "NCHAR(10),              true",
                    "national character,     true",
                    "ncharacter,             true",
                    "nvarchar,               false",
                    "char,                   false",
                    "null,                   false",
                })
        void variousDataTypes_classifyNcharFamily(String dataType, boolean expected) {
            assertThat(HELPER.isNChar(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isNVarchar()")
    class IsNVarchar {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "nvarchar,                        true",
                    "nvarchar2,                       true",
                    "nchar varying,                   true",
                    "national character varying,      true",
                    "nchar,                           false",
                    "varchar,                         false",
                    "null,                            false",
                })
        void variousDataTypes_classifyNvarcharFamily(String dataType, boolean expected) {
            assertThat(HELPER.isNVarchar(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isNString()")
    class IsNString {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "nchar,           true",
                    "nvarchar(10),    true",
                    "char,            false",
                    "varchar,         false",
                    "null,            false",
                })
        void variousDataTypes_classifyNationalStrings(String dataType, boolean expected) {
            assertThat(HELPER.isNString(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isGenericString()")
    class IsGenericString {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "char,           true",
                    "varchar,        true",
                    "nchar,          true",
                    "nvarchar2,      true",
                    "int,            false",
                    "blob,           false",
                    "null,           false",
                })
        void variousDataTypes_classifyAllStringFamilies(String dataType, boolean expected) {
            assertThat(HELPER.isGenericString(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isNumeric()")
    class IsNumeric {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "numeric,                    true",
            "'numeric(38,2)',            true",
            "NUMERIC(38),                true",
            "number,                     true",
            "dec,                        true",
            "'DEC(10,2)',                true",
            "decimal,                    true",
            "decimal unsigned,           true",
            "DECIMAL UNSIGNED,           true",
            "'decimal(10,2) unsigned',   true",
            "int,                        false",
            "bigint,                     false",
            "float,                      false",
            "double,                     false",
            "varchar,                    false",
            // "unsigned" at index 0 is not stripped, so the whole word is looked up.
            "unsigned,                   false",
        })
        void variousDataTypes_classifyExactNumericTypes(String dataType, boolean expected) {
            assertThat(HELPER.isNumeric(dataType.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("null -> IllegalArgumentException")
        void nullDataType_throwsIllegalArgumentException() {
            // Unlike isChar()/isVarchar(), blank input is rejected instead of returning false.
            assertThatThrownBy(() -> HELPER.isNumeric(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type should not be empty.");
        }

        @Test
        @DisplayName("empty string -> IllegalArgumentException")
        void emptyDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.isNumeric(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type should not be empty.");
        }

        @Test
        @DisplayName("blank string -> IllegalArgumentException")
        void blankDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.isNumeric("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type should not be empty.");
        }
    }

    @Nested
    @DisplayName("isGeneralizedNumeric()")
    class IsGeneralizedNumeric {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource({
            "numeric,               true",
            "'numeric(38,2)',       true",
            "number,                true",
            "int,                   true",
            "integer,               true",
            "bigint,                true",
            "float,                 true",
            "double,                true",
            "real,                  true",
            "monetary,              true",
            "money,                 true",
            "currency,              true",
            "int unsigned,          true",
            "INT(11) UNSIGNED,      true",
            "unsigned,              false",
            "varchar,               false",
            "bit,                   false",
        })
        void variousDataTypes_classifyAllNumericFamilies(String dataType, boolean expected) {
            assertThat(HELPER.isGeneralizedNumeric(dataType.trim())).isEqualTo(expected);
        }

        @Test
        @DisplayName("null -> IllegalArgumentException")
        void nullDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.isGeneralizedNumeric(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type should not be empty.");
        }

        @Test
        @DisplayName("blank string -> IllegalArgumentException")
        void blankDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.isGeneralizedNumeric("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type should not be empty.");
        }
    }

    @Nested
    @DisplayName("isInteger()")
    class IsInteger {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "int,             true",
                    "integer,         true",
                    "short,           true",
                    "smallint,        true",
                    "mediumint,       true",
                    "tinyint,         true",
                    "INT(11),         true",
                    // bigint is not in the integer model list, only in the numeric ones.
                    "bigint,          false",
                    // The "unsigned" suffix is NOT stripped here, unlike in isNumeric() and
                    // isGeneralizedNumeric(), so a MySQL style type is not recognized.
                    "int unsigned,    false",
                    "numeric,         false",
                    "varchar,         false",
                    "null,            false",
                    "'',              false",
                })
        void variousDataTypes_classifyIntegerFamily(String dataType, boolean expected) {
            assertThat(HELPER.isInteger(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isEnum()")
    class IsEnum {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "enum,            true",
                    "ENUM,            true",
                    "'enum(a,b)',     true",
                    "enumeration,     false",
                    "set,             false",
                    "null,            false",
                    "'',              false",
                })
        void variousDataTypes_classifyEnumType(String dataType, boolean expected) {
            assertThat(HELPER.isEnum(dataType)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isSupportAutoIncr()")
    class IsSupportAutoIncr {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @CsvSource(
                nullValues = "null",
                value = {
                    "int,               true",
                    "integer,           true",
                    "short,             true",
                    "smallint,          true",
                    "mediumint,         true",
                    "tinyint,           true",
                    "bigint,            true",
                    "INT(11),           true",
                    "numeric,           false",
                    "'numeric(38,0)',   false",
                    "float,             false",
                    "varchar,           false",
                    "int unsigned,      false",
                    "null,              false",
                    "'',                false",
                })
        void variousDataTypes_classifyAutoIncrementCapableTypes(String dataType, boolean expected) {
            assertThat(HELPER.isSupportAutoIncr(dataType, "", null)).isEqualTo(expected);
        }

        @Test
        @DisplayName("default value and scale are ignored")
        void defaultValueAndScale_areIgnored() {
            assertThat(HELPER.isSupportAutoIncr("int", "7", 5)).isTrue();
        }
    }

    @Nested
    @DisplayName("isValidDatatype()")
    class IsValidDatatype {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @CsvSource(
                nullValues = "null",
                value = {
                    "varchar(10)",
                    "!!garbage!!",
                    "null",
                })
        void anyDataType_returnsTrue(String dataTypeInstance) {
            // The base class validates nothing, the dialect helpers override this.
            assertThat(HELPER.isValidDatatype(dataTypeInstance)).isTrue();
        }
    }

    @Nested
    @DisplayName("isValidValue()")
    class IsValidValue {

        @ParameterizedTest(name = "[{index}] (\"{0}\", \"{1}\") -> true")
        @CsvSource(
                nullValues = "null",
                value = {
                    "int,        123",
                    "int,        abc",
                    "varchar(1), too long",
                    "null,       null",
                })
        void anyDataTypeAndValue_returnsTrue(String dataType, String value) {
            // The base class validates nothing, the dialect helpers override this.
            assertThat(HELPER.isValidValue(dataType, value)).isTrue();
        }
    }

    @Nested
    @DisplayName("parseDTInstance()")
    class ParseDTInstance {

        @Test
        @DisplayName("\"varchar\" -> name only")
        void nameOnly_returnsNameWithoutPrecision() {
            DataTypeInstance dti = HELPER.parseDTInstance("varchar");

            assertThat(dti.getName()).isEqualTo("varchar");
            assertThat(dti.getPrecision()).isNull();
            assertThat(dti.getScale()).isNull();
        }

        @Test
        @DisplayName("surrounding whitespace -> trimmed name")
        void surroundingWhitespace_returnsTrimmedName() {
            assertThat(HELPER.parseDTInstance("  varchar  ").getName()).isEqualTo("varchar");
        }

        @Test
        @DisplayName("\"varchar(2000)\" -> precision only")
        void precisionOnly_returnsPrecision() {
            DataTypeInstance dti = HELPER.parseDTInstance("varchar(2000)");

            assertThat(dti.getName()).isEqualTo("varchar");
            assertThat(dti.getPrecision()).isEqualTo(2000);
            assertThat(dti.getScale()).isNull();
        }

        @Test
        @DisplayName("\"varchar( 2000 )\" -> whitespace inside parenthesis is allowed")
        void precisionWithWhitespace_returnsPrecision() {
            assertThat(HELPER.parseDTInstance("varchar( 2000 )").getPrecision()).isEqualTo(2000);
        }

        @Test
        @DisplayName("\"varchar(0)\" -> precision 0")
        void zeroPrecision_returnsZero() {
            assertThat(HELPER.parseDTInstance("varchar(0)").getPrecision()).isZero();
        }

        @Test
        @DisplayName("\"numeric(38,2)\" -> precision and scale")
        void precisionAndScale_returnsBoth() {
            DataTypeInstance dti = HELPER.parseDTInstance("numeric(38,2)");

            assertThat(dti.getName()).isEqualTo("numeric");
            assertThat(dti.getPrecision()).isEqualTo(38);
            assertThat(dti.getScale()).isEqualTo(2);
        }

        @Test
        @DisplayName("\"numeric(38 , 2)\" -> whitespace around the comma is allowed")
        void precisionAndScaleWithWhitespace_returnsBoth() {
            DataTypeInstance dti = HELPER.parseDTInstance("numeric(38 , 2)");

            assertThat(dti.getPrecision()).isEqualTo(38);
            assertThat(dti.getScale()).isEqualTo(2);
        }

        @Test
        @DisplayName("\"enum('a','b')\" -> elements, case of the name kept")
        void enumType_returnsElements() {
            DataTypeInstance dti = HELPER.parseDTInstance("ENUM( 'a','b' )");

            assertThat(dti.getName()).isEqualTo("ENUM");
            assertThat(dti.getElments()).isEqualTo("'a','b'");
            assertThat(dti.getPrecision()).isNull();
        }

        @Test
        @DisplayName("\"set(int)\" -> sub type when the dialect knows collections")
        void collectionType_returnsSubType() {
            DataTypeInstance dti = COLLECTION_HELPER.parseDTInstance("set(int)");

            assertThat(dti.getName()).isEqualTo("set");
            assertThat(dti.getSubType().getName()).isEqualTo("int");
            assertThat(dti.getSubType().getPrecision()).isNull();
        }

        @Test
        @DisplayName("\"set(varchar(200))\" -> sub type with precision")
        void collectionTypeWithPrecision_returnsSubTypeWithPrecision() {
            DataTypeInstance dti = COLLECTION_HELPER.parseDTInstance("set(varchar(200))");

            assertThat(dti.getName()).isEqualTo("set");
            assertThat(dti.getSubType().getName()).isEqualTo("varchar");
            assertThat(dti.getSubType().getPrecision()).isEqualTo(200);
        }

        @Test
        @DisplayName("null -> IllegalArgumentException")
        void nullDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.parseDTInstance(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type can't be empty.");
        }

        @Test
        @DisplayName("empty string -> IllegalArgumentException")
        void emptyDataType_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.parseDTInstance(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Data type can't be empty.");
        }

        @Test
        @DisplayName("blank string -> instance with an empty name")
        void blankDataType_returnsInstanceWithEmptyName() {
            // DEFECT: a blank data type passes the isEmpty() guard, so a nameless instance is
            // returned instead of being rejected - see DBDataTypeHelper.java:320
            assertThat(HELPER.parseDTInstance("   ").getName()).isEmpty();
        }

        @Test
        @DisplayName("\"numeric(38.2)\" -> IllegalArgumentException")
        void unparsableArguments_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.parseDTInstance("numeric(38.2)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid data type:numeric(38.2)");
        }

        @Test
        @DisplayName("\"numeric(-1)\" -> IllegalArgumentException")
        void negativePrecision_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> HELPER.parseDTInstance("numeric(-1)"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid data type:numeric(-1)");
        }

        @Test
        @DisplayName("\"varchar()\" -> whole string becomes the name")
        void emptyArguments_returnsWholeStringAsName() {
            // DEFECT: empty parentheses do not match the pattern, so the parentheses end up in
            // the type name instead of raising "Invalid data type"
            // - see DBDataTypeHelper.java:328
            assertThat(HELPER.parseDTInstance("varchar()").getName()).isEqualTo("varchar()");
        }

        @Test
        @DisplayName("\"varchar(2000) x\" -> whole string becomes the name")
        void trailingGarbage_returnsWholeStringAsName() {
            // DEFECT: text after the closing parenthesis does not match the pattern, so the
            // garbage ends up in the type name instead of raising "Invalid data type"
            // - see DBDataTypeHelper.java:328
            DataTypeInstance dti = HELPER.parseDTInstance("varchar(2000) x");

            assertThat(dti.getName()).isEqualTo("varchar(2000) x");
            assertThat(dti.getPrecision()).isNull();
        }
    }

    /**
     * Minimal concrete subclass of the abstract helper under test. Only {@code isCollection()} is
     * consulted by the base class itself, from {@code parseDTInstance()}; the other abstract
     * methods are never reached by the inherited behaviour.
     */
    private static class BaseHelper extends DBDataTypeHelper {

        @Override
        public DatabaseType getDBType() {
            return null;
        }

        @Override
        public Integer getJdbcDataTypeID(
                Catalog catalog, String dataType, Integer precision, Integer scale) {
            return 0;
        }

        @Override
        public String getShownDataType(Column column) {
            return column.getShownDataType();
        }

        @Override
        public boolean isBinary(String dataType) {
            return false;
        }

        @Override
        public boolean isCollection(String dataType) {
            return false;
        }
    }

    /** Variant whose dialect knows collection types, to exercise the sub type parsing branch. */
    private static class CollectionAwareHelper extends BaseHelper {

        @Override
        public boolean isCollection(String dataType) {
            return checkType("/set/multiset/sequence/", dataType);
        }
    }
}
