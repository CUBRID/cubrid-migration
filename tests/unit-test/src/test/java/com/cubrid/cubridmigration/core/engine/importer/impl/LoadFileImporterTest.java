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
package com.cubrid.cubridmigration.core.engine.importer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.IMigrationEventHandler;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.MigrationDirAndFilesManager;
import com.cubrid.cubridmigration.core.engine.MigrationStatusManager;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.executors.IRunnableExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class LoadFileImporterTest {
    @Test
    void handleDataFile_keepsSameNamedXmlTablesUnderTheirOwnClassHeaders(@TempDir Path dir)
            throws Exception {
        Catalog catalog = new Catalog();
        catalog.setName("dump");
        catalog.setDatabaseType(DatabaseType.MYSQL);
        for (String owner : List.of("DB1", "DB2")) {
            Schema schema = new Schema();
            schema.setName(owner);
            catalog.addSchema(schema);
        }
        MigrationConfiguration config = new MigrationConfiguration();
        config.setSourceType(MigrationConfiguration.XML);
        config.setDestType(MigrationConfiguration.DEST_DB_UNLOAD);
        config.setSrcCatalog(catalog, false);
        config.setOneTableOneFile(true);
        config.setAddUserSchema(true);
        config.setTargetCharSet("UTF-8");
        config.setTargetFilePrefix("dump");

        MigrationContext context = mock(MigrationContext.class);
        when(context.getConfig()).thenReturn(config);
        when(context.getStatusMgr()).thenReturn(mock(MigrationStatusManager.class));
        when(context.getEventsHandler()).thenReturn(mock(IMigrationEventHandler.class));
        MigrationDirAndFilesManager files = mock(MigrationDirAndFilesManager.class);
        when(context.getDirAndFilesMgr()).thenReturn(files);
        when(files.getMergeFilesDir()).thenReturn(dir.toString());
        IRunnableExecutor executor = mock(IRunnableExecutor.class);
        when(context.getMergeTaskExe()).thenReturn(executor);
        doAnswer(
                        call -> {
                            call.getArgument(0, Runnable.class).run();
                            return null;
                        })
                .when(executor)
                .execute(any());

        LoadFileImporter importer = new LoadFileImporter(context);
        for (String owner : List.of("DB1", "DB2")) {
            SourceEntryTableConfig table = new SourceEntryTableConfig();
            table.setOwner(owner);
            table.setName("SHARED");
            table.setTargetOwner(owner);
            table.setTarget("shared");
            table.addColumnConfig("marker", "marker", true);
            Path batch = dir.resolve(owner + ".tmp");
            Files.writeString(batch, "'FROM_" + owner + "_A'\n'FROM_" + owner + "_B'\n");
            importer.handleDataFile(batch.toString(), table, 2, 2);
        }
        try (Stream<Path> paths = Files.list(dir.resolve("dump/objects"))) {
            List<List<String>> contents = new java.util.ArrayList<>();
            for (Path file : paths.toList()) {
                contents.add(Files.readAllLines(file));
            }
            assertThat(contents)
                    .containsExactlyInAnyOrder(
                            List.of(
                                    "%class [DB1].[shared] ([marker])",
                                    "'FROM_DB1_A'", "'FROM_DB1_B'"),
                            List.of(
                                    "%class [DB2].[shared] ([marker])",
                                    "'FROM_DB2_A'", "'FROM_DB2_B'"));
        }
    }
}
