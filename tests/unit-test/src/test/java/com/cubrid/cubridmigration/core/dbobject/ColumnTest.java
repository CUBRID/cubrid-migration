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
package com.cubrid.cubridmigration.core.dbobject;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Column")
class ColumnTest {

    private static DataTypeInstance instance(
            String name, Integer precision, Integer scale, String elements) {
        DataTypeInstance instance = new DataTypeInstance();
        instance.setName(name);
        instance.setPrecision(precision);
        instance.setScale(scale);
        instance.setElments(elements);
        return instance;
    }

    @Nested
    @DisplayName("cloneCol()")
    class CloneCol {

        @Test
        @DisplayName("the name, type, size, default and constraints are copied")
        void coreFields_areCopied() {
            Column column = new Column();
            column.setName("id");
            column.setShared(true);
            column.setSharedValue("sv");
            column.setDataType("integer");
            column.setShownDataType("integer(10)");
            column.setJdbcIDOfDataType(4);
            column.setSubDataType("sub");
            column.setJdbcIDOfSubDataType(5);
            column.setPrecision(10);
            column.setScale(2);
            column.setDefaultValue("0");
            column.setAutoIncrement(true);
            column.setAutoIncSeedVal(100);
            column.setByteLength(8);
            column.setCharLength(9);
            column.setNullable(false);
            column.setUnique(true);

            Column clone = column.cloneCol();

            assertThat(clone).isNotSameAs(column);
            assertThat(clone.getName()).isEqualTo("id");
            assertThat(clone.isShared()).isTrue();
            assertThat(clone.getSharedValue()).isEqualTo("sv");
            assertThat(clone.getDataType()).isEqualTo("integer");
            assertThat(clone.getShownDataType()).isEqualTo("integer(10)");
            assertThat(clone.getJdbcIDOfDataType()).isEqualTo(4);
            assertThat(clone.getSubDataType()).isEqualTo("sub");
            assertThat(clone.getJdbcIDOfSubDataType()).isEqualTo(5);
            assertThat(clone.getPrecision()).isEqualTo(10);
            assertThat(clone.getScale()).isEqualTo(2);
            assertThat(clone.getDefaultValue()).isEqualTo("0");
            assertThat(clone.isAutoIncrement()).isTrue();
            assertThat(clone.getAutoIncSeedVal()).isEqualTo(100);
            assertThat(clone.getByteLength()).isEqualTo(8);
            assertThat(clone.getCharLength()).isEqualTo(9);
            assertThat(clone.isNullable()).isFalse();
            assertThat(clone.isUnique()).isTrue();
        }

        @Test
        @DisplayName(
                "the expression flag, elements, charset, char use, comment and table are left out")
        void otherFields_areLeftOut() {
            Column column = new Column();
            column.setDefaultIsExpression(true);
            column.setEnumElements("'a'");
            column.setCharset("utf8");
            column.setCharUsed("C");
            column.setComment("note");
            column.setTableOrView(new Table());

            Column clone = column.cloneCol();

            assertThat(clone.isDefaultIsExpression()).isFalse();
            assertThat(clone.getEnumElements()).isNull();
            assertThat(clone.getCharset()).isNull();
            assertThat(clone.getCharUsed()).isNull();
            assertThat(clone.getComment()).isNull();
            assertThat(clone.getTableOrView()).isNull();
        }

        @Test
        @DisplayName("an unset precision and scale become 0 in the clone")
        void unsetPrecisionAndScale_becomeZero() {
            Column clone = new Column().cloneCol();

            assertThat(clone.getDataTypeInstance().getPrecision()).isZero();
            assertThat(clone.getDataTypeInstance().getScale()).isZero();
        }

        @Test
        @DisplayName("the auto increment step goes back to 1")
        void autoIncrementStep_goesBackToOne() {
            // DEFECT: only the seed is copied, so the step falls back to 1 and a source column
            // declared AUTO_INCREMENT(100, 5) turns into AUTO_INCREMENT(100, 1)
            // - see Column.cloneCol()
            Column column = new Column();
            column.setAutoIncrement(true);
            column.setAutoIncSeedVal(100);
            column.setAutoIncIncrVal(5);

            Column clone = column.cloneCol();

            assertThat(clone.getAutoIncSeedVal()).isEqualTo(100);
            assertThat(clone.getAutoIncIncrVal()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getPrecision()")
    class GetPrecision {

        @Test
        @DisplayName("a set precision comes back")
        void setPrecision_comesBack() {
            Column column = new Column();
            column.setPrecision(10);

            assertThat(column.getPrecision()).isEqualTo(10);
        }

        @Test
        @DisplayName("an unset precision reads as 0, never null")
        void unsetPrecision_readsAsZero() {
            // DEFECT: null is turned into 0, so the null checks callers make on it, as in
            // OracleSchemaFetcher and DBTransformHelper, can never pass and an unset
            // precision cannot be told from 0
            // - see Column.getPrecision()
            assertThat(new Column().getPrecision()).isZero();
        }
    }

    @Nested
    @DisplayName("getScale()")
    class GetScale {

        @Test
        @DisplayName("a set scale comes back")
        void setScale_comesBack() {
            Column column = new Column();
            column.setScale(2);

            assertThat(column.getScale()).isEqualTo(2);
        }

        @Test
        @DisplayName("an unset scale reads as 0, never null")
        void unsetScale_readsAsZero() {
            // DEFECT: null is turned into 0, so callers' getScale() != null checks, as in
            // AbstractJDBCSchemaFetcher and MySQLXMLSchemaParser, always pass and an unset
            // scale cannot be told from 0
            // - see Column.getScale()
            assertThat(new Column().getScale()).isZero();
        }
    }

    @Nested
    @DisplayName("setDefaultValue()")
    class SetDefaultValue {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("NULL in any case, or null, is stored as null")
        @ValueSource(strings = {"NULL", "null", "Null"})
        @NullSource
        void nullWord_isStoredAsNull(String value) {
            Column column = new Column();
            column.setDefaultValue("0");

            column.setDefaultValue(value);

            assertThat(column.getDefaultValue()).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("any other value is stored as it is, a quoted or padded NULL included")
        @ValueSource(strings = {"'NULL'", " NULL", "0", ""})
        void otherValue_isStoredAsItIs(String value) {
            Column column = new Column();

            column.setDefaultValue(value);

            assertThat(column.getDefaultValue()).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("getDefaultValueDisplayString()")
    class GetDefaultValueDisplayString {

        @Test
        @DisplayName(
                "no default reads as the word NULL, which setDefaultValue() turns back to null")
        void noDefault_readsAsNullWord() {
            Column column = new Column();

            assertThat(column.getDefaultValueDisplayString()).isEqualTo("NULL");

            column.setDefaultValue(column.getDefaultValueDisplayString());

            assertThat(column.getDefaultValue()).isNull();
        }

        @Test
        @DisplayName("a default reads as itself")
        void default_readsAsItself() {
            Column column = new Column();
            column.setDefaultValue("0");

            assertThat(column.getDefaultValueDisplayString()).isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("setShownDataType()")
    class SetShownDataType {

        @Test
        @DisplayName("null is stored as an empty string, like the starting value")
        void nullShownType_isStoredAsEmpty() {
            Column column = new Column();
            assertThat(column.getShownDataType()).isEmpty();
            column.setShownDataType("int");

            column.setShownDataType(null);

            assertThat(column.getShownDataType()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setDataTypeInstance()")
    class SetDataTypeInstance {

        @Test
        @DisplayName("a plain type sets the name, size and elements and clears the sub type")
        void plainType_clearsTheSubType() {
            Column column = new Column();
            column.setJdbcIDOfDataType(99);
            column.setSubDataType("old");

            column.setDataTypeInstance(instance("numeric", 10, 2, null));

            assertThat(column.getDataType()).isEqualTo("numeric");
            assertThat(column.getShownDataType()).isEqualTo("numeric(10,2)");
            assertThat(column.getPrecision()).isEqualTo(10);
            assertThat(column.getScale()).isEqualTo(2);
            assertThat(column.getSubDataType()).isNull();
            assertThat(column.getJdbcIDOfDataType()).isEqualTo(99);
        }

        @Test
        @DisplayName("a type with a sub type takes the size from the sub type")
        void typeWithSubType_takesTheSizeFromIt() {
            DataTypeInstance set = instance("set", 1, 1, null);
            set.setSubType(instance("varchar", 20, 3, null));
            Column column = new Column();

            column.setDataTypeInstance(set);

            assertThat(column.getDataType()).isEqualTo("set");
            assertThat(column.getSubDataType()).isEqualTo("varchar");
            assertThat(column.getPrecision()).isEqualTo(20);
            assertThat(column.getScale()).isEqualTo(3);
        }

        @Test
        @DisplayName("with a sub type the elements still come from the outer type")
        void typeWithSubType_takesTheOuterElements() {
            // DEFECT: the size is taken from the sub type but the elements from the outer one,
            // while getDataTypeInstance() puts them on the sub type, so a round trip loses them
            // - see Column.setDataTypeInstance()
            DataTypeInstance set = instance("set", null, null, "outer");
            set.setSubType(instance("varchar", 20, null, "inner"));
            Column column = new Column();
            column.setDataTypeInstance(set);
            assertThat(column.getEnumElements()).isEqualTo("outer");

            Column source = new Column();
            source.setDataType("enum");
            source.setSubDataType("varchar");
            source.setEnumElements("'a','b'");
            Column copy = new Column();
            copy.setDataTypeInstance(source.getDataTypeInstance());

            assertThat(copy.getEnumElements()).isNull();
        }
    }

    @Nested
    @DisplayName("getDataTypeInstance()")
    class GetDataTypeInstance {

        @Test
        @DisplayName("a plain type keeps the size and elements on the outer instance")
        void plainType_keepsTheSizeOutside() {
            Column column = new Column();
            column.setDataType("numeric");
            column.setPrecision(10);
            column.setScale(2);
            column.setEnumElements("e");

            DataTypeInstance instance = column.getDataTypeInstance();

            assertThat(instance.getName()).isEqualTo("numeric");
            assertThat(instance.getPrecision()).isEqualTo(10);
            assertThat(instance.getScale()).isEqualTo(2);
            assertThat(instance.getElments()).isEqualTo("e");
            assertThat(instance.getSubType()).isNull();
        }

        @Test
        @DisplayName("with a sub type the size and elements move to the sub instance")
        void typeWithSubType_movesTheSizeInside() {
            Column column = new Column();
            column.setDataType("set");
            column.setSubDataType("varchar");
            column.setPrecision(20);
            column.setScale(3);
            column.setEnumElements("e");

            DataTypeInstance instance = column.getDataTypeInstance();

            assertThat(instance.getPrecision()).isNull();
            assertThat(instance.getElments()).isNull();
            assertThat(instance.getSubType().getName()).isEqualTo("varchar");
            assertThat(instance.getSubType().getPrecision()).isEqualTo(20);
            assertThat(instance.getSubType().getScale()).isEqualTo(3);
            assertThat(instance.getSubType().getElments()).isEqualTo("e");
        }

        @Test
        @DisplayName("a blank sub type counts as none, and each call builds a new instance")
        void blankSubType_countsAsNone() {
            Column column = new Column();
            column.setDataType("int");
            column.setSubDataType("  ");
            column.setPrecision(5);

            DataTypeInstance instance = column.getDataTypeInstance();

            assertThat(instance.getSubType()).isNull();
            assertThat(instance.getPrecision()).isEqualTo(5);
            assertThat(column.getDataTypeInstance()).isNotSameAs(instance);
        }

        @Test
        @DisplayName("an unset size stays null here, unlike getPrecision()")
        void unsetSize_staysNull() {
            DataTypeInstance instance = new Column().getDataTypeInstance();

            assertThat(instance.getPrecision()).isNull();
            assertThat(instance.getScale()).isNull();
        }
    }
}
