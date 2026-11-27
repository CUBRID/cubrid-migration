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
package com.cubrid.cubridmigration.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.stream.Stream;

@DisplayName("CSVReader")
class CSVReaderTest {

    private static List<String[]> readAll(String text) throws IOException {
        return new CSVReader(new StringReader(text)).readAll();
    }

    @Nested
    @DisplayName("getNextLine()")
    class GetNextLine {

        @Test
        @DisplayName("each line becomes a row")
        void eachLine_becomesARow() throws Exception {
            assertThat(readAll("aaa\r\nbbb"))
                    .containsExactly(new String[] {"aaa"}, new String[] {"bbb"});
            assertThat(readAll("aaa")).containsExactly(new String[] {"aaa"});
        }

        @Test
        @DisplayName("the lines to skip are dropped once, before the first row")
        void skipLines_areDroppedOnce() throws Exception {
            CSVReader reader =
                    new CSVReader(
                            new StringReader("aaa,\"bbb\",ccc\r\nddd,\"eee\",fff\r\nggg,hhh,iii"),
                            ',',
                            '"',
                            1);

            assertThat(reader.readAll())
                    .containsExactly(
                            new String[] {"ddd", "eee", "fff"}, new String[] {"ggg", "hhh", "iii"});
        }

        @Test
        @DisplayName("reading row by row skips only before the first row")
        void rowByRow_skipsOnlyOnce() throws Exception {
            CSVReader reader = new CSVReader(new StringReader("h\na\nb"), ',', '"', 1);

            assertThat(reader.readNext()).containsExactly("a");
            assertThat(reader.readNext()).containsExactly("b");
            assertThat(reader.readNext()).isNull();
        }

        @Test
        @DisplayName("skipping past the last line reads nothing")
        void skipPastTheEnd_readsNothing() throws Exception {
            assertThat(new CSVReader(new StringReader("a\nb"), ',', '"', 5).readAll()).isEmpty();
        }

        @Test
        @DisplayName("after the last line every read gives null")
        void afterTheLastLine_returnsNull() throws Exception {
            CSVReader reader = new CSVReader(new StringReader("a"));
            reader.readNext();

            assertThat(reader.readNext()).isNull();
            assertThat(reader.readNext()).isNull();
        }
    }

    @Nested
    @DisplayName("parseLine()")
    class ParseLine {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("separators outside quotes split the line, spaces and empty fields kept")
        @MethodSource("com.cubrid.cubridmigration.core.io.CSVReaderTest#splitLines")
        void separators_splitTheLine(String line, String[] expected) throws Exception {
            assertThat(readAll(line)).containsExactly(expected);
        }

        @Test
        @DisplayName("an empty line is one empty field")
        void emptyLine_isOneEmptyField() throws Exception {
            assertThat(readAll("a\n\nb"))
                    .containsExactly(new String[] {"a"}, new String[] {""}, new String[] {"b"});
        }

        @Test
        @DisplayName("a quoted field keeps its separators, and two quotes in it stand for one")
        void quotedField_keepsSeparatorsAndDoubledQuotes() throws Exception {
            assertThat(readAll("a,\"b,c\",d")).containsExactly(new String[] {"a", "b,c", "d"});
            assertThat(readAll("\"a\"\"b\",c")).containsExactly(new String[] {"a\"b", "c"});
        }

        @Test
        @DisplayName("a quoted field goes on over line breaks")
        void quotedField_continuesOnTheNextLine() throws Exception {
            assertThat(readAll("\"a\nb\",c\nd"))
                    .containsExactly(new String[] {"a\nb", "c"}, new String[] {"d"});
        }

        @Test
        @DisplayName("only an unquoted NULL in capitals reads as null")
        void unquotedNull_readsAsNull() throws Exception {
            assertThat(readAll("NULL,\"NULL\",null,x\nx,NULL"))
                    .containsExactly(
                            new String[] {null, "NULL", "null", "x"}, new String[] {"x", null});
        }

        @Test
        @DisplayName("another separator can be given")
        void otherSeparator_isUsed() throws Exception {
            assertThat(new CSVReader(new StringReader("a;b,c"), ';').readAll())
                    .containsExactly(new String[] {"a", "b,c"});
        }

        @Test
        @DisplayName("an unclosed quote takes the rest of the input and a line break")
        void unclosedQuote_takesTheRest() throws Exception {
            assertThat(readAll("\"abc,d")).containsExactly(new String[] {"abc,d\n"});
            assertThat(readAll("\"abc,d\ne\"f,g")).containsExactly(new String[] {"abc,d\nef", "g"});
        }

        @Test
        @DisplayName(
                "a quote inside a field is kept or dropped by where the field sits in the line")
        void quoteInsideField_dependsOnLinePosition() throws Exception {
            // DEFECT: the i > 2 check that keeps a quote inside a field counts from the start of
            // the line, not of the field, so the same field loses its first quote at the start
            // of a line and keeps it further on
            // - see CSVReader.parseLine()
            assertThat(readAll("ab\"c\"d,f")).containsExactly(new String[] {"abc\"d", "f"});
            assertThat(readAll("x,ab\"c\"d")).containsExactly(new String[] {"x", "ab\"c\"d"});
        }

        @Test
        @DisplayName("a line starting with a separator cannot be read")
        void leadingSeparator_throwsStringIndexOutOfBoundsException() {
            // DEFECT: the separator branch looks at charAt(i - 1) for a closing quote, which is
            // charAt(-1) when the line starts with the separator, so an empty first field fails
            // - see CSVReader.parseLine()
            assertThatThrownBy(() -> readAll(",b,c"))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }
    }

    static Stream<Arguments> splitLines() {
        return Stream.of(
                Arguments.of("a,b,c", new String[] {"a", "b", "c"}),
                Arguments.of(" a , b ", new String[] {" a ", " b "}),
                Arguments.of("a,,c", new String[] {"a", "", "c"}),
                Arguments.of("a,", new String[] {"a", ""}));
    }
}
