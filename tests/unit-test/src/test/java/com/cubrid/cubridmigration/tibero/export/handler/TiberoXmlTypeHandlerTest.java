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
package com.cubrid.cubridmigration.tibero.export.handler;

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

@DisplayName("TiberoXmlTypeHandler")
public class TiberoXmlTypeHandlerTest {

    private static final TiberoXmlTypeHandler HANDLER = new TiberoXmlTypeHandler();

    private ResultSet rs = mock(ResultSet.class);
    private Column column = new Column();

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("XML_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() → \"XMLTYPE\"")
    void getTypeNameForError_returnsXmlType() {
        assertThat(HANDLER.getTypeNameForError()).isEqualTo("XMLTYPE");
    }

    @Test
    @DisplayName("String value -> return as is")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn("<root><id>1</id></root>");

        Object result = HANDLER.getJdbcObject(rs, column);

        assertThat(result).isEqualTo("<root><id>1</id></root>");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("other object -> delegate to rs.getString()")
    void otherObject_delegatesToGetString() throws SQLException {
        when(rs.getObject("XML_COL")).thenReturn(new Object());
        when(rs.getString("XML_COL")).thenReturn("<data/>");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("<data/>");
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new SQLException("xml read error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("xml read error");
    }

    @Test
    @DisplayName("non-SQL exception -> wrap to SQLException")
    void runtimeException_wrappedWithTypeName() throws SQLException {
        when(rs.getObject("XML_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("XML_COL");
    }
}
