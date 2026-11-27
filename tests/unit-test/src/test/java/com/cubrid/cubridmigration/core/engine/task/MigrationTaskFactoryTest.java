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
package com.cubrid.cubridmigration.core.engine.task;

import static com.cubrid.cubridmigration.testutil.TestTableFactory.createIndex;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createTable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.IMigrationEventHandler;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.importer.IMigrationImporter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationTaskFactory")
class MigrationTaskFactoryTest {

    @Nested
    @DisplayName("createImportIndexTask()")
    class CreateImportIndexTask {

        @Test
        @DisplayName("task is configured with selected target indexes from migration configuration")
        void configWithDeselectedIndex_createsTaskWithOnlySelectedIndexes() {
            Table table = createTable("orders", "c1", "c2");
            Index idxKeep = createIndex("ix_keep", "c1");
            Index idxDrop = createIndex("ix_drop", "c2");
            table.addIndex(idxKeep);
            table.addIndex(idxDrop);

            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig tableConfig = new SourceEntryTableConfig();
            tableConfig.setName("orders");
            tableConfig.setTarget("orders");
            tableConfig.addIndexConfig("ix_keep", "ix_keep", true);
            tableConfig.addIndexConfig("ix_drop", "ix_drop", false);
            config.addExpEntryTableCfg(tableConfig);

            MigrationContext context = mock(MigrationContext.class);
            when(context.getConfig()).thenReturn(config);
            IMigrationEventHandler eventHandler = mock(IMigrationEventHandler.class);
            when(context.getEventsHandler()).thenReturn(eventHandler);

            IMigrationImporter importer = mock(IMigrationImporter.class);

            MigrationTaskFactory factory = new MigrationTaskFactory();
            factory.setContext(context);
            factory.setImporter(importer);

            ImportTask task = factory.createImportIndexTask(table);
            task.run();

            verify(importer).createIndex(idxKeep);
            verify(importer, never()).createIndex(idxDrop);
        }
    }
}
