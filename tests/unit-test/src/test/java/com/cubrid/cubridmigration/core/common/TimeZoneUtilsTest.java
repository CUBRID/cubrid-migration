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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

@DisplayName("TimeZoneUtils")
@ResourceLock(Resources.LOCALE)
@ResourceLock(Resources.TIME_ZONE)
class TimeZoneUtilsTest {

    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();
    private static final TimeZone ORIGINAL_TIME_ZONE = TimeZone.getDefault();

    @BeforeEach
    void useFixedDefaults() {
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @AfterEach
    void restoreDefaults() {
        Locale.setDefault(ORIGINAL_LOCALE);
        TimeZone.setDefault(ORIGINAL_TIME_ZONE);
    }

    @Nested
    @DisplayName("getTimeZonesList()")
    class GetTimeZonesList {

        @Test
        @DisplayName("each entry is a whole-hour GMT offset followed by the zone ids sharing it")
        void entry_pairsOffsetWithZoneIds() {
            assertThat(TimeZoneUtils.getTimeZonesList())
                    .allSatisfy(entry -> assertThat(entry).matches("GMT[+-]\\d{2}:00--[^,]+(,.+)*"))
                    .anySatisfy(
                            entry ->
                                    assertThat(entry)
                                            .startsWith("GMT+09:00--")
                                            .contains("Asia/Seoul"));
        }

        @Test
        @DisplayName("a group longer than 100 characters is cut and ends in an ellipsis")
        void longGroup_isCutWithEllipsis() {
            assertThat(TimeZoneUtils.getTimeZonesList())
                    .anySatisfy(
                            entry -> assertThat(entry).startsWith("GMT+00:00--").endsWith("..."));
        }

        @Test
        @DisplayName("entries are ordered as strings, so every + offset precedes every - offset")
        void list_sortsOffsetsAsStrings() {
            // DEFECT: the offsets are keys of a TreeMap of strings, so "GMT+14:00" sorts before
            // "GMT-01:00" and the minus offsets run from -01 to -12 instead of west to east
            // - see TimeZoneUtils.getTimeZonesList()
            List<String> entries = TimeZoneUtils.getTimeZonesList();

            int lastPlus = -1;
            int firstMinus = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).startsWith("GMT+")) {
                    lastPlus = i;
                } else if (firstMinus < 0) {
                    firstMinus = i;
                }
            }
            assertThat(entries.get(lastPlus)).startsWith("GMT+14:00");
            assertThat(firstMinus).isEqualTo(lastPlus + 1);
            assertThat(entries.get(firstMinus)).startsWith("GMT-01:00");
            assertThat(entries.get(entries.size() - 1)).startsWith("GMT-12:00");
        }
    }

    @Nested
    @DisplayName("getGMTByDisplay()")
    class GetGMTByDisplay {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("any text found in a group's display resolves to that group's offset")
        @CsvSource({
            "Asia/Seoul,            GMT+09:00",
            "Seoul,                 GMT+09:00",
            "Etc/GMT+12,            GMT-12:00",
            "GMT+09:00,             GMT+09:00",
            "GMT-12:00--Etc/GMT+12, GMT-12:00",
        })
        void textInDisplay_returnsItsOffset(String text, String expected) {
            assertThat(TimeZoneUtils.getGMTByDisplay(text)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> the default zone's offset")
        @DisplayName("null and Default stand for the default time zone")
        @NullSource
        @ValueSource(strings = {"Default"})
        void nullOrDefault_returnsDefaultZoneOffset(String text) {
            assertThat(TimeZoneUtils.getGMTByDisplay(text)).isEqualTo("GMT+09:00");
        }

        @Test
        @DisplayName("an empty text is found in the first group")
        void emptyText_returnsFirstGroupOffset() {
            assertThat(TimeZoneUtils.getGMTByDisplay("")).isEqualTo("GMT+00:00");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("a zone cut from its group's display is not found, even UTC")
        @ValueSource(strings = {"UTC", "Europe/Paris", "Asia/Kolkata"})
        void zoneCutFromDisplay_returnsNull(String zoneId) {
            // DEFECT: a group keeps only the ids that fit in about 100 characters and then "...",
            // and the lookup searches that shortened text, so a zone left out of it is never
            // found
            // - see TimeZoneUtils.getGMTByDisplay()
            assertThat(TimeZoneUtils.getGMTByDisplay(zoneId)).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("text in no group, or in another case, is not found")
        @ValueSource(strings = {"nonexistent", "asia/seoul"})
        void unknownText_returnsNull(String text) {
            assertThat(TimeZoneUtils.getGMTByDisplay(text)).isNull();
        }
    }

    @Nested
    @DisplayName("getGMTFormat()")
    class GetGMTFormat {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a zone id gives its raw offset, daylight saving ignored")
        @CsvSource({
            "Asia/Seoul,       GMT+09:00",
            "America/New_York, GMT-05:00",
            "UTC,              GMT+00:00",
            "GMT+08,           GMT+08:00",
        })
        void zoneId_returnsRawOffset(String zoneId, String expected) {
            assertThat(TimeZoneUtils.getGMTFormat(zoneId)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} ms -> {1}")
        @DisplayName("a raw offset in milliseconds is written as whole hours")
        @CsvSource({
            "0,         GMT+00:00",
            "32400000,  GMT+09:00",
            "-10800000, GMT-03:00",
            "50400000,  GMT+14:00",
            "-43200000, GMT-12:00",
            "3599999,   GMT+00:00",
        })
        void rawOffset_returnsWholeHours(int rawOffset, String expected) {
            assertThat(TimeZoneUtils.getGMTFormat(rawOffset)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a zone off the whole hour loses its minutes")
        @CsvSource({
            "Asia/Kolkata,     GMT+05:00",
            "Asia/Kathmandu,   GMT+05:00",
            "America/St_Johns, GMT-03:00",
        })
        void fractionalHourZone_dropsItsMinutes(String zoneId, String expected) {
            // DEFECT: the offset is cut down to whole hours and ":00" is always appended, so
            // +05:30 and +05:45 both print as +05:00
            // - see TimeZoneUtils.getGMTFormat()
            assertThat(TimeZoneUtils.getGMTFormat(zoneId)).isEqualTo(expected);
        }

        @Test
        @DisplayName("half an hour behind GMT prints as +00:00")
        void halfHourBehindGmt_losesTheSign() {
            // DEFECT: -30 minutes truncates to 0 hours, which takes the + branch
            // - see TimeZoneUtils.getGMTFormat()
            assertThat(TimeZoneUtils.getGMTFormat(-1800000)).isEqualTo("GMT+00:00");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> GMT+00:00")
        @DisplayName("an unknown zone id quietly becomes GMT")
        @ValueSource(strings = {"No/Such", ""})
        void unknownZoneId_returnsGmt(String zoneId) {
            // DEFECT: TimeZone.getTimeZone() falls back to GMT for an id it does not know, and
            // the result is used unchecked, so a misspelt zone is indistinguishable from UTC
            // - see TimeZoneUtils.getGMTFormat()
            assertThat(TimeZoneUtils.getGMTFormat(zoneId)).isEqualTo("GMT+00:00");
        }

        @ParameterizedTest(name = "[{index}] {0}, {1} ms -> {2}")
        @DisplayName("the digits and the minus sign follow the default locale")
        @MethodSource("com.cubrid.cubridmigration.core.common.TimeZoneUtilsTest#localizedOffsets")
        void nonEnglishLocale_rendersLocalizedOffset(
                String languageTag, int rawOffset, String expected) {
            // DEFECT: the DecimalFormat takes its symbols from the default locale, so Swedish
            // writes U+2212 for the minus and Arabic, Persian or Thai-digit locales write their
            // own digits. The zone table built when the class loads goes through the same path
            // - see TimeZoneUtils.getGMTFormat()
            Locale.setDefault(Locale.forLanguageTag(languageTag));

            assertThat(TimeZoneUtils.getGMTFormat(rawOffset)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a null zone id means the default time zone")
        void nullZoneId_usesDefaultZone() {
            assertThat(TimeZoneUtils.getGMTFormat((String) null)).isEqualTo("GMT+09:00");
        }
    }

    @Nested
    @DisplayName("getTZFromOffset()")
    class GetTZFromOffset {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("an offset in hours gets a sign and at least two digits")
        @CsvSource({
            "0,   GMT+00:00",
            "1,   GMT+01:00",
            "-1,  GMT-01:00",
            "9,   GMT+09:00",
            "-5,  GMT-05:00",
            "11,  GMT+11:00",
            "-11, GMT-11:00",
            "12,  GMT+12:00",
        })
        void hourOffset_returnsSignedHours(int offset, String expected) {
            assertThat(TimeZoneUtils.getTZFromOffset(offset)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("an offset of three or more digits is written out in full")
        @CsvSource({
            "100,      GMT+100:00",
            "-100,     GMT-100:00",
            // A value in milliseconds, which is what the MySQL and MariaDB fetchers hand over
            // on their fallback path.
            "32400000, GMT+32400000:00",
        })
        void largeOffset_isWrittenInFull(int offset, String expected) {
            assertThat(TimeZoneUtils.getTZFromOffset(offset)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}, {1} -> {2}")
        @DisplayName("the digits follow the default locale while the sign stays ASCII")
        @MethodSource(
                "com.cubrid.cubridmigration.core.common.TimeZoneUtilsTest#localizedHourOffsets")
        void nonEnglishLocale_rendersLocalizedDigits(
                String languageTag, int offset, String expected) {
            // DEFECT: the two-digit DecimalFormat takes its digits from the default locale, so an
            // Arabic, Persian or Thai-digit machine writes an offset no GMT parser reads
            // - see TimeZoneUtils.getTZFromOffset()
            Locale.setDefault(Locale.forLanguageTag(languageTag));

            assertThat(TimeZoneUtils.getTZFromOffset(offset)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getOracleTZID()")
    class GetOracleTZID {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a GMT offset becomes the Etc zone with the opposite sign")
        @CsvSource({
            "GMT+09:00, Etc/GMT-9",
            "GMT-05:00, Etc/GMT+5",
            "GMT+00:00, Etc/GMT-0",
            "GMT-12:00, Etc/GMT+12",
            "GMT+14:00, Etc/GMT-14",
        })
        void gmtOffset_returnsEtcZoneWithFlippedSign(String gmt, String expected) {
            assertThat(TimeZoneUtils.getOracleTZID(gmt)).isEqualTo(expected);
        }

        @Test
        @DisplayName("the minutes of the offset are dropped")
        void offsetWithMinutes_dropsThem() {
            assertThat(TimeZoneUtils.getOracleTZID("GMT+05:30")).isEqualTo("Etc/GMT-5");
        }

        @Test
        @DisplayName("an offset without two hour digits cannot be cut")
        void shortOffset_throwsStringIndexOutOfBoundsException() {
            assertThatThrownBy(() -> TimeZoneUtils.getOracleTZID("GMT+9"))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("format()")
    class Format {

        @ParameterizedTest(name = "[{index}] {0} ms -> \"{1}\"")
        @DisplayName("a duration is written as days and time with zero padding")
        @CsvSource({
            "0,        00 00:00:00.000",
            "1,        00 00:00:00.001",
            "999,      00 00:00:00.999",
            "1000,     00 00:00:01.000",
            "3600000,  00 01:00:00.000",
            "86399999, 00 23:59:59.999",
            "90061001, 01 01:01:01.001",
        })
        void duration_rendersDaysAndTime(long ms, String expected) {
            assertThat(TimeZoneUtils.format(ms)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a hundred days or more keep every digit of the day count")
        void hundredDays_keepsAllDayDigits() {
            assertThat(TimeZoneUtils.format(8640000000L)).isEqualTo("100 00:00:00.000");
        }

        @ParameterizedTest(name = "[{index}] {0} ms -> \"{1}\"")
        @DisplayName("a negative duration puts its sign inside each padded field")
        @CsvSource({"-1, 00 00:00:00.00-1", "-61000, 00 00:0-1:0-1.000"})
        void negativeDuration_breaksThePadding(long ms, String expected) {
            // DEFECT: each field is padded by comparing it with 10 or 100, which a negative value
            // always passes, so the zeros land in front of the minus sign
            // - see TimeZoneUtils.format()
            assertThat(TimeZoneUtils.format(ms)).isEqualTo(expected);
        }
    }

    static Stream<Arguments> localizedOffsets() {
        return Stream.of(
                Arguments.of("sv-SE", -10800000, "GMT\u221203:00"),
                Arguments.of("ar-EG", 32400000, "GMT+\u0660\u0669:00"),
                Arguments.of("fa-IR", 32400000, "GMT+\u06F0\u06F9:00"),
                Arguments.of("th-TH-u-nu-thai", 32400000, "GMT+\u0E50\u0E59:00"));
    }

    static Stream<Arguments> localizedHourOffsets() {
        return Stream.of(
                Arguments.of("sv-SE", -5, "GMT-05:00"),
                Arguments.of("ar-EG", 9, "GMT+\u0660\u0669:00"),
                Arguments.of("fa-IR", -5, "GMT-\u06F0\u06F5:00"),
                Arguments.of("th-TH-u-nu-thai", 9, "GMT+\u0E50\u0E59:00"));
    }
}
