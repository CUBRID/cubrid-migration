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
package com.cubrid.cubridmigration.tibero.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@DisplayName("TiberoPartitionMetadataLoader")
class TiberoPartitionMetadataLoaderTest {

    private static final class TestContext {
        private final TiberoPartitionMetadataLoader loader = new TiberoPartitionMetadataLoader();
        private final DBObjectFactory factory = new DBObjectFactory();
        private final Schema schema = new Schema();
        private final Connection conn = mock(Connection.class);

        private final PreparedStatement partTablesStmt = mock(PreparedStatement.class);
        private final PreparedStatement partColumnsStmt = mock(PreparedStatement.class);
        private final PreparedStatement subPartColumnsStmt = mock(PreparedStatement.class);
        private final PreparedStatement partitionsStmt = mock(PreparedStatement.class);
        private final PreparedStatement subPartitionsStmt = mock(PreparedStatement.class);

        private final ResultSet partTablesRs = mock(ResultSet.class);
        private final ResultSet partColumnsRs = mock(ResultSet.class);
        private final ResultSet subPartColumnsRs = mock(ResultSet.class);
        private final ResultSet partitionsRs = mock(ResultSet.class);
        private final ResultSet subPartitionsRs = mock(ResultSet.class);

        private final List<String> partTableNames = new ArrayList<String>();
        private final List<String> partitionMethods = new ArrayList<String>();
        private final List<Integer> partitionCounts = new ArrayList<Integer>();
        private final List<Integer> partitionKeyCounts = new ArrayList<Integer>();

        private final List<String> partColumnTableNames = new ArrayList<String>();
        private final List<String> partColumnNames = new ArrayList<String>();

        private final List<String> partitionTableNames = new ArrayList<String>();
        private final List<String> partitionNames = new ArrayList<String>();
        private final List<StringReader> partitionBounds = new ArrayList<StringReader>();
        private final List<Integer> partitionPositions = new ArrayList<Integer>();

        private TestContext() {
            schema.setName("APP");
        }

        private Table addTable(String tableName, String columnName, String columnType) {
            Table table = factory.createTable();
            table.setName(tableName);

            Column column = factory.createColumn();
            column.setName(columnName);
            column.setDataType(columnType);
            table.addColumn(column);

            schema.addTable(table);
            return table;
        }

        private void addPartitionTableRow(String tableName, String method, int partitionCount) {
            partTableNames.add(tableName);
            partitionMethods.add(method);
            partitionCounts.add(partitionCount);
            partitionKeyCounts.add(1);
        }

        private void addPartitionKeyRow(String tableName, String columnName) {
            partColumnTableNames.add(tableName);
            partColumnNames.add(columnName);
        }

        private void addPartitionRow(
                String tableName, String partitionName, String bound, int partitionPosition) {
            partitionTableNames.add(tableName);
            partitionNames.add(partitionName);
            partitionBounds.add(new StringReader(bound));
            partitionPositions.add(partitionPosition);
        }

        private void prepare() throws Exception {
            when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PART_TABLES))
                    .thenReturn(partTablesStmt);
            when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PART_COLUMN))
                    .thenReturn(partColumnsStmt);
            when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_SUBPART_KEY_COLUMN))
                    .thenReturn(subPartColumnsStmt);
            when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_PARTITIONS))
                    .thenReturn(partitionsStmt);
            when(conn.prepareStatement(TiberoSqlConstants.SQL_GET_SUB_PART_TABLES))
                    .thenReturn(subPartitionsStmt);

            when(partTablesStmt.executeQuery()).thenReturn(partTablesRs);
            when(partColumnsStmt.executeQuery()).thenReturn(partColumnsRs);
            when(subPartColumnsStmt.executeQuery()).thenReturn(subPartColumnsRs);
            when(partitionsStmt.executeQuery()).thenReturn(partitionsRs);
            when(subPartitionsStmt.executeQuery()).thenReturn(subPartitionsRs);

            stubNext(partTablesRs, partTableNames.size());
            stubNext(partColumnsRs, partColumnNames.size());
            stubNext(partitionsRs, partitionNames.size());

            when(subPartColumnsRs.next()).thenReturn(false);
            when(subPartitionsRs.next()).thenReturn(false);

            stubStrings(partTablesRs, "TABLE_NAME", partTableNames);
            stubStrings(partTablesRs, "PARTITIONING_TYPE", partitionMethods);
            stubInts(partTablesRs, "PARTITION_COUNT", partitionCounts);
            stubInts(partTablesRs, "PARTITIONING_KEY_COUNT", partitionKeyCounts);
            stubStrings(
                    partTablesRs, "SUBPARTITIONING_TYPE", repeat("NONE", partTableNames.size()));
            stubInts(partTablesRs, "DEF_SUBPARTITION_COUNT", repeatInt(0, partTableNames.size()));
            stubInts(
                    partTablesRs, "SUBPARTITIONING_KEY_COUNT", repeatInt(0, partTableNames.size()));

            stubStrings(partColumnsRs, "NAME", partColumnTableNames);
            stubStrings(partColumnsRs, "COLUMN_NAME", partColumnNames);

            stubStrings(partitionsRs, "TABLE_NAME", partitionTableNames);
            stubStrings(partitionsRs, "PARTITION_NAME", partitionNames);
            stubReaders(partitionsRs, "BOUND", partitionBounds);
            stubInts(partitionsRs, "PARTITION_POSITION", partitionPositions);
        }

        private void execute() throws Exception {
            prepare();
            loader.buildPartitions(conn, schema, factory);
        }

        private static void stubNext(ResultSet rs, int rowCount) throws Exception {
            Boolean[] sequence = new Boolean[rowCount + 1];
            for (int i = 0; i < rowCount; i++) {
                sequence[i] = true;
            }
            sequence[rowCount] = false;
            when(rs.next()).thenReturn(sequence[0], tail(sequence));
        }

        private static Boolean[] tail(Boolean[] values) {
            Boolean[] tail = new Boolean[Math.max(values.length - 1, 0)];
            if (values.length > 1) {
                System.arraycopy(values, 1, tail, 0, values.length - 1);
            }
            return tail;
        }

        private static void stubStrings(ResultSet rs, String columnName, List<String> values)
                throws Exception {
            OngoingStubbing<String> stubbing = when(rs.getString(columnName));
            for (String value : values) {
                stubbing = stubbing.thenReturn(value);
            }
        }

        private static void stubStrings(ResultSet rs, String columnName, String[] values)
                throws Exception {
            OngoingStubbing<String> stubbing = when(rs.getString(columnName));
            for (String value : values) {
                stubbing = stubbing.thenReturn(value);
            }
        }

        private static void stubReaders(ResultSet rs, String columnName, List<StringReader> values)
                throws Exception {
            OngoingStubbing<java.io.Reader> stubbing = when(rs.getCharacterStream(columnName));
            for (StringReader value : values) {
                stubbing = stubbing.thenReturn(value);
            }
        }

        private static void stubInts(ResultSet rs, String columnName, List<Integer> values)
                throws Exception {
            OngoingStubbing<Integer> stubbing = when(rs.getInt(columnName));
            for (Integer value : values) {
                stubbing = stubbing.thenReturn(value);
            }
        }

        private static void stubInts(ResultSet rs, String columnName, int[] values)
                throws Exception {
            OngoingStubbing<Integer> stubbing = when(rs.getInt(columnName));
            for (int value : values) {
                stubbing = stubbing.thenReturn(value);
            }
        }

        private static String[] repeat(String value, int count) {
            String[] result = new String[count];
            for (int i = 0; i < count; i++) {
                result[i] = value;
            }
            return result;
        }

        private static int[] repeatInt(int value, int count) {
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = value;
            }
            return result;
        }
    }

    @Test
    @DisplayName("buildPartitions() keeps partition metadata when source table DDL is absent")
    void buildPartitions_doesNotRequireSourceTableDDL() throws Exception {
        TestContext context = new TestContext();
        Table table = context.addTable("CMT_RANGE_PART", "SALE_DATE", "DATE");

        context.addPartitionTableRow("CMT_RANGE_PART", "RANGE", 1);
        context.addPartitionKeyRow("CMT_RANGE_PART", "SALE_DATE");
        context.addPartitionRow("CMT_RANGE_PART", "P2024", "(DATE '2025-01-01')", 1);

        context.execute();

        PartitionInfo partitionInfo = table.getPartitionInfo();
        assertThat(partitionInfo).isNotNull();
        assertThat(partitionInfo.getPartitionMethod()).isEqualTo("RANGE");
        assertThat(partitionInfo.getPartitionColumns())
                .extracting(Column::getName)
                .containsExactly("SALE_DATE");
        assertThat(partitionInfo.getPartitions()).hasSize(1);
        assertThat(partitionInfo.getPartitions().get(0).getPartitionDesc())
                .isEqualTo("DATE '2025-01-01'");
        assertThat(partitionInfo.getDDL()).contains("PARTITION BY RANGE(SALE_DATE)");
    }

    @Test
    @DisplayName("buildPartitions() normalizes TO_DATE bounds and LIST tuple bounds")
    void buildPartitions_normalizesCommonBoundShapes() throws Exception {
        TestContext context = new TestContext();
        Table rangeTable = context.addTable("CMT_RANGE_TO_DATE", "SALE_DATE", "DATE");
        Table listTable = context.addTable("CMT_LIST_PART", "REGION", "VARCHAR2");

        context.addPartitionTableRow("CMT_RANGE_TO_DATE", "RANGE", 1);
        context.addPartitionTableRow("CMT_LIST_PART", "LIST", 1);

        context.addPartitionKeyRow("CMT_LIST_PART", "REGION");
        context.addPartitionKeyRow("CMT_RANGE_TO_DATE", "SALE_DATE");

        context.addPartitionRow(
                "CMT_RANGE_TO_DATE", "P2025", "(TO_DATE('2025-01-01','YYYY-MM-DD'))", 1);
        context.addPartitionRow("CMT_LIST_PART", "P_REGION", "(('EAST','WEST'))", 1);

        context.execute();

        PartitionInfo rangePartitionInfo = rangeTable.getPartitionInfo();
        PartitionInfo listPartitionInfo = listTable.getPartitionInfo();

        String rangePartitionDesc = rangePartitionInfo.getPartitions().get(0).getPartitionDesc();
        String rangePreviewDDL = rangePartitionInfo.getDDL();

        String listPartitionDesc = listPartitionInfo.getPartitions().get(0).getPartitionDesc();
        String listPreviewDDL = listPartitionInfo.getDDL();

        assertAll(
                () -> assertThat(rangePartitionDesc).isEqualTo("DATE '2025-01-01'"),
                () -> assertThat(rangePreviewDDL).contains("DATE '2025-01-01'"),
                () -> assertThat(listPartitionDesc).isEqualTo("'EAST','WEST'"),
                () ->
                        assertThat(listPreviewDDL)
                                .contains("PARTITION BY LIST(REGION)")
                                .contains("VALUES ('EAST','WEST')"));
    }
}
