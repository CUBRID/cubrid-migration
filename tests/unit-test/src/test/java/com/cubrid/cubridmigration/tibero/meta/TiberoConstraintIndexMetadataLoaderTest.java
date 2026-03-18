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

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.DBObjectFactory;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TiberoConstraintIndexMetadataLoader")
class TiberoConstraintIndexMetadataLoaderTest {

    private static final TiberoConstraintIndexMetadataLoader LOADER =
            new TiberoConstraintIndexMetadataLoader();

    private static final DBObjectFactory FACTORY = new DBObjectFactory();

    @Nested
    @DisplayName("buildTablePK()")
    class BuildTablePK {

        @Test
        @DisplayName("composite PK is built and same-name index is removed")
        void compositePk_builtAndSameNameIndexRemoved() throws Exception {
            Connection conn = mock(Connection.class);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("PK_NAME")).thenReturn("PK_EMP", "PK_EMP");
            when(rs.getString("COLUMN_NAME")).thenReturn("ID", "CODE");

            Schema schema = createSchema("HR");
            Table table = createTable("EMP", "ID", "CODE");
            Index sameNameIndex = FACTORY.createIndex(table);
            sameNameIndex.setName("PK_EMP");
            table.addIndex(sameNameIndex);
            Index keepIndex = FACTORY.createIndex(table);
            keepIndex.setName("IDX_CODE");
            table.addIndex(keepIndex);

            LOADER.buildTablePK(conn, schema, table, FACTORY);

            PK pk = table.getPk();
            assertThat(pk).isNotNull();
            assertThat(pk.getName()).isEqualTo("PK_EMP");
            assertThat(pk.getPkColumns()).containsExactly("ID", "CODE");
            assertThat(table.getIndexes()).extracting(Index::getName).containsExactly("IDX_CODE");
        }
    }

    @Nested
    @DisplayName("buildTableFKs()")
    class BuildTableFKs {

        @Test
        @DisplayName("composite FK rows are grouped into one FK")
        void compositeFk_rowsGroupedIntoSingleFk() throws Exception {
            Connection conn = mock(Connection.class);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, true, false);
            when(rs.getString("FK_NAME")).thenReturn("FK_EMP_DEPT", "FK_EMP_DEPT");
            when(rs.getString("DELETE_RULE")).thenReturn("CASCADE", "CASCADE");
            when(rs.getString("PK_TABLE_NAME")).thenReturn("DEPT", "DEPT");
            when(rs.getString("FK_COLUMN_NAME")).thenReturn("DEPT_ID", "DEPT_CODE");
            when(rs.getString("PK_COLUMN_NAME")).thenReturn("ID", "CODE");

            Schema schema = createSchema("HR");
            Table table = createTable("EMP", "DEPT_ID", "DEPT_CODE");

            LOADER.buildTableFKs(conn, schema, table, FACTORY);

            assertThat(table.getFks()).hasSize(1);
            FK fk = table.getFks().get(0);
            assertThat(fk.getName()).isEqualTo("FK_EMP_DEPT");
            assertThat(fk.getReferencedTableName()).isEqualTo("DEPT");
            assertThat(fk.getDeleteRule()).isEqualTo(FK.ON_DELETE_CASCADE);
            assertThat(fk.getUpdateRule()).isEqualTo(FK.ON_UPDATE_NO_ACTION);
            assertThat(fk.getColumns())
                    .containsEntry("DEPT_ID", "ID")
                    .containsEntry("DEPT_CODE", "CODE");
        }

        @Test
        @DisplayName("unsupported delete rule -> NO ACTION")
        void unsupportedDeleteRule_defaultsToNoAction() throws Exception {
            Connection conn = mock(Connection.class);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(conn.prepareStatement(anyString())).thenReturn(stmt);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString("FK_NAME")).thenReturn("FK_EMP_DEPT");
            when(rs.getString("DELETE_RULE")).thenReturn("RESTRICT");
            when(rs.getString("PK_TABLE_NAME")).thenReturn("DEPT");
            when(rs.getString("FK_COLUMN_NAME")).thenReturn("DEPT_ID");
            when(rs.getString("PK_COLUMN_NAME")).thenReturn("ID");

            Schema schema = createSchema("HR");
            Table table = createTable("EMP", "DEPT_ID");

            LOADER.buildTableFKs(conn, schema, table, FACTORY);

            assertThat(table.getFks()).hasSize(1);
            assertThat(table.getFks().get(0).getDeleteRule()).isEqualTo(FK.ON_DELETE_NO_ACTION);
        }
    }

    @Nested
    @DisplayName("buildTableIndexes()")
    class BuildTableIndexes {

        @Test
        @DisplayName("indexes are built with reverse flag, expression columns, and empty indexes removed")
        void indexes_builtAndEmptyIndexesRemoved() throws Exception {
            Connection conn = mock(Connection.class);
            PreparedStatement indexStmt = mock(PreparedStatement.class);
            PreparedStatement columnStmt = mock(PreparedStatement.class);
            ResultSet indexRs = mock(ResultSet.class);
            ResultSet nameColumnsRs = mock(ResultSet.class);
            ResultSet reverseColumnsRs = mock(ResultSet.class);
            ResultSet expressionColumnsRs = mock(ResultSet.class);
            ResultSet emptyColumnsRs = mock(ResultSet.class);

            when(conn.prepareStatement(anyString())).thenReturn(indexStmt, columnStmt);
            when(indexStmt.executeQuery()).thenReturn(indexRs);
            when(columnStmt.executeQuery())
                    .thenReturn(nameColumnsRs, reverseColumnsRs, expressionColumnsRs, emptyColumnsRs);

            when(indexRs.next()).thenReturn(true, true, true, true, false);
            when(indexRs.getString("INDEX_NAME"))
                    .thenReturn("IDX_NAME", "IDX_REV", "IDX_EXPR", "IDX_EMPTY");
            when(indexRs.getString("INDEX_TYPE"))
                    .thenReturn("NORMAL", "NORMAL/REV", "BITMAP", "NORMAL");
            when(indexRs.getString("UNIQUENESS"))
                    .thenReturn("UNIQUE", "NONUNIQUE", "NONUNIQUE", "NONUNIQUE");

            when(nameColumnsRs.next()).thenReturn(true, false);
            when(nameColumnsRs.getString("COLUMN_NAME")).thenReturn("NAME");
            when(nameColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn(null);
            when(nameColumnsRs.getString("DESCEND")).thenReturn("A");

            when(reverseColumnsRs.next()).thenReturn(true, false);
            when(reverseColumnsRs.getString("COLUMN_NAME")).thenReturn("AGE");
            when(reverseColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn(null);
            when(reverseColumnsRs.getString("DESCEND")).thenReturn("D");

            when(expressionColumnsRs.next()).thenReturn(true, false);
            when(expressionColumnsRs.getString("COLUMN_NAME")).thenReturn(null);
            when(expressionColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn("\"LOWER(NAME)\"");
            when(expressionColumnsRs.getString("DESCEND")).thenReturn(null);

            when(emptyColumnsRs.next()).thenReturn(true, false);
            when(emptyColumnsRs.getString("COLUMN_NAME")).thenReturn(null);
            when(emptyColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn(null);
            when(emptyColumnsRs.getString("DESCEND")).thenReturn(null);

            Schema schema = createSchema("HR");
            Table table = createTable("EMP", "NAME", "AGE");

            LOADER.buildTableIndexes(conn, schema, table, FACTORY);

            assertThat(table.getIndexes()).hasSize(3);
            assertThat(table.getIndexes()).extracting(Index::getName)
                    .containsExactly("IDX_NAME", "IDX_REV", "IDX_EXPR");

            Index nameIndex = table.getIndexByName("IDX_NAME");
            assertThat(nameIndex.isUnique()).isTrue();
            assertThat(nameIndex.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexClustered);
            assertThat(nameIndex.getColumnNames()).containsExactly("NAME");
            assertThat(nameIndex.getColumnOrderRules()).containsExactly(true);

            Index reverseIndex = table.getIndexByName("IDX_REV");
            assertThat(reverseIndex.isReverse()).isTrue();
            assertThat(reverseIndex.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexClustered);
            assertThat(reverseIndex.getColumnNames()).containsExactly("AGE");
            assertThat(reverseIndex.getColumnOrderRules()).containsExactly(false);

            Index expressionIndex = table.getIndexByName("IDX_EXPR");
            assertThat(expressionIndex.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexOther);
            assertThat(expressionIndex.getColumnNames()).containsExactly("LOWER(NAME)");
            assertThat(expressionIndex.getColumnOrderRules()).containsExactly(true);
        }
    }

    private Schema createSchema(String name) {
        Schema schema = new Schema();
        schema.setName(name);
        return schema;
    }

    private Table createTable(String name, String... columnNames) {
        Table table = new Table();
        table.setName(name);
        for (String columnName : columnNames) {
            Column column = FACTORY.createColumn();
            column.setName(columnName);
            table.addColumn(column);
        }
        return table;
    }
}
