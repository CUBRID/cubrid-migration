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
package com.cubrid.cubridmigration.core.dbobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.testutil.TestTableFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@DisplayName("Index")
class IndexTest {

    private static Index ascending(int columns) {
        Index index = new Index();
        index.setName("idx");
        for (int i = 0; i < columns; i++) {
            index.addColumn("C" + i, true);
        }
        return index;
    }

    @Nested
    @DisplayName("setTable()")
    class SetTable {

        @Test
        @DisplayName("a table replaces the current one without any check")
        void table_replacesTheCurrentOne() {
            Index index = new Index(new Table());
            Table other = new Table();

            index.setTable(other);

            assertThat(index.getTable()).isSameAs(other);
        }

        @Test
        @DisplayName("null is rejected")
        void nullTable_isRejected() {
            assertThatThrownBy(() -> new Index().setTable(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("Index()")
    class Constructor {

        @Test
        @DisplayName("the index belongs to the given table, and null is rejected")
        void table_isRequired() {
            Table table = new Table();

            assertThat(new Index(table).getTable()).isSameAs(table);
            assertThatThrownBy(() -> new Index(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("copyFrom()")
    class CopyFrom {

        @Test
        @DisplayName("the columns, name, reverse, unique and type are copied")
        void definition_isCopied() {
            Index source = new Index(new Table());
            source.setName("idx");
            source.addColumn("a", true);
            source.addColumn("b", false);
            source.setReverse(true);
            source.setUnique(true);
            source.setIndexType(3);
            Index copy = new Index(new Table());
            copy.addColumn("z", true);

            copy.copyFrom(source);
            source.addColumn("c", true);

            assertThat(copy.getName()).isEqualTo("idx");
            assertThat(copy.getColumnNames()).containsExactly("a", "b");
            assertThat(copy.getColumnOrderRules()).containsExactly(true, false);
            assertThat(copy.isReverse()).isTrue();
            assertThat(copy.isUnique()).isTrue();
            assertThat(copy.getIndexType()).isEqualTo(3);
        }

        @Test
        @DisplayName("the table, comment and DDL stay as they were")
        void tableCommentAndDdl_stay() {
            Index source = new Index(new Table());
            source.setComment("source note");
            source.setDDL("source ddl");
            Table table = new Table();
            Index copy = new Index(table);
            copy.setComment("note");
            copy.setDDL("ddl");

            copy.copyFrom(source);

            assertThat(copy.getTable()).isSameAs(table);
            assertThat(copy.getComment()).isEqualTo("note");
            assertThat(copy.getDDL()).isEqualTo("ddl");
        }

        @Test
        @DisplayName("null fails with an NPE")
        void nullSource_throwsNullPointerException() {
            // DEFECT: unlike FK.copyFrom(), null is not ignored, and TableMappingView passes
            // the result of getIndexByName() straight in
            // - see Index.copyFrom()
            Index copy = new Index(new Table());

            assertThatThrownBy(() -> copy.copyFrom(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getColumnNames()")
    class GetColumnNames {

        @Test
        @DisplayName("the names come in the order added, as a copy")
        void names_comeInTheOrderAdded() {
            Index index = new Index();
            index.addColumn("b", true);
            index.addColumn("a", true);

            index.getColumnNames().add("x");

            assertThat(index.getColumnNames()).containsExactly("b", "a");
        }
    }

    @Nested
    @DisplayName("getIndexColumns()")
    class GetIndexColumns {

        @Test
        @DisplayName("the columns come in the order added, as a copy")
        void columns_comeInTheOrderAdded() {
            Index index = ascending(3);

            index.getIndexColumns().put("x", true);

            assertThat(index.getIndexColumns().keySet()).containsExactly("C0", "C1", "C2");
        }

        @ParameterizedTest(name = "[{index}] {0} columns -> {1} not found")
        @DisplayName("from three columns on some columns of the returned map cannot be looked up")
        @MethodSource("com.cubrid.cubridmigration.core.dbobject.IndexTest#lostKeys")
        void largerIndex_losesKeysToLookup(int columns, List<String> lost) {
            // DEFECT: the comparator never returns a negative value, so a lookup only walks to the
            // right and misses the keys the tree has rebalanced to the left
            // - see Index.getIndexColumns()
            Map<String, Boolean> map = ascending(columns).getIndexColumns();

            List<String> notFound = new ArrayList<>();
            for (int i = 0; i < columns; i++) {
                if (map.get("C" + i) == null) {
                    notFound.add("C" + i);
                }
            }

            assertThat(notFound).isEqualTo(lost);
        }

        @ParameterizedTest(name = "[{index}] {0} columns -> {1}")
        @DisplayName("an index saved in a catalog and read back loses some ascending orders")
        @MethodSource("com.cubrid.cubridmigration.core.dbobject.IndexTest#savedOrders")
        void savedIndex_losesSomeAscendingOrders(int columns, List<String> rules, @TempDir Path dir)
                throws Exception {
            // DEFECT: XMLEncoder reads each order with get(), which misses some keys, so they are
            // written as null and read back as descending
            // - see Index.getIndexColumns()
            Catalog catalog = new Catalog();
            catalog.setDatabaseType(DatabaseType.CUBRID);
            Schema schema = new Schema();
            schema.setName("public");
            catalog.addSchema(schema);
            Table table = TestTableFactory.createTable("t");
            schema.addTable(table);
            table.addIndex(ascending(columns));
            Path file = dir.resolve("catalog.xml");
            catalog.saveXML(file.toFile());

            Catalog loaded =
                    Catalog.loadXML(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));

            Index loadedIndex = loaded.getSchemas().get(0).getTables().get(0).getIndexes().get(0);
            assertThat(loadedIndex.getColumnOrderRulesString()).isEqualTo(rules);
        }
    }

    @Nested
    @DisplayName("setIndexColumns()")
    class SetIndexColumns {

        @Test
        @DisplayName("the map replaces the columns in its own order")
        void map_replacesTheColumns() {
            Index index = new Index();
            index.addColumn("old", true);
            Map<String, Boolean> columns = new LinkedHashMap<>();
            columns.put("b", true);
            columns.put("c", false);

            index.setIndexColumns(columns);

            assertThat(index.getColumnNames()).containsExactly("b", "c");
            assertThat(index.getColumnOrderRules()).containsExactly(true, false);
        }

        @Test
        @DisplayName("null or an empty map removes every column")
        void nullOrEmptyMap_removesEveryColumn() {
            Index index = ascending(2);
            index.setIndexColumns(null);
            assertThat(index.getColumnNames()).isEmpty();

            index.addColumn("a", true);
            index.setIndexColumns(new LinkedHashMap<String, Boolean>());

            assertThat(index.getColumnNames()).isEmpty();
        }

        @Test
        @DisplayName("an order left null is stored as descending")
        void nullOrder_isStoredAsDescending() {
            // DEFECT: a null order becomes false, which is descending, while the fetchers read a
            // missing order as ascending and the field comment lists null as its own value
            // - see Index.setIndexColumns()
            Map<String, Boolean> columns = new LinkedHashMap<>();
            columns.put("a", null);
            Index index = new Index();

            index.setIndexColumns(columns);

            assertThat(index.getColumnOrderRulesString()).containsExactly("D");
        }
    }

    @Nested
    @DisplayName("getColumnOrderRules()")
    class GetColumnOrderRules {

        @Test
        @DisplayName("the orders come in the column order, as a copy")
        void orders_comeInColumnOrder() {
            Index index = new Index();
            index.addColumn("a", true);
            index.addColumn("b", false);

            index.getColumnOrderRules().add(true);

            assertThat(index.getColumnOrderRules()).containsExactly(true, false);
        }
    }

    @Nested
    @DisplayName("getColumnOrderRulesString()")
    class GetColumnOrderRulesString {

        @Test
        @DisplayName("ascending reads as A and descending as D, in column order")
        void orders_readAsAAndD() {
            Index index = new Index();
            index.addColumn("a", true);
            index.addColumn("b", false);

            assertThat(index.getColumnOrderRulesString()).containsExactly("A", "D");
        }
    }

    @Nested
    @DisplayName("addColumn()")
    class AddColumn {

        @ParameterizedTest(name = "[{index}] {0} columns")
        @DisplayName("on up to two columns a column added again keeps its first order")
        @ValueSource(ints = {1, 2})
        void smallIndex_keepsTheFirstOrder(int columns) {
            Index index = ascending(columns);

            for (int i = 0; i < columns; i++) {
                index.addColumn("C" + i, false);
            }

            assertThat(index.getColumnNames()).hasSize(columns);
            assertThat(index.getColumnOrderRules()).containsOnly(true);
        }

        @ParameterizedTest(name = "[{index}] {0} columns -> {1}")
        @DisplayName("from three columns on some columns added again are added a second time")
        @MethodSource("com.cubrid.cubridmigration.core.dbobject.IndexTest#duplicatedColumns")
        void largerIndex_duplicatesColumnsAddedAgain(int columns, List<String> names) {
            // DEFECT: the lookup that should find the column misses the keys the tree has
            // rebalanced to the left, so those columns are put in a second time
            // - see Index.addColumn()
            Index index = ascending(columns);

            for (int i = 0; i < columns; i++) {
                index.addColumn("C" + i, false);
            }

            assertThat(index.getColumnNames()).isEqualTo(names);
        }
    }

    @Nested
    @DisplayName("getIndexString()")
    class GetIndexString {

        @Test
        @DisplayName("the name is followed by the columns in brackets, comma separated")
        void nameAndColumns_areJoined() {
            Index index = new Index();
            index.setName("idx");
            index.addColumn("a", true);
            index.addColumn("b", true);

            assertThat(index.getIndexString()).isEqualTo("idx(a,b)");
        }

        @Test
        @DisplayName("no columns give empty brackets, and no name reads as null")
        void missingParts_areShown() {
            Index empty = new Index();
            empty.setName("e");
            Index unnamed = new Index();
            unnamed.addColumn("a", true);

            assertThat(empty.getIndexString()).isEqualTo("e()");
            assertThat(unnamed.getIndexString()).isEqualTo("null(a)");
        }
    }

    @Nested
    @DisplayName("isIndexNodePK()")
    class IsIndexNodePK {

        @ParameterizedTest(name = "[{index}] type {0}")
        @DisplayName("every index is reported as not the PK, whatever its type")
        @ValueSource(ints = {-1, 0, 1, 2, 3})
        void everyIndex_isNotThePK(int type) {
            // DEFECT: the string "unique index" is compared with the int index type, which never
            // matches, so the method always returns false and the PK check below is dead code
            // - see Index.isIndexNodePK()
            Table table = new Table();
            PK pk = new PK(table);
            pk.addColumn("a");
            table.setPk(pk);
            Index index = new Index(table);
            index.setIndexType(type);
            index.addColumn("a", true);

            assertThat(index.isIndexNodePK()).isFalse();
        }
    }

    static Stream<Arguments> lostKeys() {
        return Stream.of(
                Arguments.of(3, Arrays.asList("C0")),
                Arguments.of(4, Arrays.asList("C0")),
                Arguments.of(5, Arrays.asList("C0", "C1")));
    }

    static Stream<Arguments> savedOrders() {
        return Stream.of(
                Arguments.of(3, Arrays.asList("D", "A", "A")),
                Arguments.of(4, Arrays.asList("D", "A", "A", "A")),
                Arguments.of(5, Arrays.asList("D", "D", "A", "A", "A")));
    }

    static Stream<Arguments> duplicatedColumns() {
        return Stream.of(
                Arguments.of(3, Arrays.asList("C0", "C1", "C2", "C0")),
                Arguments.of(4, Arrays.asList("C0", "C1", "C2", "C3", "C0", "C2")),
                Arguments.of(5, Arrays.asList("C0", "C1", "C2", "C3", "C4", "C0", "C2", "C4")));
    }
}
