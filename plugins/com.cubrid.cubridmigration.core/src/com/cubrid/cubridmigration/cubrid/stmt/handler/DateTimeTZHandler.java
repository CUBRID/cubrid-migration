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
package com.cubrid.cubridmigration.cubrid.stmt.handler;

import com.cubrid.cubridmigration.core.common.TimeZoneConverterUtils;
import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.TimeZone;

public class DateTimeTZHandler extends DefaultHandler {

    private final TimeZone sourceTimeZone;

    public DateTimeTZHandler(TimeZone sourceTimeZone) {
        this.sourceTimeZone = sourceTimeZone;
    }

    public void handle(PreparedStatement stmt, int idx, ColumnValue columnValue)
            throws SQLException {
        Object value = columnValue.getValue();
        String columnName = columnValue.getColumn().getName();

        if (isNullOrEmpty(value)) {
            stmt.setNull(idx + 1, Types.NULL);
            return;
        }

        if (value instanceof String && shouldPassThrough((String) value)) {
            stmt.setString(idx + 1, (String) value);
            return;
        }

        try {
            bindOffsetDateTime(stmt, idx, value);
        } catch (IllegalArgumentException ex) {
            handleIllegalArgument(stmt, idx, columnName, value, ex);
        }
    }

    private void bindOffsetDateTime(PreparedStatement stmt, int idx, Object value)
            throws SQLException {
        OffsetDateTime odt =
                value instanceof OffsetDateTime
                        ? (OffsetDateTime) value
                        : TimeZoneConverterUtils.parseToOffsetDateTime(value, sourceTimeZone);
        if (odt == null) {
            stmt.setNull(idx + 1, Types.NULL);
            return;
        }
        stmt.setString(idx + 1, TimeZoneConverterUtils.formatWithOffset(odt));
    }

    private void handleIllegalArgument(
            PreparedStatement stmt, int idx, String columnName, Object value, Exception ex)
            throws SQLException {
        String valueStr = value == null ? null : value.toString();
        if (isZeroDatePattern(valueStr)) {
            stmt.setString(idx + 1, valueStr);
            return;
        }
        throw new SQLException("Failed to bind DATETIMETZ value for column " + columnName, ex);
    }

    private boolean shouldPassThrough(String valueStr) {
        boolean hasZoneId =
                valueStr.matches(".*\\s+[A-Za-z][A-Za-z0-9_/]+(?:\\s+[A-Z]{2,4})?\\s*$");
        boolean endsWithOffset = valueStr.matches(".*[+-]\\d{2}:\\d{2}\\s*$");
        return hasZoneId && !endsWithOffset;
    }

    private boolean isNullOrEmpty(Object value) {
        return value == null || "".equals(value);
    }

    private boolean isZeroDatePattern(String value) {
        if (value == null) {
            return false;
        }
        return value.matches(".*0{2,4}[/-]0{1,2}[/-]0{2,4}.*");
    }
}
