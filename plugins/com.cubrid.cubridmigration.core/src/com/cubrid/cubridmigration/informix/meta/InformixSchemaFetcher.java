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

package com.cubrid.cubridmigration.informix.meta;

import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.AbstractJDBCSchemaFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.informix.InformixDataTypeHelper;
import com.cubrid.cubridmigration.informix.export.InformixExportHelper;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * InformixSchemaFetcher Description
 *
 * @author rathana
 * @version 1.0
 * @created Aug 11, 2022
 */
public class InformixSchemaFetcher extends AbstractJDBCSchemaFetcher {

    static final String ROUTINE_QUERY =
            "select b.data, p.procname as name from sysprocbody b "
                    + "join sysprocedures p on b.procid=p.procid where p.isproc=? "
                    + "and p.owner=? AND mode IN ('O', 'R', 'D', 'T') order by b.procid, b.seqno";

    static final String SEQUENCE_QUERY =
            "select t.tabname,t.owner, s.* from systables as t, "
                    + "syssequences as s where t.tabid = s.tabid and t.owner =?";

    static final String TRIGGER_QUERY =
            "select st.*, tb.data from systriggers as st, "
                    + " systrigbody as tb where tb.trigid = st.trigid and st.owner=? "
                    + "and tb.datakey='A'";

    static final String VIEW_QUERY =
            "select st.tabname, sv.viewtext from systables as st, "
                    + "sysviews as sv where st.tabname = ? and st.owner=? and st.tabid = sv.tabid";
    static final String RETREIVE_VIEW =
            "select * FROM systables where statlevel ='A' and tabtype='V' and owner=?";
    static final String RETREIVE_TABLE =
            "select * FROM systables where statlevel ='A' and tabtype='T' and owner=?";

    private static final Logger LOG = LogUtil.getLogger(InformixSchemaFetcher.class);

    public InformixSchemaFetcher() {
        factory = new DBObjectFactory();
    }

    public Catalog buildCatalog(final Connection conn, ConnParameters cp, IBuildSchemaFilter filter)
            throws SQLException {

        Catalog catalog = super.buildCatalog(conn, cp, filter);
        catalog.setDatabaseType(DatabaseType.INFORMIX);
        return catalog;
    }

    /**
     * getSQLTable
     *
     * @param resultSetMeta ResultSetMetaData
     * @return SourceTable
     * @throws SQLException e
     */
    public Table buildSQLTable(ResultSetMetaData resultSetMeta) throws SQLException {
        Table sourceTable = super.buildSQLTable(resultSetMeta);
        List<Column> columns = sourceTable.getColumns();
        InformixDataTypeHelper dtHelper = InformixDataTypeHelper.getInstance(null);
        for (Column column : columns) {
            column.setShownDataType(dtHelper.getShownDataType(column));
            if (column.getDataType().contains("datetime year")) {
                column.setDataType("datetime");
                column.setShownDataType("datetime");
            }
        }
        return sourceTable;
    }

    /**
     * return schema names
     *
     * @param conn Connection
     * @param cp ConnParameters
     * @return List<String>
     * @throws SQLException e
     */
    protected List<String> getSchemaNames(final Connection conn, ConnParameters cp)
            throws SQLException {
        List<String> result = new ArrayList<String>();
        ResultSet resultSet = conn.getMetaData().getSchemas();
        while (resultSet.next()) {
            String name = resultSet.getString("TABLE_SCHEM").toUpperCase(Locale.US);
            result.add(name);
        }
        return result;
    }

    /**
     * extract Tables
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildTables(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildTables()");
        }
        List<String> tableNameList = getAllTableNames(conn, catalog, schema);
        Set<String> set = new HashSet<String>();
        for (String tableName : tableNameList) {
            Table table = null;
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + tableName);
                }
                String tableOwnerName = schema.getName();
                String tablePureName = tableName;
                if (set.add(tableName) == false) {
                    tablePureName = tableOwnerName + "_" + tablePureName;
                }

                // If names format like xxx.xxx means schema name prefixed

                table = factory.createTable();
                table.setOwner(tableOwnerName);
                table.setName(tablePureName);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("[VAR]tableName=" + table.getName() + ", owner=" + table.getOwner());
                }
                table.setSchema(schema);

                buildTableColumns(conn, catalog, schema, table);
                buildTablePK(conn, catalog, schema, table);
                buildTableFKs(conn, catalog, schema, table);
                buildTableIndexes(conn, catalog, schema, table);
            } catch (Exception ex) {
                LOG.error("", ex);
            }
            if (table != null) {
                schema.addTable(table);
                for (Column column : table.getColumns()) {
                    String dataType = column.getDataType();
                    if (column.getDataType().contains("sendreceive")) {
                        dataType = "set";
                    }

                    column.setDataType(dataType);
                }
                ;
            }
        }
    }

    /**
     * Return a list of view name. for different database, this method may be needed to override
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
        ResultSet rs = null; // NOPMD
        List<String> viewNameList = new ArrayList<String>();
        try {

            PreparedStatement stmt = conn.prepareStatement(RETREIVE_VIEW);

            String schemaName = getSchemaName(schema).toLowerCase(Locale.US);
            stmt.setString(1, schemaName);
            rs = stmt.executeQuery();
            while (rs.next()) {
                viewNameList.add(rs.getString("tabname"));
            }
            return viewNameList;
        } finally {
            Closer.close(rs);
        }
    }

    /**
     * return a list of table name. for different database, this method may be needed to override
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
        ResultSet rs = null; // NOPMD
        List<String> tableNameList = new ArrayList<String>();
        try {
            PreparedStatement stmt = conn.prepareStatement(RETREIVE_TABLE);

            String schemaName = getSchemaName(schema).toLowerCase(Locale.US);
            stmt.setString(1, schemaName);
            rs = stmt.executeQuery();
            while (rs.next()) {
                tableNameList.add(rs.getString("tabname"));
            }
            return tableNameList;
        } finally {
            Closer.close(rs);
        }
    }

    /**
     * Fetch all views of the given schemata.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    protected void buildViews(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildViews()");
        }
        List<String> viewNameList = getAllViewNames(conn, catalog, schema);
        for (String viewName : viewNameList) {
            String viewOwnerName = null;
            String viewPureName = null;
            String querySpec = null;
            if (viewName != null && viewName.indexOf(".") != -1) {
                String[] arr = viewName.split("\\.");
                viewOwnerName = schema.getName();
                viewPureName = arr[1];
            } else {
                viewOwnerName = schema.getName();
                viewPureName = viewName;
            }
            if (filter != null && filter.filter(schema.getName(), viewPureName)) {
                continue;
            }
            if (!isViewNameAccepted(viewName)) {
                continue;
            }

            querySpec = buildViewDDL(conn, viewOwnerName, viewName);

            final View view = factory.createView();
            view.setQuerySpec(querySpec);
            view.setOwner(viewOwnerName);
            view.setName(viewPureName);
            view.setSchema(schema);
            schema.addView(view);
            buildViewColumns(conn, catalog, schema, view);
        }
    }

    /**
     * Fetch all stored procedures and function of the given schema.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    public void buildProcedures(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {

        List<Schema> schemaList = catalog.getSchemas();

        for (Schema sc : schemaList) {
            String schemaName = sc.getName().toLowerCase(Locale.US);
            // get procedures
            List<Procedure> procedures = getProcedures(conn, schemaName);
            sc.setProcedures(procedures);

            // get functions
            List<Function> funcList = getFunctions(conn, schemaName);
            sc.setFunctions(funcList);
        }
    }

    private List<Function> getFunctions(Connection conn, String schema_name) throws SQLException {

        PreparedStatement stmt = conn.prepareStatement(ROUTINE_QUERY);
        stmt.setString(1, "F");
        stmt.setString(2, schema_name);
        ResultSet rs = stmt.executeQuery();
        List<Function> functions = new ArrayList<Function>();
        Function function = factory.createFunction();
        while (rs.next()) {
            function.setName(rs.getString("name"));
            function.setFunctionDDL(rs.getString("data"));
            functions.add(function);
        }

        Closer.close(rs);
        Closer.close(stmt);
        return functions;
    }

    private List<Procedure> getProcedures(Connection conn, String schema_name) throws SQLException {

        PreparedStatement stmt = conn.prepareStatement(ROUTINE_QUERY);
        stmt.setString(1, "T");
        stmt.setString(2, schema_name);
        ResultSet rs = stmt.executeQuery();
        List<Procedure> procedures = new ArrayList<Procedure>();
        Procedure procedure = factory.createProcedure();
        while (rs.next()) {
            procedure.setName(rs.getString("name"));
            procedure.setProcedureDDL(rs.getString("data"));
            procedures.add(procedure);
        }

        Closer.close(rs);
        Closer.close(stmt);
        return procedures;
    }

    public String buildViewDDL(final Connection conn, String schema_name, String view_name)
            throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(VIEW_QUERY);
        stmt.setString(1, view_name);
        stmt.setString(2, schema_name);
        ResultSet rs = stmt.executeQuery();
        String ddl = "";
        while (rs.next()) {
            ddl = ddl + rs.getString("viewtext");
        }
        if (ddl != "") {
            ddl = ddl.split(String.format(" as", schema_name, view_name))[1];
            ddl = ddl.replaceAll("\"" + "[a-zA-Z0-9_]+" + "\".", "");
            return ddl;
        }
        return null;
    }

    /**
     * Fetch all sequences of the given schemata.
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @param filter IBuildSchemaFilter
     * @throws SQLException e
     */
    public void buildSequence(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {
        List<Schema> schemaList = catalog.getSchemas();

        for (Schema sc : schemaList) {
            List<Sequence> sequences = getSequences(conn, sc.getName().toLowerCase(Locale.US));
            sc.setSequenceList(sequences);
        }
    }

    private List<Sequence> getSequences(Connection conn, String schema_name) throws SQLException {

        PreparedStatement stmt = conn.prepareStatement(SEQUENCE_QUERY);
        stmt.setString(1, schema_name);
        ResultSet rs = stmt.executeQuery();
        List<Sequence> sequences = new ArrayList<Sequence>();
        Sequence sequence = factory.createSequence(null, null, null, null, null, false, 0);
        while (rs.next()) {
            sequence.setOwner(schema_name);
            sequence.setName(rs.getString("tabname"));
            sequence.setMaxValue(new BigInteger(rs.getString("max_val")));
            sequence.setMinValue(new BigInteger(rs.getString("min_val")));
            sequence.setIncrementBy(new BigInteger(rs.getString("inc_val")));
            sequence.setCurrentValue(new BigInteger(rs.getString("start_val")));
            sequence.setCycleFlag(rs.getString("cycle").equals("1"));
            sequence.setCacheSize(rs.getInt("cache"));
            sequences.add(sequence);

            // missing ddl
        }
        Closer.close(rs);
        Closer.close(stmt);
        return sequences;
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
    public void buildTriggers(
            final Connection conn,
            final Catalog catalog,
            final Schema schema,
            IBuildSchemaFilter filter)
            throws SQLException {

        List<Schema> schemaList = catalog.getSchemas();

        for (Schema sc : schemaList) {
            List<Trigger> triggers = getTriggers(conn, sc.getName().toLowerCase(Locale.US));
            sc.setTriggers(triggers);
        }
    }

    private List<Trigger> getTriggers(Connection conn, String schema_name) throws SQLException {

        PreparedStatement stmt = conn.prepareStatement(TRIGGER_QUERY);
        stmt.setString(1, schema_name);
        ResultSet rs = stmt.executeQuery();
        List<Trigger> triggers = new ArrayList<Trigger>();

        while (rs.next()) {
            Trigger trigger = factory.createTrigger();
            trigger.setName(rs.getString("trigname"));
            String event = rs.getString("event");
            String ddl = buildTriggerDDL(rs.getString("data"), rs.getString("trigname"), event);
            trigger.setDDL(ddl);

            triggers.add(trigger);
        }

        Closer.close(rs);
        Closer.close(stmt);

        return triggers;
    }

    private String buildTriggerDDL(String ddl, String name, String event) {
        String event_name = null;
        if (event.equals("U")) {
            event_name = "update";
        } else if (event.equals("I")) {
            event_name = "insert";
        } else if (event.equals("S")) {
            event_name = "select";
        } else if (event.equals("D")) {
            event_name = "delete";
        }

        if (event_name != null) {
            String template = "create trigger %s before %s on %s \n execute %s";
            String ddlText = String.format(template, name, event_name, ddl);
            return ddlText;
        }

        return ddl;
    }

    /**
     * build Partitions
     *
     * @param conn Connection
     * @param catalog Catalog
     * @param schema Schema
     * @throws SQLException e
     */
    public void buildPartitions(final Connection conn, final Catalog catalog, final Schema schema)
            throws SQLException {
        // do nothing
        // not yet implement
    }

    public DatabaseType getDBType() {
        return DatabaseType.INFORMIX;
    }

    @Override
    protected DBExportHelper getExportHelper() {
        return new InformixExportHelper();
    }
}
