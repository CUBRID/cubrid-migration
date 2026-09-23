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
package com.cubrid.cubridmigration.mariadb.meta;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachStatementQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Version;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TimeZone;

@DisplayName("MariaDBSchemaFetcher")
@ResourceLock(Resources.TIME_ZONE)
class MariaDBSchemaFetcherTest {
    private static final TimeZone ORIGINAL_ZONE = TimeZone.getDefault();

    private static final MariaDBSchemaFetcher FETCHER = new MariaDBSchemaFetcher();

    // Intentionally use different catalog/schema names so a regression (falling back to
    // schema.getName()) can be detected
    private static final String CATALOG_NAME = "mydb";
    private static final String SCHEMA_NAME = "some_schema";
    private static final String TABLE_NAME = "tbl1";
    private static final String COLUMN_NAME = "col1";
    private static final String COLUMN_TYPE = "INT";
    private static final String PARTITION_NAME = "p_under_2000";
    private static final String PARTITION_METHOD = "RANGE";
    private static final String PARTITION_EXPR = "col1";
    private static final String TABLE_DDL =
            "CREATE TABLE `tbl1` (`col1` int(11) DEFAULT NULL) ENGINE=InnoDB PARTITION BY RANGE"
                    + " (col1) (PARTITION p_under_2000 VALUES LESS THAN (2000))\n";

    @Test
    @DisplayName("buildPartitions() maps INFORMATION_SCHEMA.PARTITIONS rows to table PartitionInfo")
    void buildPartitions_mapsPartitionRowsToTablePartitionInfo() throws Exception {
        Catalog catalog = createCatalog(CATALOG_NAME);
        Schema schema = createSchema(SCHEMA_NAME);
        Table table = createTable(TABLE_NAME, COLUMN_NAME, COLUMN_TYPE);
        table.setDDL(TABLE_DDL);
        catalog.addSchema(schema);
        schema.addTable(table);

        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("TABLE_NAME")).thenReturn(TABLE_NAME);
        when(rs.getString("PARTITION_NAME")).thenReturn(PARTITION_NAME);
        when(rs.getString("PARTITION_METHOD")).thenReturn(PARTITION_METHOD);
        when(rs.getString("PARTITION_EXPRESSION")).thenReturn(PARTITION_EXPR);
        when(rs.getInt("PARTITION_ORDINAL_POSITION")).thenReturn(1);
        when(rs.getString("PARTITION_DESCRIPTION")).thenReturn("2000");
        when(rs.getString("SUBPARTITION_NAME")).thenReturn(null);
        when(rs.getString("SUBPARTITION_METHOD")).thenReturn(null);
        when(rs.getString("SUBPARTITION_EXPRESSION")).thenReturn(null);
        when(rs.getInt("SUBPARTITION_ORDINAL_POSITION")).thenReturn(0);

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
                () -> assertThat(partitionInfo.getPartitions()).hasSize(1),
                () ->
                        assertThat(partitionInfo.getPartitions().get(0).getPartitionName())
                                .isEqualTo(PARTITION_NAME),
                () -> assertThat(partitionInfo.getDDL()).contains("PARTITION BY RANGE"));
    }

    @Test
    @DisplayName(
            "buildPartitions() queries INFORMATION_SCHEMA.PARTITIONS by catalog name, not schema"
                    + " name (regression guard)")
    void buildPartitions_queriesByCatalogName() throws Exception {
        Catalog catalog = createCatalog(CATALOG_NAME);
        Schema schema = createSchema(SCHEMA_NAME);
        catalog.addSchema(schema);

        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        FETCHER.buildPartitions(conn, catalog, schema, null);

        verify(stmt).setString(1, CATALOG_NAME);
    }

    @Test
    @DisplayName(
            "getSourcePartitionDDL() returns empty string when table DDL is null (regression"
                    + " guard)")
    void getSourcePartitionDDL_returnsEmptyString_whenTableDDLIsNull() {
        Table table = createTable(TABLE_NAME, COLUMN_NAME, COLUMN_TYPE);
        // table.setDDL(...) is intentionally not called, reproducing a null getDDL()

        String result = FETCHER.getSourcePartitionDDL(table);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildSQLTable() renames INTEGER to MySQL's own spelling")
    void buildSQLTable_renamesIntegerToInt() throws Exception {
        Table table =
                FETCHER.buildSQLTable(oneColumnMetaData("INTEGER", java.sql.Types.INTEGER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("int");
    }

    @Test
    @DisplayName("buildSQLTable() renames INTEGER UNSIGNED the same way")
    void buildSQLTable_renamesIntegerUnsigned() throws Exception {
        Table table =
                FETCHER.buildSQLTable(
                        oneColumnMetaData("integer unsigned", java.sql.Types.INTEGER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("int unsigned");
    }

    @Test
    @DisplayName("buildSQLTable() gives a column with no type name varchar")
    void buildSQLTable_namesUntypedColumnsVarchar() throws Exception {
        Table table = FETCHER.buildSQLTable(oneColumnMetaData("", java.sql.Types.OTHER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getJdbcIDOfDataType())
                .isEqualTo(java.sql.Types.VARCHAR);
    }

    // The instance is parsed from the shown type, where the parentheses hold the members rather
    // than a length, so unlike every other type it comes back carrying no precision.
    @Test
    @DisplayName("buildSQLTable() parses an enum's members into a type instance")
    void buildSQLTable_parsesEnumMembers() throws Exception {
        Table table =
                FETCHER.buildSQLTable(oneColumnMetaData("enum('a','b')", java.sql.Types.CHAR, 1));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("enum");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("enum('a','b')");
        assertThat(table.getColumns().get(0).getDataTypeInstance().getPrecision()).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0}.{1} -> {2}")
    @DisplayName("partitions arrived in 5.1, so anything older is not offered them")
    @CsvSource({
        // Below 5.1 the server has no partition catalog to read.
        "4, 9, false",
        "5, 0, false",
        // From 5.1 on it does.
        "5, 1, true",
        "5, 7, true",
        "6, 0, true",
        "8, 0, true",
    })
    void isSupportPartitionVersion_startsAtFiveOne(int major, int minor, boolean expected) {
        Version version = new Version();
        version.setDbMajorVersion(major);
        version.setDbMinorVersion(minor);

        assertThat(FETCHER.isSupportPartitionVersion(version)).isEqualTo(expected);
    }

    // DEFECT: the clause is cut two characters short of the end of the DDL, so the closing
    // parentheses are lost. AbstractJDBCSchemaFetcher takes the same clause to the end of the
    // string, and this override is the only place the two differ
    // - see MariaDBSchemaFetcher.getSourcePartitionDDL()
    @Test
    @DisplayName("getSourcePartitionDDL() drops the last two characters of the clause")
    void getSourcePartitionDDL_dropsTheLastTwoCharacters() {
        Table table = createTable("orders", "a", "int");
        table.setDDL(
                "CREATE TABLE orders (a INT) PARTITION BY RANGE (a) (PARTITION p0 VALUES LESS"
                        + " THAN (10))");

        assertThat(FETCHER.getSourcePartitionDDL(table))
                .isEqualTo("PARTITION BY RANGE (a) (PARTITION p0 VALUES LESS THAN (10");
    }

    @Test
    @DisplayName("getSourcePartitionDDL() returns empty when the DDL has no partition clause")
    void getSourcePartitionDDL_returnsEmpty_whenThereIsNoPartitionClause() {
        Table table = createTable("orders", "a", "int");
        table.setDDL("CREATE TABLE orders (a INT)");

        assertThat(FETCHER.getSourcePartitionDDL(table)).isEmpty();
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(ORIGINAL_ZONE);
    }

    @Test
    @DisplayName("getTimezone() turns the server's hour offset into a GMT label")
    void getTimezone_labelsTheServerOffset() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachStatementQuery(conn, 1);
        when(rs.getInt("TIMEZONE")).thenReturn(9);

        assertThat(FETCHER.getTimezone(conn)).isEqualTo("GMT+09:00");
    }

    // DEFECT: the query path hands getTZFromOffset() a number of hours, but the fallback hands it
    // the local raw offset in milliseconds, which the same method then formats as if it were
    // hours. A server whose timezone cannot be read is labelled GMT+32400000:00 in Seoul
    // - see MariaDBSchemaFetcher.getTimezone()
    @Test
    @DisplayName("a server whose timezone cannot be read -> the local offset in milliseconds")
    void unreadableServerTimezone_fallsBackToMilliseconds() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        Connection conn = mock(Connection.class);
        when(conn.createStatement()).thenThrow(new SQLException("no statement"));

        assertThat(FETCHER.getTimezone(conn)).isEqualTo("GMT+32400000:00");
    }

    // The fallback only looks right where the offset is zero, because zero milliseconds and zero
    // hours render the same.
    @Test
    @DisplayName("the fallback happens to look right only at UTC")
    void fallbackAtUtc_looksCorrectByCoincidence() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Connection conn = mock(Connection.class);
        when(conn.createStatement()).thenThrow(new SQLException("no statement"));

        assertThat(FETCHER.getTimezone(conn)).isEqualTo("GMT+00:00");
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

    private static Catalog createCatalog(String catalogName) {
        Catalog catalog = new Catalog();
        catalog.setName(catalogName);
        catalog.setDatabaseType(DatabaseType.MARIADB);
        Version version = new Version();
        version.setDbMajorVersion(10);
        version.setDbMinorVersion(5);
        catalog.setVersion(version);
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
