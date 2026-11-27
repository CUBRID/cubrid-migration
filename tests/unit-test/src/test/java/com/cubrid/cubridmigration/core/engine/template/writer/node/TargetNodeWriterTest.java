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
 * - Neither the name of the copyright holder nor the names of its contributors
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

import static com.cubrid.cubridmigration.testutil.TestTableFactory.createIndex;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

@DisplayName("TargetNodeWriter")
class TargetNodeWriterTest {

    @Test
    @DisplayName("blank partition DDL skips partitions node")
    void blankPartitionDDL_doesNotWritePartitionsNode() throws Exception {
        Table table = new Table();
        table.setOwner("CUBRID");
        table.setName("CMT_LIST_PART");
        table.setSourceOwner("SRC");
        table.setComment("");

        PartitionInfo partitionInfo = new PartitionInfo();
        partitionInfo.setPartitionMethod("LIST");
        partitionInfo.setPartitionExp("REGION");
        partitionInfo.setDDL("   ");
        table.setPartitionInfo(partitionInfo);

        String xml = writeTargetXml(table);

        assertThat(xml).doesNotContain("<partitions");
        assertThat(xml).doesNotContain("<partition_ddl");
    }

    @Test
    @DisplayName("an index deselected in mapping is omitted from the constraints node")
    void deselectedIndex_isNotWritten() throws Exception {
        Table table = new Table();
        table.setOwner("CUBRID");
        table.setName("t1");
        table.setSourceOwner("SRC");
        table.setComment("");
        table.addIndex(createIndex("ix_keep", "c1"));
        table.addIndex(createIndex("ix_drop", "c2"));

        SourceEntryTableConfig tableConfig = new SourceEntryTableConfig();
        tableConfig.setName("t1");
        tableConfig.setTarget("t1");
        tableConfig.addIndexConfig("ix_keep", "ix_keep", true);
        tableConfig.addIndexConfig("ix_drop", "ix_drop", false);

        String xml = writeTargetXml(table, tableConfig);

        assertThat(xml).contains("name=\"ix_keep\"").doesNotContain("name=\"ix_drop\"");
    }

    private String writeTargetXml(Table table) throws Exception {
        return writeTargetXml(table, null);
    }

    private String writeTargetXml(Table table, SourceEntryTableConfig tableConfig)
            throws Exception {
        MigrationConfiguration config = new MigrationConfiguration();
        config.setTargetDBVersion("");
        config.addTargetTableSchema(table);
        if (tableConfig != null) {
            config.addExpEntryTableCfg(tableConfig);
        }

        StringWriter out = new StringWriter();
        XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
        new TargetNodeWriter().write(writer, config);
        writer.flush();
        writer.close();
        return out.toString();
    }
}
