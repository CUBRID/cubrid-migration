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
import com.cubrid.cubridmigration.core.engine.template.reader.node.ParametersNodeHandler;
import com.cubrid.cubridmigration.core.engine.template.reader.node.SourceNodeHandler;
import com.cubrid.cubridmigration.core.engine.template.reader.node.TargetNodeHandler;

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

    private final MigrationConfiguration config;
    private final ParametersNodeHandler parametersNodeHandler;

    private DefaultHandler delegatingHandler;

    protected MigrationTemplateHandler() {
        this.config = new MigrationConfiguration();
        this.parametersNodeHandler = new ParametersNodeHandler();
    }

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
                parametersNodeHandler.parse(config, attributes);
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
}
