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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
