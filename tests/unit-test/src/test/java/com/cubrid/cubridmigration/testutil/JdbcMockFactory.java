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
package com.cubrid.cubridmigration.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Wires the JDBC mocks a schema fetcher walks. A fetcher reaches its rows one of two ways - through
 * the driver's own catalog methods on {@link DatabaseMetaData}, or by running a dialect query - and
 * both are three mocks deep before a single value can be stubbed. These helpers set up that chain
 * so a test only spells out the rows it cares about.
 */
public final class JdbcMockFactory {

    private JdbcMockFactory() {}

    /** A connection whose getMetaData() answers the returned mock. */
    public static DatabaseMetaData attachMetaData(Connection conn) throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(conn.getMetaData()).thenReturn(metaData);
        return metaData;
    }

    /** A result set that yields {@code rowCount} rows and then stops. */
    public static ResultSet resultSetOf(int rowCount) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        if (rowCount <= 0) {
            when(rs.next()).thenReturn(false);
            return rs;
        }
        Boolean[] rest = new Boolean[rowCount];
        for (int i = 0; i < rowCount - 1; i++) {
            rest[i] = Boolean.TRUE;
        }
        rest[rowCount - 1] = Boolean.FALSE;
        when(rs.next()).thenReturn(Boolean.TRUE, rest);
        return rs;
    }

    /** Connection -> prepareStatement() -> executeQuery(), answering a result set of that size. */
    public static ResultSet attachPreparedQuery(Connection conn, int rowCount) throws SQLException {
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = resultSetOf(rowCount);
        when(conn.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        return rs;
    }

    /** Connection -> createStatement() -> executeQuery(), answering a result set of that size. */
    public static ResultSet attachStatementQuery(Connection conn, int rowCount)
            throws SQLException {
        Statement stmt = mock(Statement.class);
        ResultSet rs = resultSetOf(rowCount);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(rs);
        return rs;
    }
}
