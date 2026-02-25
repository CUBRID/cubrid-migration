package com.cubrid.cubridmigration.tibero.meta;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class TiberoConstraintIndexMetadataLoader {

    private static final Logger LOG = LogUtil.getLogger(TiberoConstraintIndexMetadataLoader.class);

    void buildTablePK(
            final Connection conn,
            final Schema schema,
            final Table table,
            final DBObjectFactory factory,
            final String sqlGetEnabledPk)
            throws SQLException {
        LOG.debug("[IN] buildTablePK()");
        try (PreparedStatement pstmt = conn.prepareStatement(sqlGetEnabledPk)) {
            pstmt.setString(1, schema.getName());
            pstmt.setString(2, table.getName());
            LOG.debug("[SQL]{} (1={}, 2={})", sqlGetEnabledPk, schema.getName(), table.getName());
            try (ResultSet rs = pstmt.executeQuery()) {
                PK primaryKey = null;

                while (rs.next()) {
                    if (primaryKey == null) {
                        primaryKey = factory.createPK(table);
                        primaryKey.setName(rs.getString("PK_NAME"));
                        table.setPk(primaryKey);
                    }

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
    }

    void buildTableFKs(
            final Connection conn,
            final Schema schema,
            final Table table,
            final DBObjectFactory factory,
            final String sqlGetEnabledFks)
            throws SQLException {
        LOG.debug("[IN] buildTableFKs()");
        try (PreparedStatement pstmt = conn.prepareStatement(sqlGetEnabledFks)) {
            pstmt.setString(1, schema.getName());
            pstmt.setString(2, table.getName());
            LOG.debug("[SQL]{} (1={}, 2={})", sqlGetEnabledFks, schema.getName(), table.getName());

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
                        foreignKey.setUpdateRule(FK.ON_UPDATE_NO_ACTION);

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

    void buildTableIndexes(
            final Connection conn,
            final Schema schema,
            final Table table,
            final DBObjectFactory factory,
            final String sqlGetTableIndex,
            final String sqlGetIndexColumns)
            throws SQLException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[IN]buildTableIndexes()");
        }
        ResultSet rs = null;
        PreparedStatement stmt = null;
        try {
            stmt = conn.prepareStatement(sqlGetTableIndex);
            stmt.setString(1, schema.getName());
            stmt.setString(2, table.getName());
            if (LOG.isDebugEnabled()) {
                LOG.debug("[SQL]" + sqlGetTableIndex + ", 1=" + table.getName());
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
            stmt = conn.prepareStatement(sqlGetIndexColumns);
            for (Index idx : table.getIndexes()) {
                stmt.setString(1, schema.getName());
                stmt.setString(2, table.getName());
                stmt.setString(3, idx.getName());
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "[SQL]"
                                    + sqlGetIndexColumns
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

        List<Index> validIndexes = new ArrayList<Index>();
        for (Index idx : table.getIndexes()) {
            if (idx.getColumnNames().isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skip index without columns: " + idx.getName());
                }
                continue;
            }
            validIndexes.add(idx);
        }
        table.setIndexes(validIndexes);
    }
}
