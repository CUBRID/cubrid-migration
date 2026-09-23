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
package com.cubrid.cubridmigration.cubrid.meta;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachMetaData;
import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachPreparedQuery;
import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachStatementQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("CUBRIDSchemaFetcher")
class CUBRIDSchemaFetcherTest {
    private static final CUBRIDSchemaFetcher FETCHER = new CUBRIDSchemaFetcher();

    private static final String SCHEMA_NAME = "TEST_SCHEMA";

    private static final String OTHER_SCHEMA_NAME = "OTHER_SCHEMA";

    private static final String TABLE_NAME = "tbl1";

    private static final String COLUMN_NAME = "col1";

    private static final String COLUMN_TYPE = "INTEGER";

    private static final String PARTITION_NAME = "UNDER_2000";

    private static final String PARTITION_METHOD = "RANGE";

    private static final String PARTITION_EXPR = "col1";

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("each source spelling is reduced to the standard CUBRID type name")
    @CsvSource({
        // The fetcher keeps a table of its own, whose single entry maps STRING. The type helper
        // already answers varchar for that name, so the entry changes nothing today.
        "STRING,           varchar",
        // Everything else is left to the type helper.
        "VARCHAR,          varchar",
        "integer,          int",
        "SET_OF(INTEGER),  set",
    })
    void getStdDataType_reducesToTheStandardName(String sourceType, String expected)
            throws Exception {
        java.lang.reflect.Method method =
                CUBRIDSchemaFetcher.class.getDeclaredMethod("getStdDataType", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(FETCHER, sourceType)).isEqualTo(expected);
    }

    // The single-schema path keys its tables the same way it keys the cache, so two tables that
    // share an index name keep their own indexes.
    @Test
    @DisplayName("on the single-schema path two tables sharing an index name stay apart")
    void singleSchemaTablesSharingAnIndexName_stayApart() throws Exception {
        Connection conn = connectionOnVersion(10, 2);
        ResultSet rs = attachStatementQuery(conn, 2);
        when(rs.getString("class_name")).thenReturn("T1", "T2");
        when(rs.getString("index_name")).thenReturn("IX1", "IX1");
        when(rs.getString("is_unique")).thenReturn("NO", "NO");
        when(rs.getString("key_attr_name")).thenReturn("A", "B");
        when(rs.getString("asc_desc")).thenReturn("A", "A");
        Table first = tableWithColumn("T1", "A");
        Table second = tableWithColumn("T2", "B");
        Map<String, Table> tables = new HashMap<String, Table>();
        tables.put("T1", first);
        tables.put("T2", second);

        invokePrivate(
                "buildCUBRIDTableIndexes", new Class[] {Connection.class, Map.class}, conn, tables);

        assertThat(first.getIndexes().get(0).getColumnNames()).containsExactly("A");
        assertThat(second.getIndexes().get(0).getColumnNames()).containsExactly("B");
    }

    // DEFECT: the table comment is passed through commentEditor() twice - once inside the version
    // branch and again straight after - so each quote it holds is doubled and then doubled again.
    // The column comment beside it goes through once, and so does the user-schema path below
    // - see CUBRIDSchemaFetcher.buildCUBRIDTables()
    @Test
    @DisplayName("a table comment's quotes are doubled twice, the column's only once")
    void tableComment_isEscapedTwice() throws Exception {
        Connection conn = connectionOnVersion(11, 0);
        ResultSet rs = attachStatementQuery(conn, 1);
        stubOneAttributeRow(rs, "it's a table", "it's a column");
        Schema schema = schemaNamed("PUBLIC");

        @SuppressWarnings("unchecked")
        Map<String, Table> tables =
                (Map<String, Table>)
                        invokePrivate(
                                "buildCUBRIDTables",
                                new Class[] {
                                    Connection.class,
                                    Catalog.class,
                                    Schema.class,
                                    IBuildSchemaFilter.class
                                },
                                conn,
                                schema.getCatalog(),
                                schema,
                                null);

        assertThat(tables.get("T1").getComment()).isEqualTo("it" + "''''" + "s a table");
        assertThat(tables.get("T1").getColumns().get(0).getComment())
                .isEqualTo("it" + "''" + "s a column");
    }

    @Test
    @DisplayName("on the user-schema path both comments are escaped once")
    void userSchemaComments_areEscapedOnce() throws Exception {
        Connection conn = connectionOnVersion(11, 2);
        ResultSet rs = attachPreparedQuery(conn, 1);
        stubOneAttributeRow(rs, "it's a table", "it's a column");
        Schema schema = schemaNamed("HR");

        @SuppressWarnings("unchecked")
        Map<String, Table> tables =
                (Map<String, Table>)
                        invokePrivate(
                                "buildCUBRIDTablesWithUserSchema",
                                new Class[] {
                                    Connection.class,
                                    Catalog.class,
                                    Schema.class,
                                    IBuildSchemaFilter.class
                                },
                                conn,
                                schema.getCatalog(),
                                schema,
                                null);

        assertThat(tables.get("HR.T1").getComment()).isEqualTo("it" + "''" + "s a table");
        assertThat(tables.get("HR.T1").getColumns().get(0).getComment())
                .isEqualTo("it" + "''" + "s a column");
    }

    // DEFECT: the tables are held under "owner.table" but the index cache is keyed on the table
    // name alone, so two schemas that both carry a table of one name share a single Index. The
    // second schema's columns are appended to the first schema's index and its own table is left
    // with none
    // - see CUBRIDSchemaFetcher.buildCUBRIDTableIndexesWithUserSchema()
    @Test
    @DisplayName("two schemas with a same-named table share one index object")
    void sameTableNameAcrossSchemas_sharesOneIndex() throws Exception {
        Connection conn = connectionOnVersion(11, 2);
        ResultSet rs = attachStatementQuery(conn, 2);
        when(rs.getString("class_name")).thenReturn("T1", "T1");
        when(rs.getString("owner_name")).thenReturn("HR", "SALES");
        when(rs.getString("index_name")).thenReturn("IX1", "IX1");
        when(rs.getString("is_unique")).thenReturn("NO", "NO");
        when(rs.getString("key_attr_name")).thenReturn("A", "B");
        when(rs.getString("asc_desc")).thenReturn("A", "A");
        Table hrTable = tableWithColumn("T1", "A");
        Table salesTable = tableWithColumn("T1", "B");
        Map<String, Table> tables = new HashMap<String, Table>();
        tables.put("HR.T1", hrTable);
        tables.put("SALES.T1", salesTable);

        invokePrivate(
                "buildCUBRIDTableIndexesWithUserSchema",
                new Class[] {Connection.class, Map.class},
                conn,
                tables);

        assertThat(hrTable.getIndexes()).hasSize(1);
        assertThat(hrTable.getIndexes().get(0).getColumnNames()).containsExactly("A", "B");
        assertThat(salesTable.getIndexes()).isEmpty();
    }

    @Test
    @DisplayName("buildPartitions() maps db_partition rows to table PartitionInfo")
    void buildPartitions_mapsDbPartitionRowsToTablePartitionInfo() throws Exception {
        Catalog catalog = createCatalog();
        Schema schema = createSchema(SCHEMA_NAME);
        Table table = createTable(TABLE_NAME, COLUMN_NAME, COLUMN_TYPE);
        catalog.addSchema(schema);
        schema.addTable(table);

        Connection conn = mockConnection(11, 2);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        stubPartitionRow(rs, TABLE_NAME);

        FETCHER.buildPartitions(conn, catalog, schema, null);

        PartitionInfo partitionInfo = table.getPartitionInfo();

        assertAll(
                () -> assertThat(partitionInfo).isNotNull(),
                () -> assertThat(partitionInfo.getPartitionMethod()).isEqualTo(PARTITION_METHOD),
                () -> assertThat(partitionInfo.getPartitionExp()).isEqualTo(PARTITION_EXPR),
                () ->
                        assertThat(partitionInfo.getPartitionColumns())
                                .extracting(Column::getName)
                                .containsExactly(COLUMN_NAME),
                () -> assertThat(partitionInfo.getPartitionCount()).isEqualTo(1),
                () -> assertThat(partitionInfo.getPartitions()).hasSize(1),
                () ->
                        assertThat(partitionInfo.getPartitions().get(0).getPartitionName())
                                .isEqualTo(PARTITION_NAME),
                () ->
                        assertThat(partitionInfo.getPartitions().get(0).getPartitionDesc())
                                .isEqualTo("2000"),
                () -> assertThat(partitionInfo.getDDL()).contains("PARTITION BY RANGE"));
    }

    @Test
    @DisplayName(
            "buildPartitions() scopes the db_partition query by owner_name on CUBRID 11.2+"
                    + " (user-schema support)")
    void buildPartitions_scopesQueryByOwnerName_whenUserSchemaSupported() throws Exception {
        Catalog catalog = createCatalog();
        Schema schema = createSchema(SCHEMA_NAME);
        catalog.addSchema(schema);

        Connection conn = mockConnection(11, 2);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        FETCHER.buildPartitions(conn, catalog, schema, null);

        assertAll(
                () -> assertThat(sqlCaptor.getValue()).contains("owner_name"),
                () -> verify(stmt).setString(1, SCHEMA_NAME));
    }

    @Test
    @DisplayName(
            "buildPartitions() does not scope or bind owner_name on CUBRID versions before 11.2"
                    + " (no user-schema concept)")
    void buildPartitions_doesNotBindOwnerName_whenUserSchemaNotSupported() throws Exception {
        Catalog catalog = createCatalog();
        Schema schema = createSchema(SCHEMA_NAME);
        catalog.addSchema(schema);

        Connection conn = mockConnection(10, 1);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(conn.prepareStatement(sqlCaptor.capture())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        FETCHER.buildPartitions(conn, catalog, schema, null);

        assertAll(
                () -> assertThat(sqlCaptor.getValue()).doesNotContain("owner_name"),
                () -> verify(stmt, never()).setString(anyInt(), anyString()));
    }

    @Test
    @DisplayName(
            "buildPartitions() resolves the table within the schema being built, even when"
                    + " another schema has a table with the same name (regression guard)")
    void buildPartitions_doesNotLeakPartitionInfoAcrossSchemas_whenTableNameIsDuplicated()
            throws Exception {
        Catalog catalog = createCatalog();
        Schema schema = createSchema(SCHEMA_NAME);
        Schema otherSchema = createSchema(OTHER_SCHEMA_NAME);
        Table table = createTable(TABLE_NAME, COLUMN_NAME, COLUMN_TYPE);
        Table sameNamedTableInOtherSchema = createTable(TABLE_NAME, COLUMN_NAME, COLUMN_TYPE);
        catalog.addSchema(schema);
        catalog.addSchema(otherSchema);
        schema.addTable(table);
        otherSchema.addTable(sameNamedTableInOtherSchema);

        Connection conn = mockConnection(11, 2);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        stubPartitionRow(rs, TABLE_NAME);

        FETCHER.buildPartitions(conn, catalog, schema, null);

        assertAll(
                () -> assertThat(table.getPartitionInfo()).isNotNull(),
                () -> assertThat(sameNamedTableInOtherSchema.getPartitionInfo()).isNull());
    }

    @Test
    @DisplayName("buildSQLTable() gives a column with no type name varchar")
    void buildSQLTable_namesUntypedColumnsVarchar() throws Exception {
        Table table = FETCHER.buildSQLTable(oneColumnMetaData("", java.sql.Types.OTHER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getJdbcIDOfDataType())
                .isEqualTo(java.sql.Types.VARCHAR);
    }

    // The JDBC type the driver reported is replaced by the one CUBRID uses for that type name,
    // so a query's column is described the way a CUBRID column would be.
    @Test
    @DisplayName("buildSQLTable() restates the type with CUBRID's own id and spelling")
    void buildSQLTable_restatesTheTypeTheCubridWay() throws Exception {
        Table table =
                FETCHER.buildSQLTable(oneColumnMetaData("INTEGER", java.sql.Types.INTEGER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("INTEGER");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("int");
    }

    // A query returning a type CUBRID has no answer for stops the build rather than migrating a
    // column the target could never hold.
    @Test
    @DisplayName("buildSQLTable() refuses a type CUBRID does not have")
    void buildSQLTable_refusesAnUnsupportedType() {
        assertThatThrownBy(
                        () ->
                                FETCHER.buildSQLTable(
                                        oneColumnMetaData(
                                                "integer unsigned", java.sql.Types.INTEGER, 10)))
                .isInstanceOf(
                        com.cubrid.cubridmigration.cubrid.exception.UnSupportCUBRIDDataTypeException
                                .class);
    }

    // DEFECT: triggers are only read when the connecting user happens to be DBA, so a migration
    // run by an ordinary owner loses every trigger in the schema without a word
    // - see CUBRIDSchemaFetcher.buildTriggers()
    @Test
    @DisplayName("a user who is not DBA -> the trigger catalog is never even queried")
    void nonDbaUser_neverQueriesTheTriggerCatalog() throws Exception {
        Connection conn = connectionOnVersion(11, 2);
        when(conn.getMetaData().getUserName()).thenReturn("hr");
        Schema schema = schemaNamed("HR");
        List<Trigger> existing = new ArrayList<Trigger>();
        schema.setTriggers(existing);

        FETCHER.buildTriggers(conn, schema.getCatalog(), schema, null);

        assertThat(schema.getTriggers()).isSameAs(existing);
        verify(conn, never()).prepareStatement(anyString());
    }

    @Test
    @DisplayName("DBA in any case -> the trigger catalog is read")
    void dbaUser_readsTheTriggerCatalog() throws Exception {
        Connection conn = connectionOnVersion(11, 2);
        when(conn.getMetaData().getUserName()).thenReturn("dba");
        attachPreparedQuery(conn, 0);
        Schema schema = schemaNamed("HR");

        FETCHER.buildTriggers(conn, schema.getCatalog(), schema, null);

        verify(conn).prepareStatement(anyString());
        assertThat(schema.getTriggers()).isEmpty();
    }

    // DEFECT: the RETURN clause is written only when the routine returns void, which is exactly
    // when it is meaningless, and is left out for a function that does return something. The
    // generated DDL for a real function therefore names no return type
    // - see CUBRIDSchemaFetcher.creatSPDDL()
    @Test
    @DisplayName("creatSPDDL() writes RETURN for a void routine and omits it for a real one")
    void creatSPDDL_writesReturnOnlyForVoid() throws Exception {
        assertThat(storedProcedureDDL("void")).contains("RETURN void");
        assertThat(storedProcedureDDL("int")).doesNotContain("RETURN");
    }

    @Test
    @DisplayName("creatSPDDL() quotes the routine name and carries the language and target")
    void creatSPDDL_quotesTheNameAndCarriesTheBinding() throws Exception {
        assertThat(storedProcedureDDL("int"))
                .startsWith("CREATE FUNCTION \"f1\" (a int)")
                .contains("AS LANGUAGE JAVA")
                .contains("NAME 'Cls.m'");
    }

    // DEFECT: unique_name is added to the SELECT only from the user-schema version on, but the
    // row reader asks for that column whatever the version, so an older CUBRID raises on a
    // column its own query never selected
    // - see CUBRIDSchemaFetcher.getAllTriggers()
    @Test
    @DisplayName("an older CUBRID omits unique_name from the query yet still reads it")
    void olderVersion_readsAColumnItNeverSelected() throws Exception {
        Connection conn = connectionOnVersion(10, 2);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("UNIQUE_NAME")).thenThrow(new SQLException("no such column"));
        Schema schema = schemaNamed("HR");

        assertThatThrownBy(
                        () ->
                                invokePrivate(
                                        "getAllTriggers",
                                        new Class[] {Connection.class, Schema.class},
                                        conn,
                                        schema))
                .isInstanceOf(SQLException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        assertThat(sql.getValue()).doesNotContain("unique_name");
    }

    @Test
    @DisplayName("from the user-schema version on the column is selected before it is read")
    void userSchemaVersion_selectsTheColumnItReads() throws Exception {
        Connection conn = connectionOnVersion(11, 2);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("UNIQUE_NAME")).thenReturn("HR.trg1");
        when(rs.getString("NAME")).thenReturn("trg1");
        // CUBRIDTrigger parses the priority as a number, so it cannot be left unstubbed.
        when(rs.getString("PRIORITY")).thenReturn("0");
        Schema schema = schemaNamed("HR");

        @SuppressWarnings("unchecked")
        List<Trigger> triggers =
                (List<Trigger>)
                        invokePrivate(
                                "getAllTriggers",
                                new Class[] {Connection.class, Schema.class},
                                conn,
                                schema);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("unique_name");
        assertThat(triggers).extracting(Trigger::getName).containsExactly("trg1");
    }

    private static void stubPartitionRow(ResultSet rs, String tableName) throws Exception {
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("class_name")).thenReturn(tableName);
        when(rs.getString("partition_type")).thenReturn(PARTITION_METHOD);
        when(rs.getString("partition_expr")).thenReturn(PARTITION_EXPR);
        when(rs.getObject("partition_values")).thenReturn(new Object[] {"0", "2000"});
        when(rs.getString("partition_name")).thenReturn(PARTITION_NAME);
    }

    private static Connection mockConnection(int majorVersion, int minorVersion) throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseMajorVersion()).thenReturn(majorVersion);
        when(metaData.getDatabaseMinorVersion()).thenReturn(minorVersion);
        when(conn.getMetaData()).thenReturn(metaData);
        return conn;
    }

    private static String storedProcedureDDL(String returnType) throws Exception {
        java.util.Map<String, Object> row = new java.util.HashMap<String, Object>();
        row.put("SP_TYPE", "FUNCTION");
        row.put("SP_NAME", "f1");
        row.put("PARAMS", "a int");
        row.put("RETURN_TYPE", returnType);
        row.put("LANG", "JAVA");
        row.put("TARGET", "Cls.m");
        java.lang.reflect.Method method =
                CUBRIDSchemaFetcher.class.getDeclaredMethod("creatSPDDL", java.util.Map.class);
        method.setAccessible(true);
        return (String) method.invoke(FETCHER, row);
    }

    /** A one-column result set description, which is all buildSQLTable() reads. */
    private static java.sql.ResultSetMetaData oneColumnMetaData(
            String typeName, int jdbcType, int precision) throws java.sql.SQLException {
        java.sql.ResultSetMetaData rsm = mock(java.sql.ResultSetMetaData.class);
        when(rsm.getColumnCount()).thenReturn(1);
        when(rsm.getColumnLabel(1)).thenReturn("C1");
        when(rsm.getColumnName(1)).thenReturn("C1");
        when(rsm.getColumnType(1)).thenReturn(jdbcType);
        when(rsm.getColumnTypeName(1)).thenReturn(typeName);
        when(rsm.getPrecision(1)).thenReturn(precision);
        when(rsm.getScale(1)).thenReturn(0);
        return rsm;
    }

    /** A connection whose driver reports the given CUBRID version. */
    private static Connection connectionOnVersion(int major, int minor) throws SQLException {
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = attachMetaData(conn);
        when(metaData.getDatabaseMajorVersion()).thenReturn(major);
        when(metaData.getDatabaseMinorVersion()).thenReturn(minor);
        when(metaData.getDatabaseProductVersion()).thenReturn(major + "." + minor + ".0");
        return conn;
    }

    private static Object invokePrivate(String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = CUBRIDSchemaFetcher.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(FETCHER, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static void stubOneAttributeRow(ResultSet rs, String tableComment, String columnComment)
            throws SQLException {
        when(rs.getString("class_name")).thenReturn("T1");
        when(rs.getString("attr_name")).thenReturn("A");
        when(rs.getString("attr_type")).thenReturn("INSTANCE");
        when(rs.getString("data_type")).thenReturn("integer");
        when(rs.getString("is_nullable")).thenReturn("YES");
        when(rs.getString("is_reuse_oid_class")).thenReturn("NO");
        when(rs.getInt("prec")).thenReturn(10);
        when(rs.getInt("scale")).thenReturn(0);
        when(rs.getString("table_comment")).thenReturn(tableComment);
        when(rs.getString("column_comment")).thenReturn(columnComment);
        when(rs.getString("comment")).thenReturn(tableComment);
        when(rs.getString("attr_comment")).thenReturn(columnComment);
        when(rs.getString("owner_name")).thenReturn("HR");
    }

    private static Schema schemaNamed(String name) {
        Catalog catalog = new Catalog();
        catalog.setName("demodb");
        Schema schema = new Schema(catalog);
        schema.setName(name);
        catalog.addSchema(schema);
        return schema;
    }

    private static Table tableWithColumn(String tableName, String columnName) {
        Table table = new Table();
        table.setName(tableName);
        Column column = new Column();
        column.setName(columnName);
        table.addColumn(column);
        return table;
    }

    private static Catalog createCatalog() {
        Catalog catalog = new Catalog();
        catalog.setName("TEST_CATALOG");
        catalog.setDatabaseType(DatabaseType.CUBRID);
        return catalog;
    }

    private static Schema createSchema(String schemaName) {
        Schema schema = new Schema();
        schema.setName(schemaName);
        return schema;
    }

    private static Table createTable(String tableName, String columnName, String columnType) {
        Table table = new Table();
        table.setName(tableName);

        Column column = new Column();
        column.setName(columnName);
        column.setDataType(columnType);
        table.addColumn(column);

        return table;
    }
}
