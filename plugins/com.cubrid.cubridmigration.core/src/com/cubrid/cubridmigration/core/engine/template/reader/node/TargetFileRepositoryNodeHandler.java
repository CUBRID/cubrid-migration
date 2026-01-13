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

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;

public class TargetFileRepositoryNodeHandler {

    private final MigrationConfiguration config;

    public TargetFileRepositoryNodeHandler(MigrationConfiguration config) {
        this.config = config;
    }

    public void processAttributes(Attributes attributes) {
        config.setFileRepositroyPath(attributes.getValue(ATTR_DIR));
        config.setTargetFileTimeZone(attributes.getValue(ATTR_TIMEZONE));
        config.setOneTableOneFile(getBoolean(attributes.getValue(ATTR_ONE_TABLE_ONE_FILE), false));
        final String fileMaxSize = attributes.getValue(ATTR_FILE_MAX_SIZE);
        config.setMaxCountPerFile(fileMaxSize == null ? 0 : Integer.parseInt(fileMaxSize));
        config.setTargetFilePrefix(attributes.getValue(ATTR_OUTPUT_FILE_PREFIX));
        try {
            config.setDestType(Integer.parseInt(attributes.getValue(ATTR_DATA_FILE_FORMAT)));
        } catch (Exception ex) {
            config.setDestType(MigrationConfiguration.DEST_DB_UNLOAD);
        }
        config.setTargetCharSet(attributes.getValue(ATTR_CHARSET));
        if (config.targetIsCSV()) {
            String value = attributes.getValue(ATTR_CSV_SEPARATE);
            config.getCsvSettings()
                    .setSeparateChar(StringUtils.isEmpty(value) ? ',' : value.charAt(0));
            value = attributes.getValue(ATTR_CSV_QUOTE);
            config.getCsvSettings()
                    .setQuoteChar(
                            StringUtils.isEmpty(value)
                                    ? MigrationConfiguration.CSV_NO_CHAR
                                    : value.charAt(0));
            value = attributes.getValue(ATTR_CSV_ESCAPE);
            config.getCsvSettings()
                    .setEscapeChar(
                            StringUtils.isEmpty(value)
                                    ? MigrationConfiguration.CSV_NO_CHAR
                                    : value.charAt(0));
        }
        config.setTargetLOBRootPath(attributes.getValue(ATTR_LOB_ROOT_DIR));
        config.setAddUserSchema(getBoolean(attributes.getValue(ATTR_ADD_SCHEMA), false));
        config.setSplitSchema(getBoolean(attributes.getValue(ATTR_SPLIT_SCHEMA), false));
        config.setCreateUserSQL(getBoolean(attributes.getValue(ATTR_CREATE_USER_SQL), false));
    }
}
