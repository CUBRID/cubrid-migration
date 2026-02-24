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
package com.cubrid.cubridmigration.oracle.meta;

import static com.cubrid.cubridmigration.core.dbobject.ProcedureConstants.*;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.CommonUtils;
import com.cubrid.cubridmigration.core.common.DBUtils;
import com.cubrid.cubridmigration.core.common.TimeZoneUtils;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.AbstractJDBCSchemaFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.Version;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.cubrid.CUBRIDSQLHelper;
import com.cubrid.cubridmigration.oracle.OracleDataTypeHelper;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.Reader;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * OracleDBObjectBuilder
 *
 * @author moulinwang
 * @version 1.0 - 2010-4-15
 */
public final class OracleSchemaFetcher extends AbstractJDBCSchemaFetcher {
    private static final List<Object> COLUMNS_RESET1 =
            CommonUtils.createListWithArray(
                    new Object[] {"CHAR", "NCHAR", "VARCHAR", "VARCHAR2", "NVARCHAR2", "LONG"});

    private static final List<Object> COLUMNS_RESET2 =
            CommonUtils.createListWithArray(new Object[] {"RAW", "LONG RAW"});

    private static final Logger LOG = LogUtil.getLogger(OracleSchemaFetcher.class);

    @SuppressWarnings("unused")
    private static final String OBJECT_TYPE_SEQUENCE = "SEQUENCE";

    private static final String OBJECT_TYPE_TABLE = "TABLE";
    private static final String OBJECT_TYPE_TRIGGER = "TRIGGER";
    private static final String OBJECT_TYPE_VIEW = "VIEW";

    // Undefined columns will not be supported.
    private static final String SQL_GET_COLUMNS =
            "SELECT T.COLUMN_NAME, T.DATA_TYPE, T.DATA_LENGTH, T.DATA_PRECISION, T.DATA_SCALE,"
                + " T.NULLABLE, T.DATA_DEFAULT, T.CHAR_LENGTH, T.CHAR_USED, T.COLUMN_ID, C.COMMENTS"
                + " FROM ALL_TAB_COLUMNS T, ALL_COL_COMMENTS C WHERE T.OWNER=? AND T.TABLE_NAME=?"
                + " AND C.COLUMN_NAME=T.COLUMN_NAME AND T.TABLE_NAME=C.TABLE_NAME ORDER BY"
                + " COLUMN_ID";

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
                    + " C.TABLE_NAME=A.TABLE_NAME) ORDER BY A.INDEX_NAME";

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
            "SELECT SYNONYM_NAME, TABLE_OWNER, TABLE_NAME, DB_LINK FROM ALL_SYNONYMS WHERE OWNER=?";

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

    public OracleSchemaFetcher() {
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
        catalog.setDatabaseType(DatabaseType.ORACLE);
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
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD
        try {
            stmt = conn.prepareStatement(SQL_GET_PART_TABLES);
            stmt.setString(1, schema.getName());
            rs = stmt.executeQuery();

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName);
                }
                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                String partitionMethod = rs.getString("PARTITIONING_TYPE");
                int partitionCount = rs.getInt("PARTITION_COUNT");
                int partitionColumnCount = rs.getInt("PARTITIONING_KEY_COUNT");

                String subPartitionMethod = rs.getString("SUBPARTITIONING_TYPE");
                int subPartitionCount = rs.getInt("DEF_SUBPARTITION_COUNT");
                int subPartitionColumnCount = rs.getInt("SUBPARTITIONING_KEY_COUNT");

                PartitionInfo partitionInfo = factory.createPartitionInfo();
                partitionInfo.setPartitionMethod(partitionMethod);
                partitionInfo.setPartitionCount(partitionCount);
                partitionInfo.setPartitionColumnCount(partitionColumnCount);
                partitionInfo.setPartitionExp(null);
                partitionInfo.setPartitionFunc(null);
                partitionInfo.setDDL(getSourcePartitionDDL(table));
                if ("NONE".equals(subPartitionMethod)) {
                    subPartitionMethod = null;
                }
                partitionInfo.setSubPartitionMethod(subPartitionMethod);
                partitionInfo.setSubPartitionCount(subPartitionCount);
                partitionInfo.setSubPartitionColumnCount(subPartitionColumnCount);

                table.setPartitionInfo(partitionInfo);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]partitionInfo=" + partitionInfo);
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }

        getPartitionColumn(conn, schema);
        getPartitionTables(conn, schema);
        getSubPartitionTables(conn, schema);
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
        List<OraclePlsqlProcedure> oracleProcedures = getAllProcedures(conn, schema.getName());

        for (OraclePlsqlProcedure oraProc : oracleProcedures) {
            if (oraProc.getProcedureType().equals(PROCEDURE)) {
                procedures.add(factory.createPlcsqlProcedure(oraProc));
            } else {
                functions.add(factory.createPlcsqlFunction(oraProc));
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
                String targetOwnerName = rs.getString("TABLE_OWNER");
                String targetName = rs.getString("TABLE_NAME");
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
        OracleDataTypeHelper dtHelper = OracleDataTypeHelper.getInstance(null);
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildTableColumns()");
        }
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
            OracleDataTypeHelper dtHelper = OracleDataTypeHelper.getInstance(null);
            rs = stmt.executeQuery();
            while (rs.next()) {
                try {
                    // create new column
                    final Column column = factory.createColumn();
                    String columnName = rs.getString("COLUMN_NAME");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[VAR]columnName=" + columnName);
                    }
                    column.setName(columnName);
                    column.setDataType(rs.getString("DATA_TYPE"));

                    // DATA_LENGTH  Length of the column in bytes
                    column.setByteLength(rs.getInt("DATA_LENGTH"));
                    String precisionStr = rs.getString("DATA_PRECISION");

                    column.setPrecision(precisionStr == null ? null : rs.getInt("DATA_PRECISION"));
                    String scaleStr = rs.getString("DATA_SCALE");
                    column.setScale(scaleStr == null ? null : rs.getInt("DATA_SCALE"));
                    // Oracle Integer
                    if (column.getDataType().equals("NUMBER")
                            && precisionStr == null
                            && "0".equals(scaleStr)) {
                        column.setDataType("INTEGER");
                    }
                    column.setJdbcIDOfDataType(
                            dtHelper.getJdbcDataTypeID(
                                    catalog,
                                    column.getDataType(),
                                    column.getPrecision(),
                                    column.getScale()));

                    column.setNullable(!"N".equalsIgnoreCase(rs.getString("NULLABLE")));

                    // set column default value
                    String defaultValue = rs.getString("DATA_DEFAULT");
                    // if the data is last,default value add "\n" or "\r" automatically,so trim it
                    if (defaultValue != null) {
                        defaultValue = defaultValue.trim();
                    }
                    if ("NULL".equals(defaultValue)) {
                        column.setDefaultValue(null);
                    } else {
                        column.setDefaultValue(defaultValue);
                    }
                    column.setCharLength(rs.getInt("CHAR_LENGTH"));
                    // CHAR_USED: C=varchar2(xx char) B=varchar2(xx)
                    column.setCharUsed(rs.getString("CHAR_USED"));
                    resetOracleColumnPrecision(column);

                    String shownDataType = dtHelper.getShownDataType(column);
                    column.setShownDataType(shownDataType);
                    String comment = rs.getString("COMMENTS");
                    column.setComment(commentEditor(comment));

                    table.addColumn(column);
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
        LOG.debug("[IN] buildTablePK()");
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_ENABLED_PK)) {
            pstmt.setString(1, schema.getName());
            pstmt.setString(2, table.getName());
            LOG.debug(
                    "[SQL]{} (1={}, 2={})", SQL_GET_ENABLED_PK, schema.getName(), table.getName());
            try (ResultSet rs = pstmt.executeQuery()) {
                PK primaryKey = null;

                while (rs.next()) {
                    if (primaryKey == null) {
                        primaryKey = factory.createPK(table);
                        primaryKey.setName(rs.getString("PK_NAME"));
                        table.setPk(primaryKey);
                    }

                    // The SQL result is already ordered by POSITION, so we don't need to sort here.
                    String columnName = rs.getString("COLUMN_NAME");
                    Column col = table.getColumnWithNoCase(columnName);
                    if (col != null) {
                        primaryKey.addColumn(col.getName());
                    }
                }

                if (primaryKey != null) {
                    final String primaryKeyName = primaryKey.getName();
                    if (primaryKeyName != null) {
                        table.getIndexes()
                                .removeIf(idx -> primaryKeyName.equalsIgnoreCase(idx.getName()));
                    }
                }
            }
        }
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
        LOG.debug("[IN] buildTableFKs()");
        try (PreparedStatement pstmt = conn.prepareStatement(SQL_GET_ENABLED_FKS)) {
            pstmt.setString(1, schema.getName());
            pstmt.setString(2, table.getName());
            LOG.debug(
                    "[SQL]{} (1={}, 2={})", SQL_GET_ENABLED_FKS, schema.getName(), table.getName());

            try (ResultSet rs = pstmt.executeQuery()) {
                String fkName = "";
                FK foreignKey = null;

                while (rs.next()) {
                    final String newFkName = rs.getString("FK_NAME");
                    LOG.debug("[VAR]newFkName=" + newFkName);
                    if (fkName.compareToIgnoreCase(newFkName) != 0) {
                        if (foreignKey != null) {
                            table.addFK(foreignKey);
                        }

                        fkName = newFkName;
                        foreignKey = factory.createFK(table);
                        foreignKey.setName(fkName);
                        foreignKey.setUpdateRule(
                                FK.ON_UPDATE_NO_ACTION); // oracle doesn't have update rule

                        String deleteRule = rs.getString("DELETE_RULE");
                        if ("CASCADE".equalsIgnoreCase(deleteRule)) {
                            foreignKey.setDeleteRule(FK.ON_DELETE_CASCADE);
                        } else if ("SET NULL".equalsIgnoreCase(deleteRule)) {
                            foreignKey.setDeleteRule(FK.ON_DELETE_SET_NULL);
                        } else {
                            foreignKey.setDeleteRule(FK.ON_DELETE_NO_ACTION);
                        }

                        foreignKey.setReferencedTableName(rs.getString("PK_TABLE_NAME"));
                    }
                    if (foreignKey != null) {
                        // find reference table column
                        final String colName = rs.getString("FK_COLUMN_NAME");
                        Column column = table.getColumnByName(colName);
                        if (column != null) {
                            foreignKey.addRefColumnName(colName, rs.getString("PK_COLUMN_NAME"));
                        }
                    }
                }

                if (foreignKey != null) {
                    table.addFK(foreignKey);
                }
            }
        } catch (SQLException e) {
            LOG.error("Error while building table FKs", e);
            throw e;
        }
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildTableIndexes()");
        }
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD
        try {
            stmt = conn.prepareStatement(SQL_GET_TABLE_INDEX);
            stmt.setString(1, schema.getName());
            stmt.setString(2, table.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + SQL_GET_TABLE_INDEX + ", 1=" + table.getName());
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String indexType = rs.getString("INDEX_TYPE");

                Index idx = factory.createIndex(table);
                idx.setName(indexName);
                idx.setUnique("UNIQUE".equals(rs.getString("UNIQUENESS")));

                if ("NORMAL".equals(indexType)) {
                    idx.setIndexType(DatabaseMetaData.tableIndexClustered);
                } else if ("NORMAL/REV".equals(indexType)) {
                    idx.setReverse(true);
                    idx.setIndexType(DatabaseMetaData.tableIndexClustered);
                } else {
                    idx.setIndexType(DatabaseMetaData.tableIndexOther);
                }
                table.addIndex(idx);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[VAR]indexes.count="
                                + (table.getIndexes() == null ? null : table.getIndexes()));
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }

        try {
            stmt = conn.prepareStatement(SQL_GET_INDEX_COLUMNS);
            for (Index idx : table.getIndexes()) {
                stmt.setString(1, schema.getName());
                stmt.setString(2, table.getName());
                stmt.setString(3, idx.getName());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "[SQL]"
                                    + SQL_GET_INDEX_COLUMNS
                                    + ", "
                                    + "1="
                                    + table.getName()
                                    + ", "
                                    + "2="
                                    + idx.getName());
                }
                rs = stmt.executeQuery();
                while (rs.next()) {
                    Column col = table.getColumnByName(rs.getString("COLUMN_NAME"));
                    String name;
                    if (col == null) {
                        name = rs.getString("COLUMN_EXPRESSION");
                        if (name == null) {
                            continue;
                        }
                        // Some column name may be something like "test"
                        if (name.matches("^\"(\\w|\\W|\\d|_)+\"$")) {
                            name = name.substring(1, name.length() - 1);
                        }
                    } else {
                        name = col.getName();
                    }
                    if (name == null) {
                        continue;
                    }
                    String order = rs.getString("DESCEND");
                    order = order == null ? "A" : order.toUpperCase(Locale.US);
                    idx.addColumn(name, order.startsWith("A"));
                }
                rs.close();
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
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
        Version version = catalog.getVersion();
        if (version.getDbMajorVersion() < 5) { // 5.0.2 support trigger
            return;
        }

        // get triggers
        schema.setTriggers(getAllTriggers(conn, schema.getName(), schema.getName()));
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
        OracleDataTypeHelper dtHelper = OracleDataTypeHelper.getInstance(null);
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
        PreparedStatement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_SHOW_GRANT_TABLE);
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_GRANT_TABLE
                                + ", "
                                + "1="
                                + schema.getName()
                                + ", "
                                + "2="
                                + schema.getName());
            }

            stmt.setString(1, schema.getName().toUpperCase());
            rs = stmt.executeQuery();
            while (rs.next()) {
                if (!isSupportPrivilege(rs.getString("PRIVILEGE"))) {
                    continue;
                }

                Grant grant = factory.createGrant();
                grant.setGranteeName(rs.getString("GRANTEE"));
                grant.setOwner(schema.getName());
                grant.setClassOwner(rs.getString("OWNER"));
                grant.setClassName(rs.getString("TABLE_NAME"));
                grant.setGrantorName(rs.getString("GRANTOR"));
                grant.setAuthType(convertPrivilegeOracle2Cubrid(rs.getString("PRIVILEGE")));
                grant.setGrantable(rs.getString("GRANTABLE").equals("YES") ? true : false);
                grant.setSourceObjectOwner(grant.getClassOwner());
                grant.setDDL(CUBRIDSQLHelper.getInstance(null).getGrantDDL(grant, true));
                schema.addGrant(grant);
            }

            stmt = conn.prepareStatement(SQL_SHOW_GRANT_VIEW);
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_GRANT_VIEW
                                + ", "
                                + "1="
                                + schema.getName()
                                + ", "
                                + "2="
                                + schema.getName());
            }

            stmt.setString(1, schema.getName().toUpperCase());
            rs = stmt.executeQuery();
            while (rs.next()) {
                Grant grant = factory.createGrant();
                grant.setGranteeName(rs.getString("GRANTEE"));
                grant.setOwner(schema.getName());
                grant.setClassOwner(rs.getString("OWNER"));
                grant.setClassName(rs.getString("TABLE_NAME"));
                grant.setGrantorName(rs.getString("GRANTOR"));
                grant.setAuthType(rs.getString("PRIVILEGE"));
                grant.setGrantable(rs.getString("GRANTABLE").equals("YES") ? true : false);
                grant.setSourceObjectOwner(grant.getClassOwner());
                grant.setDDL(CUBRIDSQLHelper.getInstance(null).getGrantDDL(grant, true));
                schema.addGrant(grant);
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * get All Procedures
     *
     * @param conn Connection
     * @param dbName String
     * @param ownerName == schema name
     * @return List<Procedure>
     * @throws SQLException e
     */
    private List<OraclePlsqlProcedure> getAllProcedures(
            final Connection conn, final String ownerName) throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getAllProcedures()");
        }

        List<OraclePlsqlProcedure> procedures = new ArrayList<>();
        getPlcsqlProcedureMetaData(conn, ownerName, procedures);
        getPlcsqlProcedureDDL(conn, procedures);

        return procedures;
    }

    private void getPlcsqlProcedureDDL(Connection conn, List<OraclePlsqlProcedure> procedures)
            throws SQLException {
        String SQL =
                "SELECT TEXT FROM ALL_SOURCE WHERE OWNER = ? AND NAME = ? AND TYPE = ? ORDER BY"
                        + " LINE";

        ResultSet rs = null;
        try (PreparedStatement stmt = conn.prepareStatement(SQL)) {
            for (OraclePlsqlProcedure proc : procedures) {
                stmt.setString(1, proc.getOwner());
                stmt.setString(2, proc.getName());
                stmt.setString(3, proc.getProcedureType());

                StringBuilder sb = new StringBuilder();
                rs = stmt.executeQuery();
                sb.append("CREATE ");
                while (rs.next()) {
                    sb.append(rs.getString("TEXT"));
                }
                proc.setDDL(sb.toString());
            }
        } finally {
            Closer.close(rs);
        }
    }

    private void getPlcsqlProcedureMetaData(
            Connection conn, String ownerName, List<OraclePlsqlProcedure> procedures)
            throws SQLException {
        String SQL =
                "SELECT owner, object_name, authid, object_type FROM ALL_PROCEDURES WHERE"
                        + " OBJECT_TYPE IN ('PROCEDURE', 'FUNCTION') AND OWNER=?";

        ResultSet rs = null;
        try (PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, ownerName);

            rs = stmt.executeQuery();

            while (rs.next()) {
                procedures.add(
                        new OraclePlsqlProcedure(
                                rs.getString("OWNER"),
                                rs.getString("OBJECT_NAME"),
                                rs.getString("AUTHID"),
                                rs.getString("OBJECT_TYPE")));
            }
        } finally {
            Closer.close(rs);
        }
    }

    /**
     * return a list of oracle table name.
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
                tables.getString(1);
                tables.getString(2);
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
     * get All Triggers
     *
     * @param conn Connection
     * @param dbName the db name
     * @param ownerName = schema name
     * @return all triggers
     * @throws SQLException e
     */
    private List<Trigger> getAllTriggers(
            final Connection conn, final String dbName, final String ownerName)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getAllTriggers()");
        }
        final List<String> list = this.getRountines(conn, OBJECT_TYPE_TRIGGER, ownerName);
        final List<Trigger> triggers = new ArrayList<Trigger>();

        for (String name : list) {
            final Trigger trigger = factory.createTrigger();
            trigger.setName(name);
            final String trigDDL = getObjectDDL(conn, dbName, name, OBJECT_TYPE_TRIGGER);
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]trigDDL=" + trigDDL);
            }
            trigger.setDDL(trigDDL);
            triggers.add(trigger);
        }

        return triggers;
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
        return DatabaseType.ORACLE.getExportHelper();
    }

    /**
     * Get TABLE DDL
     *
     * @param conn Connection
     * @param schemaName String
     * @param objectName String
     * @param objectType String
     * @return String
     * @throws SQLException e
     */
    protected String getObjectDDL(
            final Connection conn,
            final String schemaName,
            final String objectName,
            final String objectType)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getObjectDDL()");
        }
        if (StringUtils.isBlank(objectName)) {
            throw new IllegalArgumentException("The oracle object name is null!");
        }

        PreparedStatement preStmt = null; // NOPMD
        ResultSet rs = null; // NOPMD
        try {
            preStmt = conn.prepareStatement(SQL_SHOW_DDL);
            preStmt.setString(1, objectType);
            preStmt.setString(2, objectName);
            preStmt.setString(3, schemaName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_DDL
                                + ", "
                                + "1="
                                + objectType
                                + ", "
                                + "2="
                                + objectName
                                + ", "
                                + "3="
                                + schemaName);
            }
            rs = preStmt.executeQuery();

            String ddl = "";
            while (rs.next()) {
                ddl = rs.getString(1);
            }
            return ddl;
        } catch (Exception ex) {
            LOG.error("Get Oracle Object DDL error:" + objectName, ex);
            return "";
        } finally {
            Closer.close(rs);
            Closer.close(preStmt);
        }
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
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(SQL_GET_TABLE_COMMENT);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, objectName);

            rs = pstmt.executeQuery();

            String comment = "";
            while (rs.next()) {
                comment = rs.getString("COMMENTS");
            }

            return commentEditor(comment);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Closer.close(rs);
            Closer.close(pstmt);
        }
    }

    protected String getViewComment(Connection conn, String schemaName, String viewName) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(SQL_GET_VIEW_COMMENT);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, viewName);

            rs = pstmt.executeQuery();

            String comment = "";
            while (rs.next()) {
                comment = rs.getString("COMMENTS");
            }

            return commentEditor(comment);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Closer.close(rs);
            Closer.close(pstmt);
        }
    }

    private String getViewColumnComment(
            Connection conn, String schemaName, String viewName, Column column) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = conn.prepareStatement(SQL_GET_VIEW_COLUMN_COMMENT);
            pstmt.setString(1, schemaName);
            pstmt.setString(2, viewName);
            pstmt.setString(3, column.getName());

            rs = pstmt.executeQuery();

            String comment = "";
            while (rs.next()) {
                comment = rs.getString("COMMENTS");
            }

            if (comment != null) {
                comment = commentEditor(comment);
            }

            return comment;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            Closer.close(rs);
            Closer.close(pstmt);
        }
    }

    /**
     * get partition column information
     *
     * @param conn Connection
     * @param schema Schema
     */
    private void getPartitionColumn(final Connection conn, final Schema schema) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getPartitionColumn()");
        }
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_GET_PART_COLUMN);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + SQL_GET_PART_COLUMN);
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName + ", columnName=" + columnName);
                }

                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                PartitionInfo partitionInfo = table.getPartitionInfo();
                partitionInfo.addPartitionColumn(table.getColumnByName(columnName));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]partitionInfo=" + partitionInfo);
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }

        try {
            stmt = conn.prepareStatement(SQL_GET_SUBPART_KEY_COLUMN);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + SQL_GET_SUBPART_KEY_COLUMN);
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName + ", columnName=" + columnName);
                }
                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }
                PartitionInfo partitionInfo = table.getPartitionInfo();
                partitionInfo.addSubPartitionColumn(table.getColumnByName(columnName));
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]partitionInfo=" + partitionInfo);
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * get partition table information
     *
     * @param conn Connection
     * @param schema Schema
     */
    private void getPartitionTables(final Connection conn, final Schema schema) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getPartitionTables()");
        }
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_GET_PARTITIONS);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_GET_PARTITIONS
                                + ", 1="
                                + schema.getName()
                                + ", 2="
                                + schema.getName());
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName);
                }
                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                String partitionName = rs.getString("PARTITION_NAME");
                Reader reader = rs.getCharacterStream("HIGH_VALUE");
                String partitionDesc = reader == null ? null : DBUtils.reader2String(reader);
                int partitionPosition = rs.getInt("PARTITION_POSITION");

                PartitionInfo partitionInfo = table.getPartitionInfo();
                partitionInfo.setPartitionExp(null);
                partitionInfo.setPartitionFunc(null);

                PartitionTable partition = factory.createPartitionTable();
                partition.setPartitionName(partitionName);
                partition.setPartitionDesc(partitionDesc);
                partition.setPartitionIdx(partitionPosition);

                partitionInfo.addPartition(partition);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]partition=" + partition);
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
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
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD
        try {
            stmt = conn.prepareStatement(SQL_SHOW_VIEW_QUERYTEXT);
            stmt.setString(1, schemaName);
            stmt.setString(2, viewName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_VIEW_QUERYTEXT
                                + ", 1="
                                + schemaName
                                + ", 1="
                                + viewName);
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                return rs.getString("TEXT");
            }

            return null;
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * get All Routines
     *
     * @param conn Connection
     * @param type procedure/function
     * @param ownerName == schema name
     * @return all Routines names
     * @throws SQLException e
     */
    private List<String> getRountines(
            final Connection conn, final String type, final String ownerName) throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getRountines()");
        }
        PreparedStatement stmt = null; // NOPMD
        ResultSet rs = null; // NOPMD
        try {
            stmt = conn.prepareStatement(SQL_SHOW_ALL_OBJECTS);
            stmt.setString(1, type);
            stmt.setString(2, ownerName);

            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "[SQL]"
                                + SQL_SHOW_ALL_OBJECTS
                                + ", "
                                + "1="
                                + type
                                + ", "
                                + "2="
                                + ownerName
                                + ", "
                                + "3="
                                + type);
            }
            rs = stmt.executeQuery();
            final Set<String> list = new HashSet<String>();
            while (rs.next()) {
                list.add(rs.getString(1));
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("[VAR]list=" + (list.size()));
            }
            return new ArrayList<String>(list);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * get sub partition table information
     *
     * @param conn Connection
     * @param schema Schema
     */
    private void getSubPartitionTables(final Connection conn, final Schema schema) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]getSubPartitionTables()");
        }
        ResultSet rs = null; // NOPMD
        PreparedStatement stmt = null; // NOPMD

        try {
            stmt = conn.prepareStatement(SQL_GET_SUB_PART_TABLES);
            stmt.setString(1, schema.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + SQL_GET_SUB_PART_TABLES);
            }
            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName);
                }
                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                String subPartitionName = rs.getString("SUBPARTITION_NAME");
                Reader reader = rs.getCharacterStream("HIGH_VALUE");
                String subPartitionDesc = reader == null ? null : DBUtils.reader2String(reader);
                int subPartitionPosition = rs.getInt("SUBPARTITION_POSITION");

                PartitionTable subPartition = factory.createPartitionTable();
                subPartition.setPartitionName(subPartitionName);
                subPartition.setPartitionDesc(subPartitionDesc);
                subPartition.setPartitionIdx(subPartitionPosition);

                table.getPartitionInfo().addSubPartition(subPartition);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]subPartition=" + subPartition);
                }
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    /**
     * info: DECODE (t.data_precision, null, DECODE (t.data_type, 'CHAR', t.char_length, 'VARCHAR',
     * t.char_length, 'VARCHAR2', t.char_length, t.data_length), t.data_precision)
     *
     * @param column Column
     */
    private void resetOracleColumnPrecision(Column column) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]resetOracleColumnPrecision()");
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
     * get Oracle charset
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
     * Check privilege supported by Cubrid.
     *
     * @param privilege
     * @return boolean
     */
    private boolean isSupportPrivilege(String privilege) {
        if (privilege.equals("SELECT")
                || privilege.equals("INSERT")
                || privilege.equals("UPDATE")
                || privilege.equals("DELETE")
                || privilege.equals("ALTER")
                || privilege.equals("INDEX")
                || privilege.equals("EXECUTE")
                || privilege.equals("ALL")) {
            return true;
        }
        return false;
    }

    /**
     * Change to privilege used by Cubrid.
     *
     * @param privilege
     * @return String cubridPrivilege
     */
    private String convertPrivilegeOracle2Cubrid(String privilege) {
        String cubridPrivilege = privilege;

        if (privilege.equals("ALL")) {
            cubridPrivilege = "ALL PRIVILEGES";
        }

        return cubridPrivilege;
    }

    /**
     * Oracle schemas; If default schema is specified, it will be returned directly.
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
        return DatabaseType.ORACLE;
    }
}
