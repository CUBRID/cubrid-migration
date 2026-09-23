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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

@DisplayName("DoubleHandler")
class DoubleHandlerTest {

    private static final DoubleHandler HANDLER = new DoubleHandler();

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

    @Test
    @DisplayName("1.5 -> setDouble")
    void doubleValue_setsDouble() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf(1.5d));

        verify(stmt).setDouble(1, 1.5d);
    }

    @Test
    @DisplayName("empty string -> NULL")
    void emptyString_writesNull() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf(""));

        verify(stmt).setNull(1, Types.NULL);
    }

    // The emptiness guard calls value.toString() without a null check, so a null reaches it as an
    // NPE. CUBRIDParameterSetter routes null to setNull() before handle() is ever called, so this
    // only fires when the handler is driven directly.
    @Test
    @DisplayName("null -> NullPointerException, the guard has no null check")
    void nullValue_throwsNullPointerException() {
        Assertions.assertThatThrownBy(() -> HANDLER.handle(stmt, 0, valueOf(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("number as text -> ClassCastException, the value is cast to Double")
    void numberText_throwsClassCastException() {
        Assertions.assertThatThrownBy(() -> HANDLER.handle(stmt, 0, valueOf("1.5")))
                .isInstanceOf(ClassCastException.class);
    }
}
