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
package com.cubrid.cubridmigration.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("TextFileUtils")
class TextFileUtilsTest {

    @TempDir Path dir;

    private String lines;

    @BeforeEach
    void writeLines() throws Exception {
        lines =
                Files.write(dir.resolve("lines.txt"), "a\nb\r\nc".getBytes(StandardCharsets.UTF_8))
                        .toString();
    }

    @Test
    @DisplayName("lines up to the limit are read, each ending with CRLF whatever ended it")
    void readText_readsUpToTheLimitWithCrlf() {
        assertThat(TextFileUtils.readText(lines, "UTF-8", 2)).isEqualTo("a\r\nb\r\n");
    }

    @Test
    @DisplayName("a limit past the last line reads every line, the last one included")
    void readText_readsEveryLineUnderALargeLimit() {
        assertThat(TextFileUtils.readText(lines, "UTF-8", 10)).isEqualTo("a\r\nb\r\nc\r\n");
    }

    @ParameterizedTest(name = "[{index}] limit {0} -> \"\"")
    @DisplayName("a limit of zero or less reads nothing")
    @ValueSource(ints = {0, -1})
    void readText_readsNothingUnderANonPositiveLimit(int limit) {
        assertThat(TextFileUtils.readText(lines, "UTF-8", limit)).isEmpty();
    }

    @Test
    @DisplayName("the file is decoded with the given encoding")
    void readText_decodesWithTheGivenEncoding() throws Exception {
        Path file =
                Files.write(
                        dir.resolve("utf16.txt"), "\uAC00\nx".getBytes(StandardCharsets.UTF_16BE));

        assertThat(TextFileUtils.readText(file.toString(), "UTF-16BE", 5))
                .isEqualTo("\uAC00\r\nx\r\n");
    }

    @Test
    @DisplayName("an unknown encoding is rethrown as a RuntimeException")
    void readText_wrapsAnUnknownEncoding() {
        assertThatThrownBy(() -> TextFileUtils.readText(lines, "NO-SUCH", 5))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(UnsupportedEncodingException.class);
    }

    @Test
    @DisplayName("a null encoding fails with an NPE")
    void readText_throwsOnANullEncoding() {
        assertThatThrownBy(() -> TextFileUtils.readText(lines, null, 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a missing file is rethrown as a RuntimeException")
    void readText_wrapsAMissingFile() {
        assertThatThrownBy(() -> TextFileUtils.readText(dir.resolve("none").toString(), "UTF-8", 5))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("a blank file name is rejected as it is, without wrapping")
    void readText_rejectsABlankFileName() {
        assertThatThrownBy(() -> TextFileUtils.readText("", "UTF-8", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Can't create input stream: file name can't be empty.");
    }
}
