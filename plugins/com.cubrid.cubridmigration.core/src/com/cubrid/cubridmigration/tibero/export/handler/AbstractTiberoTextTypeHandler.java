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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;

/** Base handler for Tibero text-oriented source types. */
public abstract class AbstractTiberoTextTypeHandler extends ClobTypeHandler {

    protected abstract String getTypeNameForError();

    protected Object readTextValue(ResultSet rs, Column column) throws SQLException {
        final String colName = column.getName();
        try {
            return readTextValue(rs.getObject(colName), rs, colName);
        } catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException(buildReadErrorMessage(colName), e);
        }
    }

    protected Object readTextValue(Object value, ResultSet rs, String colName) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return value;
        }
        if (value instanceof SQLXML) {
            return readFromSqlXml((SQLXML) value);
        }
        if (value instanceof Clob) {
            return getCharObject(((Clob) value).getCharacterStream());
        }
        return rs.getString(colName);
    }

    protected String readFromSqlXml(SQLXML sqlxml) throws SQLException {
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

    protected String readFromBlob(Blob blob, ResultSet rs, String colName) throws SQLException {
        if (blob == null) {
            return null;
        }
        try {
            String value = tryGetString(rs, colName);
            if (value != null) {
                return value;
            }
            return readFromInputStream(blob.getBinaryStream());
        } finally {
            try {
                blob.free();
            } catch (Exception ignored) {
                // ignore BLOB cleanup failure
            }
        }
    }

    protected String readFromInputStream(InputStream inputStream) throws SQLException {
        if (inputStream == null) {
            return null;
        }
        ByteArrayOutputStream out = null;
        try {
            out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int len = inputStream.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = inputStream.read(buffer);
            }
            return decodeBinary(out.toByteArray());
        } catch (Exception e) {
            throw new SQLException(
                    "Failed to decode Tibero " + getTypeNameForError() + " binary value", e);
        } finally {
            Closer.close(inputStream);
        }
    }

    protected String decodeBinary(byte[] bytes) throws SQLException {
        try {
            if (bytes == null) {
                return null;
            }
            if (bytes.length == 0) {
                return "";
            }
            CharsetAndOffset detected = resolveCharsetByBom(bytes);
            return new String(
                    bytes, detected.offset, bytes.length - detected.offset, detected.charset);
        } catch (Exception e) {
            throw new SQLException(
                    "Failed to decode Tibero " + getTypeNameForError() + " binary value", e);
        }
    }

    protected String tryGetString(ResultSet rs, String colName) {
        try {
            return rs.getString(colName);
        } catch (SQLException ignored) {
            return null;
        }
    }

    protected String buildReadErrorMessage(String colName) {
        return "Failed to read Tibero " + getTypeNameForError() + " value: " + colName;
    }

    protected CharsetAndOffset resolveCharsetByBom(byte[] bytes) {
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            return new CharsetAndOffset(StandardCharsets.UTF_8, 3);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new CharsetAndOffset(StandardCharsets.UTF_16BE, 2);
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new CharsetAndOffset(StandardCharsets.UTF_16LE, 2);
        }
        return new CharsetAndOffset(StandardCharsets.UTF_8, 0);
    }

    protected static final class CharsetAndOffset {
        private final Charset charset;
        private final int offset;

        protected CharsetAndOffset(Charset charset, int offset) {
            this.charset = charset;
            this.offset = offset;
        }
    }
}
