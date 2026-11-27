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
package com.cubrid.cubridmigration.mariadb.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

@DisplayName("MariaDBYearTypeHandler")
@ResourceLock(Resources.LOCALE)
class MariaDBYearTypeHandlerTest {

    private static final MariaDBYearTypeHandler HANDLER = new MariaDBYearTypeHandler();

    private ResultSet rs;
    private Column column;

    // The Date branch renders through the default locale, so the tests below need a known one -
    // and the machine's own has to come back afterwards, or whatever runs next in this JVM
    // inherits ours.
    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("a two-digit year is placed either side of the pivot at 70")
    @CsvSource({
        // Below the pivot, the year is read as this century.
        "0,    2000",
        "1,    2001",
        "69,   2069",
        // From the pivot up, it is read as the previous one.
        "70,   1970",
        "99,   1999",
        // Three digits and up are already unambiguous and pass through untouched.
        "100,  100",
        "1999, 1999",
        "2024, 2024",
    })
    void twoDigitYear_isExpandedAroundThePivot(int stored, int expected) throws SQLException {
        when(rs.getObject("C1")).thenReturn(stored);

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(expected);
    }

    @Test
    @DisplayName("SQL NULL -> null")
    void sqlNull_returnsNull() throws SQLException {
        when(rs.getObject("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("a driver that hands back a Date -> its year, with no pivot applied")
    void dateValue_returnsItsYearWithoutThePivot() throws SQLException {
        when(rs.getObject("C1")).thenReturn(Date.valueOf("1987-06-05"));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(1987);
    }

    // DEFECT: a failed read is answered with getYear(0), which the pivot turns into 2000, so a
    // column that could not be read migrates as the year 2000 instead of NULL
    // - see MariaDBYearTypeHandler.getJdbcObject()
    @Test
    @DisplayName("an unreadable column -> 2000")
    void unreadableColumn_returnsYear2000() throws SQLException {
        when(rs.getObject("C1")).thenThrow(new RuntimeException("cannot read"));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(2000);
    }

    // DEFECT: the Date branch renders the year with the default locale, so a locale whose default
    // calendar is not Gregorian produces that calendar's year and migrates it verbatim. Nothing
    // rejects the result, so the wrong number reaches the target silently
    // - see MariaDBYearTypeHandler.getJdbcObject()
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("a non-Gregorian default calendar migrates its own era year for 1987")
    @CsvSource({
        // Japanese imperial: 1987 is Showa 62.
        "ja-JP-u-ca-japanese, 62",
        // Thai Buddhist: 1987 is BE 2530.
        "th-TH-u-nu-thai,     2530",
    })
    void nonGregorianLocale_returnsThatCalendarsYear(String languageTag, int expected)
            throws SQLException {
        Locale.setDefault(Locale.forLanguageTag(languageTag));
        when(rs.getObject("C1")).thenReturn(Date.valueOf("1987-06-05"));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(expected);
    }
}
