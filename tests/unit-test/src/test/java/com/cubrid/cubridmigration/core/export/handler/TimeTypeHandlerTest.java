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
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("TimeTypeHandler")
class TimeTypeHandlerTest {

    private static final TimeTypeHandler HANDLER = new TimeTypeHandler();

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
                MySQL2CUBRIDMigParas.getMigrationParamter(MySQL2CUBRIDMigParas.UNPARSED_TIME);
    }

    @AfterEach
    void restoreMigrationParameter() {
        MySQL2CUBRIDMigParas.putMigrationParamter(
                MySQL2CUBRIDMigParas.UNPARSED_TIME, originalSubstitute);
    }

    @Test
    @DisplayName("a readable value -> what the driver returned")
    void readableValue_isReturnedAsIs() throws SQLException {
        when(rs.getTime("C1")).thenReturn(java.sql.Time.valueOf("10:20:30"));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(java.sql.Time.valueOf("10:20:30"));
    }

    // MySQL accepts values such as "0000-00-00" that the driver cannot turn into a JDBC type, and
    // it raises rather than returning null. The substitute comes from the migration parameters,
    // so a value the source could not express arrives in the target as a fixed stand-in.
    @Test
    @DisplayName("an unreadable value -> the configured substitute, not an error")
    void unreadableValue_fallsBackToTheMigrationParameter() throws SQLException {
        when(rs.getTime("C1")).thenThrow(new RuntimeException("cannot parse"));

        assertThat(HANDLER.getJdbcObject(rs, column)).hasToString("00:00:00");
    }

    @Test
    @DisplayName("the substitute is read from the migration parameters each time, not cached")
    void substitute_followsTheMigrationParameter() throws SQLException {
        MySQL2CUBRIDMigParas.putMigrationParamter(MySQL2CUBRIDMigParas.UNPARSED_TIME, "12:34:56");
        when(rs.getTime("C1")).thenThrow(new RuntimeException("cannot parse"));

        assertThat(HANDLER.getJdbcObject(rs, column)).hasToString("12:34:56");
    }

    @Test
    @DisplayName("an unparsable substitute -> null")
    void unparsableSubstitute_returnsNull() throws SQLException {
        MySQL2CUBRIDMigParas.putMigrationParamter(
                MySQL2CUBRIDMigParas.UNPARSED_TIME, "not a value");
        when(rs.getTime("C1")).thenThrow(new RuntimeException("cannot parse"));

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }
}
