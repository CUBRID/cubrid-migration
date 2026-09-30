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
package com.cubrid.cubridmigration.core.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("IntTypeHandler")
class IntTypeHandlerTest {

    private static final IntTypeHandler HANDLER = new IntTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    @Test
    @DisplayName("integer value -> the value")
    void integerValue_returnsValue() throws SQLException {
        when(rs.getObject("C1")).thenReturn(7);
        when(rs.getInt("C1")).thenReturn(7);

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(7);
    }

    // getInt() answers 0 for SQL NULL, so the getObject() probe in front of it is what keeps a
    // NULL from migrating as a zero.
    @Test
    @DisplayName("SQL NULL -> null, not the 0 that getInt() would return")
    void sqlNull_returnsNullNotZero() throws SQLException {
        when(rs.getObject("C1")).thenReturn(null);
        when(rs.getInt("C1")).thenReturn(0);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }
}
