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
package com.cubrid.cubridmigration.core.engine.task.imp;

import static com.cubrid.cubridmigration.testutil.TestTableFactory.createIndex;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createTable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.IMigrationEventHandler;
import com.cubrid.cubridmigration.core.engine.event.IgnoreCreateObjectEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationErrorEvent;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;
import com.cubrid.cubridmigration.core.engine.importer.IMigrationImporter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

@DisplayName("IndexImportTask")
class IndexImportTaskTest {

    @Nested
    @DisplayName("executeImport()")
    class ExecuteImport {

        @Test
        @DisplayName("custom index list creates only the specified indexes")
        void customIndexList_createsOnlySpecifiedIndexes() {
            Table table = createTable("t1", "c1", "c2");
            Index idx1 = createIndex("ix1", "c1");
            Index idx2 = createIndex("ix2", "c2");
            table.addIndex(idx1);
            table.addIndex(idx2);

            IMigrationImporter importer = mock(IMigrationImporter.class);
            IMigrationEventHandler eventHandler = mock(IMigrationEventHandler.class);

            IndexImportTask task = new IndexImportTask(table, Arrays.asList(idx1));
            task.setImporter(importer);
            task.setMigrationEventHandler(eventHandler);

            task.run();

            verify(importer).createIndex(idx1);
            verify(importer, never()).createIndex(idx2);
        }

        @Test
        @DisplayName("default constructor imports all indexes defined on the target table")
        void defaultConstructor_importsAllTableIndexes() {
            Table table = createTable("t1", "c1", "c2");
            Index idx1 = createIndex("ix1", "c1");
            Index idx2 = createIndex("ix2", "c2");
            table.addIndex(idx1);
            table.addIndex(idx2);

            IMigrationImporter importer = mock(IMigrationImporter.class);
            IMigrationEventHandler eventHandler = mock(IMigrationEventHandler.class);

            IndexImportTask task = new IndexImportTask(table);
            task.setImporter(importer);
            task.setMigrationEventHandler(eventHandler);

            task.run();

            verify(importer).createIndex(idx1);
            verify(importer).createIndex(idx2);
        }

        @Test
        @DisplayName("unique index duplicating primary key columns is skipped and reported")
        void uniqueIndexDuplicatingPK_isSkippedAndReported() {
            Table table = createTable("t1", "c1");
            PK pk = new PK();
            pk.addColumn("c1");
            table.setPk(pk);

            Index idx = createIndex("ix_pk_dup", "c1");
            idx.setUnique(true);
            table.addIndex(idx);

            IMigrationImporter importer = mock(IMigrationImporter.class);
            IMigrationEventHandler eventHandler = mock(IMigrationEventHandler.class);

            IndexImportTask task = new IndexImportTask(table);
            task.setImporter(importer);
            task.setMigrationEventHandler(eventHandler);

            task.run();

            verify(importer, never()).createIndex(any());
            verify(eventHandler).handleEvent(any(IgnoreCreateObjectEvent.class));
        }

        @Test
        @DisplayName(
                "importer exception during index creation is caught and reported as error event")
        void importerException_firesMigrationErrorEvent() {
            Table table = createTable("t1", "c1");
            Index idx = createIndex("ix1", "c1");
            table.addIndex(idx);

            IMigrationImporter importer = mock(IMigrationImporter.class);
            IMigrationEventHandler eventHandler = mock(IMigrationEventHandler.class);
            doThrow(new NormalMigrationException("failed to create index"))
                    .when(importer)
                    .createIndex(idx);

            IndexImportTask task = new IndexImportTask(table);
            task.setImporter(importer);
            task.setMigrationEventHandler(eventHandler);

            task.run();

            verify(eventHandler).handleEvent(any(MigrationErrorEvent.class));
        }
    }
}
