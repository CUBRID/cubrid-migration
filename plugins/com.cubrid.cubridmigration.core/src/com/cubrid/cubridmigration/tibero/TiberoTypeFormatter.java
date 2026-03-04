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
package com.cubrid.cubridmigration.tibero;

import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.dbobject.Column;

/**
 * TiberoTypeFormatter is responsible for formatting Tibero data types into strings suitable for UI
 * display and DDL generation.
 */
public final class TiberoTypeFormatter {

    private TiberoTypeFormatter() {
        // Prevent instantiation
    }

    /**
     * Generate shown data type via JDBC meta data information.
     *
     * @param column Column
     * @param helper DBDataTypeHelper to determine string/nstring types
     * @return Formatted string representation of the data type
     */
    public static String format(Column column, DBDataTypeHelper helper) {
        String colType = column.getDataType();
        if (colType == null) {
            return "";
        }
        Integer precision = column.getPrecision();
        Integer scale = column.getScale();

        // 1. String-like types that need manual precision attachment
        if (helper.isString(colType) || helper.isNString(colType) || "RAW".equals(colType)) {
            if (precision == null || precision <= 0) return colType;
            String suffix = "C".equals(column.getCharUsed()) ? " CHAR)" : ")";
            return colType + "(" + precision + suffix;
        }

        // 2. NUMBER type which needs special formatting logic
        if ("NUMBER".equals(colType)) {
            return formatNumber(precision, scale);
        }

        // 3. Other types (TIMESTAMP, INTERVAL, LOBs, etc.)
        return colType;
    }

    /**
     * Format the NUMBER type.
     *
     * @param precision Integer
     * @param scale Integer
     * @return Formatted NUMBER string
     */
    private static String formatNumber(Integer precision, Integer scale) {
        if (precision == null || precision == 0) {
            return "NUMBER";
        }
        if (scale == null) {
            return "NUMBER(" + precision + ")";
        }
        return "NUMBER(" + precision + "," + scale + ")";
    }
}
