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
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_COMMIT_COUNT;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_EXPORT_THREAD;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_IMPLICIT_ESTIMATE_PROGRESS;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_IMPORT_THREAD;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_PAGE_FETCH_COUNT;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.ATTR_UPDATE_STATISTICS;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import org.xml.sax.Attributes;

/** A SAX handler for parsing the <params> configuration section of a migration template. */
public class ParametersNodeHandler {

    public void parse(MigrationConfiguration config, Attributes attributes) {
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

        setOtherParamIfPresent(config, attributes, MySQL2CUBRIDMigParas.UNPARSED_TIME);
        setOtherParamIfPresent(config, attributes, MySQL2CUBRIDMigParas.UNPARSED_DATE);
        setOtherParamIfPresent(config, attributes, MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
        setOtherParamIfPresent(config, attributes, MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
    }

    private void setOtherParamIfPresent(
            MigrationConfiguration config, Attributes attributes, String paramName) {
        String value = attributes.getValue(paramName);
        if (value != null) {
            config.putOtherParam(paramName, value);
        }
    }
}
