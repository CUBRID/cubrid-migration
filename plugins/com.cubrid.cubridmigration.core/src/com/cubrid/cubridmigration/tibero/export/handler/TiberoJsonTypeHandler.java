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
package com.cubrid.cubridmigration.tibero.export.handler;

import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.export.handler.ClobTypeHandler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;

/** Reads Tibero JSON values as String for stable downstream conversion. */
public class TiberoJsonTypeHandler extends ClobTypeHandler {

    /**
     * Retrieves the value object of JSON column.
     *
     * @param rs the result set
     * @param column column description
     * @return value of column
     * @throws SQLException e
     */
    @Override
    public Object getJdbcObject(ResultSet rs, Column column) throws SQLException {
        final String colName = column.getName();
        try {
            Object value = rs.getObject(colName);
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                return value;
            }
            if (value instanceof SQLXML) {
                return getStringFromSQLXML((SQLXML) value);
            }
            if (value instanceof Clob) {
                return getCharObject(((Clob) value).getCharacterStream());
            }
            if (value instanceof Blob) {
                return getStringFromBlob((Blob) value);
            }
            if (value instanceof byte[]) {
                return new String((byte[]) value, StandardCharsets.UTF_8);
            }
            if (value instanceof InputStream) {
                return getStringFromBinaryStream((InputStream) value);
            }
            return rs.getString(colName);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to read Tibero JSON value: " + colName, e);
        }
    }

    private String getStringFromSQLXML(SQLXML sqlxml) throws SQLException {
        try {
            return sqlxml.getString();
        } finally {
            try {
                sqlxml.free();
            } catch (Exception ignored) {
                // ignore SQLXML cleanup failure
            }
        }
    }

    private String getStringFromBlob(Blob blob) throws SQLException {
        try {
            return getStringFromBinaryStream(blob.getBinaryStream());
        } finally {
            try {
                blob.free();
            } catch (Exception ignored) {
                // ignore BLOB cleanup failure
            }
        }
    }

    private String getStringFromBinaryStream(InputStream inputStream) throws SQLException {
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int len = inputStream.read(buf);
            while (len != -1) {
                out.write(buf, 0, len);
                len = inputStream.read(buf);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SQLException("Failed to decode Tibero JSON binary value as UTF-8", e);
        } finally {
            Closer.close(inputStream);
            Closer.close(out);
        }
    }
}
