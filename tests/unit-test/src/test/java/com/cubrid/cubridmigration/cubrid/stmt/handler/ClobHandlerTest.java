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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

@DisplayName("ClobHandler")
class ClobHandlerTest {

    private static final ClobHandler HANDLER = new ClobHandler("euc-kr", "utf-8");

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

    // CMT loads the CUBRID driver at runtime, so the reflective lookup below always fails on the
    // unit-test classpath and only the failure path is reachable here. E2E covers the rest.

    @Test
    @DisplayName("text -> NormalMigrationException wrapping the missing driver class")
    void text_failsWithoutTheDriver() {
        assertThatThrownBy(() -> HANDLER.handle(stmt, 0, valueOf("hello")))
                .isInstanceOf(NormalMigrationException.class)
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("empty string -> NULL, short-circuited before the driver is needed")
    void emptyString_writesNullWithoutTheDriver() throws SQLException {
        HANDLER.handle(stmt, 0, valueOf(""));

        verify(stmt).setNull(1, Types.NULL);
    }

    // The source charset decodes an incoming InputStream and is read before the driver lookup, so
    // an unusable charset name fails earlier and with a different cause than a missing driver.
    // That ordering is what distinguishes the two constructor arguments from outside the class.
    @Test
    @DisplayName(
            "InputStream with an unknown source charset -> fails on the charset, not the driver")
    void unknownSourceCharset_failsOnTheCharset() {
        ClobHandler handler = new ClobHandler("no-such-charset", "utf-8");

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        stmt, 0, valueOf(new ByteArrayInputStream(new byte[] {1}))))
                .isInstanceOf(NormalMigrationException.class)
                .hasCauseInstanceOf(UnsupportedEncodingException.class);
    }
}
