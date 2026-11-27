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
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("MariaDBTimestampTypeHandler")
class MariaDBTimestampTypeHandlerTest {

    private static final MariaDBTimestampTypeHandler HANDLER = new MariaDBTimestampTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    // The substitute lives in a static map the whole JVM shares, so whatever it held before this
    // class ran is what has to go back.
    private String originalSubstitute;

    @BeforeEach
    void rememberMigrationParameter() {
        originalSubstitute =
                MySQL2CUBRIDMigParas.getMigrationParamter(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
    }

    @AfterEach
    void restoreMigrationParameter() {
        MySQL2CUBRIDMigParas.putMigrationParamter(
                MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP, originalSubstitute);
    }

    // MariaDB reads the column as text and converts it here, rather than letting the driver
    // produce the JDBC type, which is what lets the zero value below be caught.
    @Test
    @DisplayName("a valid value -> the parsed Timestamp")
    void validText_isParsed() throws SQLException {
        when(rs.getString("C1")).thenReturn("2024-01-15 10:20:30");

        assertThat(HANDLER.getJdbcObject(rs, column))
                .isEqualTo(java.sql.Timestamp.valueOf("2024-01-15 10:20:30"));
    }

    @Test
    @DisplayName("MariaDB's zero value -> the configured substitute")
    void zeroValue_fallsBackToTheMigrationParameter() throws SQLException {
        when(rs.getString("C1")).thenReturn("0000-00-00 00:00:00");

        assertThat(HANDLER.getJdbcObject(rs, column)).hasToString("1970-01-02 01:00:00.0");
    }

    @Test
    @DisplayName("SQL NULL -> the configured substitute, not null")
    void sqlNull_alsoFallsBack() throws SQLException {
        when(rs.getString("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).hasToString("1970-01-02 01:00:00.0");
    }
}
