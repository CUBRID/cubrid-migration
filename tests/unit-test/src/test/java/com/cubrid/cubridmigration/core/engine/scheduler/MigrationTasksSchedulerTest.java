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
package com.cubrid.cubridmigration.core.engine.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.JDBCConManager;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.executors.IRunnableExecutor;
import com.cubrid.cubridmigration.core.engine.task.MigrationTaskFactory;
import com.cubrid.cubridmigration.core.engine.task.exp.TableRecordExportTask;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

/**
 * Covers which tables {@link MigrationTasksScheduler#createRecords()} lets {@code
 * TableSplitPlanner} split. A table that carries a condition of its own must never reach the
 * planner, which is observable as the planner's first step: opening a source connection.
 */
@DisplayName("MigrationTasksScheduler")
class MigrationTasksSchedulerTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> one unsplit task, no planning")
    @DisplayName("a table that has a condition is exported as one task and never split")
    @ValueSource(
            strings = {
                // A top-level OR would be mis-grouped by the AND-ed range predicate, so every range
                // would re-read the rows matching its left side.
                "status = 'A' OR status = 'B'",
                "WHERE status = 'A'",
                // Trailing clauses are not predicates: they cannot take an AND at all.
                "status = 'A' ORDER BY id",
                "id > 100 LIMIT 1000",
            })
    void createRecords_tableWithCondition_exportsOneUnsplitTaskWithoutPlanning(String condition) {
        Fixture fixture = new Fixture(condition);

        fixture.scheduler.createRecords();

        verify(fixture.taskFactory).createExportTableRecordsTask(fixture.table);
        verify(fixture.taskFactory, never()).createExportTableRecordsRangeTask(any(), anyString());
        verify(fixture.connManager, never()).getSourceConnection();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> split planning is attempted")
    @DisplayName("a null, empty or blank condition does not block splitting")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void createRecords_noEffectiveCondition_stillPlansTheSplit(String condition) {
        Fixture fixture = new Fixture(condition);

        fixture.scheduler.createRecords();

        // Planning starts by opening a source connection; the table has no usable split column,
        // so it still ends up as one task - but only after having been considered.
        verify(fixture.connManager).getSourceConnection();
    }

    /**
     * A scheduler wired to one large online table, with split enabled. The configuration is a mock
     * because a real one only exposes a source table through {@code setSrcCatalog()}, which also
     * builds the whole target schema; the scheduler reads just these few values.
     */
    private static final class Fixture {
        final MigrationTasksScheduler scheduler = new MigrationTasksScheduler();
        final MigrationTaskFactory taskFactory = mock(MigrationTaskFactory.class);
        final JDBCConManager connManager = mock(JDBCConManager.class);
        final SourceEntryTableConfig table = new SourceEntryTableConfig();

        Fixture(String condition) {
            table.setOwner("owner");
            table.setName("orders");
            table.setMigrateData(true);
            table.setCondition(condition);

            Table source = new Table();
            source.setOwner("owner");
            source.setName("orders");
            source.setTableRowCount(2_000_000);

            MigrationConfiguration config = mock(MigrationConfiguration.class);
            when(config.sourceIsOnline()).thenReturn(true);
            when(config.getExpEntryTableCfg()).thenReturn(Collections.singletonList(table));
            when(config.getExpSQLCfg()).thenReturn(Collections.emptyList());
            when(config.getCSVConfigs()).thenReturn(Collections.emptyList());
            when(config.isParallelExportEnabled()).thenReturn(true);
            when(config.getParallelExportMinRowCount()).thenReturn(1_000_000L);
            when(config.getParallelExportDefaultDegree()).thenReturn(4);
            when(config.getParallelExportSkewRatioThreshold()).thenReturn(3.0);
            when(config.getSrcTableSchema("owner", "orders")).thenReturn(source);
            when(config.getSourceDBType()).thenReturn(DatabaseType.ORACLE);

            MigrationContext context = mock(MigrationContext.class);
            when(context.getConfig()).thenReturn(config);
            when(context.getExportRecExe()).thenReturn(mock(IRunnableExecutor.class));
            when(context.getConnManager()).thenReturn(connManager);
            when(taskFactory.createExportTableRecordsTask(table))
                    .thenReturn(mock(TableRecordExportTask.class));

            scheduler.context = context;
            scheduler.taskFactory = taskFactory;
        }
    }
}
