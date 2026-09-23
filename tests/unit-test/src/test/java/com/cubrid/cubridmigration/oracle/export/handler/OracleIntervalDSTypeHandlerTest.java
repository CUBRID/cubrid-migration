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
package com.cubrid.cubridmigration.oracle.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("OracleIntervalDSTypeHandler")
class OracleIntervalDSTypeHandlerTest {

    private static final OracleIntervalDSTypeHandler HANDLER = new OracleIntervalDSTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    /**
     * The handler looks getINTERVALDS() up on the result set's own class, so a stand-in that
     * declares it is enough to reach the success path without an Oracle driver. Abstract so Mockito
     * can subclass it.
     */
    public abstract static class FakeOracleResultSet implements ResultSet {
        public Object getINTERVALDS(String columnName) {
            return null;
        }
    }

    @Test
    @DisplayName("an interval value -> its text form")
    void intervalValue_returnsItsText() throws SQLException {
        FakeOracleResultSet oracleRs = mock(FakeOracleResultSet.class);
        when(oracleRs.getINTERVALDS("C1")).thenReturn("+01 02:03:04.000000");

        assertThat(HANDLER.getJdbcObject(oracleRs, column)).isEqualTo("+01 02:03:04.000000");
    }

    @Test
    @DisplayName("SQL NULL -> null")
    void sqlNull_returnsNull() throws SQLException {
        FakeOracleResultSet oracleRs = mock(FakeOracleResultSet.class);
        when(oracleRs.getINTERVALDS("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(oracleRs, column)).isNull();
    }

    // A result set that is not Oracle's has no such method, and the lookup failure is logged and
    // turned into NULL rather than raised.
    @Test
    @DisplayName("a result set without getINTERVALDS() -> null")
    void resultSetWithoutTheMethod_returnsNull() throws SQLException {
        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }
}
