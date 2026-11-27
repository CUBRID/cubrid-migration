/*
 * Copyright (C) 2008 Search Solution Corporation.
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

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.ThreadUtils;
import com.cubrid.cubridmigration.core.engine.UserDefinedDataHandlerManager;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceGrantConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlFunctionConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlProcedureConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceViewConfig;
import com.cubrid.cubridmigration.core.engine.exception.BreakMigrationException;
import com.cubrid.cubridmigration.core.engine.task.IMigrationTask;
import com.cubrid.cubridmigration.core.engine.task.MigrationTaskFactory;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.ParallelExportPlan;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.SplitRange;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MigrationTasksScheduler responses to schedule migration tasks.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-30 created by Kevin Cao
 */
public class MigrationTasksScheduler {

    private static final Logger LOG = LogUtil.getLogger(MigrationTasksScheduler.class);

    private final int USERSCHEMA_VERSION = 112;

    protected MigrationTaskFactory taskFactory;
    protected MigrationContext context;

    public MigrationTasksScheduler() {}

    /** Schedule migration tasks */
    public void schedule() {
        // Execute SQL tasks
        MigrationConfiguration config = context.getConfig();
        if (config.sourceIsSQL()) {
            List<String> files = config.getSqlFiles();
            for (String file : files) {
                executeTask2(taskFactory.createExportSQLTask(file));
            }
            await();
            return;
        }
        // Clean all no used objects
        config.cleanNoUsedConfigForStart();
        config.parsingProcedureFunction(true);
        initUserDefinedHandlers();

        PathUtils.changeLocalFilePath(config);
        clearTargetDB();
        createSchema();
        createTables();
        createViews();
        if (config.targetIsOnline()
                && Integer.parseInt(config.getTargetDBVersion()) < USERSCHEMA_VERSION) {
            createNoSupportSynonyms();
        } else {
            createSynonyms();
        }
        createSerials();

        // procedure, function header
        if (config.getSourceDBType().getID() == MigrationConfiguration.SOURCE_TYPE_ORACLE
                || config.getSourceDBType().getID() == MigrationConfiguration.SOURCE_TYPE_TIBERO) {
            createProcedureHeaders();
            createFunctionHeaders();
        }

        executeUserSQLs();
        boolean constrainsCreated = false;
        // If HA mode, the constraints should be created firstly.
        if (config.targetIsOnline() && config.isCreateConstrainsBeforeData()) {
            constrainsCreated = true;
            createPKs();
        }
        createRecords();

        if (!constrainsCreated) {
            createPKs();
        }
        createIndexes();
        createFKs();
        executeUserSQLs2();
        updateAutoIncColumnsCurrentValue();
        // Export functions/procedures/triggers to a txt file
        if (config.isExportNoSupportObjects()) {
            createTriggers();
        }
        if (config.targetIsOnline()
                && Integer.parseInt(config.getTargetDBVersion()) < USERSCHEMA_VERSION) {
            createNoSupportGrants();
        } else {
            createGrants();
        }

        alterViews();

        // procedure, function body
        if (config.getSourceDBType().getID() == MigrationConfiguration.SOURCE_TYPE_ORACLE
                || config.getSourceDBType().getID() == MigrationConfiguration.SOURCE_TYPE_TIBERO) {
            createProcedureBodies();
            createFunctionBodies();
        } else {
            createProcedures();
            createFunctions();
        }

        updateIndexStatistics();

        if (!config.targetIsOnline()) {
            if (config.isSplitSchema()) {
                if (config.getSourceDBType().getID() == MigrationConfiguration.SOURCE_TYPE_ORACLE
                        || config.getSourceDBType().getID()
                                == MigrationConfiguration.SOURCE_TYPE_TIBERO) {
                    createAllPlcsqlProcedureHeaderDDL();
                    createAllPlcsqlProcedureDDL();
                    createAllPlcsqlFunctionHeaderDDL();
                    createAllPlcsqlFunctionDDL();
                    createPlcsqlProcedureSourceAndDropDDL();
                    createPlcsqlFunctionsSourceAndDropDDL();
                }
                createSchemaFileList();
            }

            if (config.isCreateUserSQL()) {
                createCreateUserSQL();
            }
        }

        clearObjectsDir();
    }

    /** Update auto_increment columns current values */
    private void updateAutoIncColumnsCurrentValue() {
        if (!context.getConfig().targetIsOnline()) {
            return;
        }
        executeTask(taskFactory.createUpdateAiColumnsCurValTask(context.getConfig()));
    }

    /** Initialize the user defined data handlers. */
    private void initUserDefinedHandlers() {
        MigrationConfiguration config = context.getConfig();
        UserDefinedDataHandlerManager udf = UserDefinedDataHandlerManager.getInstance();
        List<SourceEntryTableConfig> setcs = config.getExpEntryTableCfg();
        for (SourceEntryTableConfig setc : setcs) {
            for (SourceColumnConfig scc : setc.getColumnConfigList()) {
                if (StringUtils.isBlank(scc.getUserDataHandler())) {
                    continue;
                }
                if (!udf.putColumnDataHandler(scc.getUserDataHandler(), false)) {
                    throw new BreakMigrationException(
                            "Data handler '" + scc.getUserDataHandler() + "' was not found.");
                }
            }
        }
        List<SourceSQLTableConfig> sstcs = config.getExpSQLCfg();
        for (SourceSQLTableConfig sstc : sstcs) {
            for (SourceColumnConfig scc : sstc.getColumnConfigList()) {
                if (StringUtils.isBlank(scc.getUserDataHandler())) {
                    continue;
                }
                if (!udf.putColumnDataHandler(scc.getUserDataHandler(), false)) {
                    throw new BreakMigrationException(
                            "Data handler '" + scc.getUserDataHandler() + "' was not found.");
                }
            }
        }
    }

    /** Execute the SQLs which were set to be executed before data migration */
    private void executeUserSQLs() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> setcs = config.getExpEntryTableCfg();
        for (SourceEntryTableConfig setc : setcs) {
            if (StringUtils.isBlank(setc.getSqlBefore())) {
                continue;
            }
            executeTask(taskFactory.createExecuteSQLTask(setc, setc.getSqlBefore()));
        }
        await();
    }

    /** Execute the SQLs which were set to be executed after migration */
    private void executeUserSQLs2() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> setcs = config.getExpEntryTableCfg();
        for (SourceEntryTableConfig setc : setcs) {
            if (StringUtils.isBlank(setc.getSqlAfter())) {
                continue;
            }
            executeTask(taskFactory.createExecuteSQLTask(setc, setc.getSqlAfter()));
        }
        await();
    }

    /** Clear target database */
    private void clearTargetDB() {
        MigrationConfiguration config = context.getConfig();
        if (config.targetIsFile()) {
            List<Schema> schemaList = null;
            if (config.getTargetSchemaList().size() > 0) {
                schemaList = config.getTargetSchemaList();
            } else {
                Collection<Schema> schemas = config.getScriptSchemaMapping().values();
                schemaList = new ArrayList<Schema>(schemas);
            }

            if (config.getSrcCatalog().getDatabaseType().isSupportMultiSchema()) {
                for (Schema schema : schemaList) {
                    deleteFile(config, schema.getName());
                }
            } else {
                deleteFile(config, config.getSrcConnOwner());
            }
        }
        executeTask(taskFactory.createCleanDBTask());
    }

    /**
     * Delete the created file
     *
     * @param config MigrationConfiguration
     * @param schemaName String
     */
    private void deleteFile(MigrationConfiguration config, String schemaName) {
        if (config.isSplitSchema()) {
            PathUtils.deleteFile(new File(config.getTargetTableFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetViewFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetViewQuerySpecFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetPkFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetFkFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetSerialFileName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetSchemaFileListName(schemaName)));
            PathUtils.deleteFile(new File(config.getTargetSynonymFileName(schemaName)));

            Map<String, String> grantFilePaths = config.getTargetGrantFileName(schemaName);
            Iterator<String> keys = null;
            if (grantFilePaths != null) {
                keys = grantFilePaths.keySet().iterator();
            }
            if (keys != null) {
                while (keys.hasNext()) {
                    PathUtils.deleteFile(new File(grantFilePaths.get(keys.next())));
                }
            }

            for (String procedureFile :
                    config.getTargetPlcsqlProcedureFileName(schemaName).values()) {
                PathUtils.deleteFile(new File(procedureFile));
            }

            for (String functionFile :
                    config.getTargetPlcsqlFunctionFileName(schemaName).values()) {
                PathUtils.deleteFile(new File(functionFile));
            }
        } else {
            PathUtils.deleteFile(new File(config.getTargetSchemaFileName(schemaName)));
        }
        PathUtils.deleteFile(new File(config.getTargetUpdateStatisticFileName(schemaName)));
        PathUtils.deleteFile(new File(config.getTargetIndexFileName(schemaName)));
        if (config.isOneTableOneFile()) {
            for (String filePath : config.getTargetTableDataFileName(schemaName)) {
                PathUtils.deleteFile(new File(filePath));
            }
        } else {
            PathUtils.deleteFile(new File(config.getTargetDataFileName(schemaName)));
        }
        PathUtils.deleteFile(new File(config.getFileRepositroyPath() + schemaName));
    }

    /** Delete objects directory */
    private void clearObjectsDir() {
        MigrationConfiguration config = context.getConfig();
        Map<String, String> tableDataFiles = config.getTargetDataFileName();
        List<String> keys = new ArrayList<>(tableDataFiles.keySet());

        for (String key : keys) {
            File objectsFileParentPath =
                    new File(
                            new File(tableDataFiles.get(key)).getParent()
                                    + File.separator
                                    + "objects");
            if (!config.isOneTableOneFile() && PathUtils.checkPathEmpty(objectsFileParentPath)) {
                PathUtils.deleteFile(objectsFileParentPath);
            }
        }
    }

    /** Waiting for step finished. */
    protected void await() {
        while (context.isExecutorsBusy()) {
            ThreadUtils.threadSleep(1000, null);
        }
    }

    /**
     * Execute create DB object task
     *
     * @param task IMigrationTask
     */
    protected void executeTask(IMigrationTask task) {
        context.getDbObjectExe().execute((Runnable) task);
    }

    /**
     * Execute migration records task
     *
     * @param task Migration records task
     */
    protected void executeTask2(IMigrationTask task) {
        context.getExportRecExe().execute((Runnable) task);
    }

    protected void createSchema() {
        MigrationConfiguration config = context.getConfig();
        if (config.targetIsOnline() && !config.isAddUserSchema()) {
            return;
        }
        List<Schema> dummySchemaList = config.getTargetSchemaList();

        dummySchemaList.stream()
                .filter(Schema::isNewTargetSchema)
                .distinct()
                .forEach(
                        schema -> {
                            executeTask(taskFactory.createImportSchemaTask(schema));
                        });
    }

    /** Schedule export table schema tasks. */
    protected void createTables() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> sourceTables = config.getExpEntryTableCfg();
        List<SourceSQLTableConfig> sourceSQLTables = config.getExpSQLCfg();
        List<SourceCSVConfig> sourceCSVFiles = config.getCSVConfigs();
        List<String> tableCreated = new ArrayList<String>();
        for (SourceEntryTableConfig st : sourceTables) {
            if (!st.isCreateNewTable()) {
                continue;
            }
            if (tableCreated.indexOf((st.getTargetOwner() + "." + st.getTarget())) >= 0) {
                continue;
            }
            tableCreated.add(st.getTargetOwner() + "." + st.getTarget());
            executeTask(taskFactory.createExportTableSchemaTask(st));
        }
        for (SourceSQLTableConfig st : sourceSQLTables) {
            if (!st.isCreateNewTable()) {
                continue;
            }
            if (tableCreated.indexOf(st.getTarget()) >= 0) {
                continue;
            }
            tableCreated.add(st.getTarget());
            executeTask(taskFactory.createExportTableSchemaTask(st));
        }
        // Create CSV file table
        for (SourceCSVConfig scc : sourceCSVFiles) {
            if (!scc.isCreate()) {
                continue;
            }
            if (tableCreated.indexOf(scc.getTarget()) >= 0) {
                continue;
            }
            tableCreated.add(scc.getTarget());
            executeTask(taskFactory.createExportCSVTableSchemaTask(scc));
        }
        await();
    }

    /** Schedule export view tasks. */
    protected void createViews() {
        MigrationConfiguration config = context.getConfig();
        List<SourceViewConfig> views = config.getExpViewCfg();
        for (SourceViewConfig vw : views) {
            executeTask(taskFactory.createExportViewTask(vw));
        }
        await();
    }

    /** Schedule export view alter tasks. */
    protected void alterViews() {
        MigrationConfiguration config = context.getConfig();
        List<SourceViewConfig> views = config.getExpViewCfg();
        for (SourceViewConfig vw : views) {
            executeTask(taskFactory.createExportViewAlterTask(vw));
        }
        await();
    }

    /** Schedule export table record tasks. */
    protected void createRecords() {
        MigrationConfiguration config = context.getConfig();
        boolean isMigData = false;
        List<SourceEntryTableConfig> entryTables = config.getExpEntryTableCfg();
        for (SourceTableConfig table : entryTables) {
            isMigData = isMigData || table.isMigrateData();
        }
        List<SourceSQLTableConfig> sqlTables = config.getExpSQLCfg();
        for (SourceTableConfig table : sqlTables) {
            isMigData = isMigData || table.isMigrateData();
        }
        List<SourceCSVConfig> csvs = config.getCSVConfigs();
        isMigData = isMigData || !csvs.isEmpty();
        // If no data to be migrated, return
        if (!isMigData) {
            return;
        }
        if (config.sourceIsOnline()) {
            // schedule exporting tasks
            for (SourceTableConfig table : entryTables) {
                if (!table.isMigrateData()) {
                    continue;
                }
                scheduleTableExport(config, (SourceEntryTableConfig) table);
            }
            for (SourceTableConfig table : sqlTables) {
                if (!table.isMigrateData()) {
                    continue;
                }
                // Custom SQL source tables can't be safely restricted to a range predicate
                // (DBExportHelper explicitly refuses to paginate them), so they always run as a
                // single unsplit task regardless of any parallel-export setting.
                executeTask2(taskFactory.createExportTableRecordsTask(table));
            }
        } else if (config.sourceIsXMLDump()) {
            executeTask(taskFactory.createExportAllRecordsTask());
        } else if (config.sourceIsCSV()) {
            for (SourceCSVConfig csv : csvs) {
                executeTask(taskFactory.createExportCSVTask(csv));
            }
        }
        await();
    }

    /**
     * Schedules one table's export as either a single unsplit task (the default, and the fallback
     * whenever parallel export isn't usable for this table) or, when parallel export qualifies, as
     * N parallel range tasks.
     *
     * <p>Driven entirely by the three global {@code MigrationConfiguration} knobs for now - {@code
     * SourceEntryTableConfig#getParallelDegree()} (a per-table override) is intentionally not
     * consulted yet, so every table is judged the same way: {@link
     * MigrationConfiguration#isParallelExportEnabled()} must be on, and the table's own row count
     * (already-fetched metadata, no extra query) must meet {@link
     * MigrationConfiguration#getParallelExportMinRowCount()} - this is checked *before* touching a
     * connection, so tables that don't qualify never pay for {@code TableSplitPlanner#plan}'s
     * MIN/MAX + COUNT round trip.
     *
     * @param config migration configuration
     * @param table the table to schedule; must be a {@code SourceEntryTableConfig} (custom SQL
     *     tables never reach this method - see {@link #createRecords()})
     */
    private void scheduleTableExport(MigrationConfiguration config, SourceEntryTableConfig table) {
        if (!config.isParallelExportEnabled()) {
            executeTask2(taskFactory.createExportTableRecordsTask(table));
            return;
        }

        Table srcTable = config.getSrcTableSchema(table.getOwner(), table.getName());
        if (srcTable == null
                || srcTable.getTableRowCount() < config.getParallelExportMinRowCount()) {
            LOG.debug(
                    "[DEBUG-SPLIT] Parallel export skipped for "
                            + table.getOwner()
                            + "."
                            + table.getName()
                            + ": row count "
                            + (srcTable == null
                                    ? "unknown (table metadata not found)"
                                    : srcTable.getTableRowCount())
                            + " below configured minimum "
                            + config.getParallelExportMinRowCount()
                            + ".");
            executeTask2(taskFactory.createExportTableRecordsTask(table));
            return;
        }

        // CUBRID's "resume from target's max ID" incremental mode (CUBRIDJDBCExporter#
        // isStartFromTargetMax) seeds its single starting bound from the target table, not from
        // this table's own MIN(splitColumn) - TableSplitPlanner#calculateRanges always covers the
        // source from its first row (the first range has no lower bound), so range-splitting it
        // would re-cover already-migrated rows.
        // Excluded here rather than in TableSplitPlanner, which stays database/config-agnostic.
        if (config.getSourceDBType().getID() == DatabaseType.CUBRID.getID()
                && !table.isCreateNewTable()
                && !table.isReplace()
                && table.isStartFromTargetMax()) {
            LOG.debug(
                    "[DEBUG-SPLIT] Parallel export skipped for "
                            + table.getOwner()
                            + "."
                            + table.getName()
                            + ": incremental resume (start from target max) is not compatible"
                            + " with range splitting.");
            executeTask2(taskFactory.createExportTableRecordsTask(table));
            return;
        }

        ParallelExportPlan plan =
                planParallelExport(
                        config, table, srcTable, config.getParallelExportDefaultDegree());
        if (plan == null || !plan.isParallelizable()) {
            if (plan != null) {
                LOG.debug(
                        "[DEBUG-SPLIT] Parallel export disabled for "
                                + table.getOwner()
                                + "."
                                + table.getName()
                                + ": "
                                + plan.getDisabledReason());
            }
            executeTask2(taskFactory.createExportTableRecordsTask(table));
            return;
        }

        List<SplitRange> ranges = plan.getRanges();
        LOG.info(
                "[DEBUG-SPLIT] Parallel export ENABLED for "
                        + table.getOwner()
                        + "."
                        + table.getName()
                        + ": splitColumn="
                        + plan.getSplitColumn().getName()
                        + " (basis="
                        + plan.getSplitBasis().getDescription()
                        + "), ranges="
                        + ranges.size()
                        + ", skewRatio="
                        + plan.getSkewResult().getSkewRatio());
        context.getStatusMgr()
                .registerExportRangeCount(table.getOwner(), table.getName(), ranges.size());
        TableSplitPlanner planner = new TableSplitPlanner();
        for (SplitRange range : ranges) {
            String rangeCondition = planner.buildRangeCondition(plan.getSplitColumn(), range);
            LOG.debug(
                    "[DEBUG-SPLIT] "
                            + table.getOwner()
                            + "."
                            + table.getName()
                            + " range condition: "
                            + rangeCondition);
            executeTask2(taskFactory.createExportTableRecordsRangeTask(table, rangeCondition));
        }
    }

    /**
     * Runs {@code TableSplitPlanner#plan} for one table over a short-lived source connection,
     * acquired and released the same way each individual export task acquires its own connection.
     *
     * @param srcTable the table's metadata, already resolved by the caller
     * @param degree requested degree of parallelism (currently always {@link
     *     MigrationConfiguration#getParallelExportDefaultDegree()} - see {@link
     *     #scheduleTableExport})
     * @return the plan, or {@code null} if it could not even be attempted (e.g. the planning
     *     connection/queries failed) - callers should treat this the same as a non-parallelizable
     *     plan and fall back to a single task
     */
    private ParallelExportPlan planParallelExport(
            MigrationConfiguration config,
            SourceEntryTableConfig table,
            Table srcTable,
            int degree) {
        Connection conn = null; // NOPMD
        try {
            conn = context.getConnManager().getSourceConnection();
            TableSplitPlanner planner = new TableSplitPlanner();
            return planner.plan(
                    conn,
                    srcTable,
                    config.getSourceDBType(),
                    degree,
                    config.getParallelExportSkewRatioThreshold());
        } catch (SQLException ex) {
            LOG.warn(
                    "Failed to plan parallel export for "
                            + table.getOwner()
                            + "."
                            + table.getName()
                            + "; falling back to a single unsplit task.",
                    ex);
            return null;
        } finally {
            if (conn != null) {
                context.getConnManager().closeSrc(conn);
            }
        }
    }

    /** Schedule export Primary Key tasks. */
    protected void createPKs() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> stables = config.getExpEntryTableCfg();
        List<String> names = new ArrayList<String>();
        for (SourceTableConfig tb : stables) {
            if (!tb.isCreateNewTable()) {
                continue;
            }
            if (!((SourceEntryTableConfig) tb).isCreatePK()) {
                continue;
            }
            final String name = tb.getOwner() + "." + tb.getTarget().trim().toLowerCase(Locale.US);
            if (names.indexOf(name) >= 0) {
                continue;
            }
            names.add(name);
            executeTask(taskFactory.createExportPKTask(tb));
        }
        await();
    }

    /** Schedule export foreign key tasks. */
    protected void createFKs() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> stables = config.getExpEntryTableCfg();
        List<String> names = new ArrayList<String>();
        for (SourceTableConfig tb : stables) {
            if (!tb.isCreateNewTable()) {
                continue;
            }
            final String name = tb.getTarget().trim().toLowerCase(Locale.US);
            if (names.indexOf(name) >= 0) {
                continue;
            }
            names.add(name);
            executeTask(taskFactory.createExportFKTask(tb));
        }
        await();
    }

    /** Schedule export index tasks. */
    protected void createIndexes() {
        MigrationConfiguration config = context.getConfig();
        List<SourceEntryTableConfig> stables = config.getExpEntryTableCfg();
        List<String> names = new ArrayList<String>();
        for (SourceTableConfig tb : stables) {
            if (!tb.isCreateNewTable()) {
                continue;
            }
            final String name = tb.getTarget().trim().toLowerCase(Locale.US);
            if (names.indexOf(name) >= 0) {
                continue;
            }
            names.add(name);
            executeTask(taskFactory.createExportIndexTask(tb));
        }
        await();
    }

    /** Schedule export sequence tasks. */
    protected void createSerials() {
        MigrationConfiguration config = context.getConfig();
        List<SourceSequenceConfig> sequences = config.getExpSerialCfg();
        for (SourceSequenceConfig sq : sequences) {
            executeTask(taskFactory.createExportSequenceTask(sq));
        }
        await();
    }

    /** Schedule export synonym tasks. */
    protected void createSynonyms() {
        MigrationConfiguration config = context.getConfig();
        List<SourceSynonymConfig> synonyms = config.getExpSynonymCfg();
        for (SourceSynonymConfig sn : synonyms) {
            executeTask(taskFactory.createExportSynonymTask(sn));
        }
        await();
    }

    protected void createNoSupportSynonyms() {
        MigrationConfiguration config = context.getConfig();
        List<SourceSynonymConfig> synonyms = config.getExpSynonymCfg();
        for (SourceSynonymConfig sn : synonyms) {
            executeTask(taskFactory.createExportNoSupportSynonymTask(sn));
        }
        await();
    }

    /** Schedule export function tasks. */
    protected void createFunctions() {
        MigrationConfiguration config = context.getConfig();
        List<String> functions = config.getExpFunctionCfg();
        for (String ft : functions) {
            executeTask(taskFactory.createExportFunctionTask(ft));
        }
        await();
    }

    /** Schedule export function header tasks. */
    protected void createFunctionHeaders() {
        MigrationConfiguration config = context.getConfig();
        List<SourcePlcsqlFunctionConfig> functions = config.getExpPlcsqlFunctionCfg();
        for (SourcePlcsqlFunctionConfig sfc : functions) {
            executeTask(taskFactory.createExportPlcsqlFunctionHeaderTask(sfc));
        }
        await();
    }

    /** Schedule export function body tasks. */
    protected void createFunctionBodies() {
        MigrationConfiguration config = context.getConfig();
        List<SourcePlcsqlFunctionConfig> functions = config.getExpPlcsqlFunctionCfg();
        for (SourcePlcsqlFunctionConfig sfc : functions) {
            executeTask(taskFactory.createExportPlcsqlFunctionBodyTask(sfc));
        }
        await();
    }

    /** Schedule export procedure tasks. */
    protected void createProcedures() {
        MigrationConfiguration config = context.getConfig();
        List<String> procedures = config.getExpProcedureCfg();
        for (String pd : procedures) {
            executeTask(taskFactory.createExportProcedureTask(pd));
        }
        await();
    }

    /** Schedule export procedure header tasks. */
    protected void createProcedureHeaders() {
        MigrationConfiguration config = context.getConfig();
        List<SourcePlcsqlProcedureConfig> procedures = config.getExpPlcsqlProcedureCfg();
        for (SourcePlcsqlProcedureConfig spc : procedures) {
            executeTask(taskFactory.createExportPlcsqlProcedureHeaderTask(spc));
        }
        await();
    }

    /** Schedule export procedure body tasks. */
    protected void createProcedureBodies() {
        MigrationConfiguration config = context.getConfig();
        List<SourcePlcsqlProcedureConfig> procedures = config.getExpPlcsqlProcedureCfg();
        for (SourcePlcsqlProcedureConfig spc : procedures) {
            executeTask(taskFactory.createExportPlcsqlProcedureBodyTask(spc));
        }
        await();
    }

    /** Schedule export trigger tasks. */
    protected void createTriggers() {
        MigrationConfiguration config = context.getConfig();
        List<String> triggers = config.getExpTriggerCfg();
        for (String tg : triggers) {
            executeTask(taskFactory.createExportTriggerTask(tg));
        }
        await();
    }

    /** Schedule export grant tasks. */
    protected void createGrants() {
        MigrationConfiguration config = context.getConfig();
        List<SourceGrantConfig> grants = config.getExpGrantCfg();
        for (SourceGrantConfig gr : grants) {
            executeTask(taskFactory.createExportGrantTask(gr));
        }
        await();
    }

    protected void createNoSupportGrants() {
        MigrationConfiguration config = context.getConfig();
        List<SourceGrantConfig> grants = config.getExpGrantCfg();
        for (SourceGrantConfig gr : grants) {
            executeTask(taskFactory.createExportNoSupportGrantTask(gr));
        }
        await();
    }

    /** Append update sql for updating statistics of all indexes */
    private void updateIndexStatistics() {
        executeTask(taskFactory.createUpdateStatisticsTask());
    }

    /** List of schema file names to be used in loaddb */
    private void createSchemaFileList() {
        executeTask(taskFactory.createSchemaFileListTask());
    }

    private void createCreateUserSQL() {
        executeTask(taskFactory.createCreateUserSQLTask());
    }

    private void createAllPlcsqlProcedureDDL() {
        executeTask(taskFactory.createAllPlcsqlProcedureDDL());
    }

    private void createAllPlcsqlProcedureHeaderDDL() {
        executeTask(taskFactory.createAllPlcsqlProcedureHeaderDDL());
    }

    private void createPlcsqlProcedureSourceAndDropDDL() {
        executeTask(taskFactory.createPlcsqlProcedureSourceAndDropDDL());
    }

    private void createAllPlcsqlFunctionDDL() {
        executeTask(taskFactory.createAllPlcsqlFunctionDDL());
    }

    private void createAllPlcsqlFunctionHeaderDDL() {
        executeTask(taskFactory.createAllPlcsqlFunctionHeaderDDL());
    }

    private void createPlcsqlFunctionsSourceAndDropDDL() {
        executeTask(taskFactory.createPlcsqlFunctionSourceAndDropDDL());
    }

    public void setTaskFactory(MigrationTaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    public void setContext(MigrationContext context) {
        this.context = context;
    }
}
