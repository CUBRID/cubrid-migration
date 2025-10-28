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
package com.cubrid.cubridmigration.core.engine.template.reader;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.*;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.template.reader.node.SourceNodeHandler;
import com.cubrid.cubridmigration.core.engine.template.reader.node.TargetNodeHandler;
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses a migration template XML file into a {@link MigrationConfiguration} using SAX.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-9-13 created by Kevin Cao
 */
public final class MigrationTemplateHandler extends DefaultHandler {

    private final MigrationConfiguration config = new MigrationConfiguration();

    private DefaultHandler delegatingHandler;

    protected MigrationTemplateHandler() {}

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (delegatingHandler != null) {
            delegatingHandler.startElement(uri, localName, qName, attributes);
            return;
        }

        switch (qName) {
            case TAG_SOURCE:
                handleSource(attributes);
                break;
            case TAG_TARGET:
                handleTarget(attributes);
                break;
            case TAG_MIGRATION:
                handleMigration(attributes);
                break;
            case TAG_PARAMS:
                handleParams(attributes);
                break;
            default:
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (delegatingHandler != null) {
            delegatingHandler.endElement(uri, localName, qName);
            if (TAG_SOURCE.equals(qName) || TAG_TARGET.equals(qName)) {
                delegatingHandler = null;
            }
            return;
        }

        if (TAG_MIGRATION.equals(qName)) {}
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (delegatingHandler != null) {
            delegatingHandler.characters(ch, start, length);
            return;
        }
    }

    public MigrationConfiguration getResult() {
        return config;
    }

    private void handleSource(Attributes attributes) {
        SourceNodeHandler sourceHandler = new SourceNodeHandler(config);
        sourceHandler.processAttributes(attributes);
        delegatingHandler = sourceHandler;
    }

    private void handleTarget(Attributes attributes) {
        TargetNodeHandler targetHandler = new TargetNodeHandler(config);
        targetHandler.processAttributes(attributes);
        delegatingHandler = targetHandler;
    }

    private void handleMigration(Attributes attributes) {
        config.setName(attributes.getValue(ATTR_NAME));
        config.setWizardStartDateTime(attributes.getValue(ATTR_WIZARD_START_DATE_TIME));
        String version = attributes.getValue(ATTR_VERSION);
        int versionValue = convertVersionToInt(version);

        if (versionValue < 1110) {
            config.setOldScript(true);
        }
    }

    private void handleParams(Attributes attributes) {
        config.setExportThreadCount(Integer.parseInt(attributes.getValue(ATTR_EXPORT_THREAD)));
        String attrImportThread = attributes.getValue(ATTR_IMPORT_THREAD);
        attrImportThread =
                attrImportThread == null ? ("" + config.getExportThreadCount()) : attrImportThread;
        config.setImportThreadCount(Integer.parseInt(attrImportThread));
        config.setCommitCount(Integer.parseInt(attributes.getValue(ATTR_COMMIT_COUNT)));
        final String fetchCount = attributes.getValue(ATTR_PAGE_FETCH_COUNT);
        config.setPageFetchCount(fetchCount == null ? 1000 : Integer.parseInt(fetchCount));
        config.setImplicitEstimate(
                getBoolean(attributes.getValue(ATTR_IMPLICIT_ESTIMATE_PROGRESS), false));
        config.setUpdateStatistics(getBoolean(attributes.getValue(ATTR_UPDATE_STATISTICS), true));

        setOtherParamIfPresent(attributes, MySQL2CUBRIDMigParas.UNPARSED_TIME);
        setOtherParamIfPresent(attributes, MySQL2CUBRIDMigParas.UNPARSED_DATE);
        setOtherParamIfPresent(attributes, MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
        setOtherParamIfPresent(attributes, MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
    }

    private void setOtherParamIfPresent(Attributes attributes, String paramName) {
        String value = attributes.getValue(paramName);
        if (value != null) {
            config.putOtherParam(paramName, value);
        }
    }
}
