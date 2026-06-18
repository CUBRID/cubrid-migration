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
package com.cubrid.cubridmigration.core.engine.template.reader.node;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.getBoolean;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class ConnectionsNodeHandler extends DefaultHandler {

    private final MigrationConfiguration config;

    public ConnectionsNodeHandler(MigrationConfiguration config) {
        this.config = config;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (!TAG_CONNECTION.equals(qName)) {
            return;
        }

        String id = attributes.getValue(ATTR_ID);
        if ("source".equals(id)) {
            ConnParameters scp =
                    ConnParameters.getConParam(
                            null,
                            attributes.getValue(ATTR_HOST),
                            Integer.parseInt(attributes.getValue(ATTR_PORT)),
                            attributes.getValue(ATTR_NAME),
                            DatabaseType.getDatabaseTypeIDByDBName(
                                    attributes.getValue(ATTR_DB_TYPE)),
                            attributes.getValue(ATTR_CHARSET),
                            attributes.getValue(ATTR_USER),
                            attributes.getValue(ATTR_PASSWORD),
                            attributes.getValue(ATTR_DRIVER),
                            attributes.getValue(ATTR_SCHEMA));
            scp.setUserJDBCURL(attributes.getValue(ATTR_USER_JDBC_URL));
            scp.setTimeZone(attributes.getValue(ATTR_TIMEZONE));
            scp.setConServer(attributes.getValue(ATTR_CON_SERVER));
            config.setSourceConParams(scp);
        } else if ("target".equals(id)) {
            ConnParameters cp =
                    ConnParameters.getConParam(
                            null,
                            attributes.getValue(ATTR_HOST),
                            Integer.parseInt(attributes.getValue(ATTR_PORT)),
                            attributes.getValue(ATTR_NAME),
                            DatabaseType.CUBRID,
                            attributes.getValue(ATTR_CHARSET),
                            attributes.getValue(ATTR_USER),
                            attributes.getValue(ATTR_PASSWORD),
                            attributes.getValue(ATTR_DRIVER),
                            attributes.getValue(ATTR_SCHEMA));
            cp.setUserJDBCURL(attributes.getValue(ATTR_USER_JDBC_URL));
            cp.setTimeZone(attributes.getValue(ATTR_TIMEZONE));
            cp.setConServer(attributes.getValue(ATTR_CON_SERVER));

            config.setTargetConParams(cp);
            config.setCreateConstrainsBeforeData(
                    getBoolean(attributes.getValue(ATTR_CREATE_CONSTRAINT_NOW), false));
            config.setWriteErrorRecords(
                    getBoolean(attributes.getValue(ATTR_WRITE_ERROR_RECORDS), false));
            config.setAddUserSchema(getBoolean(attributes.getValue(ATTR_ADD_SCHEMA), false));
        }
    }
}
