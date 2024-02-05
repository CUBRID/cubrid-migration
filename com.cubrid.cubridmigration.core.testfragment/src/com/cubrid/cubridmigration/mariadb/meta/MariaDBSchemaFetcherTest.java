/*
 * Copyright (c) 2016 CUBRID Corporation.
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
package com.cubrid.cubridmigration.mariadb.meta;

import com.cubrid.cubridmigration.core.TestUtil2;
import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.Version;
import com.cubrid.cubridmigration.mariadb.export.MariaDBExportHelper;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * DbUtilTest
 *
 * @author Rathana
 */
public class MariaDBSchemaFetcherTest {

    private static final String TEST_NUMBER = "tb_bt_bbs";
    private static final String CMT_MARIA = "cmt_maria";

    @Test
    public void testSupportPartitionVersion() {

        final MariaDBSchemaFetcher builder = new MariaDBSchemaFetcher();
        Version version = new Version();
        version.setDbMajorVersion(4);
        version.setDbMinorVersion(9);
        Assert.assertFalse(builder.isSupportParitionVersion(version));
        version.setDbMajorVersion(5);
        version.setDbMinorVersion(0);
        Assert.assertFalse(builder.isSupportParitionVersion(version));
        version.setDbMajorVersion(5);
        version.setDbMinorVersion(1);
        Assert.assertTrue(builder.isSupportParitionVersion(version));
        version.setDbMajorVersion(6);
        version.setDbMinorVersion(0);
        Assert.assertTrue(builder.isSupportParitionVersion(version));
    }

    /**
     * testBuildProcedures
     *
     * @throws SQLException e
     */
    @Test
    public final void testBuildProcedures() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            Catalog catalog =
                    new MariaDBSchemaFetcher()
                            .buildCatalog(conn, TestUtil2.getMariaDBConParam(), null);
            List<Procedure> procList = catalog.getSchemas().get(0).getProcedures();
            Assert.assertEquals(1, procList.size());
            System.out.print(procList);
            List<Function> funcList = catalog.getSchemas().get(0).getFunctions();
            System.out.print(funcList.size());
            Assert.assertTrue(funcList.size() > 0);
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * testBuildTriggers
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testBuildTriggers() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            Catalog catalog =
                    new MariaDBSchemaFetcher()
                            .buildCatalog(conn, TestUtil2.getMariaDBConParam(), null);
            List<Trigger> list = catalog.getSchemas().get(0).getTriggers();

            for (Trigger trig : list) {
                System.out.println(trig.getDDL());
            }

        } finally {
            Closer.close(conn);
        }
    }

    /**
     * getAutoIncMaxValByTableName
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testGetAutoIncMaxValByTableName() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            String tableName = TEST_NUMBER;
            Long maxVal = new MariaDBSchemaFetcher().getAutoIncNextValByTableName(conn, tableName);
            Assert.assertTrue(maxVal >= 0);
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * test GetTimezone
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testGetTimezone() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            String timezone = new MariaDBSchemaFetcher().getTimezone(conn);
            System.out.println("timezone: " + timezone);
            Assert.assertEquals("GMT+09:00", timezone);
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * testBuildCatalog
     *
     * @e
     */
    @Test
    public final void testBuildCatalog() {
        Connection conn = null;
        try {
            conn = TestUtil2.getMariaDBConn();
            Catalog catalog =
                    new MariaDBSchemaFetcher()
                            .buildCatalog(conn, TestUtil2.getMariaDBConParam(), null);

            String json = TestUtil2.getCatalogJson(catalog);

            String sb =
                    TestUtil2.readStrFromFile(
                            "/com/cubrid/cubridmigration/mariadb/meta/schema.json");

            Assert.assertEquals(
                    json.replaceAll("\r\n", " ").replaceAll("\r", " ").replaceAll("\n", " "),
                    sb.replaceAll("\r\n", " ").replaceAll("\r", " ").replaceAll("\n", " "));

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * testBuildTableColumns
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testBuildTableColumns() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            Catalog catalog = new Catalog();
            catalog.setName(CMT_MARIA);
            Version version = new Version();
            version.setDbMajorVersion(4);
            catalog.setVersion(version);
            Schema schema = new Schema(catalog);
            schema.setName(CMT_MARIA);
            Table table = new Table(schema);
            table.setName(TEST_NUMBER);
            new MariaDBSchemaFetcher().buildTableColumns(conn, catalog, schema, table);
            System.out.println("column count:" + table.getColumns().size());
            Assert.assertTrue(table.getColumns().size() >= 0);
            version.setDbMajorVersion(5);
            new MariaDBSchemaFetcher().buildTableColumns(conn, catalog, schema, table);
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * testBuildTableIndexes
     *
     * @throws SQLException e
     */
    @Test
    public final void testBuildTableIndexes() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            final MariaDBSchemaFetcher mariaDBDBObjectBuilder = new MariaDBSchemaFetcher();
            Catalog catalog =
                    mariaDBDBObjectBuilder.buildCatalog(conn, TestUtil2.getMariaDBConParam(), null);

            Table table = catalog.getSchemas().get(0).getTableByName("est_data_table");

            System.out.println("table.getIndexes().size()=" + table.getIndexes().size());
            Assert.assertTrue(table.getIndexes().size() >= 0);

            // Test getSourcePartitionDLL
            table.setDDL("create table est_data_table () PARTITION BY f1 hash 4");
            mariaDBDBObjectBuilder.getSourcePartitionDDL(table);

        } finally {
            Closer.close(conn);
        }
    }

    /**
     * test GetTableDDL
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testGetTableDDL() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            String ddl = new MariaDBSchemaFetcher().getTableDDL(conn, TEST_NUMBER);
            System.out.println(ddl);
            Assert.assertTrue(ddl != null && ddl.trim().length() > 0);

        } finally {
            Closer.close(conn);
        }
    }

    /**
     * test GetViewDDL
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testGetViewDDL() throws SQLException {
        String viewName = "comvnusermaster";
        Connection conn = TestUtil2.getMariaDBConn();
        try {
            String ddl = new MariaDBSchemaFetcher().getViewDDL(conn, viewName);
            System.out.println(ddl);
            Assert.assertTrue(ddl != null && ddl.trim().length() > 0);
        } finally {
            Closer.close(conn);
        }
    }

    /**
     * testSelectTable
     *
     * @e
     * @throws SQLException e
     */
    @Test
    public final void testSelectTable() throws SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        Statement stmt = null;
        ResultSet rs = null;

        try {
            String sql = "select * from " + TEST_NUMBER;
            stmt = conn.createStatement();

            rs = stmt.executeQuery(sql);

            ResultSetMetaData resultSetMeta = rs.getMetaData();

            for (int i = 1; i < resultSetMeta.getColumnCount() + 1; i++) {

                //				String tableName = resultSetMeta.getTableName(i);
                String columnName = resultSetMeta.getColumnName(i);
                String type = resultSetMeta.getColumnTypeName(i);

                System.out.print(columnName + "\t" + type + "\t");
                System.out.print("id=" + resultSetMeta.getColumnType(i) + "\t");
                System.out.print("size=" + resultSetMeta.getColumnDisplaySize(i) + "\t");

                int precision = resultSetMeta.getPrecision(i);
                int scale = resultSetMeta.getScale(i);

                System.out.print("p=" + precision + "\t" + "s=" + scale + "\t");
                System.out.print(resultSetMeta.isAutoIncrement(i) + "\t");
                System.out.print(resultSetMeta.isNullable(i) + "\t");

                System.out.println();
            }
        } finally {
            Closer.close(rs);
            Closer.close(stmt);
            Closer.close(conn);
        }
    }

    /**
     * test GetObject
     *
     * @e
     * @throws CloneNotSupportedException e
     * @throws IOException e
     * @throws SQLException e
     */
    @Test
    public final void testGetValue() throws CloneNotSupportedException, IOException, SQLException {
        Connection conn = TestUtil2.getMariaDBConn();
        MariaDBExportHelper mariaDBExportHelper = new MariaDBExportHelper();

        MariaDBSchemaFetcher helper = new MariaDBSchemaFetcher();

        Catalog catalog = helper.buildCatalog(conn, TestUtil2.getMariaDBConParam(), null);
        Schema schema = catalog.getSchemas().get(0);

        for (Table table : schema.getTables()) {
            String tableName = table.getName();
            List<Column> list = table.getColumns();

            Statement stmt = null;
            ResultSet rs = null;
            try {
                String sql = "select * from `" + tableName + "`";
                stmt = conn.createStatement();
                rs = stmt.executeQuery(sql);

                int m = 0;
                while (rs.next() && m < 10) {

                    for (int i = 0; i < list.size(); i++) {
                        Column column = list.get(i);

                        Object obj = mariaDBExportHelper.getJdbcObject(rs, column);

                        System.out.println(obj == null ? null : obj.toString());

                        System.out.println();
                    }

                    m++; // just get top 10
                }

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                Closer.close(rs);
                Closer.close(stmt);
            }
        }
    }
}
