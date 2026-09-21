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
package com.cubrid.cubridmigration.cubrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.MigrationDirAndFilesManager;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.cubrid.exception.FormatCUBRIDDataTypeException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

@DisplayName("Data2StrTranslator")
@ResourceLock(Resources.TIME_ZONE)
class Data2StrTranslatorTest {

    private static final CUBRIDDataTypeHelper TYPES = CUBRIDDataTypeHelper.getInstance(null);

    // The temporal formatters render through the default zone, so the zone is part of the input.
    private static final Data2StrTranslator TRANSLATOR = unloadTranslator();

    private static TimeZone defaultZone;

    private static Data2StrTranslator unloadTranslator() {
        // config is a constructor parameter the body never reads; only the DEST_* constants are.
        MigrationConfiguration config = new MigrationConfiguration();
        return new Data2StrTranslator(
                "",
                config,
                new MigrationDirAndFilesManager(config),
                MigrationConfiguration.DEST_DB_UNLOAD);
    }

    @BeforeAll
    static void pinTimeZone() {
        defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(defaultZone);
    }

    private static Column columnOf(String dataType, String name) {
        Column column = new Column();
        Table table = new Table();
        table.setName("test");
        column.setTableOrView(table);
        column.setName(name);
        column.setDataType(dataType);
        column.setJdbcIDOfDataType(TYPES.getCUBRIDDataTypeID(dataType));
        column.setScale(0);
        return column;
    }

    private static String rendered(String dataType, Object value) {
        return TRANSLATOR.stringValueOf(value, columnOf(dataType, "c"), new ArrayList<>());
    }

    @Nested
    @DisplayName("scalar values")
    class ScalarValues {

        @ParameterizedTest(name = "[{index}] {0} \"2\" -> 2")
        @DisplayName("every whole-number type renders the digits and nothing else")
        @ValueSource(strings = {"monetary", "integer", "smallint", "BIGINT", "FLOAT"})
        void wholeNumber_rendersDigits(String dataType) {
            assertThat(rendered(dataType, "2")).isEqualTo("2");
        }

        @ParameterizedTest(name = "[{index}] NUMERIC(s={0}) \"{1}\" -> {2}")
        @DisplayName("a numeric always carries its decimal point, whatever the scale says")
        @CsvSource({
            // The scale does not pad: the point is written, the fraction is whatever was given.
            "0,  2,     2.",
            "1,  2,     2.",
            "3,  2,     2.",
            "1,  23.3,  23.3",
        })
        void numeric_keepsItsDecimalPoint(int scale, String value, String expected) {
            Column column = columnOf("NUMERIC", "c");
            column.setScale(scale);

            assertThat(TRANSLATOR.stringValueOf(value, column, new ArrayList<>()))
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] DOUBLE \"{0}\" -> {1}")
        @DisplayName("an infinity becomes the largest double CUBRID can hold")
        @CsvSource({
            "-Infinity,  -1.7976931348623157e+308",
            "Infinity,   1.7976931348623157e+308",
        })
        void infinity_rendersAsTheDoubleLimit(String value, String expected) {
            assertThat(rendered("DOUBLE", value)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a double inside the range keeps the text it was given")
        void double_insideRange_isPassedThrough() {
            assertThat(rendered("DOUBLE", "23.3")).isEqualTo("23.3");
        }

        @ParameterizedTest(name = "[{index}] {0} -> X'616263'")
        @DisplayName("bytes are rendered as a hexadecimal literal, never as text")
        @ValueSource(strings = {"BIT", "BIT VARYING"})
        void bytes_renderAsHexLiteral(String dataType) {
            assertThat(rendered(dataType, "abc".getBytes())).isEqualTo("X'616263'");
        }

        @ParameterizedTest(name = "[{index}] {0} -> 'NULL'")
        @DisplayName("the four-letter string NULL is data, and is quoted like any other string")
        @ValueSource(strings = {"character", "NCHAR"})
        void theWordNull_isQuotedAsData(String dataType) {
            assertThat(rendered(dataType, "NULL")).isEqualTo("'NULL'");
        }

        @Test
        @DisplayName("a real null is the unquoted NULL keyword")
        void nullValue_rendersAsKeyword() {
            assertThat(rendered("character", null)).isEqualTo("NULL");
        }
    }

    @Nested
    @DisplayName("temporal values")
    class TemporalValues {

        @Test
        @DisplayName("each temporal type carries its own lower-case keyword and its own shape")
        void temporalValue_rendersItsOwnLiteral() {
            Timestamp stamp = Timestamp.valueOf("2010-01-20 10:09:08");

            assertThat(rendered("TIME", Time.valueOf("10:09:08"))).isEqualTo("time'10:09:08'");
            assertThat(rendered("DATE", Date.valueOf("2010-01-20"))).isEqualTo("date'01/20/2010'");
            assertThat(rendered("TIMESTAMP", stamp)).isEqualTo("timestamp'10:09:08 AM 01/20/2010'");
            assertThat(rendered("DATETIME", stamp)).isEqualTo("datetime'2010-01-20 10:09:08.000'");
        }
    }

    @Nested
    @DisplayName("collection values")
    class CollectionValues {

        private Column collectionOf(String dataType, String elementType) {
            Column column = columnOf(dataType, "c");
            column.setSubDataType(elementType);
            column.setJdbcIDOfSubDataType(TYPES.getCUBRIDDataTypeID(elementType));
            return column;
        }

        @Test
        @DisplayName("a collection is braced whether it arrives as a List or as an array")
        void collection_isBracedFromEitherShape() {
            Column column = collectionOf("SET", "INTEGER");

            assertThat(
                            TRANSLATOR.stringValueOf(
                                    new ArrayList<>(Arrays.asList("1", "2")),
                                    column,
                                    new ArrayList<>()))
                    .isEqualTo("{1,2}");
            assertThat(TRANSLATOR.stringValueOf(new Object[] {"1", "2"}, column, new ArrayList<>()))
                    .isEqualTo("{1,2}");
        }

        @Test
        @DisplayName("an empty collection column is the NULL keyword, not empty braces")
        void nullCollection_rendersAsKeyword() {
            assertThat(rendered("MULTISET", null)).isEqualTo("NULL");
        }

        @Test
        @DisplayName("an element the type cannot hold names both the value and the type")
        void unconvertibleElement_throwsNamingBoth() {
            Column column = collectionOf("SEQUENCE", "INTEGER");

            assertThatThrownBy(() -> TRANSLATOR.stringValueOf("ab", column, new ArrayList<>()))
                    .isInstanceOf(FormatCUBRIDDataTypeException.class)
                    .hasMessage("Can not format data \"ab\" to data type \"SEQUENCE\"");
        }
    }

    @Nested
    @DisplayName("the loaddb record line")
    class RecordLine {

        @Test
        @DisplayName("a record is its column values in order, separated by single spaces")
        void getRecordString_joinsTheRenderedColumns() {
            List<ColumnValue> columns = new ArrayList<>();
            List<String> values = new ArrayList<>();
            for (String[] pair :
                    new String[][] {
                        {"integer", "a1", "1"},
                        {"character", "a2", "NULL"},
                        {"character", "a3", "1aa"},
                        {"bit varying", "a4", "B'1aa'"},
                    }) {
                columns.add(new ColumnValue(columnOf(pair[0], pair[1]), ""));
                values.add(pair[2]);
            }

            // The legacy test built this fixture and then only asserted the result was non-null.
            assertThat(TRANSLATOR.getRecordString(columns, values)).isEqualTo("1 NULL 1aa B'1aa'");
        }
    }

    @Nested
    @DisplayName("quoting helpers")
    class Quoting {

        @Test
        @DisplayName("quoteString wraps the value and doubles the quotes inside it")
        void quoteString_doublesEmbeddedQuotes() {
            assertThat(Data2StrTranslator.quoteString("ab'c")).isEqualTo("'ab''c'");
            assertThat(Data2StrTranslator.quoteString("abc")).isEqualTo("'abc'");
        }

        @Test
        @DisplayName("quoteAndSeparateString quotes a plain value the same way")
        void quoteAndSeparateString_quotesThePlainValue() {
            assertThat(Data2StrTranslator.quoteAndSeparateString("ab")).isEqualTo("'ab'");
        }
    }
}
