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
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
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
package com.cubrid.cubridmigration.tibero.export;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.event.LobMigrationErrorEvent;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.core.export.IExportDataHandler;
import com.cubrid.cubridmigration.core.export.handler.CharTypeHandler;
import com.cubrid.cubridmigration.core.export.handler.TimestampTypeHandler;
import com.cubrid.cubridmigration.tibero.TiberoDataTypeHelper;
import com.cubrid.cubridmigration.tibero.export.handler.TiberoIntervalDSTypeHandler;
import com.cubrid.cubridmigration.tibero.export.handler.TiberoIntervalYMTypeHandler;

import org.slf4j.Logger;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** a class help to export Tibero data and verify Tibero sql statement */
public class TiberoExportHelper extends DBExportHelper {
    private static final Logger LOG = LogUtil.getLogger(TiberoExportHelper.class);

    /** constructor */
    public TiberoExportHelper() {
        super();
        handlerMap1.put(Types.DATE, new TimestampTypeHandler());
        handlerMap2.put("INTERVALDS", new TiberoIntervalDSTypeHandler());
        handlerMap2.put("INTERVALYM", new TiberoIntervalYMTypeHandler());
        handlerMap2.put("TIMESTAMP WITH LOCAL TIME ZONE", new TimestampTypeHandler());
        handlerMap2.put("TIMESTAMP WITH TIME ZONE", new CharTypeHandler());
    }

    /**
     * get JDBC Object
     *
     * @param rs ResultSet
     * @param column Column
     * @return Object
     * @throws SQLException e
     */
    public Object getJdbcObject(final ResultSet rs, final Column column) throws SQLException {
        String tibType = TiberoDataTypeHelper.getTiberoDataTypeKey(column.getDataType());
        IExportDataHandler edh = handlerMap2.get(tibType);
        try {
            if (edh != null) {
                return edh.getJdbcObject(rs, column);
            }
            return super.getJdbcObject(rs, column);
        } catch (SQLException e) {
            if (column.getDataType().equalsIgnoreCase("BLOB")
                    || column.getDataType().equalsIgnoreCase("CLOB")) {
                return new LobMigrationErrorEvent(e);
            }
            throw e;
        }
    }

    /**
     * return database object name
     *
     * @param objectName String
     * @return String
     */
    protected String getQuotedObjName(String objectName) {
        return DatabaseType.TIBERO.getSQLHelper(null).getQuotedObjName(objectName);
    }

    /**
     * Retrieves the sql with page condition
     *
     * @param sql to be change
     * @param rows per-page
     * @param exportedRecords start position
     * @param pk table's primary key
     * @return SQL
     */
    public String getPagedSelectSQL(String sql, long rows, long exportedRecords, PK pk) {
        return sql;
    }

    /**
     * Retrieves the Database type.
     *
     * @return DatabaseType
     */
    public DatabaseType getDBType() {
        return DatabaseType.TIBERO;
    }

    private static final String SEQUENCE_LAST_NUMBER_SQL =
            "SELECT S.LAST_NUMBER,S.SEQUENCE_OWNER FROM ALL_SEQUENCES S "
                    + "WHERE S.SEQUENCE_NAME=? ORDER BY S.SEQUENCE_OWNER";

    /**
     * Retrieves the current value of input serial.
     *
     * @param sourceConParams JDBC connection configuration
     * @param sq sequence to be synchronized.
     * @return The current value of the input SQ
     */
    public BigInteger getSerialStartValue(ConnParameters sourceConParams, SourceSequenceConfig sq) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = sourceConParams.createConnection();
            stmt = conn.prepareStatement(SEQUENCE_LAST_NUMBER_SQL);
            stmt.setString(1, sq.getName());
            rs = stmt.executeQuery();
            while (rs.next()) {
                if (sq.getOwner() == null) {
                    return new BigInteger(rs.getString(1));
                }
                if (rs.getString(2).equalsIgnoreCase(sq.getOwner())) {
                    return new BigInteger(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            LOG.error("TIBERO_SEQUENCE_LAST_NUMBER_SQL", e);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
            Closer.close(conn);
        }
        return null;
    }
}
