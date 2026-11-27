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
 *   and/or other materials provided with the distribution
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
package com.cubrid.cubridmigration.core.engine.exporter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.JDBCConManager;
import com.cubrid.cubridmigration.core.engine.RecordExportedListener;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.cubrid.Data2StrTranslator;
import com.cubrid.cubridmigration.testutil.TestColumnFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Covers {@link JDBCExporter#combineCondition}, the export loop of {@link
 * JDBCExporter#exportTableRecords(SourceTableConfig, String, RecordExportedListener)}, {@link
 * JDBCExporter#isLatestPage} and {@link JDBCExporter#resolveSerializationTargetColumns}.
 *
 * <p>Export loop regression note: most dialects' {@code getPagedSelectSQL} is a no-op (the SQL
 * comes back unchanged), so one execution already returns everything the query matches. For those
 * dialects the loop must not ask for a "next page" - re-running the same SQL re-reads (and
 * re-imports) the same rows. That used to be decided by comparing the exported count against the
 * *whole table's* row count, which a TableSplitPlanner range query (only a subset) never reaches:
 * the real 4x duplication bug. The tests below count how many times the SQL is actually executed.
 */
@DisplayName("JDBCExporter")
class JDBCExporterTest {

    private final JDBCExporter exporter = new JDBCExporter();

    @Test
    @DisplayName("a blank extra condition leaves the SQL unchanged")
    void combineCondition_blankExtraCondition_returnsSqlUnchanged() {
        String sql = "SELECT id FROM orders";

        assertThat(exporter.combineCondition(sql, null)).isEqualTo(sql);
        assertThat(exporter.combineCondition(sql, "")).isEqualTo(sql);
        assertThat(exporter.combineCondition(sql, "   ")).isEqualTo(sql);
    }

    @Test
    @DisplayName("no existing WHERE clause: the extra condition is added with WHERE")
    void combineCondition_noExistingWhere_addsWhere() {
        String sql = "SELECT id FROM orders";

        String result = exporter.combineCondition(sql, "id BETWEEN 1 AND 1000");

        assertThat(result).isEqualTo("SELECT id FROM orders WHERE (id BETWEEN 1 AND 1000)");
    }

    @Test
    @DisplayName(
            "an existing WHERE clause (from the table's own condition): the extra condition"
                    + " is ANDed on instead of replacing it")
    void combineCondition_existingWhere_addsAnd() {
        String sql = "SELECT id FROM orders WHERE status = 'ACTIVE'";

        String result = exporter.combineCondition(sql, "id BETWEEN 1 AND 1000");

        assertThat(result)
                .isEqualTo(
                        "SELECT id FROM orders WHERE status = 'ACTIVE' AND (id BETWEEN 1 AND"
                                + " 1000)");
    }

    @Test
    @DisplayName("WHERE detection is case-insensitive")
    void combineCondition_lowercaseWhere_isDetected() {
        String sql = "select id from orders where status = 'ACTIVE'";

        String result = exporter.combineCondition(sql, "id BETWEEN 1 AND 1000");

        assertThat(result).contains(" AND (id BETWEEN 1 AND 1000)");
        assertThat(result).doesNotContain(" WHERE (id BETWEEN 1 AND 1000)");
    }

    // ---------------------------------------------------------------
    // exportTableRecords(): how many times the SQL is executed
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "a dialect without paging runs a range query once, though the range is far smaller"
                    + " than the whole table (the 4x-duplication bug)")
    void exportTableRecords_unpagedDialectRange_executesOnce() {
        // 50,000 rows came back from a range of a 200,000-row table. The old stopping condition
        // compared that against the whole table and re-ran the same SQL until 200,000 rows had
        // been "exported": exactly 4 executions for a degree=4 split.
        CountingExporter counting = newExporter(false, 200_000, false, 50_000);

        counting.exportTableRecords(
                ordersTable(), "id >= 26 AND id < 51", mock(RecordExportedListener.class));

        assertThat(counting.executedSql)
                .containsExactly(BASE_SQL + " WHERE (id >= 26 AND id < 51)");
    }

    @Test
    @DisplayName("a dialect without paging reads a whole (unsplit) table in one execution")
    void exportTableRecords_unpagedDialectWholeTable_executesOnce() {
        CountingExporter counting = newExporter(false, 200_000, false, 200_000);

        counting.exportTableRecords(ordersTable(), null, mock(RecordExportedListener.class));

        assertThat(counting.executedSql).containsExactly(BASE_SQL);
    }

    @Test
    @DisplayName(
            "a dialect without paging stops after one execution even with implicit estimate on")
    void exportTableRecords_unpagedDialectImplicitEstimateOn_executesOnce() {
        // With implicit estimate on the table row count is 0 and the row-count condition can never
        // fire, so a loop that waited for it would not end at all.
        CountingExporter counting = newExporter(false, 0, true, 50_000);

        counting.exportTableRecords(ordersTable(), null, mock(RecordExportedListener.class));

        assertThat(counting.executedSql).hasSize(1);
    }

    @Test
    @DisplayName("a dialect with paging fetches page by page until a short page comes back")
    void exportTableRecords_pagedDialect_readsPageByPageUntilShortPage() {
        CountingExporter counting = newExporter(true, 2_400, false, 1_000, 1_000, 400);

        counting.exportTableRecords(ordersTable(), null, mock(RecordExportedListener.class));

        assertThat(counting.executedSql)
                .containsExactly(
                        BASE_SQL + " /*offset=0*/",
                        BASE_SQL + " /*offset=1000*/",
                        BASE_SQL + " /*offset=2000*/");
    }

    @Test
    @DisplayName("a dialect with paging still pages through a range query")
    void exportTableRecords_pagedDialectRange_readsPageByPage() {
        CountingExporter counting = newExporter(true, 200_000, false, 1_000, 300);

        counting.exportTableRecords(
                ordersTable(), "id >= 26 AND id < 51", mock(RecordExportedListener.class));

        assertThat(counting.executedSql)
                .containsExactly(
                        BASE_SQL + " WHERE (id >= 26 AND id < 51) /*offset=0*/",
                        BASE_SQL + " WHERE (id >= 26 AND id < 51) /*offset=1000*/");
    }

    @Test
    @DisplayName("a custom SQL table is never paged, so even a paging dialect reads it once")
    void exportTableRecords_customSqlTableOnPagingDialect_executesOnce() {
        // DBExportHelper#getPagedSelectSQL returns a user's SQL as-is, so a "next page" would be
        // the very same query again.
        CountingExporter counting = newExporter(true, 200_000, false, 50_000);

        counting.exportTableRecords(customSqlTable(), null, mock(RecordExportedListener.class));

        assertThat(counting.executedSql).containsExactly(BASE_SQL);
    }

    // ---------------------------------------------------------------
    // isLatestPage(): the stopping condition of a paged export
    // ---------------------------------------------------------------

    @Test
    @DisplayName("a paged export stops on an empty or short page, or once the whole table was read")
    void isLatestPage_rowCountConditions_returnsTrueOnlyForLastPage() {
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.isImplicitEstimate()).thenReturn(false);
        when(config.getPageFetchCount()).thenReturn(1000);
        exporter.setConfig(config);

        Table table = new Table();
        table.setName("orders");
        table.setTableRowCount(200_000);

        // A full page (1000 rows) with plenty of the table still unread: not the last page.
        assertThat(exporter.isLatestPage(table, 1000, 1000)).isFalse();
        // Exported count reached the (whole-table) total: last page.
        assertThat(exporter.isLatestPage(table, 200_000, 1000)).isTrue();
        // A short page (fewer rows than a full page) always signals the last page.
        assertThat(exporter.isLatestPage(table, 500, 500)).isTrue();
    }

    @Test
    @DisplayName(
            "serialization switched off: no target lookup at all, and the table is not"
                    + " serialized")
    void resolveSerializationTargetColumns_serializationDisabled_returnsNullWithoutLookup() {
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.isRecordSerializationEnabled()).thenReturn(false);
        when(config.targetIsDBDump()).thenReturn(true);
        exporter.setConfig(config);

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID"));

        assertThat(result).isNull();
        verify(config, never()).getTargetTableSchema(anyString());
    }

    @Test
    @DisplayName("a non-dump (online) target is never serialized, even when the option is on")
    void resolveSerializationTargetColumns_onlineTarget_returnsNull() {
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.isRecordSerializationEnabled()).thenReturn(true);
        when(config.targetIsDBDump()).thenReturn(false);
        exporter.setConfig(config);

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID"));

        assertThat(result).isNull();
        verify(config, never()).getTargetTableSchema(anyString());
    }

    @Test
    @DisplayName("an eligible table gets its resolved target columns, in export-column order")
    void resolveSerializationTargetColumns_eligibleTable_returnsTargetColumns() {
        Column id = TestColumnFactory.createColumn("ID", "integer", null, null);
        Column name = TestColumnFactory.createColumn("NAME", "varchar", 30, null);
        exporter.setConfig(dumpConfigWithTargetTable("orders", name, id));
        exporter.setUnloadFileUtil(mock(Data2StrTranslator.class));

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID", "NAME"));

        assertThat(result).containsExactly(id, name);
    }

    @Test
    @DisplayName("a LOB target column keeps the whole table on the importer's conversion path")
    void resolveSerializationTargetColumns_lobColumn_returnsNull() {
        Column id = TestColumnFactory.createColumn("ID", "integer", null, null);
        Column doc = TestColumnFactory.createColumn("DOC", "clob", null, null);
        exporter.setConfig(dumpConfigWithTargetTable("orders", id, doc));
        exporter.setUnloadFileUtil(mock(Data2StrTranslator.class));

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID", "DOC"));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("a column with a user-defined data handler cannot be pre-converted")
    void resolveSerializationTargetColumns_userDataHandler_returnsNull() {
        Column id = TestColumnFactory.createColumn("ID", "integer", null, null);
        exporter.setConfig(dumpConfigWithTargetTable("orders", id));
        exporter.setUnloadFileUtil(mock(Data2StrTranslator.class));
        List<SourceColumnConfig> columns = exportColumns("ID");
        columns.get(0).setUserDataHandler("my.Handler#convert");

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", columns);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("a target table that does not exist yet cannot be serialized against")
    void resolveSerializationTargetColumns_missingTargetTable_returnsNull() {
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.isRecordSerializationEnabled()).thenReturn(true);
        when(config.targetIsDBDump()).thenReturn(true);
        when(config.getTargetTableSchema("orders")).thenReturn(null);
        exporter.setConfig(config);
        exporter.setUnloadFileUtil(mock(Data2StrTranslator.class));

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID"));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("no unload-file translator wired up (not a dump run): nothing to serialize with")
    void resolveSerializationTargetColumns_noTranslator_returnsNull() {
        Column id = TestColumnFactory.createColumn("ID", "integer", null, null);
        exporter.setConfig(dumpConfigWithTargetTable("orders", id));

        Column[] result =
                exporter.resolveSerializationTargetColumns(
                        entryTable("orders"), "HR.orders", exportColumns("ID"));

        assertThat(result).isNull();
    }

    private static MigrationConfiguration dumpConfigWithTargetTable(
            String tableName, Column... columns) {
        Table target = new Table();
        target.setName(tableName);
        target.setColumns(Arrays.asList(columns));
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.isRecordSerializationEnabled()).thenReturn(true);
        when(config.targetIsDBDump()).thenReturn(true);
        when(config.getTargetTableSchema(tableName)).thenReturn(target);
        return config;
    }

    private static SourceEntryTableConfig entryTable(String targetName) {
        SourceEntryTableConfig stc = new SourceEntryTableConfig();
        stc.setName(targetName);
        stc.setTarget(targetName);
        return stc;
    }

    private static List<SourceColumnConfig> exportColumns(String... names) {
        List<SourceColumnConfig> columns = new ArrayList<SourceColumnConfig>();
        for (String name : names) {
            SourceColumnConfig scc = new SourceColumnConfig();
            scc.setName(name);
            scc.setTarget(name);
            columns.add(scc);
        }
        return columns;
    }

    // ---------------------------------------------------------------
    // export loop fixtures
    // ---------------------------------------------------------------

    private static final String BASE_SQL = "SELECT id FROM owner.orders";

    /**
     * Builds an exporter whose "database" is a stub: {@code handleSQL} only records the SQL it was
     * asked to run and answers with {@code rowsPerExecution} (the last value repeats), and the
     * export helper either pages (appending an offset marker to the SQL) or returns the SQL
     * unchanged, like the real dialects.
     */
    private static CountingExporter newExporter(
            boolean pagingSupported,
            long tableRowCount,
            boolean implicitEstimate,
            long... rowsPerExecution) {
        DBExportHelper helper = mock(DBExportHelper.class);
        when(helper.isPagingSupported()).thenReturn(pagingSupported);
        when(helper.getSelectSQL(any(SourceTableConfig.class), any(MigrationConfiguration.class)))
                .thenReturn(BASE_SQL);
        when(helper.getPagedSelectSQL(
                        any(SourceTableConfig.class), anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation -> {
                            SourceTableConfig stc = invocation.getArgument(0);
                            String sql = invocation.getArgument(1);
                            long offset = invocation.getArgument(3);
                            // Same policy as DBExportHelper: a custom SQL table is never paged.
                            if (!pagingSupported || stc instanceof SourceSQLTableConfig) {
                                return sql;
                            }
                            return sql + " /*offset=" + offset + "*/";
                        });

        Table table = new Table();
        table.setOwner("owner");
        table.setName("orders");
        table.setTableRowCount(tableRowCount);
        // A real MigrationConfiguration can only expose a source table through setSrcCatalog(),
        // which also builds the whole target schema; the loop reads just these four values.
        MigrationConfiguration config = mock(MigrationConfiguration.class);
        when(config.getPageFetchCount()).thenReturn(1000);
        when(config.isImplicitEstimate()).thenReturn(implicitEstimate);
        when(config.getSrcTableSchema(anyString(), anyString())).thenReturn(table);
        when(config.getSrcSQLSchema(anyString())).thenReturn(table);

        JDBCConManager connManager = mock(JDBCConManager.class);
        when(connManager.getSourceConnection()).thenReturn(mock(Connection.class));

        CountingExporter counting = new CountingExporter(helper, rowsPerExecution);
        counting.setConfig(config);
        counting.setConnManager(connManager);
        return counting;
    }

    /** The {@code owner.orders} table the export loop tests read. */
    private static SourceEntryTableConfig ordersTable() {
        SourceEntryTableConfig stc = entryTable("orders");
        stc.setOwner("owner");
        return stc;
    }

    /** A user-written SQL table, which the helper never pages. */
    private static SourceSQLTableConfig customSqlTable() {
        SourceSQLTableConfig stc = new SourceSQLTableConfig();
        stc.setName("orders");
        return stc;
    }

    private static final class CountingExporter extends JDBCExporter {
        /** A loop that never terminates would hang the test run, so fail fast instead. */
        private static final int RUNAWAY_LIMIT = 50;

        private final DBExportHelper helper;
        private final long[] rowsPerExecution;
        private final List<String> executedSql = new ArrayList<String>();

        CountingExporter(DBExportHelper helper, long... rowsPerExecution) {
            this.helper = helper;
            this.rowsPerExecution = rowsPerExecution;
        }

        @Override
        protected DBExportHelper getSrcDBExportHelper() {
            return helper;
        }

        @Override
        protected long handleSQL(
                Connection conn,
                String sql,
                SourceTableConfig stc,
                Table sTable,
                List<SourceColumnConfig> expColConfs,
                Column[] sCols,
                List<Record> records,
                RecordExportedListener newRecsHandler) {
            executedSql.add(sql);
            if (executedSql.size() > RUNAWAY_LIMIT) {
                throw new IllegalStateException(
                        "export loop did not stop after " + RUNAWAY_LIMIT + " executions");
            }
            return rowsPerExecution[Math.min(executedSql.size() - 1, rowsPerExecution.length - 1)];
        }
    }
}
