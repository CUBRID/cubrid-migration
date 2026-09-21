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

import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

@DisplayName("CUBRIDTimeUtil")
@ResourceLock(Resources.TIME_ZONE)
class CUBRIDTimeUtilTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** 2009-11-05 06:44:15.111 UTC, so every rendering below is a fixed string. */
    private static final Date FIXED = new Date(1257403455111L);

    private static TimeZone defaultZone;

    @BeforeAll
    static void pinTimeZone() {
        defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(UTC);
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(defaultZone);
    }

    @Nested
    @DisplayName("the default renderings")
    class DefaultFormats {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("each default rendering has its own fixed shape")
        @CsvSource({
            // The legacy test pinned only the string length; these pin the text.
            "date,          2009-11-05",
            "dateTime,      2009-11-05 06:44:15",
            "milin,         2009-11-05 06:44:15.111",
            "time,          06:44:15",
            "timeMilin,     06:44:15.111",
            // The migration wizard's start stamp carries no separators at all.
            "wizardStart,   200911050644",
        })
        void defaultFormat_rendersItsShape(String which, String expected) {
            assertThat(render(which)).isEqualTo(expected);
        }

        private String render(String which) {
            switch (which) {
                case "date":
                    return CUBRIDTimeUtil.defaultFormatDate(FIXED);
                case "dateTime":
                    return CUBRIDTimeUtil.defaultFormatDateTime(FIXED);
                case "milin":
                    return CUBRIDTimeUtil.defaultFormatMilin(FIXED);
                case "time":
                    return CUBRIDTimeUtil.defaultFormatTime(FIXED);
                case "timeMilin":
                    return CUBRIDTimeUtil.defaultFormatTimeMilin(FIXED);
                case "wizardStart":
                    return CUBRIDTimeUtil.wizardStarDateTimeFormat(FIXED);
                default:
                    throw new IllegalArgumentException(which);
            }
        }
    }

    @Nested
    @DisplayName("re-formatting a source value")
    class Reformatting {

        @ParameterizedTest(name = "[{index}] \"{0}\" as {1} -> {2}")
        @DisplayName("a value is re-read and re-rendered in the requested pattern")
        @CsvSource({
            "06:44:15 AM 11/05/2009,      'yyyy-MM-dd HH:mm:ss',      2009-11-05 06:44:15",
            "06:44:15.111 AM 11/05/2009,  'yyyy-MM-dd HH:mm:ss.SSS',  2009-11-05 06:44:15.111",
        })
        void formatDateTime_appliesThePattern(String written, String pattern, String expected) {
            assertThat(CUBRIDTimeUtil.formatDateTime(written, pattern, UTC)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a value it cannot read yields no string, rather than throwing")
        void formatDateTime_ofUnreadableValue_returnsNull() {
            assertThat(CUBRIDTimeUtil.formatDateTime("not a date", "yyyy-MM-dd HH:mm:ss", UTC))
                    .isNull();
        }

        @Test
        @DisplayName("the epoch-millis overload renders the instant in the zone it is handed")
        void formatDateTime_ofEpochMillis_usesTheGivenZone() {
            assertThat(
                            CUBRIDTimeUtil.formatDateTime(
                                    FIXED.getTime(), "yyyy-MM-dd HH:mm:ss.SSS", UTC))
                    .isEqualTo("2009-11-05 06:44:15.111");
            assertThat(
                            CUBRIDTimeUtil.formatDateTime(
                                    FIXED.getTime(),
                                    "yyyy-MM-dd HH:mm:ss",
                                    TimeZone.getTimeZone("Asia/Seoul")))
                    .isEqualTo("2009-11-05 15:44:15");
        }

        @Test
        @DisplayName("formatTimestampLong reads milliseconds, which is what every caller hands it")
        void formatTimestampLong_readsMilliseconds() {
            assertThat(
                            CUBRIDTimeUtil.formatTimestampLong(
                                    FIXED.getTime(), "yyyy-MM-dd HH:mm:ss", UTC))
                    .isEqualTo("2009-11-05 06:44:15");
            // The same number read as seconds would land in 1970, which is what pins the unit.
            // Callers pass parseDate2Long/parseTime2Long/parseTimestamp results, all milliseconds
            // - see CUBRIDFormator.formatDate(), formatTime() and formatTimeStamp().
            assertThat(
                            CUBRIDTimeUtil.formatTimestampLong(
                                    1_257_403_455L, "yyyy-MM-dd HH:mm:ss", UTC))
                    .isEqualTo("1970-01-15 13:16:43");
        }

        @Test
        @DisplayName("formatDate renders a Date in the zone it is handed")
        void formatDate_usesTheGivenZone() {
            assertThat(CUBRIDTimeUtil.formatDate(FIXED, "yyyy-MM-dd HH:mm:ss", UTC))
                    .isEqualTo("2009-11-05 06:44:15");
            assertThat(
                            CUBRIDTimeUtil.formatDate(
                                    FIXED,
                                    "yyyy-MM-dd HH:mm:ss",
                                    TimeZone.getTimeZone("Asia/Seoul")))
                    .isEqualTo("2009-11-05 15:44:15");
        }

        @Test
        @DisplayName("getDateFormat carries the locale's zone through to the formatter")
        void getDateFormat_keepsTheZone() {
            assertThat(
                            CUBRIDTimeUtil.getDateFormat("yyyy-MM-dd", Locale.CHINESE, UTC)
                                    .getTimeZone())
                    .isEqualTo(UTC);
        }
    }

    @Nested
    @DisplayName("parsing a source value into epoch millis")
    class Parsing {

        @Test
        @DisplayName("a time is milliseconds since midnight, not an epoch instant")
        void parseTime2Long_returnsMillisSinceMidnight() {
            assertThat(catchParse(() -> CUBRIDTimeUtil.parseTime2Long("11:12:13 am", UTC)))
                    .isEqualTo(40_333_000L);
        }

        @Test
        @DisplayName("a date is the epoch instant of its midnight")
        void parseDate2Long_returnsEpochMillis() {
            assertThat(catchParse(() -> CUBRIDTimeUtil.parseDate2Long("2009-11-01", UTC)))
                    .isEqualTo(1_257_033_600_000L);
        }

        @Test
        @DisplayName("a timestamp and a datetime read the same text the same way")
        void parseTimestampAndDatetime_agree() {
            long timestamp =
                    catchParse(() -> CUBRIDTimeUtil.parseTimestamp("2009-02-20 16:42:46", UTC));
            long datetime =
                    catchParse(() -> CUBRIDTimeUtil.parseDatetime2Long("2009-02-20 16:42:46", UTC));

            assertThat(timestamp).isEqualTo(1_235_148_166_000L).isEqualTo(datetime);
        }

        @ParameterizedTest(name = "[{index}] parseDate2Long(\"{0}\")")
        @DisplayName("a value the pattern cannot read is refused, never guessed at")
        @ValueSource(strings = {"2009", "not a date", ""})
        void unparseableDate_throwsParseException(String value) {
            assertThatThrownBy(() -> CUBRIDTimeUtil.parseDate2Long(value, UTC))
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("Unparseable date");
        }

        @Test
        @DisplayName("each parser names the kind of value it could not read")
        void unparseableValue_namesItsKind() {
            assertThatThrownBy(() -> CUBRIDTimeUtil.parseTime2Long("11:12:1311", UTC))
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("Unparseable time");
            assertThatThrownBy(() -> CUBRIDTimeUtil.parseDatetime2Long("2009", UTC))
                    .isInstanceOf(ParseException.class)
                    .hasMessageContaining("Unparseable datetime");
        }

        private long catchParse(ParseCall call) {
            try {
                return call.get();
            } catch (ParseException e) {
                throw new AssertionError("should have parsed", e);
            }
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("a value is valid only against the pattern it was written for")
        void validateDateString_matchesThePattern() {
            assertThat(
                            CUBRIDTimeUtil.validateDateString(
                                    "2009-11-05 06:44:15", "yyyy-MM-dd HH:mm:ss"))
                    .isTrue();
            assertThat(
                            CUBRIDTimeUtil.validateDateString(
                                    "06:44:15 AM 11/05/2009", "yyyy-MM-dd HH:mm:ss"))
                    .isFalse();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("every temporal pseudo column is accepted as a function")
        @ValueSource(strings = {"SYS_DATE", "SYS_TIME", "SYS_TIMESTAMP", "SYS_DATETIME"})
        void validateDateTimeFunction_acceptsPseudoColumns(String function) {
            assertThat(CUBRIDTimeUtil.validateDateTimeFunction(function)).isTrue();
        }

        @Test
        @DisplayName("a word that is not a pseudo column is not a function")
        void validateDateTimeFunction_rejectsAnythingElse() {
            assertThat(CUBRIDTimeUtil.validateDateTimeFunction("nonsense")).isFalse();
        }
    }

    private interface ParseCall {
        long get() throws ParseException;
    }
}
