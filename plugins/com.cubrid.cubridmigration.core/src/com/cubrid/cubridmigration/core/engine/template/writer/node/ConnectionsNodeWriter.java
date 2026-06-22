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
package com.cubrid.cubridmigration.core.engine.template.writer.node;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.getBooleanString;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbtype.DBConstant;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class ConnectionsNodeWriter {

    public void write(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        ConnParameters scp = config.getSourceConParams();
        ConnParameters tcp = config.getTargetConParams();

        if (scp == null && tcp == null) {
            return;
        }

        writer.writeStartElement(TAG_CONNECTIONS);
        if (scp != null) {
            writer.writeEmptyElement(TAG_CONNECTION);
            writer.writeAttribute(ATTR_ID, "source");
            sourceConnectionAttributes(writer, scp, config);
        }

        if (tcp != null) {
            writer.writeEmptyElement(TAG_CONNECTION);
            writer.writeAttribute(ATTR_ID, "target");
            targetConnectionAttributes(writer, tcp, config);
        }
        writer.writeEndElement(); // </connections>
    }

    private void sourceConnectionAttributes(
            XMLStreamWriter writer, ConnParameters scp, MigrationConfiguration config)
            throws XMLStreamException {
        writeCommonAttributes(writer, scp);
        writer.writeAttribute(ATTR_DB_TYPE, config.getSourceTypeName());
    }

    private void targetConnectionAttributes(
            XMLStreamWriter writer, ConnParameters tcp, MigrationConfiguration config)
            throws XMLStreamException {
        writeCommonAttributes(writer, tcp);
        writer.writeAttribute(
                ATTR_CREATE_CONSTRAINT_NOW,
                getBooleanString(config.isCreateConstrainsBeforeData()));
        writer.writeAttribute(
                ATTR_WRITE_ERROR_RECORDS, getBooleanString(config.isWriteErrorRecords()));
        writer.writeAttribute(ATTR_ADD_SCHEMA, getBooleanString(config.isAddUserSchema()));
    }

    private void writeCommonAttributes(XMLStreamWriter writer, ConnParameters cp)
            throws XMLStreamException {
        writer.writeAttribute(ATTR_HOST, cp.getHost());
        writer.writeAttribute(ATTR_PORT, String.valueOf(cp.getPort()));
        writer.writeAttribute(ATTR_DRIVER, cp.getDriverFileName());
        writer.writeAttribute(ATTR_NAME, cp.getDbName());
        writer.writeAttribute(ATTR_USER, cp.getConUser());
        writer.writeAttribute(ATTR_PASSWORD, cp.getConPassword());
        writer.writeAttribute(ATTR_CHARSET, cp.getCharset());
        writer.writeAttribute(ATTR_TIMEZONE, cp.getTimeZone());
        writer.writeAttribute(ATTR_USER_JDBC_URL, cp.getUserJDBCURL());
        if (cp.getDbType() == DBConstant.DBTYPE_INFORMIX) {
            writer.writeAttribute(ATTR_CON_SERVER, cp.getConServer());
        }
    }
}
