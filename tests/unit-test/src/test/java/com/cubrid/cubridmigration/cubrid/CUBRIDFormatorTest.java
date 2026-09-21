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

import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;

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
import java.util.TimeZone;

@DisplayName("CUBRIDFormator")
@ResourceLock(Resources.TIME_ZONE)
class CUBRIDFormatorTest {

    // format() renders an epoch-millis default through the default zone, so the zone is part of
    // the input. Asia/Shanghai is the zone the legacy fixture pinned; any fixed zone would do.
    private static TimeZone defaultZone;

    @BeforeAll
    static void pinTimeZone() {
        defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(defaultZone);
    }

    private static DataTypeInstance columnOf(String dataType) {
        DataTypeInstance dti = new DataTypeInstance();
        dti.setName(dataType);
        dti.setPrecision(10);
        dti.setScale(0);
        return dti;
    }

    private static String formatted(String dataType, String defaultValue) {
        return CUBRIDFormator.format(columnOf(dataType), defaultValue).getFormatResult();
    }

    @Nested
    @DisplayName("pseudo column defaults")
    class PseudoColumnDefaults {

        @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> {2}")
        @DisplayName("every accepted spelling collapses onto the canonical CUBRID keyword")
        @CsvSource({
            // formatQueryMap holds one lookup table per temporal type; the key is lower-cased
            // first, so the stored spellings match whatever case the source wrote.
            "date,      sysdate,            SYS_DATE",
            "date,      sys_date,           SYS_DATE",
            "date,      sysDate,            SYS_DATE",
            "date,      SYSDATE,            SYS_DATE",
            "date,      currentdate,        CURRENT_DATE",
            "date,      current_date,       CURRENT_DATE",
            "date,      currentdATe,        CURRENT_DATE",
            "time,      systime,            SYS_TIME",
            "time,      sys_time,           SYS_TIME",
            "time,      sysTime,            SYS_TIME",
            "time,      currenttime,        CURRENT_TIME",
            "time,      current_time,       CURRENT_TIME",
            "timestamp, systimestamp,       SYS_TIMESTAMP",
            "timestamp, sys_timestamp,      SYS_TIMESTAMP",
            "timestamp, currenttimestamp,   CURRENT_TIMESTAMP",
            "timestamp, current_timestamp,  CURRENT_TIMESTAMP",
            "datetime,  sysdatetime,        SYS_DATETIME",
            "datetime,  sys_datetime,       SYS_DATETIME",
            "datetime,  currentdatetime,    CURRENT_DATETIME",
            "datetime,  current_datetime,   CURRENT_DATETIME",
        })
        void pseudoColumn_returnsCanonicalKeyword(String type, String written, String expected) {
            assertThat(formatted(type, written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} \"nosuchkeyword\" -> null")
        @DisplayName("a word no lookup table holds is not a default at all")
        @ValueSource(strings = {"date", "time", "timestamp", "datetime"})
        void unknownKeyword_returnsNull(String type) {
            assertThat(formatted(type, "nosuchkeyword")).isNull();
        }
    }

    @Nested
    @DisplayName("temporal literals")
    class TemporalLiterals {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("every accepted date spelling renders as one MM/dd/yyyy literal")
        @CsvSource({
            // Three separator styles and two field orders all reach the same DATE_FORMAT.
            "02/23/2009,    DATE'02/23/2009'",
            "2009/02/23,    DATE'02/23/2009'",
            "2009-02-23,    DATE'02/23/2009'",
            // A year-less value keeps the two fields it was given.
            "02/23,         DATE'02/23'",
            // An already-quoted literal is passed through untouched.
            "DATE'02/23',   DATE'02/23'",
        })
        void dateValue_returnsDateLiteral(String written, String expected) {
            assertThat(formatted("date", written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("12-hour, 24-hour and second-less times all render as HH:mm:ss")
        @CsvSource({
            "am 09:53:06,   TIME'09:53:06'",
            "09:53:06 am,   TIME'09:53:06'",
            "19:53:06,      TIME'19:53:06'",
            // Without seconds the field is zero-filled rather than dropped.
            "am 09:53,      TIME'09:53:00'",
            "09:53 am,      TIME'09:53:00'",
            "19:53,         TIME'19:53:00'",
            "TIME'19:53:00', TIME'19:53:00'",
        })
        void timeValue_returnsTimeLiteral(String written, String expected) {
            assertThat(formatted("time", written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a bare number is epoch millis, rendered through the default time zone")
        void epochMillis_returnsTimeInDefaultZone() {
            assertThat(formatted("time", "1245")).isEqualTo("TIME'08:00:01'");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("a timestamp takes its date and time fields in either order")
        @CsvSource({
            "2009/02/23 am 09:53:08,    TIMESTAMP'02/23/2009 09:53:08'",
            "2009-02-23 am 09:53:08,    TIMESTAMP'02/23/2009 09:53:08'",
            "2009/02/23 09:53:08,       TIMESTAMP'02/23/2009 09:53:08'",
            "2009-02-23 09:53:08,       TIMESTAMP'02/23/2009 09:53:08'",
            "09:53:08 am 02/23/2009,    TIMESTAMP'02/23/2009 09:53:08'",
            "09:53:08 02/23/2009,       TIMESTAMP'02/23/2009 09:53:08'",
            // Second-less forms zero-fill, as for a plain time.
            "2009/02/23 am 09:53,       TIMESTAMP'02/23/2009 09:53:00'",
            "2009-02-23 09:53,          TIMESTAMP'02/23/2009 09:53:00'",
        })
        void timestampValue_returnsTimestampLiteral(String written, String expected) {
            assertThat(formatted("timestamp", written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("a datetime carries milliseconds, right-padded to three digits")
        @CsvSource({
            "2009/02/23 am 09:53:08.333,    DATETIME'2009-02-23 09:53:08.333'",
            "2009-02-23 am 09:53:08.333,    DATETIME'2009-02-23 09:53:08.333'",
            "2009/02/23 09:53:08.333,       DATETIME'2009-02-23 09:53:08.333'",
            "09:53:08.333 am 02/23/2009,    DATETIME'2009-02-23 09:53:08.333'",
            // One fractional digit means tenths, not units: .3 is 300 ms.
            "2009/02/23 am 09:53:08.3,      DATETIME'2009-02-23 09:53:08.300'",
        })
        void datetimeValue_returnsDatetimeLiteral(String written, String expected) {
            assertThat(formatted("datetime", written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> null")
        @DisplayName("an unterminated or misspelled literal is rejected, not repaired")
        @CsvSource({
            // The opening keyword is there but the closing quote is not.
            "date,  date'",
            "time,  TIME'19:53:00",
            // A keyword one character short of DATE never enters the literal branch.
            "date,  DAT02/23",
        })
        void malformedLiteral_returnsNull(String type, String written) {
            assertThat(formatted(type, written)).isNull();
        }

        @ParameterizedTest(name = "[{index}] {0} \"\" -> null")
        @DisplayName("a temporal column with no default gets none")
        @ValueSource(strings = {"date", "time", "timestamp", "datetime"})
        void emptyValue_returnsNull(String type) {
            assertThat(formatted(type, "")).isNull();
        }
    }

    @Nested
    @DisplayName("string literals")
    class StringLiterals {

        // quoteCharacter is " because every cell here is about the single quote itself.
        @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> {2}")
        @DisplayName("a string default is quoted once and its own quotes are doubled")
        @CsvSource(
                quoteCharacter = '"',
                value = {
                    // Already quoted: left as it stands.
                    "char,     'abc',     'abc'",
                    "char,     'ab''c',   'ab''c'",
                    "varchar,  'abc',     'abc'",
                    // Unquoted: wrapped, and trailing blanks are significant.
                    "char,     abc,       'abc'",
                    "char,     'abc ',    'abc '",
                    "varchar,  abc,       'abc'",
                    // An embedded quote is doubled so the literal stays closed.
                    "char,     ab'c,      'ab''c'",
                    "varchar,  ab'c,      'ab''c'",
                })
        void stringValue_returnsQuotedLiteral(String type, String written, String expected) {
            assertThat(formatted(type, written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} \"\" -> ''")
        @DisplayName("an empty string default is the empty literal, not no default")
        @ValueSource(strings = {"char", "varchar"})
        void emptyValue_returnsEmptyLiteral(String type) {
            assertThat(formatted(type, "")).isEqualTo("''");
        }
    }

    @Nested
    @DisplayName("bit literals")
    class BitLiterals {

        @ParameterizedTest(name = "[{index}] {0} \"{1}\" -> {2}")
        @DisplayName("an unprefixed value is read as hexadecimal")
        @CsvSource({
            // Both CUBRID prefixes are kept as written.
            "bit,              B'001',    B'001'",
            "bit,              X'001',    X'001'",
            "bit varying,      B'001',    B'001'",
            "bit varying,      X'001',    X'001'",
            // No prefix: X, not B.
            "bit,              001,       X'001'",
            "bit varying,      001,       X'001'",
        })
        void bitValue_returnsBitLiteral(String type, String written, String expected) {
            assertThat(formatted(type, written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} \"\" -> B'0'")
        @DisplayName("an empty bit default is a single zero bit")
        @ValueSource(strings = {"bit", "bit varying"})
        void emptyValue_returnsZeroBit(String type) {
            assertThat(formatted(type, "")).isEqualTo("B'0'");
        }
    }

    @Nested
    @DisplayName("collection literals")
    class CollectionLiterals {

        private String formattedCollection(String type, String elementType, String value) {
            DataTypeInstance dti = columnOf(type);
            dti.setSubType(columnOf(elementType));
            return CUBRIDFormator.format(dti, value).getFormatResult();
        }

        @Test
        @DisplayName("every element of a set is formatted as its own element type")
        void setOfTimestamp_formatsEachElement() {
            assertThat(
                            formattedCollection(
                                    "set_of",
                                    "timestamp",
                                    "2009/02/23 am 09:53:08,2009-02-23 am 09:53:09,"
                                            + "2009/02/23 09:53:10"))
                    .isEqualTo(
                            "{TIMESTAMP'02/23/2009 09:53:08',TIMESTAMP'02/23/2009 09:53:09',"
                                    + "TIMESTAMP'02/23/2009 09:53:10'}");
        }

        @Test
        @DisplayName("a single value still gets collection braces")
        void multisetOfOneValue_isStillBraced() {
            assertThat(formattedCollection("multiset_of", "numeric", "10")).isEqualTo("{10}");
        }

        @Test
        @DisplayName("getCollectionValues splits on commas and converts each element to its type")
        void getCollectionValues_returnsTypedElements() throws ParseException {
            DataTypeInstance dti = columnOf("set");
            dti.setSubType(columnOf("numeric"));

            // Not the source text: a scale-0 numeric element arrives as a Long.
            assertThat(CUBRIDFormator.getCollectionValues(dti, "10,11,12"))
                    .containsExactly(10L, 11L, 12L);
        }
    }

    @Nested
    @DisplayName("values that are not defaults")
    class RejectedValues {

        @Test
        @DisplayName("a numeric default keeps its own text")
        void numericValue_isPassedThrough() {
            assertThat(formatted("numeric", "10")).isEqualTo("10");
        }

        @Test
        @DisplayName("a numeric column rejects a value that is not a number")
        void numericValue_thatIsNotANumber_returnsNull() {
            assertThat(formatted("numeric", "aa")).isNull();
        }

        @Test
        @DisplayName("a literal whose keyword is misspelled is rejected")
        void misspelledLiteralKeyword_returnsNull() {
            assertThat(formatted("datetime", "DATETIM2'2009-02-23 09:53:08.300'")).isNull();
        }
    }
}
