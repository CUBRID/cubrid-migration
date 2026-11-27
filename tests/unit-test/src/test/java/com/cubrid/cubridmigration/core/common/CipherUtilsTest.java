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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

@DisplayName("CipherUtils")
class CipherUtilsTest {

    // Text of 31 bytes, or of 33 and more, leaves no room for random padding inside the first
    // block, so its ciphertext is fixed. Saved connection passwords rely on it staying readable.
    private static final String THIRTY_ONE_A = repeat('a', 31);
    private static final String THIRTY_ONE_A_CIPHERTEXT =
            "5a59445b7aafd0055a59445b7aafd0055a59445b7aafd005cf228360cd423f9b";
    private static final String THIRTY_THREE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456";
    private static final String THIRTY_THREE_CHARS_CIPHERTEXT =
            "01025cca6a7797793e8188ffea4b5eacd520658106f47a633ad9983ec8b655e6"
                    + "1965ca76c0fe03c068233b78dcdcee2268233b78dcdcee2268233b78dcdcee22";

    @Nested
    @DisplayName("encrypt()")
    class Encrypt {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("text that needs no random padding encrypts to the same ciphertext every time")
        @MethodSource("com.cubrid.cubridmigration.core.common.CipherUtilsTest#fixedCiphertexts")
        void textWithoutRandomPadding_returnsFixedCiphertext(String text, String expected) {
            assertThat(CipherUtils.encrypt(text)).isEqualTo(expected);
        }

        @Test
        @DisplayName("short text fills one 32-byte block with random padding, as lower-case hex")
        void shortText_returnsRandomlyPaddedBlock() {
            String first = CipherUtils.encrypt("pw");
            String second = CipherUtils.encrypt("pw");

            assertThat(first).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(second);
        }

        @ParameterizedTest(name = "[{index}] {0} characters")
        @DisplayName("text filling whole 32-byte blocks fails with an out-of-bounds write")
        @ValueSource(ints = {32, 64})
        void wholeBlocksOfText_throwsArrayIndexOutOfBoundsException(int length) {
            // DEFECT: the terminating zero is written at buf[length], which is one past the end
            // when the text already fills whole 32-byte blocks, so a 32- or 64-character
            // password cannot be saved
            // - see CipherUtils.encrypt()
            String text = repeat('p', length);

            assertThatThrownBy(() -> CipherUtils.encrypt(text))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"\"")
        @DisplayName("null or empty text encrypts to an empty string")
        @NullAndEmptySource
        void nullOrEmpty_returnsEmptyString(String text) {
            assertThat(CipherUtils.encrypt(text)).isEmpty();
        }
    }

    @Nested
    @DisplayName("decrypt()")
    class Decrypt {

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("the text encrypt() produced comes back unchanged")
        @ValueSource(
                strings = {
                    "pw",
                    "abcdefghijklmnopqrstuvwxyz01234",
                    "abcdefghijklmnopqrstuvwxyz0123456",
                    "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0",
                    "\uBE44\uBC00\uBC88\uD638"
                })
        void ownCiphertext_restoresText(String text) {
            assertThat(CipherUtils.decrypt(CipherUtils.encrypt(text))).isEqualTo(text);
        }

        @Test
        @DisplayName("a ciphertext written by an earlier run still decrypts")
        void fixedCiphertext_restoresText() {
            assertThat(CipherUtils.decrypt(THIRTY_ONE_A_CIPHERTEXT)).isEqualTo(THIRTY_ONE_A);
        }

        @Test
        @DisplayName("upper-case hex digits are read like lower-case ones")
        void upperCaseCiphertext_restoresText() {
            assertThat(CipherUtils.decrypt(THIRTY_ONE_A_CIPHERTEXT.toUpperCase()))
                    .isEqualTo(THIRTY_ONE_A);
        }

        @Test
        @DisplayName("a ciphertext that is not hex decrypts silently as if it were all zeros")
        void nonHexCiphertext_decryptsLikeZeros() {
            // DEFECT: every character outside 0-9, a-f and A-F is read as the digit 0, so a
            // damaged ciphertext decrypts to garbage instead of being rejected
            // - see CipherUtils.decrypt()
            assertThat(CipherUtils.decrypt(repeat('z', 64)))
                    .isEqualTo(CipherUtils.decrypt(repeat('0', 64)));
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("null, empty or blank text is returned as it came")
        @NullSource
        @ValueSource(strings = {"", "   "})
        void nullOrBlank_returnsItUnchanged(String text) {
            assertThat(CipherUtils.decrypt(text)).isEqualTo(text);
        }
    }

    @Nested
    @DisplayName("getHex()")
    class GetHex {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("two hex digits, in either case, make one byte")
        @CsvSource({"a1, -95", "A1, -95", "ff, -1", "FF, -1", "00, 0", "7f, 127"})
        void hexDigitPair_returnsByte(String digits, byte expected) throws Exception {
            assertThat(getHex(digits)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("a character that is not a hex digit counts as 0")
        @CsvSource({"zz, 0", "1z, 16", "9g, -112"})
        void nonHexDigit_countsAsZero(String digits, byte expected) throws Exception {
            assertThat(getHex(digits)).isEqualTo(expected);
        }

        private byte getHex(String digits) throws Exception {
            Method method = CipherUtils.class.getDeclaredMethod("getHex", byte[].class, int.class);
            method.setAccessible(true);
            return (Byte) method.invoke(null, digits.getBytes(StandardCharsets.US_ASCII), 0);
        }
    }

    @Nested
    @DisplayName("endian()")
    class Endian {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the little-endian flag reverses the four bytes")
        @CsvSource({"0x01020304, 0x04030201", "0xFF000000, 0x000000FF", "0xFFFFFFFF, 0xFFFFFFFF"})
        void littleEndianFlag_reversesBytes(String value, String expected) throws Exception {
            assertThat(endian(Integer.parseUnsignedInt(value.substring(2), 16), true))
                    .isEqualTo(Integer.parseUnsignedInt(expected.substring(2), 16));
        }

        @Test
        @DisplayName("without the flag the value is returned as it came")
        void bigEndianFlag_returnsValueUnchanged() throws Exception {
            assertThat(endian(0x01020304, false)).isEqualTo(0x01020304);
        }

        private int endian(int value, boolean littleEndian) throws Exception {
            Method method = CipherUtils.class.getDeclaredMethod("endian", int.class, boolean.class);
            method.setAccessible(true);
            return (Integer) method.invoke(null, value, littleEndian);
        }
    }

    static Stream<Arguments> fixedCiphertexts() {
        return Stream.of(
                Arguments.of(THIRTY_ONE_A, THIRTY_ONE_A_CIPHERTEXT),
                Arguments.of(THIRTY_THREE_CHARS, THIRTY_THREE_CHARS_CIPHERTEXT));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
