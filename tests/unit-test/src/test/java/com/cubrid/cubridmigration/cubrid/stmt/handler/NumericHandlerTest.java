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
package com.cubrid.cubridmigration.cubrid.stmt.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

@DisplayName("NumericHandler")
@ResourceLock(Resources.LOCALE)
class NumericHandlerTest {

    private static final NumericHandler HANDLER = new NumericHandler();

    private PreparedStatement stmt;
    private Column column;

    @BeforeEach
    void setUp() {
        stmt = mock(PreparedStatement.class);
        column = new Column();
        column.setName("C1");
    }

    private ColumnValue valueOf(Object value) {
        return new ColumnValue(column, value);
    }

    // The rendering runs through the default locale, so the tests below need a known one - and
    // the machine's own has to come back afterwards, or whatever runs next in this JVM inherits
    // ours.
    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    @BeforeEach
    void pinLocale() {
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    @Test
    @DisplayName("\"12.50\" -> \"12.5\", the trailing zero is dropped")
    void trailingZero_isDropped() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf("12.50"));

        verify(stmt).setString(1, "12.5");
    }

    @Test
    @DisplayName("BigDecimal -> the same rendering as its text form")
    void bigDecimal_isRenderedLikeText() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf(new BigDecimal("12.50")));

        verify(stmt).setString(1, "12.5");
    }

    @Test
    @DisplayName("no grouping separator is inserted for large values")
    void largeValue_hasNoGroupingSeparator() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf("1234567.89"));

        verify(stmt).setString(1, "1234567.89");
    }

    @Test
    @DisplayName("empty string -> NULL")
    void emptyString_writesNull() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf(""));

        verify(stmt).setNull(1, Types.NULL);
    }

    // DEFECT: the value is rendered with a DecimalFormat built on the default locale, so on a
    // machine running under de_DE or fr_FR the decimal separator becomes a comma and "12,5" is
    // what gets bound to a CUBRID numeric column
    // - see CommonUtils.formatCUBRIDNumber(), reached from NumericHandler.handle()
    @Test
    @DisplayName("a comma-decimal locale renders \"12,5\" into the statement")
    void commaDecimalLocale_rendersCommaSeparator() throws SQLException {
        Locale.setDefault(Locale.GERMANY);

        HANDLER.handle(stmt, 0, valueOf("12.5"));

        verify(stmt).setString(1, "12,5");
    }
}
