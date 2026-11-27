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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@DisplayName("RmInvalidXMLCharReader")
class RmInvalidXMLCharReaderTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static RmInvalidXMLCharReader reader(String text) throws IOException {
        return new RmInvalidXMLCharReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "UTF-8");
    }

    private static RmInvalidXMLCharReader reader(String text, List<Character> invalidChars)
            throws IOException {
        return new RmInvalidXMLCharReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                "UTF-8",
                invalidChars);
    }

    private static String readOneByOne(RmInvalidXMLCharReader reader) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int c = reader.read(); c != -1; c = reader.read()) {
            text.append((char) c);
        }
        return text.toString();
    }

    private static String character(String hex) {
        return String.valueOf((char) Integer.parseInt(hex, 16));
    }

    @ParameterizedTest(name = "[{index}] U+{0}")
    @DisplayName("a character XML allows passes through as it is")
    @ValueSource(strings = {"0009", "000A", "000D", "0020", "0041", "AC00", "D7FE", "E000", "FFFC"})
    void read_passesAllowedCharactersThrough(String hex) throws Exception {
        assertThat(readOneByOne(reader(character(hex)))).isEqualTo(character(hex));
    }

    @ParameterizedTest(name = "[{index}] U+{0}")
    @DisplayName("a character XML does not allow becomes a space")
    @ValueSource(strings = {"0000", "0008", "001F", "FFFE", "FFFF"})
    void read_turnsDisallowedCharactersIntoSpaces(String hex) throws Exception {
        assertThat(readOneByOne(reader(character(hex)))).isEqualTo(" ");
    }

    @ParameterizedTest(name = "[{index}] U+{0}")
    @DisplayName("the last character of the U+0020 and U+E000 ranges becomes a space")
    @ValueSource(strings = {"D7FF", "FFFD"})
    void read_turnsTheLastCharacterOfARangeIntoASpace(String hex) throws Exception {
        // DEFECT: Arrays.fill() leaves out its end index, so the flag table stops one short of
        // each range and the allowed U+D7FF and U+FFFD read as spaces
        // - see RmInvalidXMLCharReader.isValid()
        assertThat(readOneByOne(reader(character(hex)))).isEqualTo(" ");
    }

    @Test
    @DisplayName("a character above U+FFFF becomes two spaces, one at a time or in a buffer")
    void read_turnsASupplementaryCharacterIntoTwoSpaces() throws Exception {
        // DEFECT: each UTF-16 half of a character above U+FFFF is checked on its own, and a half
        // is never allowed, so an emoji comes out as two spaces
        // - see RmInvalidXMLCharReader.read()
        char[] buffer = new char[2];

        assertThat(readOneByOne(reader(EMOJI))).isEqualTo("  ");
        assertThat(reader(EMOJI).read(buffer, 0, 2)).isEqualTo(2);
        assertThat(buffer).containsExactly(' ', ' ');
    }

    @Test
    @DisplayName("one character at a time ignores the list of invalid characters")
    void read_ignoresTheInvalidCharacterList() throws Exception {
        // DEFECT: the single-character read never looks at the list, so it turns an invalid
        // character into a space and records nothing, unlike the buffer read
        // - see RmInvalidXMLCharReader.read()
        List<Character> invalidChars = new ArrayList<>();

        assertThat(reader("\u0001", invalidChars).read()).isEqualTo(' ');
        assertThat(invalidChars).isEmpty();
    }

    @Test
    @DisplayName("one character at a time tells the reader event nothing")
    void read_firesNoReaderEventForOneCharacter() throws Exception {
        List<Integer> counts = new ArrayList<>();
        RmInvalidXMLCharReader reader = reader("a");
        reader.setReaderEvent(counts::add);

        reader.read();

        assertThat(counts).isEmpty();
    }

    @Test
    @DisplayName("without a list the buffer gets a space for each invalid character")
    void read_putsSpacesInTheBufferWithoutAList() throws Exception {
        char[] buffer = new char[3];

        assertThat(reader("a\u0001b").read(buffer, 0, 3)).isEqualTo(3);
        assertThat(buffer).containsExactly('a', ' ', 'b');
    }

    @Test
    @DisplayName("with a list invalid characters and U+FFFD are recorded and become U+FFFD")
    void read_recordsInvalidCharactersInTheList() throws Exception {
        List<Character> invalidChars = new ArrayList<>();
        char[] buffer = new char[5];

        assertThat(reader("a\u0001b\uFFFDc", invalidChars).read(buffer, 0, 5)).isEqualTo(5);
        assertThat(buffer).containsExactly('a', '\uFFFD', 'b', '\uFFFD', 'c');
        assertThat(invalidChars).containsExactly('\u0001', '\uFFFD');
    }

    @Test
    @DisplayName("the buffer is filled from the offset")
    void read_fillsTheBufferFromTheOffset() throws Exception {
        char[] buffer = {'x', 'x', 'x', 'x'};

        assertThat(reader("a\u0001").read(buffer, 1, 2)).isEqualTo(2);
        assertThat(buffer).containsExactly('x', 'a', ' ', 'x');
    }

    @Test
    @DisplayName("the reader event gets the count of each buffer read")
    void read_reportsTheCountToTheReaderEvent() throws Exception {
        List<Integer> counts = new ArrayList<>();
        RmInvalidXMLCharReader reader = reader("abc");
        reader.setReaderEvent(counts::add);
        char[] buffer = new char[8];

        assertThat(reader.read(buffer, 0, 8)).isEqualTo(3);
        assertThat(reader.read(buffer, 0, 8)).isEqualTo(-1);
        assertThat(counts).containsExactly(3);
    }

    @Test
    @DisplayName("the end of the stream gives -1, one at a time or in a buffer")
    void read_returnsMinusOneAtTheEnd() throws Exception {
        List<Integer> counts = new ArrayList<>();
        List<Character> invalidChars = new ArrayList<>();
        RmInvalidXMLCharReader reader = reader("", invalidChars);
        reader.setReaderEvent(counts::add);

        assertThat(reader("").read()).isEqualTo(-1);
        assertThat(reader.read(new char[2], 0, 2)).isEqualTo(-1);
        assertThat(invalidChars).isEmpty();
        assertThat(counts).isEmpty();
    }

    @Test
    @DisplayName("without a list the buffer past the count read is rewritten too")
    void read_rewritesTheBufferPastTheCount() throws Exception {
        // DEFECT: the loop runs to the length asked for instead of the count read, so the chars
        // past the count, never filled or left from before, are checked and rewritten as well
        // - see RmInvalidXMLCharReader.read()
        char[] buffer = new char[6];
        buffer[4] = 'Z';

        assertThat(reader("ab").read(buffer, 0, 6)).isEqualTo(2);
        assertThat(buffer).containsExactly('a', 'b', ' ', ' ', 'Z', ' ');
    }

    @Test
    @DisplayName("with a list the chars past the count read are recorded as invalid")
    void read_recordsCharsPastTheCountAsInvalid() throws Exception {
        // DEFECT: the loop runs to the length asked for instead of the count read, so the empty
        // chars past the count go into the list and come back as U+FFFD
        // - see RmInvalidXMLCharReader.read()
        List<Character> invalidChars = new ArrayList<>();
        char[] buffer = new char[6];
        buffer[4] = 'Z';

        assertThat(reader("ab", invalidChars).read(buffer, 0, 6)).isEqualTo(2);
        assertThat(buffer).containsExactly('a', 'b', '\uFFFD', '\uFFFD', 'Z', '\uFFFD');
        assertThat(invalidChars).containsExactly('\u0000', '\u0000', '\u0000');
    }
}
