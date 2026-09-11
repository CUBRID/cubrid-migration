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
package com.cubrid.cubridmigration.mysql.meta;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@DisplayName("MySQLSchemaFetcher")
class MySQLSchemaFetcherTest {

    private static final MySQLSchemaFetcher FETCHER = new MySQLSchemaFetcher();

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

    private static Catalog createCatalog(String catalogName) {
        Catalog catalog = new Catalog();
        catalog.setName(catalogName);
        catalog.setDatabaseType(DatabaseType.MYSQL);
        Version version = new Version();
        version.setDbMajorVersion(8);
        version.setDbMinorVersion(0);
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
