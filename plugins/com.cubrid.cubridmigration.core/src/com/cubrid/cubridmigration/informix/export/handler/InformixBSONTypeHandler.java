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

package com.cubrid.cubridmigration.informix.export.handler;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.export.IExportDataHandler;

import org.bson.RawBsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * InformixBSONTypeHandler Description
 *
 * @author rathana
 * @version 1.0
 * @created Oct 13, 2022
 */
public class InformixBSONTypeHandler implements IExportDataHandler {

    private static final Logger LOG = LogUtil.getLogger(InformixBSONTypeHandler.class);
    private static final JsonWriterSettings JSON_SETTINGS =
            JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();

    /**
     * Retrieves the value object of BSON column.
     *
     * @param rs the result set
     * @param column column description
     * @return JSON string representation of BSON column
     * @throws SQLException e
     */
    public Object getJdbcObject(ResultSet rs, Column column) throws SQLException {
        final String colName = column.getName();
        final byte[] bytes = rs.getBytes(colName);
        if (bytes == null) return null;
        try {
            RawBsonDocument doc = new RawBsonDocument(bytes);
            return doc.toJson(JSON_SETTINGS);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to convert Informix BSON to JSON. column={}", colName, ex);
            return "";
        }
    }
}
