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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.URIParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

@DisplayName("CommonUtils")
@ResourceLock(Resources.LOCALE)
class CommonUtilsTest {

    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    @Nested
    @DisplayName("str2Long()")
    class Str2Long {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("a decimal integer, signed or not, parses as it reads")
        @CsvSource({"23444, 23444", "-5,    -5", "+5,    5"})
        void decimalInteger_returnsItsValue(String text, long expected) {
            assertThat(CommonUtils.str2Long(text)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> 0")
        @DisplayName("anything Long.parseLong rejects comes back as 0 instead of an error")
        @NullAndEmptySource
        @ValueSource(strings = {"po", " 1", "1.0", "99999999999999999999"})
        void unparsableText_returnsZero(String text) {
            assertThat(CommonUtils.str2Long(text)).isZero();
        }
    }

    @Nested
    @DisplayName("isASCII()")
    class IsASCII {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName(
                "text made only of Basic Latin characters, control characters included, is ASCII")
        @ValueSource(strings = {"", "A", "Az09 ~", "\t\r\n"})
        void basicLatinText_returnsTrue(String text) {
            assertThat(CommonUtils.isASCII(text)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("one character from any other block makes the text non-ASCII")
        @ValueSource(strings = {"caf\u00E9", "\uAC00"})
        void characterOutsideBasicLatin_returnsFalse(String text) {
            assertThat(CommonUtils.isASCII(text)).isFalse();
        }

        @Test
        @DisplayName("a code point that belongs to no Unicode block fails with an NPE")
        void characterInNoBlock_throwsNullPointerException() {
            // DEFECT: UnicodeBlock.of() returns null for a code point outside every block (U+2FE0
            // lies in an unassigned gap), and the result is compared without a null check
            // - see CommonUtils.isASCII()
            assertThatThrownBy(() -> CommonUtils.isASCII("\u2FE0"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null text fails with an NPE")
        void nullText_throwsNullPointerException() {
            assertThatThrownBy(() -> CommonUtils.isASCII(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("validateCheckInIdentifier()")
    class ValidateCheckInIdentifier {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"\"")
        @DisplayName("letters, digits, _, #, % and non-ASCII letters are all accepted")
        @ValueSource(strings = {"abc", "a_b#c%d", "\uAC00\uB098"})
        void validIdentifier_returnsEmptyString(String identifier) {
            assertThat(CommonUtils.validateCheckInIdentifier(identifier)).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("an identifier holding a forbidden character gets that character back")
        @MethodSource("com.cubrid.cubridmigration.core.common.CommonUtilsTest#forbiddenCharacters")
        void forbiddenCharacter_returnsThatCharacter(String identifier, String expected) {
            assertThat(CommonUtils.validateCheckInIdentifier(identifier)).isEqualTo(expected);
        }

        @Test
        @DisplayName(
                "with several forbidden characters the first in the rule list wins, not the first"
                        + " in the text")
        void severalForbiddenCharacters_returnsFirstInRuleOrder() {
            assertThat(CommonUtils.validateCheckInIdentifier("x.y z")).isEqualTo(" ");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"empty\"")
        @DisplayName("a null or empty identifier is reported as the word empty")
        @NullAndEmptySource
        void nullOrEmpty_returnsEmptyWord(String identifier) {
            assertThat(CommonUtils.validateCheckInIdentifier(identifier)).isEqualTo("empty");
        }
    }

    @Nested
    @DisplayName("formatCUBRIDNumber()")
    class FormatCUBRIDNumber {

        @BeforeEach
        void useUsLocale() {
            Locale.setDefault(Locale.US);
        }

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @DisplayName("the value is written in plain digits, without grouping or trailing zeros")
        @CsvSource({
            "000099.9900,                              99.99",
            "1E+5,                                     100000",
            "0.000001,                                 0.000001",
            "12345678901234567890.123,                 12345678901234567890.123",
            "-12.34,                                   -12.34",
            "-0.0,                                     0",
            // 38 fraction digits, the most the pattern keeps.
            "0.00000000000000000000000000000000000005, 0.00000000000000000000000000000000000005",
        })
        void decimalValue_rendersPlainDigits(String value, String expected) {
            assertThat(CommonUtils.formatCUBRIDNumber(new BigDecimal(value))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @DisplayName("digits past the 38th fraction digit are rounded away, keeping the sign")
        @CsvSource({"1E-40, 0", "-1E-40, -0"})
        void fractionBeyond38Digits_isRoundedAway(String value, String expected) {
            assertThat(CommonUtils.formatCUBRIDNumber(new BigDecimal(value))).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @DisplayName("the separator and even the digits follow the default locale")
        @MethodSource("com.cubrid.cubridmigration.core.common.CommonUtilsTest#localizedNumbers")
        void nonEnglishLocale_rendersLocalizedNumber(String languageTag, String expected) {
            // DEFECT: the DecimalFormat takes its symbols from the default locale, so a German or
            // French machine writes 1234,5 and an Arabic or Thai-digit one writes its own digits.
            // The text is handed on as a SQL numeric value
            // - see CommonUtils.formatCUBRIDNumber()
            Locale.setDefault(Locale.forLanguageTag(languageTag));

            assertThat(CommonUtils.formatCUBRIDNumber(new BigDecimal("1234.5")))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("a null value is rejected by DecimalFormat")
        void nullValue_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> CommonUtils.formatCUBRIDNumber(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("createListWithArray()")
    class CreateListWithArray {

        @Test
        @DisplayName("the elements are copied in order, nulls included")
        void array_copiesElementsInOrder() {
            assertThat(CommonUtils.createListWithArray(new Object[] {"a", null, 1}))
                    .containsExactly("a", null, 1);
        }

        @Test
        @DisplayName("changing the list leaves the array as it was")
        void changedList_leavesArrayUntouched() {
            Object[] array = {"a", "b"};

            CommonUtils.createListWithArray(array).set(0, "changed");

            assertThat(array).containsExactly("a", "b");
        }

        @Test
        @DisplayName("a null array gives an empty list that can still be added to")
        void nullArray_returnsEmptyMutableList() {
            List<Object> list = CommonUtils.createListWithArray(null);

            list.add("x");

            assertThat(list).isInstanceOf(ArrayList.class).containsExactly("x");
        }
    }

    @Nested
    @DisplayName("getBytesFromByteArray()")
    class GetBytesFromByteArray {

        @Test
        @DisplayName("each boxed byte is unboxed in order")
        void byteArray_unboxesEachByteInOrder() {
            assertThat(CommonUtils.getBytesFromByteArray(new Byte[] {1, -1, 0}))
                    .containsExactly(1, -1, 0);
        }

        @Test
        @DisplayName("an empty array gives an empty array")
        void emptyArray_returnsEmptyArray() {
            assertThat(CommonUtils.getBytesFromByteArray(new Byte[0])).isEmpty();
        }

        @Test
        @DisplayName("a null array gives null, not an empty array")
        void nullArray_returnsNull() {
            assertThat(CommonUtils.getBytesFromByteArray(null)).isNull();
        }

        @Test
        @DisplayName("a null element fails with an NPE")
        void nullElement_throwsNullPointerException() {
            assertThatThrownBy(() -> CommonUtils.getBytesFromByteArray(new Byte[] {1, null}))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getGSSLoginConfigContent()")
    class GetGSSLoginConfigContent {

        @Test
        @DisplayName("both Kerberos login entries read their tickets from the given cache")
        void ticketCache_isWrittenIntoBothEntries() {
            assertThat(CommonUtils.getGSSLoginConfigContent("/tmp/krb5cc_u"))
                    .isEqualTo(
                            "com.sun.security.jgss.krb5.initiate{com.sun.security.auth.module.Krb5LoginModule"
                                + "  required debug=\"false\" doNotPrompt=\"true\""
                                + " useTicketCache=\"true\" ticketCache=\"/tmp/krb5cc_u\";};\n"
                                + "com.sun.security.jgss.initiate{com.sun.security.auth.module.Krb5LoginModule"
                                + " required debug=\"false\" doNotPrompt=\"true\""
                                + " useTicketCache=\"true\" ticketCache=\"/tmp/krb5cc_u\";};");
        }

        @Test
        @DisplayName("the text parses as a JAAS configuration that keeps a POSIX path intact")
        void posixPath_survivesJaasParsing(@TempDir Path dir) throws Exception {
            Configuration configuration = parse(dir, "/tmp/krb5cc_u");

            AppConfigurationEntry entry =
                    onlyEntry(configuration, "com.sun.security.jgss.initiate");
            assertThat(entry.getLoginModuleName())
                    .isEqualTo("com.sun.security.auth.module.Krb5LoginModule");
            assertThat(entry.getControlFlag())
                    .isEqualTo(AppConfigurationEntry.LoginModuleControlFlag.REQUIRED);
            assertThat(entry.getOptions())
                    .extracting("ticketCache", "useTicketCache", "doNotPrompt", "debug")
                    .containsExactly("/tmp/krb5cc_u", "true", "true", "false");
        }

        @Test
        @DisplayName("a Windows path loses its backslashes once JAAS parses the text")
        void windowsPath_losesBackslashesWhenParsed(@TempDir Path dir) throws Exception {
            // DEFECT: the path goes into a quoted JAAS string unescaped, and JAAS reads a
            // backslash as an escape, so a Windows ticket cache path comes back with every
            // separator gone
            // - see CommonUtils.getGSSLoginConfigContent()
            Configuration configuration = parse(dir, "C:\\Users\\u\\krb5cc");

            for (String name :
                    new String[] {
                        "com.sun.security.jgss.krb5.initiate", "com.sun.security.jgss.initiate"
                    }) {
                assertThat(onlyEntry(configuration, name).getOptions())
                        .extractingByKey("ticketCache")
                        .isEqualTo("C:Usersukrb5cc");
            }
        }

        private Configuration parse(Path dir, String ticketCache) throws Exception {
            Path file = dir.resolve("login.conf");
            Files.write(
                    file,
                    CommonUtils.getGSSLoginConfigContent(ticketCache)
                            .getBytes(StandardCharsets.UTF_8));
            return Configuration.getInstance("JavaLoginConfig", new URIParameter(file.toUri()));
        }

        private AppConfigurationEntry onlyEntry(Configuration configuration, String name) {
            AppConfigurationEntry[] entries = configuration.getAppConfigurationEntry(name);
            assertThat(entries).hasSize(1);
            return entries[0];
        }
    }

    static Stream<Arguments> forbiddenCharacters() {
        return Stream.of(
                Arguments.of("a b", " "),
                Arguments.of("a\tb", "\t"),
                Arguments.of("a/b", "/"),
                Arguments.of("a'b", "'"),
                Arguments.of("q?", "?"),
                Arguments.of("`", "`"));
    }

    static Stream<Arguments> localizedNumbers() {
        return Stream.of(
                Arguments.of("de-DE", "1234,5"),
                Arguments.of("fr-FR", "1234,5"),
                // Arabic-Indic digits with the Arabic decimal separator.
                Arguments.of("ar-EG", "\u0661\u0662\u0663\u0664\u066B\u0665"),
                // Thai digits, requested through the Unicode numbering system extension.
                Arguments.of("th-TH-u-nu-thai", "\u0E51\u0E52\u0E53\u0E54.\u0E55"));
    }
}
