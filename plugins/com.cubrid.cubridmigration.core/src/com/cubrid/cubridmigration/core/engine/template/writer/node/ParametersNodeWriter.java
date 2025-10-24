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

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.*;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Writes the <params> section of the migration XML file. */
public class ParametersNodeWriter {

    public void write(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeEmptyElement(TAG_PARAMS);
        writer.writeAttribute(ATTR_EXPORT_THREAD, String.valueOf(config.getExportThreadCount()));
        writer.writeAttribute(ATTR_IMPORT_THREAD, String.valueOf(config.getImportThreadCount()));
        writer.writeAttribute(ATTR_COMMIT_COUNT, String.valueOf(config.getCommitCount()));
        writer.writeAttribute(ATTR_PAGE_FETCH_COUNT, String.valueOf(config.getPageFetchCount()));
        writer.writeAttribute(
                ATTR_IMPLICIT_ESTIMATE_PROGRESS, getBooleanString(config.isImplicitEstimate()));
        writer.writeAttribute(
                ATTR_UPDATE_STATISTICS, getBooleanString(config.isUpdateStatistics()));

        if (config.hasOtherParam()) {
            String s1 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIME);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_TIME, s1 == null ? "" : s1);
            String s2 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_DATE);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_DATE, s2 == null ? "" : s2);
            String s3 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP, s3 == null ? "" : s3);
            String s4 = config.getOtherParam(MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
            writer.writeAttribute(MySQL2CUBRIDMigParas.REPLAXE_CHAR0, s4 == null ? "" : s4);
        }
    }
}
