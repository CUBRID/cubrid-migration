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
import java.util.TimeZone;

public final class TiberoSchemaFetcher extends AbstractJDBCSchemaFetcher {
    private static final List<Object> COLUMNS_RESET1 =
            CommonUtils.createListWithArray(
                    new Object[] {"CHAR", "NCHAR", "VARCHAR", "VARCHAR2", "NVARCHAR2", "LONG"});

    private static final List<Object> COLUMNS_RESET2 =
            CommonUtils.createListWithArray(new Object[] {"RAW", "LONG RAW"});

    private static final Logger LOG = LogUtil.getLogger(TiberoSchemaFetcher.class);

    private final TiberoCommentQueryLoader commentQueryLoader = new TiberoCommentQueryLoader();
    private final TiberoPartitionMetadataLoader partitionMetadataLoader =
            new TiberoPartitionMetadataLoader();
    private final TiberoConstraintIndexMetadataLoader constraintIndexMetadataLoader =
            new TiberoConstraintIndexMetadataLoader();
    private final TiberoRoutineTriggerGrantLoader routineTriggerGrantLoader =
            new TiberoRoutineTriggerGrantLoader();

    private static final String OBJECT_TYPE_TABLE = "TABLE";
    private static final String OBJECT_TYPE_TRIGGER = "TRIGGER";
    private static final String OBJECT_TYPE_VIEW = "VIEW";

    // Undefined columns will not be supported.
    private static final String SQL_GET_COLUMNS =
            "SELECT T.COLUMN_NAME, T.DATA_TYPE, T.DATA_LENGTH, T.DATA_PRECISION, T.DATA_SCALE,"
                + " T.NULLABLE, T.DATA_DEFAULT, T.CHAR_LENGTH, T.CHAR_USED, T.COLUMN_ID, C.COMMENTS"
                + " FROM ALL_TAB_COLUMNS T LEFT JOIN ALL_COL_COMMENTS C ON C.OWNER=T.OWNER AND"
                + " C.TABLE_NAME=T.TABLE_NAME AND C.COLUMN_NAME=T.COLUMN_NAME WHERE T.OWNER=? AND"
                + " T.TABLE_NAME=? ORDER BY T.COLUMN_ID";

    private static final String SQL_GET_INDEX_COLUMNS =
            "SELECT A.COLUMN_NAME, A.DESCEND, B.COLUMN_EXPRESSION FROM ALL_IND_COLUMNS A LEFT JOIN"
                + " ALL_IND_EXPRESSIONS B ON A.TABLE_OWNER=B.TABLE_OWNER AND"
                + " A.TABLE_NAME=B.TABLE_NAME AND A.INDEX_NAME=B.INDEX_NAME AND"
                + " A.COLUMN_POSITION=B.COLUMN_POSITION  WHERE A.TABLE_OWNER=? AND A.TABLE_NAME=?"
                + " AND A.INDEX_NAME=? ORDER BY A.COLUMN_POSITION";

    private static final String SQL_GET_PART_COLUMN =
            "SELECT * FROM ALL_PART_KEY_COLUMNS WHERE OBJECT_TYPE='TABLE' AND OWNER=? "
                    + " ORDER BY NAME, COLUMN_POSITION";

    private static final String SQL_GET_PART_TABLES =
            "SELECT T.* FROM ALL_PART_TABLES T WHERE T.OWNER=? ORDER BY TABLE_NAME";

    private static final String SQL_GET_PARTITIONS =
            "SELECT T.TABLE_NAME, T.PARTITION_NAME, T.HIGH_VALUE, T.PARTITION_POSITION "
                    + "FROM ALL_TAB_PARTITIONS T WHERE T.TABLE_OWNER=? "
                    + "ORDER BY TABLE_NAME, PARTITION_POSITION";

    private static final String SQL_GET_SUB_PART_TABLES =
            "SELECT TABLE_NAME, PARTITION_NAME, SUBPARTITION_NAME, HIGH_VALUE,"
                + " SUBPARTITION_POSITION  FROM ALL_TAB_SUBPARTITIONS WHERE TABLE_OWNER=? ORDER BY"
                + " TABLE_NAME, SUBPARTITION_POSITION";

    private static final String SQL_GET_SUBPART_KEY_COLUMN =
            "SELECT * FROM ALL_SUBPART_KEY_COLUMNS WHERE OBJECT_TYPE='TABLE' AND OWNER=? "
                    + " ORDER BY NAME, COLUMN_POSITION";

    private static final String SQL_GET_TABLE_INDEX =
            "SELECT INDEX_NAME, INDEX_TYPE, UNIQUENESS FROM ALL_INDEXES A  WHERE A.TABLE_OWNER=?"
                    + " AND A.TABLE_NAME=? AND A.INDEX_NAME NOT IN (SELECT C.CONSTRAINT_NAME FROM"
                    + " ALL_CONSTRAINTS C WHERE C.CONSTRAINT_TYPE='P' AND C.OWNER=A.TABLE_OWNER AND"
                    + " C.TABLE_NAME=A.TABLE_NAME) AND UPPER(A.INDEX_TYPE) <> 'LOB' ORDER BY"
                    + " A.INDEX_NAME";

    private static final String SQL_SHOW_ALL_OBJECTS =
            "SELECT NAME FROM ALL_SOURCE S "
                    + "WHERE S.TYPE=? AND S.OWNER=? AND NOT S.NAME LIKE 'BIN$%' "
                    + "AND NOT S.NAME LIKE 'MLOG$%' AND NOT S.NAME LIKE 'RUPD$%'";

    private static final String SQL_SHOW_DDL = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM dual";

    private static final String SQL_SHOW_SEQUENCES =
            "SELECT S.* FROM ALL_SEQUENCES S WHERE S.SEQUENCE_OWNER=? AND NOT S.SEQUENCE_NAME LIKE"
                    + " 'BIN$%' AND NOT S.SEQUENCE_NAME LIKE 'MLOG$%' AND NOT S.SEQUENCE_NAME LIKE"
                    + " 'RUPD$%' ";

    private static final String SQL_SHOW_SYNONYM =
            "SELECT SYNONYM_NAME, ORG_OBJECT_OWNER, ORG_OBJECT_NAME FROM ALL_SYNONYMS WHERE"
                    + " OWNER=?";

    private static final String SQL_SHOW_VIEW_QUERYTEXT =
            "SELECT TEXT from ALL_VIEWS WHERE OWNER=? AND VIEW_NAME=?";

    private static final String SQL_GET_VIEW_COMMENT =
            "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND " + "TABLE_NAME=?";

    private static final String SQL_GET_VIEW_COLUMN_COMMENT =
            "SELECT COMMENTS FROM ALL_COL_COMMENTS WHERE OWNER=? AND "
                    + "TABLE_NAME=? AND COLUMN_NAME=?";

    private static final String SQL_GET_TABLE_COMMENT =
            "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER=? AND " + "TABLE_NAME=?";

    private static final String SQL_SHOW_GRANT_TABLE =
            "SELECT P.GRANTEE, P.OWNER, P.TABLE_NAME, P.GRANTOR, P.PRIVILEGE, P.GRANTABLE"
                    + " FROM USER_TAB_PRIVS P, ALL_TABLES T"
                    + " WHERE P.TABLE_NAME=T.TABLE_NAME"
                    + " AND P.OWNER=T.OWNER"
                    + " AND P.GRANTEE=?";

    private static final String SQL_SHOW_GRANT_VIEW =
            "SELECT P.GRANTEE, P.OWNER, P.TABLE_NAME, P.GRANTOR, P.PRIVILEGE, P.GRANTABLE"
                    + " FROM USER_TAB_PRIVS P, ALL_VIEWS V"
                    + " WHERE P.TABLE_NAME=V.VIEW_NAME"
                    + " AND P.OWNER=V.OWNER"
                    + " AND P.GRANTEE=?";

    private static final String SQL_GET_ENABLED_PK =
            "SELECT acc.COLUMN_NAME, ac.CONSTRAINT_NAME AS PK_NAME FROM ALL_CONSTRAINTS ac JOIN"
                + " ALL_CONS_COLUMNS acc ON ac.OWNER = acc.OWNER AND ac.CONSTRAINT_NAME ="
                + " acc.CONSTRAINT_NAME WHERE ac.CONSTRAINT_TYPE = 'P' AND ac.STATUS = 'ENABLED'"
                + " AND ac.OWNER = ? AND ac.TABLE_NAME = ? ORDER BY acc.POSITION";

    private static final String SQL_GET_ENABLED_FKS =
            "SELECT fk.constraint_name AS FK_NAME, fk.delete_rule AS DELETE_RULE,"
                + " fk_col.column_name AS FK_COLUMN_NAME, pk_col.table_name AS PK_TABLE_NAME,"
                + " pk_col.column_name AS PK_COLUMN_NAME FROM all_constraints fk JOIN"
                + " all_cons_columns fk_col ON fk.owner = fk_col.owner AND fk.constraint_name ="
                + " fk_col.constraint_name JOIN all_cons_columns pk_col ON fk.r_owner ="
                + " pk_col.owner AND fk.r_constraint_name = pk_col.constraint_name AND"
                + " fk_col.position = pk_col.position WHERE fk.owner = ? AND fk.table_name = ? AND"
                + " fk.constraint_type = 'R' AND fk.status = 'ENABLED' ORDER BY fk.constraint_name,"
                + " fk_col.position";

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
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]schema=" + schema.getName());
            }
            // get tables
            List<Table> tableList = schema.getTables();
            if (tableList == null) {
                tableList = new ArrayList<Table>();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]tableList.count=" + tableList.size());
            }
            for (Table table : tableList) {
                String comment = getTableComment(conn, schema.getName(), table.getName());
                table.setComment(comment);
            }
            // get views
            List<View> viewList = schema.getViews();
            if (viewList == null) {
                viewList = new ArrayList<View>();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]viewList.count=" + viewList.size());
            }
            for (View view : viewList) {
                view.setQuerySpec(getQueryText(conn, schema.getName(), view.getName(), view));

                String comment = getViewComment(conn, schema.getName(), view.getName());
                view.setComment(comment);
            }
            buildPartitions(conn, catalog, schema);
        }
        return catalog;
    }

    @Override
    public Catalog buildSchemaObjects(
            final Connection conn, final SchemaCatalog sc, List<String> schemaNames)
            throws SQLException {
        Catalog catalog = super.buildSchemaObjects(conn, sc, schemaNames);
        if (catalog == null) {
            return null;
        }

        for (Schema schema : catalog.getSchemas()) {
            String schemaName = schema.getName();
            for (Table table : schema.getTables()) {
                table.setComment(getTableComment(conn, schemaName, table.getName()));
            }

            for (View view : schema.getViews()) {
                view.setQuerySpec(getQueryText(conn, schemaName, view.getName(), view));
                view.setComment(getViewComment(conn, schemaName, view.getName()));
            }
            buildPartitions(conn, catalog, schema);
        }

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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildPartitions()");
        }
        partitionMetadataLoader.buildPartitions(
                conn,
                schema,
                factory,
                SQL_GET_PART_TABLES,
                SQL_GET_PART_COLUMN,
                SQL_GET_SUBPART_KEY_COLUMN,
                SQL_GET_PARTITIONS,
                SQL_GET_SUB_PART_TABLES,
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildProcedures()");
        }

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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildSequence()");
        }
        PreparedStatement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_SHOW_SEQUENCES);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_SEQUENCES
                                + ", "
                                + "1="
                                + schema.getName()
                                + ", "
                                + "2="
                                + schema.getName());
            }

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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildSynonym()");
        }
        PreparedStatement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_SHOW_SYNONYM);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_SYNONYM
                                + ", "
                                + "1="
                                + schema.getName()
                                + ", "
                                + "2="
                                + schema.getName());
            }

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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildSQLTable()");
        }
        TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
        Table sourceTable = super.buildSQLTable(resultSetMeta);
        List<Column> columns = sourceTable.getColumns();
        for (Column column : columns) {
            if (isNULLType(column.getDataType())) {
                column.setDataType("VARCHAR2");
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
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_GET_COLUMNS
                                + ", 1="
                                + schema.getName()
                                + ", 2="
                                + table.getName());
            }
            TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
            rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[VAR]columnName=" + columnName);
                    }
                    Column column = table.getColumnWithNoCase(columnName);
                    if (column == null) {
                        continue;
                    }

                    String dataType = rs.getString("DATA_TYPE");
                    if ("NVARCHAR".equalsIgnoreCase(dataType)) {
                        dataType = "NVARCHAR2";
                    }
                    column.setDataType(dataType);

                    column.setByteLength(rs.getInt("DATA_LENGTH"));
                    String precisionStr = rs.getString("DATA_PRECISION");
                    column.setPrecision(precisionStr == null ? null : rs.getInt("DATA_PRECISION"));
                    String scaleStr = rs.getString("DATA_SCALE");
                    column.setScale(scaleStr == null ? null : rs.getInt("DATA_SCALE"));
                    if ("NUMBER".equals(column.getDataType())
                            && precisionStr == null
                            && "0".equals(scaleStr)) {
                        column.setDataType("INTEGER");
                    }

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
                } catch (Exception ex) {
                    LOG.error("Read table column information error:" + table.getName(), ex);
                }
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
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
        constraintIndexMetadataLoader.buildTablePK(
                conn, schema, table, factory, SQL_GET_ENABLED_PK);
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
        constraintIndexMetadataLoader.buildTableFKs(
                conn, schema, table, factory, SQL_GET_ENABLED_FKS);
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
        constraintIndexMetadataLoader.buildTableIndexes(
                conn, schema, table, factory, SQL_GET_TABLE_INDEX, SQL_GET_INDEX_COLUMNS);

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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildTriggers()");
        }

        schema.setTriggers(
                routineTriggerGrantLoader.getAllTriggers(
                        conn,
                        schema.getName(),
                        schema.getName(),
                        factory,
                        SQL_SHOW_ALL_OBJECTS,
                        SQL_SHOW_DDL,
                        OBJECT_TYPE_TRIGGER));
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildViewColumns()");
        }
        super.buildViewColumns(conn, catalog, schema, view);
        TiberoDataTypeHelper dtHelper = TiberoDataTypeHelper.getInstance(null);
        for (Column column : view.getColumns()) {
            String shownDataType = dtHelper.getShownDataType(column);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]shownDataType=" + shownDataType + ", column=" + column);
            }
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildGrant()");
        }
        routineTriggerGrantLoader.buildGrant(
                conn, schema, factory, SQL_SHOW_GRANT_TABLE, SQL_SHOW_GRANT_VIEW);
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getAllTableNames()");
        }
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getAllViewNames()");
        }
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
                commentQueryLoader.getComment(
                        conn,
                        SQL_GET_TABLE_COMMENT,
                        "Get table comment error: " + objectName,
                        schemaName,
                        objectName);
        return comment == null ? null : commentEditor(comment);
    }

    protected String getViewComment(Connection conn, String schemaName, String viewName) {
        String comment =
                commentQueryLoader.getComment(
                        conn,
                        SQL_GET_VIEW_COMMENT,
                        "Get view comment error: " + viewName,
                        schemaName,
                        viewName);
        return comment == null ? null : commentEditor(comment);
    }

    private String getViewColumnComment(
            Connection conn, String schemaName, String viewName, Column column) {
        String comment =
                commentQueryLoader.getComment(
                        conn,
                        SQL_GET_VIEW_COLUMN_COMMENT,
                        "Get view column comment error: " + viewName + "." + column.getName(),
                        schemaName,
                        viewName,
                        column.getName());
        return comment == null ? null : commentEditor(comment);
    }

    /**
     * Return query text of a view
     *
     * @param conn Connection
     * @param schemaName schema name
     * @param viewName String
     * @return String
     * @throws SQLException e
     */
    private String getQueryText(
            final Connection conn, String schemaName, final String viewName, View view)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getQueryText()");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("[SQL]" + SQL_SHOW_VIEW_QUERYTEXT + ", 1=" + schemaName + ", 1=" + viewName);
        }
        return commentQueryLoader.getViewQueryText(
                conn, SQL_SHOW_VIEW_QUERYTEXT, schemaName, viewName);
    }

    /**
     * info: DECODE (t.data_precision, null, DECODE (t.data_type, 'CHAR', t.char_length, 'VARCHAR',
     * t.char_length, 'VARCHAR2', t.char_length, t.data_length), t.data_precision)
     *
     * @param column Column
     */
    private void resetTiberoColumnPrecision(Column column) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]resetTiberoColumnPrecision()");
        }
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]setCharset()");
        }
        Statement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD
        try {
            final String sqlStr = "SELECT * FROM NLS_DATABASE_PARAMETERS";
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + sqlStr);
            }
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
