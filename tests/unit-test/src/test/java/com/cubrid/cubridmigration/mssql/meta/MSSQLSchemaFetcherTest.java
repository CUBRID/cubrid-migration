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
package com.cubrid.cubridmigration.mssql.meta;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachMetaData;
import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachPreparedQuery;
import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.resultSetOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("MSSQLSchemaFetcher")
class MSSQLSchemaFetcherTest {

    private static final MSSQLSchemaFetcher FETCHER = new MSSQLSchemaFetcher();

    @Test
    @DisplayName("buildSQLTable() fills in how the type is shown and keeps the type itself")
    void buildSQLTable_fillsInTheShownType() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("varchar", Types.VARCHAR, 20));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("varchar(20)");
    }

    // The other dialects put their own widest text type in when the driver names none. MSSQL
    // does not, so such a column reaches the transform stage still nameless.
    @Test
    @DisplayName("a column with no type name is left nameless, unlike the other dialects")
    void untypedColumn_isLeftNameless() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("", Types.OTHER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEmpty();
    }

    // MSSQL reports an identity column's type with the keyword attached, and the type lookup
    // only matches the bare name.
    @Test
    @DisplayName("an identity column's type is matched on its bare name")
    void identityColumn_isMatchedOnItsBareName() throws SQLException {
        Table table = buildOneColumn("varchar identity", null, catalogKnowing("varchar"));

        assertThat(table.getColumns()).extracting(Column::getDataType).containsExactly("varchar");
    }

    @Test
    @DisplayName("a default value is unwrapped from the parentheses MSSQL adds")
    void defaultValue_isUnwrapped() throws SQLException {
        Table table = buildOneColumn("varchar", "((5))", catalogKnowing("varchar"));

        assertThat(table.getColumns().get(0).getDefaultValue()).isEqualTo("5");
    }

    @Test
    @DisplayName("MSSQL's own spelling of a null default -> no default at all")
    void nullDefault_becomesNoDefault() throws SQLException {
        Table table = buildOneColumn("varchar", "(NULL)", catalogKnowing("varchar"));

        assertThat(table.getColumns().get(0).getDefaultValue()).isNull();
    }

    // DEFECT: a column whose type is not in the catalog's supported list is stepped over without
    // a word, so a table using a spatial or other unmapped type migrates with that column simply
    // missing rather than with an error naming it
    // - see MSSQLSchemaFetcher.buildTableColumns()
    @Test
    @DisplayName("a type the catalog does not list -> the column is dropped silently")
    void unlistedType_dropsTheColumnSilently() throws SQLException {
        Table table = buildOneColumn("geography", null, catalogKnowing("varchar"));

        assertThat(table.getColumns()).isEmpty();
    }

    // hierarchyid has no JDBC type of its own, so the name's hash stands in as the identifier.
    // Nothing else in CMT derives that number, which is why it is pinned here.
    @Test
    @DisplayName("hierarchyid is registered under an id derived from its own name")
    void hierarchyId_isRegisteredUnderItsNameHash() throws Exception {
        Map<String, List<DataType>> supportedTypes = new HashMap<String, List<DataType>>();
        Method method =
                MSSQLSchemaFetcher.class.getDeclaredMethod(
                        "addHierarchyIDTypeToSupportedTypes", Map.class);
        method.setAccessible(true);

        method.invoke(FETCHER, supportedTypes);

        assertThat(supportedTypes).containsOnlyKeys("hierarchyid");
        assertThat(supportedTypes.get("hierarchyid")).hasSize(1);
        assertThat(supportedTypes.get("hierarchyid").get(0).getTypeName()).isEqualTo("hierarchyid");
        assertThat(supportedTypes.get("hierarchyid").get(0).getJdbcDataTypeID())
                .isEqualTo("hierarchyid".hashCode());
    }

    /** A catalog that knows varchar and int, and nothing else. */
    private static Catalog catalogKnowing(String... typeNames) {
        Catalog catalog = new Catalog();
        catalog.setName("db");
        Map<String, List<DataType>> supported = new HashMap<String, List<DataType>>();
        for (String typeName : typeNames) {
            DataType type = new DataType();
            type.setTypeName(typeName);
            type.setJdbcDataTypeID(Types.VARCHAR);
            supported.put(typeName, Collections.singletonList(type));
        }
        catalog.setSupportedDataType(supported);
        return catalog;
    }

    private static ResultSet oneColumnRow(
            Connection conn, String columnName, String typeName, String defaultValue)
            throws SQLException {
        DatabaseMetaData metaData = attachMetaData(conn);
        ResultSet rs = resultSetOf(1);
        when(metaData.getColumns(any(), any(), anyString(), any())).thenReturn(rs);
        when(rs.getString("COLUMN_NAME")).thenReturn(columnName);
        when(rs.getString("TYPE_NAME")).thenReturn(typeName);
        when(rs.getInt("COLUMN_SIZE")).thenReturn(20);
        when(rs.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
        when(rs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
        when(rs.getString("COLUMN_DEF")).thenReturn(defaultValue);
        attachPreparedQuery(conn, 0);
        return rs;
    }

    private static Table buildOneColumn(String typeName, String defaultValue, Catalog catalog)
            throws SQLException {
        Connection conn = mock(Connection.class);
        oneColumnRow(conn, "A", typeName, defaultValue);
        Schema schema = new Schema(catalog);
        schema.setName("dbo");
        catalog.addSchema(schema);
        Table table = new Table();
        table.setName("T1");
        FETCHER.buildTableColumns(conn, catalog, schema, table);
        return table;
    }

    private static ResultSetMetaData oneColumn(String typeName, int jdbcType, int precision)
            throws SQLException {
        ResultSetMetaData rsm = mock(ResultSetMetaData.class);
        when(rsm.getColumnCount()).thenReturn(1);
        when(rsm.getColumnLabel(1)).thenReturn("C1");
        when(rsm.getColumnName(1)).thenReturn("C1");
        when(rsm.getColumnType(1)).thenReturn(jdbcType);
        when(rsm.getColumnTypeName(1)).thenReturn(typeName);
        when(rsm.getPrecision(1)).thenReturn(precision);
        when(rsm.getScale(1)).thenReturn(0);
        return rsm;
    }
}
