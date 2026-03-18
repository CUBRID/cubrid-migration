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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
            Connection conn =
                    connectionOf(
                            preparedStatementOf(
                                    resultSetOf(
                                            row("PK_NAME", "PK_EMP", "COLUMN_NAME", "ID"),
                                            row("PK_NAME", "PK_EMP", "COLUMN_NAME", "CODE"))));

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
            Connection conn =
                    connectionOf(
                            preparedStatementOf(
                                    resultSetOf(
                                            row(
                                                    "FK_NAME", "FK_EMP_DEPT",
                                                    "DELETE_RULE", "CASCADE",
                                                    "PK_TABLE_NAME", "DEPT",
                                                    "FK_COLUMN_NAME", "DEPT_ID",
                                                    "PK_COLUMN_NAME", "ID"),
                                            row(
                                                    "FK_NAME", "FK_EMP_DEPT",
                                                    "DELETE_RULE", "CASCADE",
                                                    "PK_TABLE_NAME", "DEPT",
                                                    "FK_COLUMN_NAME", "DEPT_CODE",
                                                    "PK_COLUMN_NAME", "CODE"))));

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
            Connection conn =
                    connectionOf(
                            preparedStatementOf(
                                    resultSetOf(
                                            row(
                                                    "FK_NAME", "FK_EMP_DEPT",
                                                    "DELETE_RULE", "RESTRICT",
                                                    "PK_TABLE_NAME", "DEPT",
                                                    "FK_COLUMN_NAME", "DEPT_ID",
                                                    "PK_COLUMN_NAME", "ID"))));

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
            Connection conn =
                    connectionOf(
                            preparedStatementOf(
                                    resultSetOf(
                                            row(
                                                    "INDEX_NAME", "IDX_NAME",
                                                    "INDEX_TYPE", "NORMAL",
                                                    "UNIQUENESS", "UNIQUE"),
                                            row(
                                                    "INDEX_NAME", "IDX_REV",
                                                    "INDEX_TYPE", "NORMAL/REV",
                                                    "UNIQUENESS", "NONUNIQUE"),
                                            row(
                                                    "INDEX_NAME", "IDX_EXPR",
                                                    "INDEX_TYPE", "BITMAP",
                                                    "UNIQUENESS", "NONUNIQUE"),
                                            row(
                                                    "INDEX_NAME", "IDX_EMPTY",
                                                    "INDEX_TYPE", "NORMAL",
                                                    "UNIQUENESS", "NONUNIQUE"))),
                            preparedStatementOf(
                                    resultSetOf(
                                            row(
                                                    "COLUMN_NAME", "NAME",
                                                    "COLUMN_EXPRESSION", null,
                                                    "DESCEND", "A")),
                                    resultSetOf(
                                            row(
                                                    "COLUMN_NAME", "AGE",
                                                    "COLUMN_EXPRESSION", null,
                                                    "DESCEND", "D")),
                                    resultSetOf(
                                            row(
                                                    "COLUMN_NAME", null,
                                                    "COLUMN_EXPRESSION", "\"LOWER(NAME)\"",
                                                    "DESCEND", null)),
                                    resultSetOf(
                                            row(
                                                    "COLUMN_NAME", null,
                                                    "COLUMN_EXPRESSION", null,
                                                    "DESCEND", null))));

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

    private Connection connectionOf(PreparedStatement... statements) {
        final Deque<PreparedStatement> queue = new ArrayDeque<PreparedStatement>(Arrays.asList(statements));
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {Connection.class},
                        new InvocationHandler() {
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                if ("prepareStatement".equals(method.getName())) {
                                    return queue.removeFirst();
                                }
                                if ("close".equals(method.getName())) {
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            }
                        });
    }

    private PreparedStatement preparedStatementOf(ResultSet... resultSets) {
        final Deque<ResultSet> queue = new ArrayDeque<ResultSet>(Arrays.asList(resultSets));
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {PreparedStatement.class},
                        new InvocationHandler() {
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                if ("executeQuery".equals(method.getName())) {
                                    return queue.removeFirst();
                                }
                                if ("setString".equals(method.getName()) || "close".equals(method.getName())) {
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            }
                        });
    }

    private ResultSet resultSetOf(Map<String, Object>... rows) {
        final List<Map<String, Object>> data = new ArrayList<Map<String, Object>>(Arrays.asList(rows));
        return (ResultSet)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {ResultSet.class},
                        new InvocationHandler() {
                            private int index = -1;

                            public Object invoke(Object proxy, Method method, Object[] args) {
                                if ("next".equals(method.getName())) {
                                    index++;
                                    return index < data.size();
                                }
                                if ("getString".equals(method.getName())) {
                                    return currentRow().get(args[0]);
                                }
                                if ("close".equals(method.getName())) {
                                    return null;
                                }
                                return defaultValue(method.getReturnType());
                            }

                            private Map<String, Object> currentRow() {
                                return data.get(index);
                            }
                        });
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new HashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }

    private Object defaultValue(Class<?> returnType) {
        if (Boolean.TYPE.equals(returnType)) {
            return false;
        }
        if (Integer.TYPE.equals(returnType)) {
            return 0;
        }
        if (Long.TYPE.equals(returnType)) {
            return 0L;
        }
        if (Double.TYPE.equals(returnType)) {
            return 0d;
        }
        if (Float.TYPE.equals(returnType)) {
            return 0f;
        }
        if (Short.TYPE.equals(returnType)) {
            return (short) 0;
        }
        if (Byte.TYPE.equals(returnType)) {
            return (byte) 0;
        }
        if (Character.TYPE.equals(returnType)) {
            return '\0';
        }
        return null;
    }
}
