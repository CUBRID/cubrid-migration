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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.util.Locale;

@DisplayName("CharsetUtils")
@ResourceLock(Resources.LOCALE)
class CharsetUtilsTest {

    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    @BeforeEach
    void useUsLocale() {
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    @Nested
    @DisplayName("getCharsetByte()")
    class GetCharsetByte {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName(
                "a known charset, in any case and with surrounding spaces, gives its bytes per"
                        + " character")
        @CsvSource({
            "utf-8,       3",
            "UTF-8,       3",
            "' UTF-8 ',   3",
            "utf8,        3",
            "al32utf8,    4",
            "iso-8859-1,  1",
            "latin1,      1",
            "iso8859-15,  1",
            "euc-kr,      2",
            "gbk,         2",
        })
        void knownCharset_returnsBytesPerCharacter(String charset, int expected) {
            assertThat(CharsetUtils.getCharsetByte(charset)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> 2")
        @DisplayName("an unknown charset falls back to 2")
        @ValueSource(strings = {"1111", "x-unknown"})
        void unknownCharset_returnsTwo(String charset) {
            assertThat(CharsetUtils.getCharsetByte(charset)).isEqualTo(2);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> 3")
        @DisplayName("a missing charset is taken to be 3 bytes per character")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void blankCharset_returnsThree(String charset) {
            assertThat(CharsetUtils.getCharsetByte(charset)).isEqualTo(3);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("under a Turkish locale a charset name with an I misses the table")
        @CsvSource({"ISO-8859-1, 2", "LATIN1, 2", "ASCII, 2", "UTF-8, 3"})
        void turkishLocale_missesNamesWithCapitalI(String charset, int expected) {
            // DEFECT: the name is lower-cased with the default locale, and Turkish lower-cases I
            // to a dotless i, so ISO-8859-1, LATIN1 and ASCII all miss the table and fall back
            // to 2
            // - see CharsetUtils.getCharsetByte()
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(CharsetUtils.getCharsetByte(charset)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getCharsets()")
    class GetCharsets {

        @Test
        @DisplayName(
                "the choices start with a blank entry followed by the common charsets in a fixed"
                        + " order")
        void list_offersCommonCharsetsInFixedOrder() {
            assertThat(CharsetUtils.getCharsets())
                    .containsSubsequence(
                            "", "UTF-8", "ISO-8859-1", "EUC-KR", "EUC-JP", "GB2312", "GBK");
        }
    }

    @Nested
    @DisplayName("turnOracleCharset2Normal()")
    class TurnOracleCharset2Normal {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the first table entry contained in the Oracle name gives the Java name")
        @CsvSource({
            "US7ASCII,       ASCII",
            "AL32UTF8,       UTF8",
            "AL16UTF16,      UTF16",
            "ZHS16GBK,       GBK",
            "ZHS16CGB231280, GB2312",
            "WE8ISO8859P1,   ISO8859-1",
            "EE8ISO8859P2,   ISO8859-2",
        })
        void oracleCharset_returnsJavaName(String oracleCharset, String expected) {
            assertThat(CharsetUtils.turnOracleCharset2Normal(oracleCharset)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> ISO8859-1")
        @DisplayName("ISO8859P13 and ISO8859P15 are caught by the ISO8859P1 entry first")
        @ValueSource(strings = {"BLT8ISO8859P13", "WE8ISO8859P15"})
        void laterIsoPart_resolvesToIso8859Part1(String oracleCharset) {
            // DEFECT: the table is searched by substring from the top, and ISO8859P1 comes
            // before ISO8859P13 and ISO8859P15, so both resolve to ISO8859-1 and their own
            // entries can never match
            // - see CharsetUtils.turnOracleCharset2Normal()
            assertThat(CharsetUtils.turnOracleCharset2Normal(oracleCharset)).isEqualTo("ISO8859-1");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> the JVM default charset")
        @DisplayName("a name matching no entry, in any case, falls back to the JVM default charset")
        @ValueSource(strings = {"KO16MSWIN949", "JA16SJIS", "al32utf8", ""})
        void unmatchedName_returnsJvmDefaultCharset(String oracleCharset) {
            // DEFECT: a charset missing from the table, KO16MSWIN949 or JA16SJIS for one, turns
            // into whatever the JVM default is, and CharConverter then decodes the source bytes
            // with that charset
            // - see CharsetUtils.turnOracleCharset2Normal()
            assertThat(CharsetUtils.turnOracleCharset2Normal(oracleCharset))
                    .isEqualTo(Charset.defaultCharset().name());
        }

        @Test
        @DisplayName("a null name fails with an NPE")
        void nullName_throwsNullPointerException() {
            assertThatThrownBy(() -> CharsetUtils.turnOracleCharset2Normal(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
