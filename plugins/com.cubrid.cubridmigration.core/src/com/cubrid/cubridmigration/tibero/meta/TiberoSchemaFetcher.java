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
package com.cubrid.cubridmigration.tibero.meta;

import static com.cubrid.cubridmigration.core.dbobject.ProcedureConstants.*;
import static com.cubrid.cubridmigration.tibero.meta.TiberoSqlConstants.*;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.CommonUtils;
import com.cubrid.cubridmigration.core.common.TimeZoneUtils;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.AbstractJDBCSchemaFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.cubrid.CUBRIDSQLHelper;
import com.cubrid.cubridmigration.tibero.TiberoDataTypeHelper;

import org.slf4j.Logger;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class TiberoSchemaFetcher extends AbstractJDBCSchemaFetcher {

    private static final Logger LOG = LogUtil.getLogger(TiberoSchemaFetcher.class);

    private final TiberoCommentQueryLoader commentQueryLoader = new TiberoCommentQueryLoader();
    private final TiberoPartitionMetadataLoader partitionMetadataLoader =
            new TiberoPartitionMetadataLoader();
    private final TiberoConstraintIndexMetadataLoader constraintIndexMetadataLoader =
            new TiberoConstraintIndexMetadataLoader();
    private final TiberoRoutineTriggerGrantLoader routineTriggerGrantLoader =
            new TiberoRoutineTriggerGrantLoader();

    private static final List<Object> COLUMNS_RESET1 =
            CommonUtils.createListWithArray(
                    new Object[] {"CHAR", "NCHAR", "VARCHAR", "VARCHAR2", "NVARCHAR2", "LONG"});

    private static final List<Object> COLUMNS_RESET2 =
            CommonUtils.createListWithArray(new Object[] {"RAW", "LONG RAW"});

    private static final String OBJECT_TYPE_TABLE = "TABLE";
    private static final String OBJECT_TYPE_TRIGGER = "TRIGGER";
    private static final String OBJECT_TYPE_VIEW = "VIEW";

    public TiberoSchemaFetcher() {
        factory = new DBObjectFactory() {};
    }

    /**
     * Build Catalog
     *
     * @param conn Connection
     * @param cp ConnParameters
     * @param filter IBuildSchemaFilter
     * @return Catalog
     * @throws SQLException e
     */
    public Catalog buildCatalog(final Connection conn, ConnParameters cp, IBuildSchemaFilter filter)
            throws SQLException {
        final Catalog catalog = super.buildCatalog(conn, cp, filter);
        catalog.setDatabaseType(DatabaseType.TIBERO);
        setCharset(conn, catalog);
        setCatalogTimezone(catalog);
        final List<Schema> schemaList = new ArrayList<Schema>(catalog.getSchemas());
        for (Schema schema : schemaList) {
            LOG.debug("[VAR]schema={}", schema.getName());
        }
        return catalog;
    }

    @Override
    public Catalog buildSchemaObjects(
            final Connection conn, final SchemaCatalog sc, List<String> schemaNames)
            throws SQLException {
        Catalog catalog = super.buildSchemaObjects(conn, sc, schemaNames);
        return catalog;
    }

    /**
     * build Partitions
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     */
    protected void buildPartitions(
            final Connection conn, final Catalog catalog, final Schema schema) {
        partitionMetadataLoader.buildPartitions(
                conn,
                schema,
                factory,
                new TiberoPartitionMetadataLoader.PartitionDDLProvider() {
                    public String getPartitionDDL(Table table) {
                        return getSourcePartitionDDL(table);
                    }
                });
    }

    /**
     * Fetch all stored procedures of the given schemata.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildProcedures(
            Connection conn, Catalog catalog, Schema schema, IBuildSchemaFilter filter)
            throws SQLException {
        List<PlcsqlProcedure> procedures = new ArrayList<>();
        List<PlcsqlFunction> functions = new ArrayList<>();
        List<TiberoPlsqlProcedure> tiberoProcedures =
                routineTriggerGrantLoader.getAllProcedures(conn, schema.getName());

        for (TiberoPlsqlProcedure tibProc : tiberoProcedures) {
            if (tibProc.getProcedureType().equals(PROCEDURE)) {
                procedures.add(factory.createPlcsqlProcedure(tibProc));
            } else {
                functions.add(factory.createPlcsqlFunction(tibProc));
            }
        }

        schema.setPlcsqlProcedures(procedures);
        schema.setPlcsqlFunctions(functions);
    }

    /**
     * Fetch all sequences of the given schemata. <br>
     * SEQUENCE_NAME NOT NULL VARCHAR2(30) <br>
     * MIN_VALUE NUMBER<br>
     * MAX_VALUE NUMBER<br>
     * INCREMENT_BY NOT NULL NUMBER<br>
     * CYCLE_FLAG VARCHAR2(1)<br>
     * ORDER_FLAG VARCHAR2(1)<br>
     * CACHE_SIZE NOT NULL NUMBER<br>
     * LAST_NUMBER NOT NULL NUMBER<br>
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildSequence(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement(SQL_SHOW_SEQUENCES);
            stmt.setString(1, schema.getName());
            LOG.debug("[SQL]{}, 1={}", SQL_SHOW_SEQUENCES, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String sequenceName = rs.getString("SEQUENCE_NAME");
                if (filter != null && filter.filter(schema.getName(), sequenceName)) {
                    continue;
                }
                BigInteger minValue = new BigInteger(rs.getString("MIN_VALUE"));
                BigInteger maxValue = new BigInteger(rs.getString("MAX_VALUE"));
                BigInteger incrementBy = new BigInteger(rs.getString("INCREMENT_BY"));
                BigInteger currentValue = new BigInteger(rs.getString("LAST_NUMBER"));
                boolean cycleFlag = "N".equals(rs.getString("CYCLE_FLAG")) ? false : true;
                int cacheSize = rs.getInt("CACHE_SIZE");
                Sequence seq =
                        factory.createSequence(
                                sequenceName,
                                minValue,
                                maxValue,
                                incrementBy,
                                currentValue,
                                cycleFlag,
                                cacheSize);
                seq.setNoMaxValue(false);
                seq.setNoMinValue(false);
                seq.setNoCache(cacheSize <= 1);
                seq.setOwner(schema.getName());
                schema.addSequence(seq);
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    protected void buildSynonym(
            Connection conn, Catalog catlog, Schema schema, IBuildSchemaFilter filter)
            throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement(SQL_SHOW_SYNONYM);
            stmt.setString(1, schema.getName());
            LOG.debug("[SQL]{}, 1={}", SQL_SHOW_SYNONYM, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String synonymName = rs.getString("SYNONYM_NAME");
                if (filter != null && filter.filter(schema.getName(), synonymName)) {
                    continue;
                }
                String targetOwnerName = rs.getString("ORG_OBJECT_OWNER");
                String targetName = rs.getString("ORG_OBJECT_NAME");
                Synonym synonym = factory.createSynonym();
                synonym.setName(synonymName);
                synonym.setOwner(schema.getName());
                synonym.setPublic(false);
                synonym.setObjectName(targetName);
                synonym.setObjectOwner(targetOwnerName);
                synonym.setDDL(CUBRIDSQLHelper.getInstance(null).getSynonymDDL(synonym, true));
                schema.addSynonym(synonym);
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * Get metadata from SQLTable
     *
     * @param resultSetMeta ResultSetMetaData
     * @return SourceTable
     * @throws SQLException e
     */
    public Table buildSQLTable(ResultSetMetaData resultSetMeta) throws SQLException {
        TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
        Table sourceTable = super.buildSQLTable(resultSetMeta);
        List<Column> columns = sourceTable.getColumns();
        for (Column column : columns) {
            if (isNULLType(column.getDataType())) {
                column.setDataType("VARCHAR");
                column.setJdbcIDOfDataType(Types.VARCHAR);
            }
            column.setShownDataType(dtHelper.getShownDataType(column));
        }
        return sourceTable;
    }

    /**
     * Extract Table's Columns
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param table Table
     * @throws SQLException e
     */
    protected void buildTableColumns(
            final Connection conn, final Catalog catalog, final Schema schema, final Table table)
            throws SQLException {
        super.buildTableColumns(conn, catalog, schema, table);

        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD
        try {
            stmt = conn.prepareStatement(SQL_GET_COLUMNS);
            stmt.setString(1, schema.getName());
            stmt.setString(2, table.getName());
            LOG.debug("[SQL]{}, 1={}, 2={}", SQL_GET_COLUMNS, schema.getName(), table.getName());
            TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
            rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    String columnName = rs.getString("COLUMN_NAME");
                    LOG.debug("[VAR]columnName={}", columnName);
                    Column column = table.getColumnWithNoCase(columnName);
                    if (column == null) {
                        continue;
                    }
                    fillColumnMetadata(catalog, column, rs, dtHelper);
                } catch (Exception ex) {
                    LOG.error("Read table column information error:{}", table.getName(), ex);
                }
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * Fill column metadata from ResultSet.
     *
     * @param column Column
     * @param rs ResultSet
     * @param dtHelper TiberoDataTypeHelper
     * @throws SQLException e
     */
    private void fillColumnMetadata(
            Catalog catalog, Column column, ResultSet rs, TiberoDataTypeHelper dtHelper)
            throws SQLException {
        String dataType = rs.getString("DATA_TYPE");
        column.setDataType(dataType);

        column.setByteLength(rs.getInt("DATA_LENGTH"));
        String precisionStr = rs.getString("DATA_PRECISION");
        Integer precision = precisionStr == null ? null : rs.getInt("DATA_PRECISION");
        column.setPrecision(precision);
        String scaleStr = rs.getString("DATA_SCALE");
        Integer scale = scaleStr == null ? null : rs.getInt("DATA_SCALE");
        column.setScale(scale);

        column.setJdbcIDOfDataType(dtHelper.getJdbcDataTypeID(catalog, dataType, precision, scale));

        column.setByteLength(rs.getInt("DATA_LENGTH"));
        column.setNullable(!"N".equalsIgnoreCase(rs.getString("NULLABLE")));

        String defaultValue = rs.getString("DATA_DEFAULT");
        if (defaultValue != null) {
            defaultValue = defaultValue.trim();
        }
        if ("NULL".equals(defaultValue)) {
            column.setDefaultValue(null);
        } else {
            column.setDefaultValue(defaultValue);
        }

        column.setCharLength(rs.getInt("CHAR_LENGTH"));
        column.setCharUsed(rs.getString("CHAR_USED"));
        resetTiberoColumnPrecision(column);

        column.setShownDataType(dtHelper.getShownDataType(column));
        String comment = rs.getString("COMMENTS");
        column.setComment(commentEditor(comment));
    }

    @Override
    protected void buildTables(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        super.buildTables(conn, catalog, schema, filter);
        Map<String, String> comments =
                commentQueryLoader.findAllTabComments(conn, schema.getName());
        for (Table table : schema.getTables()) {
            table.setComment(commentEditor(comments.get(table.getName())));
        }
        buildPartitions(conn, catalog, schema);
    }

    /**
     * Build enabled primary key information for the given table.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param table Table
     * @throws SQLException e
     */
    @Override
    protected void buildTablePK(
            final Connection conn, final Catalog catalog, final Schema schema, final Table table)
            throws SQLException {
        constraintIndexMetadataLoader.buildTablePK(conn, schema, table, factory);
        setUniquColumnByPK(table);
    }

    /**
     * extract Table's FK
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param table Table
     * @throws SQLException e
     */
    protected void buildTableFKs(
            final Connection conn, final Catalog catalog, final Schema schema, final Table table)
            throws SQLException {
        constraintIndexMetadataLoader.buildTableFKs(conn, schema, table, factory);
    }

    /**
     * Build Table's indexes
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param table Table
     * @throws SQLException e
     */
    protected void buildTableIndexes(
            final Connection conn, final Catalog catalog, final Schema schema, final Table table)
            throws SQLException {
        constraintIndexMetadataLoader.buildTableIndexes(conn, schema, table, factory);

        setUniquColumnByIndex(table);
    }

    /**
     * Fetch all stored Triggers of the given schemata.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildTriggers(
            Connection conn, Catalog catalog, Schema schema, IBuildSchemaFilter filter)
            throws SQLException {
        schema.setTriggers(
                routineTriggerGrantLoader.getAllTriggers(
                        conn, schema.getName(), schema.getName(), factory, OBJECT_TYPE_TRIGGER));
    }

    /**
     * Extract View's Columns
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param view View
     * @throws SQLException e
     */
    protected void buildViewColumns(
            final Connection conn, final Catalog catalog, final Schema schema, final View view)
            throws SQLException {
        super.buildViewColumns(conn, catalog, schema, view);
        TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
        for (Column column : view.getColumns()) {
            String shownDataType = dtHelper.getShownDataType(column);
            LOG.debug("[VAR]shownDataType={}, column={}", shownDataType, column);

            column.setShownDataType(shownDataType);
            column.setComment(getViewColumnComment(conn, schema.getName(), view.getName(), column));
        }
    }

    /**
     * Build Grant
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildGrant(
            Connection conn, Catalog catalog, Schema schema, IBuildSchemaFilter filter)
            throws SQLException {
        routineTriggerGrantLoader.buildGrant(conn, schema, factory);
    }

    @Override
    protected void buildViews(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        super.buildViews(conn, catalog, schema, filter);
        Map<String, String> comments =
                commentQueryLoader.findAllTabComments(conn, schema.getName());
        Map<String, String> queryTexts =
                commentQueryLoader.findAllViewQuerySpecs(conn, schema.getName());
        for (View view : schema.getViews()) {
            view.setComment(commentEditor(comments.get(view.getName())));
            view.setQuerySpec(queryTexts.get(view.getName()));
        }
    }

    /**
     * return a list of tibero table name.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @return List<String>
     * @throws SQLException e
     */
    protected List<String> getAllTableNames(
            final Connection conn, final Catalog catalog, final Schema schema) throws SQLException {
        final DatabaseMetaData metaData = conn.getMetaData();
        final ResultSet tables =
                metaData.getTables(
                        catalog.getName(),
                        schema.getName(),
                        null,
                        new String[] {OBJECT_TYPE_TABLE});
        try {
            final String owner = schema.getName();
            List<String> tableNameList = new ArrayList<String>();
            while (tables.next()) {
                String name = tables.getString(3);
                if (name.startsWith("BIN$")
                        || name.startsWith("MLOG$")
                        || name.startsWith("RUPD$")) {
                    continue;
                }
                tableNameList.add(owner + "." + name);
            }
            return tableNameList;
        } finally {
            Closer.close(tables);
        }
    }

    /**
     * return a list of view name. for different database, this method may be needed to override
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @return List<String>
     * @throws SQLException e
     */
    protected List<String> getAllViewNames(
            final Connection conn, final Catalog catalog, final Schema schema) throws SQLException {
        List<String> viewNameList = new ArrayList<String>();
        final String owner = schema.getName();
        final ResultSet rs =
                conn.getMetaData()
                        .getTables(
                                catalog.getName(),
                                schema.getName(),
                                null,
                                new String[] {OBJECT_TYPE_VIEW});
        try {
            while (rs.next()) {
                String name = rs.getString(3);
                if (name.startsWith("BIN$")
                        || name.startsWith("MLOG$")
                        || name.startsWith("RUPD$")
                        || "USER_SEQUENCES".equals(name)) {
                    continue;
                }
                viewNameList.add(owner + "." + name);
            }
            return viewNameList;
        } finally {
            Closer.close(rs);
            // Closer.close(stmt);
        }
    }

    protected DBExportHelper getExportHelper() {
        return DatabaseType.TIBERO.getExportHelper();
    }

    /**
     * get TABLE comment
     *
     * @param conn Connection
     * @param schemaName String
     * @param objectName String
     * @return processed comment
     */
    protected String getTableComment(Connection conn, String schemaName, String objectName) {
        String comment =
                commentQueryLoader.getTableComment(
                        conn, "Get table comment error: " + objectName, schemaName, objectName);
        return comment == null ? null : commentEditor(comment);
    }

    protected String getViewComment(Connection conn, String schemaName, String viewName) {
        String comment =
                commentQueryLoader.getViewComment(
                        conn, "Get view comment error: " + viewName, schemaName, viewName);
        return comment == null ? null : commentEditor(comment);
    }

    private String getViewColumnComment(
            Connection conn, String schemaName, String viewName, Column column) {
        String comment =
                commentQueryLoader.getViewColumnComment(
                        conn,
                        "Get view column comment error: " + viewName + "." + column.getName(),
                        schemaName,
                        viewName,
                        column.getName());
        return comment == null ? null : commentEditor(comment);
    }

    /**
     * info: DECODE (t.data_precision, null, DECODE (t.data_type, 'CHAR', t.char_length, 'VARCHAR',
     * t.char_length, 'VARCHAR2', t.char_length, t.data_length), t.data_precision)
     *
     * @param column Column
     */
    private void resetTiberoColumnPrecision(Column column) {
        if (column.getPrecision() == null || column.getPrecision() == 0) {
            String dataType = column.getDataType();

            if (COLUMNS_RESET1.indexOf(dataType) >= 0) {
                column.setPrecision(column.getCharLength());
            } else if (COLUMNS_RESET2.indexOf(dataType) >= 0) {
                column.setPrecision(column.getByteLength());
            }
        }
    }

    /**
     * setCatalogTimezone
     *
     * @param catalog Catalog
     */
    private void setCatalogTimezone(final Catalog catalog) {
        try {
            catalog.setTimezone(TimeZoneUtils.getGMTFormat(TimeZone.getDefault().getID()));
        } catch (Exception ex) {
            LOG.error("", ex);
        }
    }

    /**
     * get Tibero charset
     *
     * @param conn Connection
     * @param catalog Catalog
     */
    private void setCharset(final Connection conn, final Catalog catalog) {
        Statement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD
        try {
            final String sqlStr = "SELECT * FROM NLS_DATABASE_PARAMETERS";
            LOG.debug("[SQL]" + sqlStr);

            stmt = conn.createStatement();
            rs = stmt.executeQuery(sqlStr);
            while (rs.next()) {
                String key = rs.getString(1);
                String value = rs.getString(2);
                catalog.getAdditionalInfo().put(key, value);
            }
            catalog.setCharset(catalog.getAdditionalInfo().get("NLS_CHARACTERSET"));
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * Tibero schemas; If default schema is specified, it will be returned directly.
     *
     * @param conn Connection
     * @param cp ConnParameters
     * @return schema names
     * @throws SQLException ex;
     */
    protected List<String> getSchemaNames(Connection conn, ConnParameters cp) throws SQLException {
        List<String> schemaNames = new ArrayList<String>();
        String sql = "SELECT OWNER FROM USER_TAB_PRIVS WHERE PRIVILEGE='SELECT' GROUP BY OWNER";
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                schemaNames.add(rs.getString(1).toUpperCase(Locale.US));
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }

        String defaultSchema = cp.getConUser().toUpperCase(Locale.US);
        if (!schemaNames.contains(defaultSchema)) {
            schemaNames.add(defaultSchema);
        }
        return schemaNames;
    }

    /**
     * Retrieves the Database type.
     *
     * @return DatabaseType
     */
    public DatabaseType getDBType() {
        return DatabaseType.TIBERO;
    }
}
