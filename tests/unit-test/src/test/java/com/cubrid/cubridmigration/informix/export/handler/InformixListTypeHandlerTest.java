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
package com.cubrid.cubridmigration.informix.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("InformixListTypeHandler")
class InformixListTypeHandlerTest {

    private static final InformixListTypeHandler HANDLER = new InformixListTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    @Test
    @DisplayName("a list's square brackets are swapped for CUBRID's braces")
    void bracketedValue_isRewrittenWithBraces() throws SQLException {
        when(rs.getObject("C1")).thenReturn("[1, 2, 3]");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{1, 2, 3}");
    }

    @Test
    @DisplayName("an empty list -> \"{}\"")
    void emptyValue_becomesEmptyBraces() throws SQLException {
        when(rs.getObject("C1")).thenReturn("[]");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{}");
    }

    @Test
    @DisplayName("SQL NULL -> null")
    void sqlNull_returnsNull() throws SQLException {
        when(rs.getObject("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    // DEFECT: the first and last characters are cut off without checking that they are brackets or
    // that the text is long enough, so any value shorter than two characters aborts the row
    // - see InformixListTypeHandler.getJdbcObject()
    @Test
    @DisplayName("a value shorter than two characters -> StringIndexOutOfBoundsException")
    void tooShortValue_throwsStringIndexOutOfBounds() throws SQLException {
        when(rs.getObject("C1")).thenReturn("x");

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
    }
}
