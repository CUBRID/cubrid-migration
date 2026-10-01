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

import com.cubrid.cubridmigration.testutil.TestColumnFactory;
import com.cubrid.cubridmigration.testutil.TestTableFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

@DisplayName("TableOrView")
class TableOrViewTest {

    private static Column column(String name) {
        return TestColumnFactory.createColumn(name, "int", null, null);
    }

    @Nested
    @DisplayName("getColumns()")
    class GetColumns {

        @Test
        @DisplayName("changing the returned list leaves the columns alone, which are shared")
        void returnedList_isACopy() {
            Table table = TestTableFactory.createTable("t", "c");
            Column column = table.getColumns().get(0);

            table.getColumns().clear();

            assertThat(table.getColumns()).containsExactly(column);
        }
    }

    @Nested
    @DisplayName("setColumns()")
    class SetColumns {

        @Test
        @DisplayName("the list replaces the columns, skipping nulls and names seen in any case")
        void list_replacesTheColumns() {
            Table table = TestTableFactory.createTable("t", "old");
            Column column = column("a");

            table.setColumns(Arrays.asList(column, null, column("A")));

            assertThat(table.getColumns()).containsExactly(column);
            assertThat(column.getTableOrView()).isSameAs(table);
        }

        @Test
        @DisplayName("null or an empty list removes every column")
        void nullOrEmptyList_removesEveryColumn() {
            Table table = TestTableFactory.createTable("t", "a");
            table.setColumns(null);
            assertThat(table.getColumns()).isEmpty();

            table.addColumn(column("a"));
            table.setColumns(new ArrayList<Column>());

            assertThat(table.getColumns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addColumn()")
    class AddColumn {

        @Test
        @DisplayName("a column is added and owned, and can be removed again")
        void column_isAddedAndOwned() {
            Table table = new Table();
            Column column = new Column();
            column.setName("f1");

            table.addColumn(column);
            assertThat(table.getColumns()).containsExactly(column);
            assertThat(column.getTableOrView()).isSameAs(table);

            table.removeColumn(column);
            table.removeColumn(null);
            assertThat(table.getColumns()).isEmpty();
        }

        @Test
        @DisplayName("null is ignored")
        void nullColumn_isIgnored() {
            Table table = new Table();

            table.addColumn(null);

            assertThat(table.getColumns()).isEmpty();
        }

        @Test
        @DisplayName("a column whose name differs only in case is dropped")
        void nameDifferingInCase_isDropped() {
            // DEFECT: the name check ignores case, so a second column such as a quoted Oracle
            // "id" next to ID is dropped without notice
            // - see TableOrView.addColumn()
            Table table = new Table();
            table.addColumn(column("ID"));
            Column lower = column("id");

            table.addColumn(lower);

            assertThat(table.getColumns()).extracting(Column::getName).containsExactly("ID");
            assertThat(lower.getTableOrView()).isNull();
        }
    }

    @Nested
    @DisplayName("getColumnByName()")
    class GetColumnByName {

        @Test
        @DisplayName("the name matches ignoring case")
        void name_matchesIgnoringCase() {
            Table table = TestTableFactory.createTable("t", "Col");

            assertThat(table.getColumnByName("col")).isSameAs(table.getColumns().get(0));
        }

        @Test
        @DisplayName("a null or unknown name gives null")
        void nullOrUnknownName_returnsNull() {
            Table table = TestTableFactory.createTable("t", "Col");

            assertThat(table.getColumnByName((String) null)).isNull();
            assertThat(table.getColumnByName("zz")).isNull();
        }

        @Test
        @DisplayName("a column without a name fails the lookup")
        void unnamedColumn_throwsNullPointerException() {
            Table table = new Table();
            table.addColumn(new Column());

            assertThatThrownBy(() -> table.getColumnByName("c"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("with an owner and a table both must match the column's table with case")
        void ownerAndTable_mustMatchWithCase() {
            Table table = TestTableFactory.createTable("T", "c");
            table.setOwner("O");

            assertThat(table.getColumnByName("O", "T", "C")).isSameAs(table.getColumns().get(0));
            assertThat(table.getColumnByName("o", "T", "c")).isNull();
            assertThat(table.getColumnByName("O", "t", "c")).isNull();
        }

        @Test
        @DisplayName("without an owner or a table only the column name counts")
        void missingOwnerOrTable_fallsBackToTheName() {
            Table table = TestTableFactory.createTable("T", "c");
            table.setOwner("O");
            Column column = table.getColumns().get(0);

            assertThat(table.getColumnByName(null, "other", "c")).isSameAs(column);
            assertThat(table.getColumnByName("", "other", "c")).isSameAs(column);
            assertThat(table.getColumnByName("O", null, "c")).isSameAs(column);
        }

        @Test
        @DisplayName("with an owner and a table, a table without an owner fails the lookup")
        void tableWithoutOwner_throwsNullPointerException() {
            // DEFECT: the owner of the column's table is compared without a null check, so the
            // lookup fails on any table whose owner is not set
            // - see TableOrView.getColumnByName()
            Table table = TestTableFactory.createTable("T", "c");

            assertThatThrownBy(() -> table.getColumnByName("O", "T", "c"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getColumnWithNoCase()")
    class GetColumnWithNoCase {

        @Test
        @DisplayName("the name matches ignoring case, and an unknown one gives null")
        void name_matchesIgnoringCase() {
            Table table = TestTableFactory.createTable("t", "Col");

            assertThat(table.getColumnWithNoCase("COL")).isSameAs(table.getColumns().get(0));
            assertThat(table.getColumnWithNoCase("zz")).isNull();
        }
    }
}
