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
package com.cubrid.cubridmigration.core.dbmetadata;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachMetaData;
import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.resultSetOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaEntry;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.DBExportHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@DisplayName("AbstractJDBCSchemaFetcher")
class AbstractJDBCSchemaFetcherTest {

    /** The class is abstract only for the three hooks below, so a bare subclass exercises it. */
    private static final class TestFetcher extends AbstractJDBCSchemaFetcher {

        TestFetcher() {
            factory = new DBObjectFactory();
        }

        @Override
        protected DBExportHelper getExportHelper() {
            return null;
        }

        @Override
        protected String getTableComment(Connection conn, String schemaName, String tableName) {
            return null;
        }

        @Override
        protected String getViewComment(Connection conn, String schemaName, String viewName) {
            return null;
        }

        @Override
        public DatabaseType getDBType() {
            return DatabaseType.CUBRID;
        }
    }

    private static final TestFetcher FETCHER = new TestFetcher();

    private Connection conn;
    private DatabaseMetaData metaData;
    private Catalog catalog;
    private Schema schema;

    /** A result set that yields nothing; stateless, so one instance serves every empty read. */
    private ResultSet noRows;

    @BeforeEach
    void setUp() throws SQLException {
        conn = mock(Connection.class);
        metaData = attachMetaData(conn);
        noRows = resultSetOf(0);
        catalog = new Catalog();
        catalog.setName("demodb");
        schema = new Schema(catalog);
        schema.setName("hr");
        catalog.addSchema(schema);
    }

    private static ConnParameters connectionTo(DatabaseType type, String dbName, String user) {
        return ConnParameters.getConParam(
                "name", "host", 33000, dbName, type, "utf-8", user, "pw", null, null);
    }

    /** The version and type-info reads every catalog build starts with. */
    private void stubCatalogPreamble() throws SQLException {
        when(metaData.getDatabaseProductName()).thenReturn("CUBRID");
        when(metaData.getDatabaseProductVersion()).thenReturn("11.2");
        when(metaData.getDriverName()).thenReturn("CUBRID JDBC");
        when(metaData.getDriverVersion()).thenReturn("11.2");
        when(metaData.getDatabaseMajorVersion()).thenReturn(11);
        when(metaData.getDatabaseMinorVersion()).thenReturn(2);
        when(metaData.getTypeInfo()).thenReturn(noRows);
    }

    private static Table tableWith(String name, String... columnNames) {
        Table table = new Table();
        table.setName(name);
        for (String columnName : columnNames) {
            Column column = new Column();
            column.setName(columnName);
            table.addColumn(column);
        }
        return table;
    }

    @Nested
    @DisplayName("buildCatalog()")
    class BuildCatalog {

        @Test
        @DisplayName("the catalog carries the connection's own name, host and port")
        void catalog_carriesTheConnectionDetails() throws SQLException {
            stubCatalogPreamble();
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built =
                    FETCHER.buildCatalog(
                            conn, connectionTo(DatabaseType.CUBRID, "demodb", "dba"), null);

            assertThat(built.getName()).isEqualTo("demodb");
            assertThat(built.getHost()).isEqualTo("host");
            assertThat(built.getPort()).isEqualTo(33000);
            assertThat(built.getDatabaseType()).isEqualTo(DatabaseType.CUBRID);
        }

        // Oracle and Tibero take a service-style database name, so only the part before the first
        // slash names the catalog, and it is upper-cased on the way.
        @Test
        @DisplayName("an Oracle service name -> only the part before the first slash, upper-cased")
        void oracleServiceName_isReducedToTheCatalogName() throws SQLException {
            stubCatalogPreamble();
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built =
                    FETCHER.buildCatalog(
                            conn, connectionTo(DatabaseType.ORACLE, "/orcl/service", "HR"), null);

            assertThat(built.getName()).isEqualTo("ORCL");
        }

        @Test
        @DisplayName("every other database keeps its name as it was given")
        void otherDatabase_keepsTheNameVerbatim() throws SQLException {
            stubCatalogPreamble();
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built =
                    FETCHER.buildCatalog(
                            conn, connectionTo(DatabaseType.MYSQL, "MyDb", "root"), null);

            assertThat(built.getName()).isEqualTo("MyDb");
        }

        // The schema is named after the connecting user, so a user whose name matches owns the
        // schema and anyone else is a grantor.
        @Test
        @DisplayName("the schema is built from the connecting user and marked as its own")
        void schema_isBuiltFromTheConnectingUser() throws SQLException {
            stubCatalogPreamble();
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built =
                    FETCHER.buildCatalog(
                            conn, connectionTo(DatabaseType.CUBRID, "demodb", "hr"), null);

            assertThat(built.getSchemas()).extracting(Schema::getName).containsExactly("HR");
        }
    }

    @Nested
    @DisplayName("buildSchemaCatalog()")
    class BuildSchemaCatalog {

        @Test
        @DisplayName("the schema list is the connecting user, upper-cased")
        void schemas_areTheConnectingUser() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalog(
                            conn, connectionTo(DatabaseType.CUBRID, "demodb", "hr"));

            assertThat(built.getSchemas()).containsExactly(new SchemaEntry("HR", false));
        }

        @Test
        @DisplayName("the version and supported types come from the driver's metadata")
        void versionAndTypes_comeFromTheDriver() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalog(
                            conn, connectionTo(DatabaseType.CUBRID, "demodb", "hr"));

            assertThat(built.getVersion().getDbProductName()).isEqualTo("CUBRID");
            assertThat(built.getVersion().getDriverName()).isEqualTo("CUBRID JDBC");
            assertThat(built.getSupportedDataType()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildSchemaCatalogForSchemas()")
    class BuildSchemaCatalogForSchemas {

        @Test
        @DisplayName("names are trimmed, upper-cased and de-duplicated, keeping the order given")
        void names_areNormalisedAndDeduplicated() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.CUBRID, "demodb", "hr"),
                            Arrays.asList(" sales ", "SALES", "hr"));

            assertThat(built.getSchemas())
                    .extracting(SchemaEntry::name)
                    .containsExactly("SALES", "HR");
        }

        @Test
        @DisplayName("null and blank entries are dropped rather than rejected")
        void nullAndBlankEntries_areDropped() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.CUBRID, "demodb", "hr"),
                            Arrays.asList(null, "  ", "hr"));

            assertThat(built.getSchemas()).extracting(SchemaEntry::name).containsExactly("HR");
        }

        // On a source that keeps one schema per user, only the connecting user's own schema is
        // his; every other name is one he was granted access to.
        @Test
        @DisplayName("on a multi-schema source every schema but the user's own is a grantor schema")
        void multiSchemaSource_marksTheOtherSchemasAsGrantor() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.ORACLE, "orcl", "HR"),
                            Arrays.asList("hr", "sales"));

            assertThat(built.getSchemas())
                    .containsExactly(new SchemaEntry("HR", false), new SchemaEntry("SALES", true));
        }

        @Test
        @DisplayName("on a single-schema source nothing is a grantor schema")
        void singleSchemaSource_marksNothingAsGrantor() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.CUBRID, "demodb", "dba"),
                            Arrays.asList("hr", "sales"));

            assertThat(built.getSchemas())
                    .containsExactly(new SchemaEntry("HR", false), new SchemaEntry("SALES", false));
        }

        // Only the leading slash and the upper-casing are reachable from here: ConnParameters
        // already splits a service-style name on "/" and keeps just the first segment, so by the
        // time resolveCatalogName() runs there is never an interior slash left for it to cut.
        @Test
        @DisplayName("an Oracle name -> upper-cased, with the leading slash dropped")
        void oracleName_isUpperCasedWithoutTheLeadingSlash() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog fromService =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.ORACLE, "/orcl/service", "HR"),
                            Collections.singletonList("hr"));
            SchemaCatalog fromPlainName =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.ORACLE, "orcl", "HR"),
                            Collections.singletonList("hr"));

            assertThat(fromService.getName()).isEqualTo("ORCL");
            assertThat(fromPlainName.getName()).isEqualTo("ORCL");
        }

        @Test
        @DisplayName("every other database keeps its name exactly as given")
        void otherDatabaseName_isKeptVerbatim() throws SQLException {
            stubCatalogPreamble();

            SchemaCatalog built =
                    FETCHER.buildSchemaCatalogForSchemas(
                            conn,
                            connectionTo(DatabaseType.CUBRID, "demoDB", "dba"),
                            Collections.singletonList("hr"));

            assertThat(built.getName()).isEqualTo("demoDB");
        }

        @Test
        @DisplayName("a list that normalises to nothing -> IllegalArgumentException")
        void listThatNormalisesToNothing_throwsIllegalArgumentException() {
            assertThatThrownBy(
                            () ->
                                    FETCHER.buildSchemaCatalogForSchemas(
                                            conn,
                                            connectionTo(DatabaseType.CUBRID, "demodb", "hr"),
                                            Arrays.asList(null, "   ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid schema or no schema specified.");
        }

        @Test
        @DisplayName("a null list -> IllegalArgumentException")
        void nullList_throwsIllegalArgumentException() {
            assertThatThrownBy(
                            () ->
                                    FETCHER.buildSchemaCatalogForSchemas(
                                            conn,
                                            connectionTo(DatabaseType.CUBRID, "demodb", "hr"),
                                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid schema or no schema specified.");
        }
    }

    @Nested
    @DisplayName("buildSchemaObjects()")
    class BuildSchemaObjects {

        private SchemaCatalog schemaCatalogFor(String... names) throws SQLException {
            stubCatalogPreamble();
            return FETCHER.buildSchemaCatalogForSchemas(
                    conn, connectionTo(DatabaseType.CUBRID, "demodb", "hr"), Arrays.asList(names));
        }

        @Test
        @DisplayName("only the named schemas are built")
        void onlyTheNamedSchemas_areBuilt() throws SQLException {
            SchemaCatalog sc = schemaCatalogFor("hr", "sales");
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built = FETCHER.buildSchemaObjects(conn, sc, Collections.singletonList("HR"));

            assertThat(built.getSchemas()).extracting(Schema::getName).containsExactly("HR");
        }

        @Test
        @DisplayName("an empty schema list -> IllegalArgumentException")
        void emptySchemaList_throwsIllegalArgumentException() throws SQLException {
            SchemaCatalog sc = schemaCatalogFor("hr");

            assertThatThrownBy(() -> FETCHER.buildSchemaObjects(conn, sc, Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid schema or no schema specified.");
        }

        // Tables are the only step the build cannot do without, so their failure stops everything
        // while the rest are logged and stepped over. That is what lets a source with, say, no
        // readable trigger catalog still produce a usable schema.
        @Test
        @DisplayName("a table read that fails stops the build")
        void failingTableRead_stopsTheBuild() throws SQLException {
            SchemaCatalog sc = schemaCatalogFor("hr");
            when(metaData.getTables(any(), any(), any(), any()))
                    .thenThrow(new SQLException("catalog unavailable"));

            assertThatThrownBy(
                            () ->
                                    FETCHER.buildSchemaObjects(
                                            conn, sc, Collections.singletonList("HR")))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("catalog unavailable");
        }

        @Test
        @DisplayName("a schema the catalog does not carry is skipped rather than created")
        void unknownSchemaName_isSkipped() throws SQLException {
            SchemaCatalog sc = schemaCatalogFor("hr");
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(noRows);

            Catalog built =
                    FETCHER.buildSchemaObjects(conn, sc, Arrays.asList("HR", "NOSUCHSCHEMA"));

            assertThat(built.getSchemas()).extracting(Schema::getName).containsExactly("HR");
        }
    }

    @Nested
    @DisplayName("getSchemaNames()")
    class GetSchemaNames {

        @Test
        @DisplayName("the connecting user, upper-cased, is the only schema")
        void connectingUser_isTheOnlySchema() throws SQLException {
            List<String> names =
                    FETCHER.getSchemaNames(conn, connectionTo(DatabaseType.CUBRID, "demodb", "hr"));

            assertThat(names).containsExactly("HR");
        }
    }

    @Nested
    @DisplayName("buildSQLTable()")
    class BuildSQLTable {

        private ResultSetMetaData oneColumn(
                String label, String name, int type, String typeName, int precision, int scale)
                throws SQLException {
            ResultSetMetaData rsm = mock(ResultSetMetaData.class);
            when(rsm.getColumnCount()).thenReturn(1);
            when(rsm.getColumnLabel(1)).thenReturn(label);
            when(rsm.getColumnName(1)).thenReturn(name);
            when(rsm.getColumnType(1)).thenReturn(type);
            when(rsm.getColumnTypeName(1)).thenReturn(typeName);
            when(rsm.getPrecision(1)).thenReturn(precision);
            when(rsm.getScale(1)).thenReturn(scale);
            return rsm;
        }

        @Test
        @DisplayName("the column label wins over the column name, so a SELECT alias is kept")
        void columnLabel_winsOverColumnName() throws SQLException {
            Table table =
                    FETCHER.buildSQLTable(
                            oneColumn("total", "sum(amount)", Types.INTEGER, "INT", 10, 0));

            assertThat(table.getColumns()).extracting(Column::getName).containsExactly("total");
        }

        @Test
        @DisplayName("a blank label falls back to the column name")
        void blankLabel_fallsBackToColumnName() throws SQLException {
            Table table =
                    FETCHER.buildSQLTable(oneColumn("", "amount", Types.INTEGER, "INT", 10, 0));

            assertThat(table.getColumns()).extracting(Column::getName).containsExactly("amount");
        }

        @Test
        @DisplayName("a null label falls back the same way")
        void nullLabel_fallsBackToColumnName() throws SQLException {
            Table table =
                    FETCHER.buildSQLTable(oneColumn(null, "amount", Types.INTEGER, "INT", 10, 0));

            assertThat(table.getColumns()).extracting(Column::getName).containsExactly("amount");
        }

        // A driver reports no precision for types that have none, and a column of precision zero
        // would render as an invalid target type, so one is the floor.
        @Test
        @DisplayName("precision of zero or less -> 1")
        void nonPositivePrecision_becomesOne() throws SQLException {
            Table zero = FETCHER.buildSQLTable(oneColumn("c", "c", Types.INTEGER, "INT", 0, 0));
            Table negative =
                    FETCHER.buildSQLTable(oneColumn("c", "c", Types.INTEGER, "INT", -5, 0));

            assertThat(zero.getColumns().get(0).getPrecision()).isEqualTo(1);
            assertThat(negative.getColumns().get(0).getPrecision()).isEqualTo(1);
        }

        @Test
        @DisplayName("a positive precision is kept as reported")
        void positivePrecision_isKept() throws SQLException {
            Table table =
                    FETCHER.buildSQLTable(oneColumn("c", "c", Types.NUMERIC, "NUMERIC", 12, 3));

            assertThat(table.getColumns().get(0).getPrecision()).isEqualTo(12);
            assertThat(table.getColumns().get(0).getScale()).isEqualTo(3);
        }

        // A result set says nothing about nullability, so every column of a SQL-defined source is
        // treated as nullable regardless of what the underlying table declares.
        @Test
        @DisplayName("every column is nullable, whatever the query selects")
        void everyColumn_isNullable() throws SQLException {
            Table table = FETCHER.buildSQLTable(oneColumn("c", "c", Types.INTEGER, "INT", 10, 0));

            assertThat(table.getColumns().get(0).isNullable()).isTrue();
        }
    }

    @Nested
    @DisplayName("buildTableColumns()")
    class BuildTableColumns {

        private ResultSet oneColumnRow(String tableName, String columnName, String typeName)
                throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(rs);
            when(rs.getString("TABLE_NAME")).thenReturn(tableName);
            when(rs.getString("COLUMN_NAME")).thenReturn(columnName);
            when(rs.getString("TYPE_NAME")).thenReturn(typeName);
            when(rs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
            return rs;
        }

        // The driver's catalog can answer for more than the table asked about, so rows naming a
        // different table are stepped over instead of being added to it.
        @Test
        @DisplayName("a row naming another table is skipped")
        void rowForAnotherTable_isSkipped() throws SQLException {
            oneColumnRow("OTHER_TABLE", "A", "VARCHAR");
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns()).isEmpty();
        }

        @Test
        @DisplayName("a matching row becomes a column carrying the driver's type and sizes")
        void matchingRow_becomesAColumn() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "VARCHAR");
            when(rs.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
            when(rs.getInt("COLUMN_SIZE")).thenReturn(30);
            when(rs.getInt("CHAR_OCTET_LENGTH")).thenReturn(90);
            when(rs.getInt("DECIMAL_DIGITS")).thenReturn(0);
            when(rs.getString("COLUMN_DEF")).thenReturn("none");
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            Column column = table.getColumns().get(0);
            assertThat(column.getName()).isEqualTo("A");
            assertThat(column.getDataType()).isEqualTo("VARCHAR");
            assertThat(column.getJdbcIDOfDataType()).isEqualTo(Types.VARCHAR);
            assertThat(column.getCharLength()).isEqualTo(30);
            assertThat(column.getPrecision()).isEqualTo(30);
            assertThat(column.getByteLength()).isEqualTo(90);
            assertThat(column.getDefaultValue()).isEqualTo("none");
            assertThat(column.isNullable()).isTrue();
        }

        // A scale wider than the precision cannot be rendered, so the precision is widened to 16
        // and then, if the scale is wider still, to one past it.
        @Test
        @DisplayName("a scale wider than the precision -> precision widened to 16")
        void scaleWiderThanPrecision_widensPrecisionToSixteen() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "DECIMAL");
            when(rs.getInt("COLUMN_SIZE")).thenReturn(2);
            when(rs.getInt("DECIMAL_DIGITS")).thenReturn(5);
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns().get(0).getPrecision()).isEqualTo(16);
            assertThat(table.getColumns().get(0).getScale()).isEqualTo(5);
        }

        @Test
        @DisplayName("a scale wider than 16 -> precision widened to one past the scale")
        void scaleWiderThanSixteen_widensPrecisionPastTheScale() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "DECIMAL");
            when(rs.getInt("COLUMN_SIZE")).thenReturn(2);
            when(rs.getInt("DECIMAL_DIGITS")).thenReturn(20);
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns().get(0).getPrecision()).isEqualTo(21);
        }

        @Test
        @DisplayName("a VARCHAR whose byte length the driver leaves at zero -> 255")
        void varcharWithoutByteLength_getsTwoHundredAndFiftyFive() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "varchar");
            when(rs.getInt("COLUMN_SIZE")).thenReturn(10);
            when(rs.getInt("CHAR_OCTET_LENGTH")).thenReturn(0);
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns().get(0).getByteLength()).isEqualTo(255);
        }

        @Test
        @DisplayName("IS_AUTOINCREMENT is read the same way whatever its case")
        void autoIncrementFlag_isCaseInsensitive() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "INT");
            when(rs.getString("IS_AUTOINCREMENT")).thenReturn("yes");
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns().get(0).isAutoIncrement()).isTrue();
        }

        @Test
        @DisplayName("NULLABLE other than columnNullable -> not nullable")
        void nullableFlagOtherThanColumnNullable_meansNotNullable() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "INT");
            when(rs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNoNulls);
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns().get(0).isNullable()).isFalse();
        }

        // A single unreadable column is logged and dropped so the rest of the table still builds.
        @Test
        @DisplayName("a row that fails mid-read is dropped, and the build carries on")
        void rowThatFailsMidRead_isDropped() throws SQLException {
            ResultSet rs = oneColumnRow("T1", "A", "INT");
            when(rs.getInt("COLUMN_SIZE")).thenThrow(new RuntimeException("bad row"));
            Table table = tableWith("T1");

            FETCHER.buildTableColumns(conn, catalog, schema, table);

            assertThat(table.getColumns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildTableFKs()")
    class BuildTableFKs {

        private ResultSet oneForeignKeyRow(short deleteRule, short updateRule) throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getImportedKeys(any(), any(), anyString())).thenReturn(rs);
            when(rs.getString("FK_NAME")).thenReturn("FK_ORDER_CUSTOMER");
            when(rs.getString("PKTABLE_NAME")).thenReturn("CUSTOMER");
            when(rs.getShort("DELETE_RULE")).thenReturn(deleteRule);
            when(rs.getShort("UPDATE_RULE")).thenReturn(updateRule);
            when(rs.getString("FKCOLUMN_NAME")).thenReturn("CUSTOMER_ID");
            when(rs.getString("PKCOLUMN_NAME")).thenReturn("ID");
            return rs;
        }

        @Test
        @DisplayName("a foreign key row -> a key naming its parent table and column pair")
        void foreignKeyRow_becomesAKey() throws SQLException {
            oneForeignKeyRow(
                    (short) DatabaseMetaData.importedKeyCascade,
                    (short) DatabaseMetaData.importedKeyCascade);
            Table table = tableWith("ORDERS", "CUSTOMER_ID");

            FETCHER.buildTableFKs(conn, catalog, schema, table);

            FK fk = table.getFks().get(0);
            assertThat(fk.getName()).isEqualTo("FK_ORDER_CUSTOMER");
            assertThat(fk.getReferencedTableName()).isEqualTo("CUSTOMER");
            assertThat(fk.getColumnNames()).containsExactly("CUSTOMER_ID");
        }

        @Test
        @DisplayName("cascade, restrict and set null are carried across unchanged")
        void knownRules_areCarriedAcross() throws SQLException {
            for (short rule :
                    new short[] {
                        DatabaseMetaData.importedKeyCascade,
                        DatabaseMetaData.importedKeyRestrict,
                        DatabaseMetaData.importedKeySetNull
                    }) {
                setUp();
                oneForeignKeyRow(rule, rule);
                Table table = tableWith("ORDERS", "CUSTOMER_ID");

                FETCHER.buildTableFKs(conn, catalog, schema, table);

                assertThat(table.getFks().get(0).getDeleteRule()).isEqualTo(rule);
                assertThat(table.getFks().get(0).getUpdateRule()).isEqualTo(rule);
            }
        }

        // CUBRID has no SET DEFAULT, so a source that declares one is migrated as NO ACTION
        // rather than being rejected.
        @Test
        @DisplayName("set default -> no action, the rule CUBRID has no answer for")
        void setDefaultRule_becomesNoAction() throws SQLException {
            oneForeignKeyRow(
                    (short) DatabaseMetaData.importedKeySetDefault,
                    (short) DatabaseMetaData.importedKeySetDefault);
            Table table = tableWith("ORDERS", "CUSTOMER_ID");

            FETCHER.buildTableFKs(conn, catalog, schema, table);

            assertThat(table.getFks().get(0).getDeleteRule()).isEqualTo(FK.ON_DELETE_NO_ACTION);
            assertThat(table.getFks().get(0).getUpdateRule()).isEqualTo(FK.ON_UPDATE_NO_ACTION);
        }

        @Test
        @DisplayName("a column the table does not carry is left out of the key")
        void unknownColumn_isLeftOut() throws SQLException {
            oneForeignKeyRow(
                    (short) DatabaseMetaData.importedKeyCascade,
                    (short) DatabaseMetaData.importedKeyCascade);
            Table table = tableWith("ORDERS", "SOMETHING_ELSE");

            FETCHER.buildTableFKs(conn, catalog, schema, table);

            assertThat(table.getFks().get(0).getColumnNames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildTableIndexes()")
    class BuildTableIndexes {

        private ResultSet indexRows(int rowCount) throws SQLException {
            ResultSet rs = resultSetOf(rowCount);
            when(metaData.getIndexInfo(any(), any(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn(rs);
            return rs;
        }

        @Test
        @DisplayName("an index row -> an index carrying its type, uniqueness and column")
        void indexRow_becomesAnIndex() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX_ORDERS_DATE");
            when(rs.getString("TYPE")).thenReturn(String.valueOf(DatabaseMetaData.tableIndexOther));
            when(rs.getString("COLUMN_NAME")).thenReturn("ORDER_DATE");
            when(rs.getBoolean("NON_UNIQUE")).thenReturn(false);
            when(rs.getString("ASC_OR_DESC")).thenReturn("A");
            Table table = tableWith("ORDERS", "ORDER_DATE");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            Index index = table.getIndexes().get(0);
            assertThat(index.getName()).isEqualTo("IX_ORDERS_DATE");
            assertThat(index.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexOther);
            assertThat(index.isUnique()).isTrue();
            assertThat(index.getColumnNames()).containsExactly("ORDER_DATE");
        }

        // The JDBC spec types TYPE as a short, but some drivers answer text that is not a number
        // at all, and hashed is what an unrecognised index falls back to.
        @Test
        @DisplayName("a TYPE that is not a number -> hashed")
        void unparsableType_becomesHashed() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX1");
            when(rs.getString("TYPE")).thenReturn("BTREE");
            when(rs.getString("COLUMN_NAME")).thenReturn("A");
            Table table = tableWith("T1", "A");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            assertThat(table.getIndexes().get(0).getIndexType())
                    .isEqualTo(DatabaseMetaData.tableIndexHashed);
        }

        @Test
        @DisplayName("a statistics row is not an index and is skipped")
        void statisticsRow_isSkipped() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX1");
            when(rs.getString("TYPE"))
                    .thenReturn(String.valueOf(DatabaseMetaData.tableIndexStatistic));
            // Everything the row would need to become an index is in place, so only the
            // statistics check can be what keeps it out.
            when(rs.getString("COLUMN_NAME")).thenReturn("A");
            when(rs.getBoolean("NON_UNIQUE")).thenReturn(true);
            when(rs.getString("ASC_OR_DESC")).thenReturn("A");
            Table table = tableWith("T1", "A");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            assertThat(table.getIndexes()).isEmpty();
        }

        @Test
        @DisplayName("a row naming a column the table does not carry is skipped")
        void rowForUnknownColumn_isSkipped() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX1");
            when(rs.getString("TYPE")).thenReturn("3");
            when(rs.getString("COLUMN_NAME")).thenReturn("NOT_A_COLUMN");
            Table table = tableWith("T1", "A");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            assertThat(table.getIndexes()).isEmpty();
        }

        @Test
        @DisplayName("no sort direction -> ascending")
        void missingSortDirection_meansAscending() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX1");
            when(rs.getString("TYPE")).thenReturn("3");
            when(rs.getString("COLUMN_NAME")).thenReturn("A");
            when(rs.getString("ASC_OR_DESC")).thenReturn(null);
            Table table = tableWith("T1", "A");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            assertThat(table.getIndexes().get(0).getColumnOrderRules()).containsExactly(true);
        }

        @Test
        @DisplayName("\"D\" -> descending")
        void descendingMarker_meansDescending() throws SQLException {
            ResultSet rs = indexRows(1);
            when(rs.getString("INDEX_NAME")).thenReturn("IX1");
            when(rs.getString("TYPE")).thenReturn("3");
            when(rs.getString("COLUMN_NAME")).thenReturn("A");
            when(rs.getString("ASC_OR_DESC")).thenReturn("D");
            Table table = tableWith("T1", "A");

            FETCHER.buildTableIndexes(conn, catalog, schema, table);

            assertThat(table.getIndexes().get(0).getColumnOrderRules()).containsExactly(false);
        }
    }

    @Nested
    @DisplayName("buildTablePK()")
    class BuildTablePK {

        private ResultSet primaryKeyRows(int rowCount) throws SQLException {
            ResultSet rs = resultSetOf(rowCount);
            when(metaData.getPrimaryKeys(any(), any(), anyString())).thenReturn(rs);
            return rs;
        }

        // The driver may answer the key's columns in any order, so KEY_SEQ, not row order, is
        // what puts them back together.
        @Test
        @DisplayName("columns are ordered by KEY_SEQ, not by the order the rows arrive")
        void columns_areOrderedByKeySeq() throws SQLException {
            ResultSet rs = primaryKeyRows(2);
            when(rs.getString("PK_NAME")).thenReturn("PK_ORDER_LINE");
            when(rs.getString("COLUMN_NAME")).thenReturn("LINE_NO", "ORDER_ID");
            when(rs.getInt("KEY_SEQ")).thenReturn(2, 1);
            Table table = tableWith("ORDER_LINE", "ORDER_ID", "LINE_NO");

            FETCHER.buildTablePK(conn, catalog, schema, table);

            assertThat(table.getPk().getName()).isEqualTo("PK_ORDER_LINE");
            assertThat(table.getPk().getPkColumns()).containsExactly("ORDER_ID", "LINE_NO");
        }

        @Test
        @DisplayName("the column is matched without regard to case")
        void column_isMatchedIgnoringCase() throws SQLException {
            ResultSet rs = primaryKeyRows(1);
            when(rs.getString("PK_NAME")).thenReturn("PK_T");
            when(rs.getString("COLUMN_NAME")).thenReturn("id");
            when(rs.getInt("KEY_SEQ")).thenReturn(1);
            Table table = tableWith("T1", "ID");

            FETCHER.buildTablePK(conn, catalog, schema, table);

            assertThat(table.getPk().getPkColumns()).containsExactly("ID");
        }

        @Test
        @DisplayName("a single-column key marks that column unique")
        void singleColumnKey_marksTheColumnUnique() throws SQLException {
            ResultSet rs = primaryKeyRows(1);
            when(rs.getString("PK_NAME")).thenReturn("PK_T");
            when(rs.getString("COLUMN_NAME")).thenReturn("ID");
            when(rs.getInt("KEY_SEQ")).thenReturn(1);
            Table table = tableWith("T1", "ID");

            FETCHER.buildTablePK(conn, catalog, schema, table);

            assertThat(table.getColumnByName("ID").isUnique()).isTrue();
        }

        @Test
        @DisplayName("no primary key row -> no key is set")
        void noRows_leaveTheTableWithoutAKey() throws SQLException {
            primaryKeyRows(0);
            Table table = tableWith("T1", "ID");

            FETCHER.buildTablePK(conn, catalog, schema, table);

            assertThat(table.getPk()).isNull();
        }

        // DEFECT: the index sharing the key's name is meant to be dropped here, but the loop works
        // on the list Table.getIndexes() hands back, which is a fresh copy, so the removal never
        // reaches the table. The dialect fetchers that do drop it call table.removeIndex()
        // themselves afterwards
        // - see AbstractJDBCSchemaFetcher.buildTablePK()
        @Test
        @DisplayName("the index named after the key survives, though the code means to drop it")
        void indexNamedAfterTheKey_isNotActuallyRemoved() throws SQLException {
            ResultSet rs = primaryKeyRows(1);
            when(rs.getString("PK_NAME")).thenReturn("PK_T");
            when(rs.getString("COLUMN_NAME")).thenReturn("ID");
            when(rs.getInt("KEY_SEQ")).thenReturn(1);
            Table table = tableWith("T1", "ID");
            Index sameName = new Index(table);
            sameName.setName("pk_t");
            table.addIndex(sameName);

            FETCHER.buildTablePK(conn, catalog, schema, table);

            assertThat(table.getIndexes()).extracting(Index::getName).containsExactly("pk_t");
        }
    }

    @Nested
    @DisplayName("buildTables()")
    class BuildTables {

        private void stubTableCatalog(String... tableNames) throws SQLException {
            ResultSet tables = resultSetOf(tableNames.length);
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(tables);
            if (tableNames.length == 1) {
                when(tables.getString("TABLE_NAME")).thenReturn(tableNames[0]);
            } else if (tableNames.length > 1) {
                when(tables.getString("TABLE_NAME"))
                        .thenReturn(
                                tableNames[0],
                                Arrays.copyOfRange(tableNames, 1, tableNames.length));
            }
            when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(noRows);
            when(metaData.getPrimaryKeys(any(), any(), anyString())).thenReturn(noRows);
            when(metaData.getImportedKeys(any(), any(), anyString())).thenReturn(noRows);
            when(metaData.getIndexInfo(any(), any(), anyString(), anyBoolean(), anyBoolean()))
                    .thenReturn(noRows);
        }

        @Test
        @DisplayName("each name in the catalog becomes a table on the schema")
        void eachCatalogName_becomesATable() throws SQLException {
            stubTableCatalog("ORDERS");

            FETCHER.buildTables(conn, catalog, schema, null);

            assertThat(schema.getTables()).extracting(Table::getName).containsExactly("ORDERS");
        }

        // A source that qualifies its names hands back "owner.table", and the owner is split off
        // so the table keeps its bare name and remembers who owns it.
        @Test
        @DisplayName("a qualified name is split into owner and table")
        void qualifiedName_isSplitIntoOwnerAndTable() throws SQLException {
            stubTableCatalog("HR.ORDERS");

            FETCHER.buildTables(conn, catalog, schema, null);

            Table built = schema.getTables().get(0);
            assertThat(built.getName()).isEqualTo("ORDERS");
            assertThat(built.getOwner()).isEqualTo("HR");
        }

        @Test
        @DisplayName("an unqualified name leaves the owner unset")
        void unqualifiedName_leavesTheOwnerUnset() throws SQLException {
            stubTableCatalog("ORDERS");

            FETCHER.buildTables(conn, catalog, schema, null);

            assertThat(schema.getTables().get(0).getOwner()).isNull();
        }

        @Test
        @DisplayName("a table the filter rejects is never built")
        void filteredTable_isNeverBuilt() throws SQLException {
            stubTableCatalog("ORDERS");

            FETCHER.buildTables(conn, catalog, schema, (sch, name) -> true);

            assertThat(schema.getTables()).isEmpty();
        }

        // A table whose columns cannot be read is kept rather than dropped, so the rest of the
        // migration still sees it - with whatever was built before the failure.
        @Test
        @DisplayName("a table that fails mid-build is still added, partially filled")
        void tableThatFailsMidBuild_isStillAdded() throws SQLException {
            stubTableCatalog("ORDERS");
            when(metaData.getColumns(any(), any(), anyString(), any()))
                    .thenThrow(new SQLException("column catalog unavailable"));

            FETCHER.buildTables(conn, catalog, schema, null);

            assertThat(schema.getTables()).extracting(Table::getName).containsExactly("ORDERS");
            assertThat(schema.getTables().get(0).getColumns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildViewColumns()")
    class BuildViewColumns {

        @Test
        @DisplayName("a view column carries the driver's type, size and nullability")
        void viewColumn_carriesTheDriverValues() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(rs);
            when(rs.getString("COLUMN_NAME")).thenReturn("TOTAL");
            when(rs.getString("TYPE_NAME")).thenReturn("NUMERIC");
            when(rs.getInt("DATA_TYPE")).thenReturn(Types.NUMERIC);
            when(rs.getInt("COLUMN_SIZE")).thenReturn(12);
            when(rs.getInt("DECIMAL_DIGITS")).thenReturn(2);
            when(rs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
            View view = new View();
            view.setName("V_TOTALS");

            FETCHER.buildViewColumns(conn, catalog, schema, view);

            Column column = view.getColumns().get(0);
            assertThat(column.getName()).isEqualTo("TOTAL");
            assertThat(column.getPrecision()).isEqualTo(12);
            assertThat(column.getScale()).isEqualTo(2);
            assertThat(column.isNullable()).isTrue();
        }

        // Unlike a table column, a view column keeps the precision the driver reports even when
        // the scale is wider, so no correction is applied here.
        @Test
        @DisplayName("a scale wider than the precision is left alone, unlike on a table")
        void scaleWiderThanPrecision_isLeftAlone() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(rs);
            when(rs.getString("COLUMN_NAME")).thenReturn("C");
            when(rs.getString("TYPE_NAME")).thenReturn("DECIMAL");
            when(rs.getInt("COLUMN_SIZE")).thenReturn(2);
            when(rs.getInt("DECIMAL_DIGITS")).thenReturn(5);
            View view = new View();
            view.setName("V1");

            FETCHER.buildViewColumns(conn, catalog, schema, view);

            assertThat(view.getColumns().get(0).getPrecision()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("buildViews()")
    class BuildViews {

        private void stubViewCatalog(String viewName) throws SQLException {
            ResultSet views = resultSetOf(1);
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(views);
            when(views.getString("TABLE_NAME")).thenReturn(viewName);
            when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(noRows);
        }

        // getAllViewNames() qualifies each name with the schema, and buildViews() splits it back
        // apart, so the view keeps its bare name and the schema becomes its owner.
        @Test
        @DisplayName("the schema prefix added on the way in is split off again")
        void schemaPrefix_isSplitOffAgain() throws SQLException {
            stubViewCatalog("V_ORDERS");

            FETCHER.buildViews(conn, catalog, schema, null);

            View built = schema.getViews().get(0);
            assertThat(built.getName()).isEqualTo("V_ORDERS");
            assertThat(built.getOwner()).isEqualTo("hr");
        }

        @Test
        @DisplayName("a view the filter rejects is never built")
        void filteredView_isNeverBuilt() throws SQLException {
            stubViewCatalog("V_ORDERS");

            FETCHER.buildViews(conn, catalog, schema, (sch, name) -> true);

            assertThat(schema.getViews()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllViewNames()")
    class GetAllViewNames {

        // Table names come back bare while view names are qualified with the schema. The two are
        // read from the same driver call, so the difference is this method's alone.
        @Test
        @DisplayName("each view name is qualified with the schema")
        void viewNames_areQualifiedWithTheSchema() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(rs);
            when(rs.getString("TABLE_NAME")).thenReturn("V_ORDERS");

            assertThat(FETCHER.getAllViewNames(conn, catalog, schema))
                    .containsExactly("hr.V_ORDERS");
        }

        @Test
        @DisplayName("a schema with no name leaves the view names bare")
        void unnamedSchema_leavesTheNamesBare() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(rs);
            when(rs.getString("TABLE_NAME")).thenReturn("V_ORDERS");

            assertThat(FETCHER.getAllViewNames(conn, catalog, new Schema(catalog)))
                    .containsExactly("V_ORDERS");
        }

        @Test
        @DisplayName("table names, read from the same call, are not qualified")
        void tableNames_areNotQualified() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getTables(any(), any(), any(), any())).thenReturn(rs);
            when(rs.getString("TABLE_NAME")).thenReturn("ORDERS");

            assertThat(FETCHER.getAllTableNames(conn, catalog, schema)).containsExactly("ORDERS");
        }
    }

    @Nested
    @DisplayName("getSourcePartitionDDL()")
    class GetSourcePartitionDDL {

        @Test
        @DisplayName("the DDL from PARTITION BY onwards")
        void partitionClause_isTakenFromPartitionByOnwards() {
            Table table = new Table();
            table.setDDL(
                    "CREATE TABLE t (a INT) PARTITION BY RANGE (a) (PARTITION p0 LESS THAN (10))");

            assertThat(FETCHER.getSourcePartitionDDL(table))
                    .isEqualTo("PARTITION BY RANGE (a) (PARTITION p0 LESS THAN (10))");
        }

        @Test
        @DisplayName("a DDL without a partition clause -> the empty string")
        void ddlWithoutAPartitionClause_givesTheEmptyString() {
            Table table = new Table();
            table.setDDL("CREATE TABLE t (a INT)");

            assertThat(FETCHER.getSourcePartitionDDL(table)).isEmpty();
        }

        @Test
        @DisplayName("no DDL at all -> the empty string")
        void missingDdl_givesTheEmptyString() {
            assertThat(FETCHER.getSourcePartitionDDL(new Table())).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSupportedSqlTypes()")
    class GetSupportedSqlTypes {

        @Test
        @DisplayName("each type row becomes an entry keyed by its name")
        void typeRow_becomesAnEntry() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getTypeInfo()).thenReturn(rs);
            when(rs.getString("TYPE_NAME")).thenReturn("VARCHAR");
            when(rs.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
            when(rs.getLong("PRECISION")).thenReturn(1073741823L);

            Map<String, List<DataType>> types = FETCHER.getSupportedSqlTypes(conn);

            assertThat(types.get("VARCHAR")).hasSize(1);
            assertThat(types.get("VARCHAR").get(0).getJdbcDataTypeID()).isEqualTo(Types.VARCHAR);
            assertThat(types.get("VARCHAR").get(0).getPrecision()).isEqualTo(1073741823L);
        }

        // Some drivers answer DATA_TYPE as text, so a failed numeric read falls back to parsing
        // the string form rather than losing the type.
        @Test
        @DisplayName("a DATA_TYPE the driver will not give as a number is parsed from its text")
        void textualDataType_isParsedFromItsText() throws SQLException {
            ResultSet rs = resultSetOf(1);
            when(metaData.getTypeInfo()).thenReturn(rs);
            when(rs.getString("TYPE_NAME")).thenReturn("VARCHAR");
            when(rs.getInt("DATA_TYPE")).thenThrow(new SQLException("not an int"));
            when(rs.getString("DATA_TYPE")).thenReturn("12");

            Map<String, List<DataType>> types = FETCHER.getSupportedSqlTypes(conn);

            assertThat(types.get("VARCHAR").get(0).getJdbcDataTypeID()).isEqualTo(12);
        }

        // A driver may report the same name more than once, one row per precision, and all of
        // them are kept under that one key.
        @Test
        @DisplayName("rows sharing a type name pile up under the same key")
        void rowsSharingAName_pileUpUnderOneKey() throws SQLException {
            ResultSet rs = resultSetOf(2);
            when(metaData.getTypeInfo()).thenReturn(rs);
            when(rs.getString("TYPE_NAME")).thenReturn("VARCHAR", "VARCHAR");
            when(rs.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR, Types.VARCHAR);
            when(rs.getLong("PRECISION")).thenReturn(255L, 65535L);

            Map<String, List<DataType>> types = FETCHER.getSupportedSqlTypes(conn);

            assertThat(types.get("VARCHAR"))
                    .extracting(DataType::getPrecision)
                    .containsExactly(255L, 65535L);
        }

        @Test
        @DisplayName("a driver with no metadata -> an empty map")
        void driverWithoutMetaData_givesAnEmptyMap() throws SQLException {
            Connection bare = mock(Connection.class);
            when(bare.getMetaData()).thenReturn(null);

            assertThat(FETCHER.getSupportedSqlTypes(bare)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isNULLType()")
    class IsNULLType {

        @Test
        @DisplayName("blank, NULL and UNKNOWN all count as no type, whatever their case")
        void blankNullAndUnknown_countAsNoType() {
            assertThat(FETCHER.isNULLType(null)).isTrue();
            assertThat(FETCHER.isNULLType("")).isTrue();
            assertThat(FETCHER.isNULLType("   ")).isTrue();
            assertThat(FETCHER.isNULLType("null")).isTrue();
            assertThat(FETCHER.isNULLType("UNKNOWN")).isTrue();
        }

        @Test
        @DisplayName("a real type name does not")
        void realTypeName_doesNot() {
            assertThat(FETCHER.isNULLType("varchar")).isFalse();
        }
    }

    @Nested
    @DisplayName("setUniquColumnByIndex()")
    class SetUniquColumnByIndex {

        @Test
        @DisplayName("a unique index over one column marks that column unique")
        void singleColumnUniqueIndex_marksTheColumn() {
            Table table = tableWith("T1", "A");
            Index index = new Index(table);
            index.setName("IX1");
            index.setUnique(true);
            index.addColumn("A", true);
            table.addIndex(index);

            FETCHER.setUniquColumnByIndex(table);

            assertThat(table.getColumnByName("A").isUnique()).isTrue();
        }

        // A unique index over several columns constrains the combination, not any one column, so
        // none of them is marked.
        @Test
        @DisplayName("a unique index over several columns marks none of them")
        void multiColumnUniqueIndex_marksNothing() {
            Table table = tableWith("T1", "A", "B");
            Index index = new Index(table);
            index.setName("IX1");
            index.setUnique(true);
            index.addColumn("A", true);
            index.addColumn("B", true);
            table.addIndex(index);

            FETCHER.setUniquColumnByIndex(table);

            assertThat(table.getColumnByName("A").isUnique()).isFalse();
            assertThat(table.getColumnByName("B").isUnique()).isFalse();
        }

        @Test
        @DisplayName("an index that is not unique marks nothing")
        void nonUniqueIndex_marksNothing() {
            Table table = tableWith("T1", "A");
            Index index = new Index(table);
            index.setName("IX1");
            index.setUnique(false);
            index.addColumn("A", true);
            table.addIndex(index);

            FETCHER.setUniquColumnByIndex(table);

            assertThat(table.getColumnByName("A").isUnique()).isFalse();
        }

        // DEFECT: the column looked up from the index name is used without a null check, so an
        // index naming a column the table does not carry aborts the whole table. The primary-key
        // twin below guards the same lookup
        // - see AbstractJDBCSchemaFetcher.setUniquColumnByIndex()
        @Test
        @DisplayName("an index naming a column the table lacks -> NullPointerException")
        void indexOverUnknownColumn_throwsNullPointerException() {
            Table table = tableWith("T1", "A");
            Index index = new Index(table);
            index.setName("IX1");
            index.setUnique(true);
            index.addColumn("GONE", true);
            table.addIndex(index);

            assertThatThrownBy(() -> FETCHER.setUniquColumnByIndex(table))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("setUniquColumnByPK()")
    class SetUniquColumnByPK {

        @Test
        @DisplayName("a single-column key marks that column unique")
        void singleColumnKey_marksTheColumn() {
            Table table = tableWith("T1", "ID");
            PK pk = new PK(table);
            pk.setName("PK_T");
            pk.addColumn("ID");
            table.setPk(pk);

            FETCHER.setUniquColumnByPK(table);

            assertThat(table.getColumnByName("ID").isUnique()).isTrue();
        }

        @Test
        @DisplayName("a key over several columns marks none of them")
        void multiColumnKey_marksNothing() {
            Table table = tableWith("T1", "A", "B");
            PK pk = new PK(table);
            pk.setName("PK_T");
            pk.addColumn("A");
            pk.addColumn("B");
            table.setPk(pk);

            FETCHER.setUniquColumnByPK(table);

            assertThat(table.getColumnByName("A").isUnique()).isFalse();
        }

        @Test
        @DisplayName("no key at all -> nothing happens")
        void noKey_changesNothing() {
            Table table = tableWith("T1", "A");

            FETCHER.setUniquColumnByPK(table);

            assertThat(table.getColumnByName("A").isUnique()).isFalse();
        }

        // Unlike the index twin above, a key naming a column the table lacks is stepped over.
        @Test
        @DisplayName("a key naming a column the table lacks is stepped over")
        void keyOverUnknownColumn_isSteppedOver() {
            Table table = tableWith("T1", "A");
            PK pk = new PK(table);
            pk.setName("PK_T");
            pk.addColumn("GONE");
            table.setPk(pk);

            FETCHER.setUniquColumnByPK(table);

            assertThat(table.getColumnByName("A").isUnique()).isFalse();
        }
    }

    @Nested
    @DisplayName("isYes()")
    class IsYes {

        @Test
        @DisplayName("\"YES\" in any case is the only true, and null is safe")
        void onlyYesIsTrue() {
            assertThat(AbstractJDBCSchemaFetcher.isYes("YES")).isTrue();
            assertThat(AbstractJDBCSchemaFetcher.isYes("yes")).isTrue();
            assertThat(AbstractJDBCSchemaFetcher.isYes("NO")).isFalse();
            assertThat(AbstractJDBCSchemaFetcher.isYes("")).isFalse();
            assertThat(AbstractJDBCSchemaFetcher.isYes(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("commentEditor()")
    class CommentEditor {

        // A comment is pasted into generated DDL between single quotes, so each quote it carries
        // has to be doubled first.
        @Test
        @DisplayName("a single quote is doubled so the comment survives being quoted")
        void singleQuote_isDoubled() {
            assertThat(FETCHER.commentEditor("it's here")).isEqualTo("it''s here");
        }

        @Test
        @DisplayName("null -> null")
        void nullComment_staysNull() {
            assertThat(FETCHER.commentEditor(null)).isNull();
        }

        @Test
        @DisplayName("a comment without quotes is untouched")
        void commentWithoutQuotes_isUntouched() {
            assertThat(FETCHER.commentEditor("plain comment")).isEqualTo("plain comment");
        }

        // The method has no idea whether it has run before, so a comment that already carries
        // doubled quotes comes back with four. Callers that apply it twice double them again.
        @Test
        @DisplayName("already doubled quotes are doubled once more")
        void alreadyDoubledQuotes_areDoubledAgain() {
            assertThat(FETCHER.commentEditor("a''b")).isEqualTo("a''''b");
        }
    }
}
