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

import static com.cubrid.cubridmigration.tibero.meta.TiberoSqlConstants.*;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.DBUtils;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.slf4j.Logger;

import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

class TiberoPartitionMetadataLoader {

    interface PartitionDDLProvider {
        String getPartitionDDL(Table table);
    }

    private static final Logger LOG = LogUtil.getLogger(TiberoPartitionMetadataLoader.class);

    void buildPartitions(
            final Connection conn,
            final Schema schema,
            final DBObjectFactory factory,
            final PartitionDDLProvider partitionDDLProvider) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(SQL_GET_PART_TABLES);
            stmt.setString(1, schema.getName());
            rs = stmt.executeQuery();

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                LOG.debug("[VAR]tableName={}", tableName);

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
                partitionInfo.setDDL(partitionDDLProvider.getPartitionDDL(table));
                if ("NONE".equals(subPartitionMethod)) {
                    subPartitionMethod = null;
                }
                partitionInfo.setSubPartitionMethod(subPartitionMethod);
                partitionInfo.setSubPartitionCount(subPartitionCount);
                partitionInfo.setSubPartitionColumnCount(subPartitionColumnCount);

                table.setPartitionInfo(partitionInfo);
                LOG.debug("[VAR]partitionInfo={}", partitionInfo);
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }

        addPartitionColumns(conn, schema);
        addPartitionTables(conn, schema, factory);
        addSubPartitionTables(conn, schema, factory);
    }

    private void addPartitionColumns(final Connection conn, final Schema schema) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement(SQL_GET_PART_COLUMN);
            stmt.setString(1, schema.getName());
            LOG.debug("[SQL]{}, 1={}", SQL_GET_PART_COLUMN, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("NAME");
                String columnName = rs.getString("COLUMN_NAME");
                LOG.debug("[VAR]tableName={}, columnName={}", tableName, columnName);

                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                PartitionInfo partitionInfo = table.getPartitionInfo();
                if (partitionInfo == null) {
                    continue;
                }
                partitionInfo.addPartitionColumn(table.getColumnByName(columnName));
                LOG.debug("[VAR]partitionInfo={}", partitionInfo);
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
            LOG.debug("[SQL]{}, 1={}", SQL_GET_SUBPART_KEY_COLUMN, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("NAME");
                String columnName = rs.getString("COLUMN_NAME");
                LOG.debug("[VAR]tableName={}, columnName={}", tableName, columnName);

                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }
                PartitionInfo partitionInfo = table.getPartitionInfo();
                if (partitionInfo == null) {
                    continue;
                }
                partitionInfo.addSubPartitionColumn(table.getColumnByName(columnName));
                LOG.debug("[VAR]partitionInfo={}", partitionInfo);
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    private void addPartitionTables(
            final Connection conn, final Schema schema, final DBObjectFactory factory) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement(SQL_GET_PARTITIONS);
            stmt.setString(1, schema.getName());
            LOG.debug("[SQL]{}, 1={}", SQL_GET_PARTITIONS, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                LOG.debug("[VAR]tableName={}", tableName);

                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                String partitionName = rs.getString("PARTITION_NAME");
                Reader reader = rs.getCharacterStream("HIGH_VALUE");
                String partitionDesc = reader == null ? null : DBUtils.reader2String(reader);
                int partitionPosition = rs.getInt("PARTITION_POSITION");

                PartitionInfo partitionInfo = table.getPartitionInfo();
                if (partitionInfo == null) {
                    continue;
                }

                PartitionTable partition = factory.createPartitionTable();
                partition.setPartitionName(partitionName);
                partition.setPartitionDesc(partitionDesc);
                partition.setPartitionIdx(partitionPosition);

                partitionInfo.addPartition(partition);
                LOG.debug("[VAR]partition={}", partition);
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }

    private void addSubPartitionTables(
            final Connection conn, final Schema schema, final DBObjectFactory factory) {
        ResultSet rs = null;
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement(SQL_GET_SUB_PART_TABLES);
            stmt.setString(1, schema.getName());
            LOG.debug("[SQL]{}, 1={}", SQL_GET_SUB_PART_TABLES, schema.getName());

            rs = stmt.executeQuery();
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                LOG.debug("[VAR]tableName={}", tableName);

                Table table = schema.getTableByName(tableName);
                if (table == null) {
                    continue;
                }

                String subPartitionName = rs.getString("SUBPARTITION_NAME");
                Reader reader = rs.getCharacterStream("HIGH_VALUE");
                String subPartitionDesc = reader == null ? null : DBUtils.reader2String(reader);
                int subPartitionPosition = rs.getInt("SUBPARTITION_POSITION");

                PartitionInfo partitionInfo = table.getPartitionInfo();
                if (partitionInfo == null) {
                    continue;
                }

                PartitionTable subPartition = factory.createPartitionTable();
                subPartition.setPartitionName(subPartitionName);
                subPartition.setPartitionDesc(subPartitionDesc);
                subPartition.setPartitionIdx(subPartitionPosition);

                partitionInfo.addSubPartition(subPartition);
                LOG.debug("[VAR]subPartition={}", subPartition);
            }
        } catch (Exception ex) {
            LOG.error("", ex);
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
        }
    }
}
