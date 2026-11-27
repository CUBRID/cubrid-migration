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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.tibero.TiberoDataTypeHelper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@DisplayName("TiberoSchemaFetcher")
class TiberoSchemaFetcherTest {

    @Test
    @DisplayName(
            "fillColumnMetadata() falls back to DATA_LENGTH when CHAR_LENGTH is zero for VARCHAR")
    void fillColumnMetadata_usesByteLengthWhenCharLengthIsZero() throws Exception {
        TiberoSchemaFetcher fetcher = new TiberoSchemaFetcher();
        Catalog catalog = createCatalogWithVarcharType();
        ResultSet rs = mock(ResultSet.class);
        TiberoDataTypeHelper helper = TiberoDataTypeHelper.getInstance(null);

        Column column = new Column();
        column.setName("NAME");
        column.setDataType("VARCHAR");

        when(rs.getString("DATA_TYPE")).thenReturn("VARCHAR");
        when(rs.getInt("DATA_LENGTH")).thenReturn(100);
        when(rs.getString("DATA_PRECISION")).thenReturn("0");
        when(rs.getInt("DATA_PRECISION")).thenReturn(0);
        when(rs.getString("DATA_SCALE")).thenReturn(null);
        when(rs.getString("NULLABLE")).thenReturn("Y");
        when(rs.getString("DATA_DEFAULT")).thenReturn(null);
        when(rs.getInt("CHAR_LENGTH")).thenReturn(0);
        when(rs.getString("CHAR_USED")).thenReturn("B");
        when(rs.getString("COMMENTS")).thenReturn(null);

        invokeFillColumnMetadata(fetcher, catalog, column, rs, helper);

        assertThat(column.getPrecision()).isEqualTo(100);
        assertThat(column.getByteLength()).isEqualTo(100);
        assertThat(column.getShownDataType()).isEqualTo("VARCHAR(100)");
    }

    @Test
    @DisplayName(
            "fillColumnMetadata() preserves JDBC precision when Tibero length metadata is empty")
    void fillColumnMetadata_preservesOriginalPrecisionWhenLengthsAreMissing() throws Exception {
        TiberoSchemaFetcher fetcher = new TiberoSchemaFetcher();
        Catalog catalog = createCatalogWithVarcharType();
        ResultSet rs = mock(ResultSet.class);
        TiberoDataTypeHelper helper = TiberoDataTypeHelper.getInstance(null);

        Column column = new Column();
        column.setName("NAME");
        column.setDataType("VARCHAR");
        column.setPrecision(100);
        column.setCharLength(100);
        column.setByteLength(100);

        when(rs.getString("DATA_TYPE")).thenReturn("VARCHAR");
        when(rs.getInt("DATA_LENGTH")).thenReturn(0);
        when(rs.getString("DATA_PRECISION")).thenReturn(null);
        when(rs.getString("DATA_SCALE")).thenReturn(null);
        when(rs.getString("NULLABLE")).thenReturn("Y");
        when(rs.getString("DATA_DEFAULT")).thenReturn(null);
        when(rs.getInt("CHAR_LENGTH")).thenReturn(0);
        when(rs.getString("CHAR_USED")).thenReturn("B");
        when(rs.getString("COMMENTS")).thenReturn(null);

        invokeFillColumnMetadata(fetcher, catalog, column, rs, helper);

        assertThat(column.getPrecision()).isEqualTo(100);
        assertThat(column.getCharLength()).isEqualTo(100);
        assertThat(column.getByteLength()).isEqualTo(100);
        assertThat(column.getShownDataType()).isEqualTo("VARCHAR(100)");
    }

    @Test
    @DisplayName(
            "fillColumnMetadata() uses CHAR_LENGTH for NCHAR even when JDBC precision is"
                    + " byte-based")
    void fillColumnMetadata_usesCharLengthForNchar() throws Exception {
        assertNationalStringPrecision("NCHAR", Types.NCHAR);
    }

    @Test
    @DisplayName(
            "fillColumnMetadata() uses CHAR_LENGTH for NVARCHAR even when JDBC precision is"
                    + " byte-based")
    void fillColumnMetadata_usesCharLengthForNvarchar() throws Exception {
        assertNationalStringPrecision("NVARCHAR", Types.NVARCHAR);
    }

    @Test
    @DisplayName(
            "fillColumnMetadata() uses CHAR_LENGTH for NVARCHAR2 even when JDBC precision is"
                    + " byte-based")
    void fillColumnMetadata_usesCharLengthForNvarchar2() throws Exception {
        assertNationalStringPrecision("NVARCHAR2", Types.NVARCHAR);
    }

    @Test
    @DisplayName("buildTableIndexes() removes duplicate PK index after index metadata is loaded")
    void buildTableIndexes_removesPkNamedIndexAfterLoading() throws Exception {
        TiberoSchemaFetcher fetcher = new TiberoSchemaFetcher();
        Connection conn = mock(Connection.class);
        PreparedStatement indexStmt = mock(PreparedStatement.class);
        PreparedStatement columnStmt = mock(PreparedStatement.class);
        ResultSet indexRs = mock(ResultSet.class);
        ResultSet pkColumnsRs = mock(ResultSet.class);
        ResultSet keepColumnsRs = mock(ResultSet.class);

        when(conn.prepareStatement(anyString())).thenReturn(indexStmt, columnStmt);
        when(indexStmt.executeQuery()).thenReturn(indexRs);
        when(columnStmt.executeQuery()).thenReturn(pkColumnsRs, keepColumnsRs);

        when(indexRs.next()).thenReturn(true, true, false);
        when(indexRs.getString("INDEX_NAME")).thenReturn("PK_EMP", "IDX_CODE");
        when(indexRs.getString("INDEX_TYPE")).thenReturn("NORMAL", "NORMAL");
        when(indexRs.getString("UNIQUENESS")).thenReturn("UNIQUE", "NONUNIQUE");

        when(pkColumnsRs.next()).thenReturn(true, false);
        when(pkColumnsRs.getString("COLUMN_NAME")).thenReturn("ID");
        when(pkColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn(null);
        when(pkColumnsRs.getString("DESCEND")).thenReturn("A");

        when(keepColumnsRs.next()).thenReturn(true, false);
        when(keepColumnsRs.getString("COLUMN_NAME")).thenReturn("CODE");
        when(keepColumnsRs.getString("COLUMN_EXPRESSION")).thenReturn(null);
        when(keepColumnsRs.getString("DESCEND")).thenReturn("A");

        Schema schema = new Schema();
        schema.setName("HR");
        Table table = new Table(schema);
        table.setName("EMP");
        table.addColumn(createColumn("ID"));
        table.addColumn(createColumn("CODE"));
        PK pk = new PK(table);
        pk.setName("PK_EMP");
        pk.addColumn("ID");
        table.setPk(pk);

        fetcher.buildTableIndexes(conn, new Catalog(), schema, table);

        assertThat(table.getIndexes()).extracting(Index::getName).containsExactly("IDX_CODE");
    }

    private void invokeFillColumnMetadata(
            TiberoSchemaFetcher fetcher,
            Catalog catalog,
            Column column,
            ResultSet rs,
            TiberoDataTypeHelper helper)
            throws Exception {
        Method method =
                TiberoSchemaFetcher.class.getDeclaredMethod(
                        "fillColumnMetadata",
                        Catalog.class,
                        Column.class,
                        ResultSet.class,
                        TiberoDataTypeHelper.class);
        method.setAccessible(true);
        method.invoke(fetcher, catalog, column, rs, helper);
    }

    private Catalog createCatalogWithVarcharType() {
        Catalog catalog = new Catalog();
        DataType varchar = new DataType();
        varchar.setTypeName("VARCHAR");
        varchar.setJdbcDataTypeID(Types.VARCHAR);

        Map<String, List<DataType>> supportedTypes = new HashMap<String, List<DataType>>();
        supportedTypes.put("VARCHAR", Collections.singletonList(varchar));
        catalog.setSupportedDataType(supportedTypes);
        return catalog;
    }

    private Catalog createCatalogWithStringTypes() {
        Catalog catalog = createCatalogWithVarcharType();

        DataType nchar = new DataType();
        nchar.setTypeName("NCHAR");
        nchar.setJdbcDataTypeID(Types.NCHAR);

        DataType nvarchar = new DataType();
        nvarchar.setTypeName("NVARCHAR");
        nvarchar.setJdbcDataTypeID(Types.NVARCHAR);

        catalog.getSupportedDataType().put("NCHAR", Collections.singletonList(nchar));
        catalog.getSupportedDataType().put("NVARCHAR", Collections.singletonList(nvarchar));
        catalog.getSupportedDataType().put("NVARCHAR2", Collections.singletonList(nvarchar));
        return catalog;
    }

    private void assertNationalStringPrecision(String dataType, int jdbcType) throws Exception {
        TiberoSchemaFetcher fetcher = new TiberoSchemaFetcher();
        Catalog catalog = createCatalogWithStringTypes();
        ResultSet rs = mock(ResultSet.class);
        TiberoDataTypeHelper helper = TiberoDataTypeHelper.getInstance(null);

        Column column = new Column();
        column.setName("NAME");
        column.setDataType(dataType);
        column.setPrecision(20);
        column.setCharLength(20);
        column.setByteLength(20);
        column.setJdbcIDOfDataType(jdbcType);

        when(rs.getString("DATA_TYPE")).thenReturn(dataType);
        when(rs.getString("DATA_PRECISION")).thenReturn(null);
        when(rs.getString("DATA_SCALE")).thenReturn(null);
        when(rs.getInt("DATA_LENGTH")).thenReturn(20);
        when(rs.getInt("CHAR_LENGTH")).thenReturn(10);
        when(rs.getString("CHAR_USED")).thenReturn("C");
        when(rs.getString("NULLABLE")).thenReturn("Y");
        when(rs.getString("DATA_DEFAULT")).thenReturn(null);
        when(rs.getString("COMMENTS")).thenReturn(null);

        invokeFillColumnMetadata(fetcher, catalog, column, rs, helper);

        assertThat(column.getPrecision()).isEqualTo(10);
        assertThat(column.getCharLength()).isEqualTo(10);
        assertThat(column.getByteLength()).isEqualTo(20);
        assertThat(column.getShownDataType()).isEqualTo(dataType + "(10 CHAR)");
    }

    private Column createColumn(String name) {
        Column column = new Column();
        column.setName(name);
        return column;
    }
}
