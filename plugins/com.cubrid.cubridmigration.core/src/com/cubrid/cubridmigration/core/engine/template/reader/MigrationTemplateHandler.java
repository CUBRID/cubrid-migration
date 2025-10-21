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
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MigrationTemplateHandler Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-9-13 created by Kevin Cao
 */
public final class MigrationTemplateHandler extends DefaultHandler {

    private final MigrationConfiguration config = new MigrationConfiguration();

    private DefaultHandler delegatingHandler;

    MigrationTemplateHandler() {
        // Do nothing
    }

    /**
     * Receive notification of the start of an element.
     *
     * <p>By default, do nothing. Application writers may override this method in a subclass to take
     * specific actions at the start of each element (such as allocating a new tree node or writing
     * output to a file).
     *
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or if
     *     Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace processing
     *     is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are not
     *     available.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *     shall be an empty Attributes object.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#startElement
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (delegatingHandler != null) {
            delegatingHandler.startElement(uri, localName, qName, attributes);
            return;
        }

        switch (qName) {
            case TAG_SOURCE:
                SourceNodeHandler sourceHandler = new SourceNodeHandler(config);
                sourceHandler.processAttributes(attributes);
                delegatingHandler = sourceHandler;
                break;
            case TAG_TARGET:
                TargetNodeHandler targetHandler = new TargetNodeHandler(config);
                targetHandler.processAttributes(attributes);
                delegatingHandler = targetHandler;
                break;
            case TAG_MIGRATION:
                config.setName(attributes.getValue(ATTR_NAME));
                config.setWizardStartDateTime(attributes.getValue(ATTR_WIZARD_START_DATE_TIME));
                String version = attributes.getValue(ATTR_VERSION);
                int versionValue = convertVersionToInt(version);

                if (versionValue < 1110) {
                    config.setOldScript(true);
                }
                break;
            case TAG_PARAMS:
                config.setExportThreadCount(
                        Integer.parseInt(attributes.getValue(ATTR_EXPORT_THREAD)));
                String attrImportThread = attributes.getValue(ATTR_IMPORT_THREAD);
                attrImportThread =
                        attrImportThread == null
                                ? ("" + config.getExportThreadCount())
                                : attrImportThread;
                config.setImportThreadCount(Integer.parseInt(attrImportThread));
                config.setCommitCount(Integer.parseInt(attributes.getValue(ATTR_COMMIT_COUNT)));
                final String fetchCount = attributes.getValue(ATTR_PAGE_FETCH_COUNT);
                config.setPageFetchCount(fetchCount == null ? 1000 : Integer.parseInt(fetchCount));
                config.setImplicitEstimate(
                        getBoolean(attributes.getValue(ATTR_IMPLICIT_ESTIMATE_PROGRESS), false));
                config.setUpdateStatistics(
                        getBoolean(attributes.getValue(ATTR_UPDATE_STATISTICS), true));
                String s1 = attributes.getValue(MySQL2CUBRIDMigParas.UNPARSED_TIME);
                if (s1 != null) {
                    config.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIME, s1);
                }
                String s2 = attributes.getValue(MySQL2CUBRIDMigParas.UNPARSED_DATE);
                if (s2 != null) {
                    config.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_DATE, s2);
                }
                String s3 = attributes.getValue(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
                if (s3 != null) {
                    config.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP, s3);
                }
                String s4 = attributes.getValue(MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
                if (s4 != null) {
                    config.putOtherParam(MySQL2CUBRIDMigParas.REPLAXE_CHAR0, s4);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Receive notification of the end of an element.
     *
     * <p>By default, do nothing. Application writers may override this method in a subclass to take
     * specific actions at the end of each element (such as finalising a tree node or writing output
     * to a file).
     *
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or if
     *     Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace processing
     *     is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are not
     *     available.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#endElement
     */
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

    /**
     * Receive notification of character data inside an element.
     *
     * <p>By default, do nothing. Application writers may override this method to take specific
     * actions for each chunk of character data (such as adding the data to a node or buffer, or
     * printing it to a file).
     *
     * @param ch The characters.
     * @param start The start position in the character array.
     * @param length The number of characters to use from the character array.
     * @exception org.xml.sax.SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#characters
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (delegatingHandler != null) {
            delegatingHandler.characters(ch, start, length);
            return;
        }
    }

    /**
     * Get parsing result
     *
     * @return MigrationConfiguration
     */
    public MigrationConfiguration getResult() {
        return config;
    }
}
