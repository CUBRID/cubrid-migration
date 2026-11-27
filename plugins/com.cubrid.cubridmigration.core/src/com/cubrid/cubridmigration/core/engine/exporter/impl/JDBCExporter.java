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
package com.cubrid.cubridmigration.core.engine.exporter.impl;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.JDBCConManager;
import com.cubrid.cubridmigration.core.engine.MigrationStatusManager;
import com.cubrid.cubridmigration.core.engine.RecordExportedListener;
import com.cubrid.cubridmigration.core.engine.ThreadUtils;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.LobMigrationErrorEvent;
import com.cubrid.cubridmigration.core.engine.event.MigrationErrorEvent;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;
import com.cubrid.cubridmigration.core.engine.exporter.MigrationExporter;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.core.trans.DBTransformHelper;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.cubrid.Data2StrTranslator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBCMigrationExporter Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-9 created by Kevin Cao
 */
public class JDBCExporter extends MigrationExporter {
    protected static final Logger LOG = LogUtil.getLogger(JDBCExporter.class);

    /**
     * JDBCObjContainer to reuse the JDBC objects
     *
     * @author Kevin Cao
     * @version 1.0 - 2014-2-25 created by Kevin Cao
     */
    protected static class JDBCObjContainer {
        private Connection conn = null;
        private Statement stmt = null; // NOPMD
        private ResultSet rs = null; // NOPMD

        public Connection getConn() {
            return conn;
        }

        public void setConn(Connection conn) {
            this.conn = conn;
        }

        public Statement getStmt() {
            return stmt;
        }

        public void setStmt(Statement stmt) {
            this.stmt = stmt;
        }

        public ResultSet getRs() {
            return rs;
        }

        public void setRs(ResultSet rs) {
            this.rs = rs;
        }
    }

    protected JDBCConManager connManager;
    protected MigrationStatusManager msm;

    // Only set when config.isRecordSerializationEnabled() && config.targetIsDBDump() - see
    // MigrationProcessManager#buildTaskFactory, which hands this exporter the *same*
    // Data2StrTranslator instance the dump-file importer already builds for itself, rather than
    // constructing a second one. Data2StrTranslator's own caches are already safe for concurrent
    // use by multiple export range threads (see its dataTypeIdCache).
    protected Data2StrTranslator unloadFileUtil;

    /**
     * Export all records of all tables
     *
     * @param oneNewRecord processor
     */
    public void exportAllRecords(RecordExportedListener oneNewRecord) {
        // This method will not be called by clients
    }

    /**
     * Go to next record of result set
     *
     * @param rs result set
     * @return success or failed
     */
    protected boolean nextRecord(ResultSet rs) {
        try {
            return rs.next();
        } catch (SQLException e) {
            throw new NormalMigrationException(e);
        }
    }

    /**
     * Resolves each export column's {@link Column} once per table instead of once per row. {@code
     * st.getColumnByName(...)} is a linear, case-insensitive scan over all of the table's columns
     * ({@link com.cubrid.cubridmigration.core.dbobject.TableOrView#getColumnByName(String)}), so
     * for a wide table (hundreds of columns) doing this per row/per column is the dominant cost of
     * {@link #createNewRecord}. The column list a table exports never changes mid-export, so the
     * resolved array can be computed once and reused for every row/page.
     *
     * @param st source table
     * @param expCols source table's export columns, in the same order the caller will iterate them
     * @return one resolved {@link Column} per {@code expCols} entry, same order/index
     */
    protected Column[] resolveSourceColumns(Table st, List<SourceColumnConfig> expCols) {
        Column[] cols = new Column[expCols.size()];
        for (int i = 0; i < expCols.size(); i++) {
            cols[i] = st.getColumnByName(expCols.get(i).getName());
        }
        // This should log exactly once per table export (never once per row/page) - if it shows
        // up more than once for the same table in one run, the column cache isn't actually being
        // reused and something upstream is calling this per row again.
        LOG.info(
                "[DEBUG-COLCACHE] resolveSourceColumns() for "
                        + st.getOwner()
                        + "."
                        + st.getName()
                        + ": resolved "
                        + cols.length
                        + " column(s) once for this export.");
        return cols;
    }

    /**
     * Resolves each export column's *target* {@link Column} once per table - the export-side
     * counterpart of {@link #resolveSourceColumns}, needed only when record serialization (see
     * {@link #isRecordSerializationEligible}) is in play, since that is the only path that needs to
     * know the target type before the record ever reaches the importer.
     *
     * @param targetTable the resolved target table, or {@code null} if it doesn't exist yet
     * @param expCols source table's export columns, in the same order the caller will iterate them
     * @return one resolved target {@link Column} per {@code expCols} entry (possibly {@code null}
     *     if a target column can't be found), same order/index - empty if {@code targetTable} is
     *     {@code null}
     */
    protected Column[] resolveTargetColumns(Table targetTable, List<SourceColumnConfig> expCols) {
        if (targetTable == null) {
            return new Column[0];
        }
        Column[] cols = new Column[expCols.size()];
        for (int i = 0; i < expCols.size(); i++) {
            cols[i] = targetTable.getColumnByName(expCols.get(i).getTarget());
        }
        return cols;
    }

    /**
     * Decides, once per table (never once per row), whether this export can convert each record to
     * its final dump-file string immediately instead of leaving that to the importer.
     *
     * <p>Requires all of:
     *
     * <ul>
     *   <li>{@code config.isRecordSerializationEnabled()} - the opt-in user setting
     *   <li>{@code config.targetIsDBDump()} - {@link #unloadFileUtil} is only wired up for this
     *       target (see {@code MigrationProcessManager#buildTaskFactory}); binding a value to an
     *       online target needs the original typed value, not a pre-joined string
     *   <li>no export column uses a user-defined data handler - its output can only be known by
     *       actually running it, which still has to happen wherever the handler runs today
     *   <li>no export column's target type is a CUBRID collection, BLOB or CLOB - {@link
     *       Data2StrTranslator#stringValueOf} would write a LOB file as a side effect for those,
     *       and the file's directory is only known to the importer, not this exporter
     * </ul>
     *
     * @param tableLabel {@code owner.name} of the table being exported, for the {@code
     *     [DEBUG-RECORDSERIALIZE]} log line only
     * @param expCols source table's export columns
     * @param targetCols {@code expCols[i]}'s resolved target {@link Column}, from {@link
     *     #resolveTargetColumns} - same order/index as {@code expCols}
     * @return true if every record from this table can be pre-serialized during export
     */
    protected boolean isRecordSerializationEligible(
            String tableLabel, List<SourceColumnConfig> expCols, Column[] targetCols) {
        if (!config.isRecordSerializationEnabled() || !config.targetIsDBDump()) {
            return false;
        }
        if (unloadFileUtil == null || targetCols.length != expCols.size()) {
            LOG.info(
                    "[DEBUG-RECORDSERIALIZE] Disabled for "
                            + tableLabel
                            + ": target table/columns could not be resolved.");
            return false;
        }
        for (SourceColumnConfig scc : expCols) {
            if (StringUtils.isNotBlank(scc.getUserDataHandler())) {
                LOG.info(
                        "[DEBUG-RECORDSERIALIZE] Disabled for "
                                + tableLabel
                                + ": column "
                                + scc.getName()
                                + " has a user-defined data handler.");
                return false;
            }
        }
        CUBRIDDataTypeHelper dataTypeHelper = CUBRIDDataTypeHelper.getInstance(null);
        for (int i = 0; i < targetCols.length; i++) {
            Column targetColumn = targetCols[i];
            if (targetColumn == null) {
                LOG.info(
                        "[DEBUG-RECORDSERIALIZE] Disabled for "
                                + tableLabel
                                + ": target column for "
                                + expCols.get(i).getName()
                                + " was not found.");
                return false;
            }
            String dataType = targetColumn.getDataType();
            if (dataTypeHelper.isCollection(dataType)) {
                LOG.info(
                        "[DEBUG-RECORDSERIALIZE] Disabled for "
                                + tableLabel
                                + ": column "
                                + targetColumn.getName()
                                + " is a collection type ("
                                + dataType
                                + ").");
                return false;
            }
            int dataTypeId = dataTypeHelper.getCUBRIDDataTypeID(dataType);
            if (dataTypeId == DataTypeConstant.CUBRID_DT_BLOB
                    || dataTypeId == DataTypeConstant.CUBRID_DT_CLOB) {
                LOG.info(
                        "[DEBUG-RECORDSERIALIZE] Disabled for "
                                + tableLabel
                                + ": column "
                                + targetColumn.getName()
                                + " is a LOB type ("
                                + dataType
                                + ").");
                return false;
            }
        }
        LOG.info(
                "[DEBUG-RECORDSERIALIZE] Enabled for "
                        + tableLabel
                        + ": "
                        + expCols.size()
                        + " column(s) will be pre-serialized during export.");
        return true;
    }

    /**
     * Decides, once per table, whether its records can be pre-serialized during export and, if so,
     * resolves the target columns {@link #serializeRecord} needs. Every export loop that builds
     * records calls this instead of repeating the check, so none of them can forget it: {@link
     * #handleSQL} and {@code CUBRIDJDBCExporter}'s keyset-paging loop both do.
     *
     * <p>Call it once per table export, never per page or per row: {@link
     * #isRecordSerializationEligible} logs a line and looks up the target table each time.
     *
     * @param stc source table configuration (its target name locates the target table)
     * @param tableLabel {@code owner.name} of the table being exported, for log lines only
     * @param expCols source table's export columns
     * @return one resolved target {@link Column} per {@code expCols} entry (same order) when every
     *     record of this table can be pre-serialized; {@code null} when it cannot - serialization
     *     disabled, a non-dump target, or an ineligible table - so callers use the {@code null}
     *     check as their "serialize or not" switch
     */
    protected Column[] resolveSerializationTargetColumns(
            SourceTableConfig stc, String tableLabel, List<SourceColumnConfig> expCols) {
        if (!config.isRecordSerializationEnabled() || !config.targetIsDBDump()) {
            return null;
        }
        Table targetTable = config.getTargetTableSchema(stc.getTarget());
        Column[] targetCols = resolveTargetColumns(targetTable, expCols);
        return isRecordSerializationEligible(tableLabel, expCols, targetCols) ? targetCols : null;
    }

    /**
     * Converts {@code record} to its final dump-file line and stores it on the record itself, so
     * the importer can skip its own per-column convert+format work for it (see {@code
     * OfflineImporter.UnloadFileWriter#writeData}). Only called when {@link
     * #isRecordSerializationEligible} already confirmed this table qualifies.
     *
     * <p>Failures here fall back silently (the record is left without a serialized string, so the
     * importer just does the conversion itself as usual) rather than propagating - this is an
     * opt-in performance path, not a correctness-critical one, and an uncaught exception here would
     * otherwise be treated as a fatal, whole-migration-aborting error (see {@code
     * ExportTask#run}/{@code MigrationErrorEvent#isFatalError}).
     *
     * @param record the already-built record (see {@link #createNewRecord}) to serialize in place
     * @param expCols source table's export columns
     * @param targetCols {@code expCols[i]}'s resolved target {@link Column}, from {@link
     *     #resolveTargetColumns} - same order/index as {@code expCols}
     */
    protected void serializeRecord(
            Record record, List<SourceColumnConfig> expCols, Column[] targetCols) {
        try {
            final DBTransformHelper dbTransformHelper = config.getDBTransformHelper();
            final Map<String, Object> recordMap = record.getColumnValueMap();
            final List<Record.ColumnValue> columnValues = record.getColumnValueList();
            final List<String> lobFiles = new ArrayList<String>();
            final List<String> columnStrings = new ArrayList<String>(expCols.size());
            for (int i = 0; i < expCols.size(); i++) {
                SourceColumnConfig scc = expCols.get(i);
                Column targetColumn = targetCols[i];
                Record.ColumnValue cv = columnValues.get(i);
                Object targetValue =
                        dbTransformHelper.convertValueToTargetDBValue(
                                config,
                                recordMap,
                                scc,
                                cv.getColumn(),
                                targetColumn,
                                cv.getValue());
                columnStrings.add(
                        unloadFileUtil.stringValueOf(targetValue, targetColumn, lobFiles));
            }
            if (!lobFiles.isEmpty()) {
                // Should be unreachable given isRecordSerializationEligible's checks - defensive
                // only: a LOB file was written as a side effect that nothing here tracks, so don't
                // hand back a string that silently drops it.
                return;
            }
            record.setSerializedString(unloadFileUtil.getRecordString(columnValues, columnStrings));
        } catch (RuntimeException ex) {
            LOG.warn("[RECORD SERIALIZATION] Falling back to normal import-side conversion.", ex);
        }
    }

    /**
     * Create a new record with target table columns configurations and source values
     *
     * @param st source table
     * @param expCols source table's export columns
     * @param sCols {@code expCols[i]}'s resolved {@link Column}, from {@link #resolveSourceColumns}
     *     - same order/index as {@code expCols}
     * @param rs result set
     * @return new record object
     */
    protected Record createNewRecord(
            Table st, List<SourceColumnConfig> expCols, Column[] sCols, ResultSet rs) {
        Column sCol = null;
        Record record = new Record();
        try {
            final DBExportHelper srcDBExportHelper = getSrcDBExportHelper();
            for (int ci = 1; ci <= expCols.size(); ci++) {
                SourceColumnConfig cc = expCols.get(ci - 1);
                sCol = sCols[ci - 1];
                Object value = srcDBExportHelper.getJdbcObject(rs, sCol);

                if (value instanceof LobMigrationErrorEvent) {
                    LobMigrationErrorEvent LobError = (LobMigrationErrorEvent) value;
                    String lobWarning =
                            "[LOB WARNING]  table: "
                                    + st.getName()
                                    + "  column: "
                                    + (sCol != null ? sCol.getName() : "")
                                    + "  pk:"
                                    + getPkValues(st, record);

                    LOG.warn(lobWarning, LobError.getError());
                    eventHandler.handleEvent(
                            new MigrationErrorEvent(
                                    new NormalMigrationException(lobWarning, LobError.getError())));
                }
                record.addColumnValue(sCol, value);
            }
            return record;
        } catch (NormalMigrationException e) {
            LOG.error("[RECORD ERROR]", e);
            eventHandler.handleEvent(new MigrationErrorEvent(e));
        } catch (SQLException e) {
            LOG.error("[RECORD ERROR]", e);
            eventHandler.handleEvent(
                    new MigrationErrorEvent(
                            new NormalMigrationException(
                                    "[RECORD ERROR]  table: "
                                            + st.getName()
                                            + "  column: "
                                            + (sCol != null ? sCol.getName() : "")
                                            + "  pk:"
                                            + getPkValues(st, record),
                                    e)));
        } catch (Exception e) {
            LOG.error("[RECORD ERROR]", e);
            eventHandler.handleEvent(
                    new MigrationErrorEvent(
                            new NormalMigrationException(
                                    "[RECORD ERROR]  table: "
                                            + st.getName()
                                            + "  column: "
                                            + (sCol != null ? sCol.getName() : "")
                                            + "  pk:"
                                            + getPkValues(st, record),
                                    e)));
        }
        return null;
    }

    /**
     * Returns the value of pk to be used when RECORD ERROR
     *
     * @param table Table
     * @param record Record
     * @return Returns the value of pk
     */
    private String getPkValues(Table table, Record record) {
        List<String> tablePkColumns = table.getPk() != null ? table.getPk().getPkColumns() : null;
        Map<String, Object> columnValueMap = record.getColumnValueMap();
        StringBuffer pkValue = new StringBuffer();

        if (tablePkColumns == null || columnValueMap.isEmpty()) {
            return " NULL";
        }

        for (String tablePkColumn : tablePkColumns) {
            String value = columnValueMap.get(tablePkColumn).toString();
            pkValue.append(" " + value);
        }

        return pkValue.toString();
    }

    /**
     * Export source data records
     *
     * @param stc source table configuration
     * @param newRecordProcessor to process new records
     */
    public void exportTableRecords(
            SourceTableConfig stc, RecordExportedListener newRecordProcessor) {
        exportTableRecords(stc, null, newRecordProcessor);
    }

    /**
     * Export source data records, restricted to an extra caller-supplied predicate. Used by {@code
     * TableRecordRangeExportTask} to read only one {@code TableSplitPlanner} range instead of the
     * whole table; ordinary (unsplit) exports go through {@link
     * #exportTableRecords(SourceTableConfig, RecordExportedListener)}, which delegates here with
     * {@code extraCondition == null}, so this is the only copy of the export loop.
     *
     * @param stc source table configuration
     * @param extraCondition a standalone boolean SQL predicate (e.g. from {@code
     *     TableSplitPlanner#buildRangeCondition}) to AND onto the table's existing condition, or
     *     {@code null}/blank for no extra restriction
     * @param newRecordProcessor to process new records
     */
    public void exportTableRecords(
            SourceTableConfig stc,
            String extraCondition,
            RecordExportedListener newRecordProcessor) {
        // Start normal exporting.
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]exportTableRecordsByPaging()");
        }

        Table sTable;
        if (stc instanceof SourceSQLTableConfig) {
            sTable = config.getSrcSQLSchema(stc.getName());
        } else {
            sTable = config.getSrcTableSchema(stc.getOwner(), stc.getName());
        }

        if (sTable == null) {
            throw new NormalMigrationException("Table " + stc.getName() + " was not found.");
        }
        final PK srcPK = sTable.getPk();
        Connection conn = connManager.getSourceConnection(); // NOPMD
        try {
            final DBExportHelper expHelper = getSrcDBExportHelper();
            PK pk = expHelper.supportFastSearchWithPK(conn) ? srcPK : null;
            newRecordProcessor.startExportTable(stc.getName());
            List<Record> records = new ArrayList<Record>();
            List<SourceColumnConfig> expColConfs = stc.getColumnConfigList();
            Column[] sCols = resolveSourceColumns(sTable, expColConfs);
            long totalExported = 0L;
            long intPageCount = config.getPageFetchCount();
            String sql = combineCondition(expHelper.getSelectSQL(stc, config), extraCondition);
            // Custom SQL tables are never paginated (DBExportHelper#getPagedSelectSQL returns their
            // SQL as-is), so they are read in one execution just like a dialect without paging.
            final boolean paged =
                    expHelper.isPagingSupported() && !(stc instanceof SourceSQLTableConfig);
            while (true) {
                if (interrupted) {
                    return;
                }
                long realPageCount = intPageCount;
                if (!config.isImplicitEstimate()) {
                    realPageCount =
                            Math.min(sTable.getTableRowCount() - totalExported, intPageCount);
                }
                String pagesql =
                        expHelper.getPagedSelectSQL(stc, sql, realPageCount, totalExported, pk);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[SQL]PAGINATED=" + pagesql);
                }
                long recordCountOfQuery =
                        handleSQL(
                                conn,
                                pagesql,
                                stc,
                                sTable,
                                expColConfs,
                                sCols,
                                records,
                                newRecordProcessor);
                totalExported = totalExported + recordCountOfQuery;
                // Without paging, one execution already returned every row the query matches, so
                // there is no "next page": re-running the same SQL would re-read (and re-import)
                // the same rows. This must not depend on comparing against the whole table's row
                // count - a TableSplitPlanner range only ever returns its own, smaller subset.
                if (!paged) {
                    break;
                }
                // Stop fetching condition: no result;less then fetching count;great then total
                // count
                if (isLatestPage(sTable, totalExported, recordCountOfQuery)) {
                    break;
                }
            }
            if (!records.isEmpty()) {
                newRecordProcessor.processRecords(stc.getName(), records);
            }
        } finally {
            newRecordProcessor.endExportTable(stc.getName());
            connManager.closeSrc(conn);
        }
    }

    /**
     * When new record was exported, CMT should make a choice to commit or continue or waiting for
     * more free memory.
     *
     * @param stc SourceTableConfig
     * @param newRecordProcessor RecordExportedListener
     * @param sTable Table
     * @param records List<Record> Notice:it will be cleared after committed.
     */
    protected void handleCommit(
            SourceTableConfig stc,
            RecordExportedListener newRecordProcessor,
            Table sTable,
            List<Record> records) {
        // Watching memory to avoid out of memory errors
        int status = MigrationStatusManager.STATUS_WAITING;
        int counter = 0;
        while (true) {
            status = msm.isCommitNow(sTable.getName(), records.size(), config.getCommitCount());
            if (status == MigrationStatusManager.STATUS_WAITING) {
                ThreadUtils.threadSleep(1000, null);
                counter++;
            } else {
                break;
            }
            if (counter >= 10) {
                status = MigrationStatusManager.STATUS_COMMIT;
                break;
            }
        }
        if (MigrationStatusManager.STATUS_COMMIT == status) {
            newRecordProcessor.processRecords(stc.getName(), records);
            // After records processed, clear it.
            records.clear();
        }
    }

    /**
     * Execute the selection SQL and handle the result set.
     *
     * @param conn Connection
     * @param sql String
     * @param stc SourceTableConfig
     * @param sTable Source Table
     * @param expColConfs List<SourceColumnConfig> of Source Table
     * @param sCols {@code expColConfs[i]}'s resolved {@link Column}, from {@link
     *     #resolveSourceColumns} - same order/index as {@code expColConfs}
     * @param records data cache
     * @param newRecsHandler processor
     * @return how many records were handled.
     */
    protected long handleSQL(
            Connection conn,
            String sql,
            SourceTableConfig stc,
            Table sTable,
            List<SourceColumnConfig> expColConfs,
            Column[] sCols,
            List<Record> records,
            RecordExportedListener newRecsHandler) {
        JDBCObjContainer joc = new JDBCObjContainer();
        joc.setConn(conn);
        // Cheap no-op unless the feature is actually on: the target table/columns are only
        // looked up when there's a chance record serialization applies at all.
        String tableLabel = sTable.getOwner() + "." + sTable.getName();
        Column[] targetCols = resolveSerializationTargetColumns(stc, tableLabel, expColConfs);
        boolean serialize = targetCols != null;
        long serializedCount = 0;
        try {
            long totalExported = 0;
            // Execute SQL with retry
            joc = getResultSet(sql, null, joc);
            if (joc.getRs() == null) {
                return totalExported;
            }
            while (nextRecord(joc.getRs())) {
                if (interrupted) {
                    return totalExported;
                }
                totalExported++;
                Record record = createNewRecord(sTable, expColConfs, sCols, joc.getRs());
                if (record == null) {
                    continue;
                }
                if (serialize) {
                    serializeRecord(record, expColConfs, targetCols);
                    if (record.getSerializedString() != null) {
                        serializedCount++;
                    }
                }
                records.add(record);
                handleCommit(stc, newRecsHandler, sTable, records);
            }
            return totalExported;
        } finally {
            if (serialize) {
                LOG.info(
                        "[DEBUG-RECORDSERIALIZE] "
                                + tableLabel
                                + ": "
                                + serializedCount
                                + " record(s) pre-serialized during export this call.");
            }
            Closer.close(joc.getRs());
            Closer.close(joc.getStmt());
        }
    }

    /**
     * If it is page SQL with page query parameters
     *
     * @param sql originate SQL
     * @param pageSQL replaced by page query parameters SQL
     * @return true if it is the SQL with page query parameters
     */
    protected boolean isWithPageQueryParamSQL(String sql, String pageSQL) {
        return !sql.equals(pageSQL);
    }

    /**
     * Get result set with retry.
     *
     * <p>A {@code null}/empty {@code params} uses a plain {@link Statement} instead of an unbound
     * {@link PreparedStatement}: the CUBRID JDBC driver's {@code CUBRIDPreparedStatement
     * #executeQuery()} can throw "The query is not applicable to the executeQuery()" for a
     * PreparedStatement that was never given any {@code setXxx(...)} call, even though the same
     * literal-only SELECT runs fine as a plain {@code Statement} (see {@code
     * CUBRIDJDBCExporter#initExportingStatus}, which already does this for its MIN/MAX/COUNT
     * query). Every caller of this method builds the query's literals directly into {@code sql}
     * rather than binding them, so {@code params} is {@code null} far more often than not.
     *
     * @param sql to be executed.
     * @param params parameters to be set to execute SQL
     * @param joc to return result set and statement.
     */
    protected JDBCObjContainer getResultSet(String sql, Object[] params, JDBCObjContainer joc) {
        if (joc.getConn() == null) {
            throw new IllegalArgumentException("Connection can't be NULL.");
        }
        boolean hasParams = params != null && params.length > 0;
        // Reset objects.
        joc.setStmt(null);
        joc.setRs(null);
        Statement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD
        int retryCount = 0;
        while (true) {
            try {
                if (hasParams) {
                    PreparedStatement pstmt =
                            joc.getConn()
                                    .prepareStatement(
                                            sql,
                                            ResultSet.TYPE_FORWARD_ONLY,
                                            ResultSet.CONCUR_READ_ONLY); // NOPMD
                    getSrcDBExportHelper().configStatement(pstmt);
                    for (int i = 0; i < params.length; i++) {
                        pstmt.setObject(i + 1, params[i]);
                    }
                    rs = pstmt.executeQuery(); // NOPMD
                    stmt = pstmt;
                } else {
                    Statement plainStmt =
                            joc.getConn()
                                    .createStatement(
                                            ResultSet.TYPE_FORWARD_ONLY,
                                            ResultSet.CONCUR_READ_ONLY); // NOPMD
                    getSrcDBExportHelper().configStatement(plainStmt);
                    rs = plainStmt.executeQuery(sql); // NOPMD
                    stmt = plainStmt;
                }
                break;
            } catch (Exception ex) {
                LOG.error(
                        "[DEBUG-FETCHSIZE] getResultSet() attempt "
                                + (retryCount + 1)
                                + " failed for sql: "
                                + sql,
                        ex);
                // Release statement and result set.
                Closer.close(rs);
                Closer.close(stmt);
                stmt = null;
                rs = null;
                ThreadUtils.threadSleep(2000, eventHandler);
                retryCount++;
                if (retryCount == 3) {
                    throw new NormalMigrationException(ex);
                }
            }
        }
        joc.setStmt(stmt);
        joc.setRs(rs);
        return joc;
    }

    /**
     * Retrieves the source DB export helper
     *
     * @return DBExportHelper
     */
    protected DBExportHelper getSrcDBExportHelper() {
        return config.getSourceDBType().getExportHelper();
    }

    /**
     * ANDs {@code extraCondition} onto {@code sql}, which may or may not already have a WHERE
     * clause (added by {@code DBExportHelper#getSelectSQL} from the table's own condition).
     *
     * @param sql a SELECT statement, with or without a WHERE clause
     * @param extraCondition a standalone boolean predicate, or {@code null}/blank for none
     * @return {@code sql} unchanged if {@code extraCondition} is blank, otherwise {@code sql} with
     *     {@code extraCondition} ANDed (or WHEREd) on
     */
    protected String combineCondition(String sql, String extraCondition) {
        if (StringUtils.isBlank(extraCondition)) {
            return sql;
        }
        String keyword = sql.toUpperCase(Locale.US).contains(" WHERE ") ? " AND " : " WHERE ";
        return sql + keyword + "(" + extraCondition + ")";
    }

    public void setConnManager(JDBCConManager connManager) {
        this.connManager = connManager;
    }

    public void setStatusManager(MigrationStatusManager msm) {
        this.msm = msm;
    }

    public void setUnloadFileUtil(Data2StrTranslator unloadFileUtil) {
        this.unloadFileUtil = unloadFileUtil;
    }

    /**
     * Whether the page just read was the last one. Only called for an export that really is paged
     * (see {@link DBExportHelper#isPagingSupported()}); an unpaged export reads everything in one
     * execution and never reaches this check.
     *
     * @param sTable source table
     * @param exportedRecords how many records this export has read so far
     * @param recordCountOfCurrentPage how many records the page just read returned
     * @return true if there is no further page to read
     */
    protected boolean isLatestPage(
            Table sTable, long exportedRecords, long recordCountOfCurrentPage) {
        return recordCountOfCurrentPage == 0
                || recordCountOfCurrentPage < config.getPageFetchCount()
                || (!config.isImplicitEstimate() && exportedRecords >= sTable.getTableRowCount());
    }
}
