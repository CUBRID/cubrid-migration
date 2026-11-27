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
package com.cubrid.cubridmigration.cubrid.stmt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

@DisplayName("CUBRIDParameterSetter")
class CUBRIDParameterSetterTest {

    private static final CUBRIDParameterSetter SETTER = new CUBRIDParameterSetter(config());

    private PreparedStatement stmt;

    @BeforeEach
    void setUp() {
        stmt = mock(PreparedStatement.class);
    }

    /** An online MySQL to CUBRID setup, the only shape whose charsets the constructor can read. */
    private static MigrationConfiguration config() {
        MigrationConfiguration config = new MigrationConfiguration();
        config.setSourceConParams(
                ConnParameters.getConParam(
                        "src",
                        "host",
                        3306,
                        "srcdb",
                        DatabaseType.MYSQL,
                        "euc-kr",
                        "u",
                        "p",
                        null,
                        null));
        config.setTargetConParams(
                ConnParameters.getConParam(
                        "tgt",
                        "host",
                        33000,
                        "tgtdb",
                        DatabaseType.CUBRID,
                        "utf-8",
                        "u",
                        "p",
                        null,
                        null));
        return config;
    }

    private static Record recordOf(Object... typeIdAndValuePairs) {
        Record record = new Record();
        for (int i = 0; i < typeIdAndValuePairs.length; i += 2) {
            Column column = new Column();
            column.setName("c" + (i / 2 + 1));
            column.setJdbcIDOfDataType((Integer) typeIdAndValuePairs[i]);
            record.addColumnValue(column, typeIdAndValuePairs[i + 1]);
        }
        return record;
    }

    @Test
    @DisplayName("a value is handed to the handler its data type is registered under")
    void registeredType_reachesItsHandler() throws SQLException {
        SETTER.setRecord2Statement(recordOf(DataTypeConstant.CUBRID_DT_VARCHAR, "abc"), stmt);

        verify(stmt).setString(1, "abc");
    }

    @Test
    @DisplayName("null -> setNull(), handle() is never reached")
    void nullValue_goesToSetNull() throws SQLException {
        SETTER.setRecord2Statement(recordOf(DataTypeConstant.CUBRID_DT_VARCHAR, null), stmt);

        verify(stmt).setNull(1, Types.NULL);
    }

    // The null goes to the handler registered for the type, not to a shared null path, so
    // VarBitHandler's own setNull() decides what a NULL bit varying becomes.
    @Test
    @DisplayName("null on a bit varying column -> the empty string, through VarBitHandler")
    void nullOnBitVarying_writesEmptyString() throws SQLException {
        SETTER.setRecord2Statement(recordOf(DataTypeConstant.CUBRID_DT_VARBIT, null), stmt);

        verify(stmt).setString(1, "");
    }

    @Test
    @DisplayName("a data type with no entry -> DefaultHandler, the value becomes text")
    void unregisteredType_fallsBackToDefaultHandler() throws SQLException {
        SETTER.setRecord2Statement(recordOf(Integer.MAX_VALUE, 7), stmt);

        verify(stmt).setString(1, "7");
    }

    @Test
    @DisplayName("a data type with no entry and a null value -> setNull() through DefaultHandler")
    void unregisteredTypeWithNull_writesNull() throws SQLException {
        SETTER.setRecord2Statement(recordOf(Integer.MAX_VALUE, null), stmt);

        verify(stmt).setNull(1, Types.NULL);
    }

    @Test
    @DisplayName("column n of the record -> parameter n + 1 of the statement")
    void columnOrder_mapsToParameterIndexes() throws SQLException {
        SETTER.setRecord2Statement(
                recordOf(
                        DataTypeConstant.CUBRID_DT_VARCHAR, "a",
                        DataTypeConstant.CUBRID_DT_VARCHAR, "b",
                        DataTypeConstant.CUBRID_DT_VARCHAR, "c"),
                stmt);

        verify(stmt).setString(1, "a");
        verify(stmt).setString(2, "b");
        verify(stmt).setString(3, "c");
    }

    // JDBCImporter separates a dropped connection from a bad row by exception type, so everything
    // the binding raises has to arrive as a NormalMigrationException with the original attached.
    @Test
    @DisplayName("a failing statement -> NormalMigrationException carrying the SQLException")
    void statementFailure_isWrappedInNormalMigrationException() throws SQLException {
        doThrow(new SQLException("bind failed")).when(stmt).setString(anyInt(), anyString());

        assertThatThrownBy(
                        () ->
                                SETTER.setRecord2Statement(
                                        recordOf(DataTypeConstant.CUBRID_DT_VARCHAR, "abc"), stmt))
                .isInstanceOf(NormalMigrationException.class)
                .hasCauseInstanceOf(SQLException.class);
    }
}
