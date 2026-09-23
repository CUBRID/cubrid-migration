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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("InformixCustomTypeHandler")
class InformixCustomTypeHandlerTest {

    private static final InformixCustomTypeHandler HANDLER = new InformixCustomTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    /** Stands in for an Informix user-defined type, which exposes getAttributes() by name only. */
    public static class FakeUdt {
        public Object[] getAttributes() {
            return new Object[] {1, "two", null};
        }
    }

    @Test
    @DisplayName("a user-defined type -> its attributes rendered as a list")
    void userDefinedType_returnsAttributesAsText() throws SQLException {
        when(rs.getObject("C1")).thenReturn(new FakeUdt());

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("[1, two, null]");
    }

    // DEFECT: a SQL NULL reaches o.getClass() and raises, and the catch only prints the stack
    // trace, so the column silently migrates as NULL with the failure left on stderr
    // - see InformixCustomTypeHandler.getJdbcObject()
    @Test
    @DisplayName("SQL NULL -> null, after an NPE that is printed and dropped")
    void sqlNull_returnsNullAfterSwallowedError() throws SQLException {
        when(rs.getObject("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("an object without getAttributes() -> null")
    void objectWithoutTheMethod_returnsNull() throws SQLException {
        when(rs.getObject("C1")).thenReturn("plain string");

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }
}
