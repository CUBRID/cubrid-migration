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
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

@DisplayName("FK")
class FKTest {

    private static FK declaredBThenA() {
        FK fk = new FK(new Table());
        fk.addRefColumnName("B", "RB");
        fk.addRefColumnName("A", "RA");
        return fk;
    }

    @Nested
    @DisplayName("FK()")
    class Constructor {

        @Test
        @DisplayName("the FK belongs to the given table, and null is rejected")
        void table_isRequired() {
            Table table = new Table();

            assertThat(new FK(table).getTable()).isSameAs(table);
            assertThatThrownBy(() -> new FK(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("setTable()")
    class SetTable {

        @Test
        @DisplayName("a table replaces the current one, and null is rejected in lower case")
        void table_replacesTheCurrentOne() {
            FK fk = new FK(new Table());
            Table other = new Table();

            fk.setTable(other);

            assertThat(fk.getTable()).isSameAs(other);
            assertThatThrownBy(() -> fk.setTable(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("getCol2RefMapping()")
    class GetCol2RefMapping {

        @Test
        @DisplayName("a new FK has no referenced columns, and each added one is listed")
        void addedColumn_isListed() {
            FK fk = new FK(new Table());
            assertThat(fk.getCol2RefMapping()).isEmpty();

            fk.addRefColumnName("name", "name");

            assertThat(fk.getCol2RefMapping()).containsExactly("name");
        }

        @Test
        @DisplayName("changing the returned list leaves the mapping alone")
        void returnedList_isACopy() {
            FK fk = declaredBThenA();

            fk.getCol2RefMapping().clear();

            assertThat(fk.getCol2RefMapping()).hasSize(2);
        }

        @Test
        @DisplayName("the referenced columns follow the FK column names, not the declared order")
        void referencedColumns_followTheColumnNames() {
            // DEFECT: the mapping is a TreeMap, so the referenced columns come sorted by FK
            // column name instead of in KEY_SEQ order, and a composite FK DDL can list them in a
            // different order than the referenced key
            // - see FK.getCol2RefMapping()
            assertThat(declaredBThenA().getCol2RefMapping()).containsExactly("RA", "RB");
        }
    }

    @Nested
    @DisplayName("addRefColumnName()")
    class AddRefColumnName {

        @Test
        @DisplayName("the first mapping of a column is kept unless it was null")
        void firstMapping_isKept() {
            FK fk = new FK(new Table());

            fk.addRefColumnName("A", "R1");
            fk.addRefColumnName("A", "R2");
            fk.addRefColumnName("N", null);
            fk.addRefColumnName("N", "RN");

            assertThat(fk.getColumns()).containsExactly(entry("A", "R1"), entry("N", "RN"));
        }

        @Test
        @DisplayName("column names compare with case")
        void columnNames_compareWithCase() {
            FK fk = new FK(new Table());

            fk.addRefColumnName("A", "RA");
            fk.addRefColumnName("a", "ra");

            assertThat(fk.getColumnNames()).containsExactly("A", "a");
        }
    }

    @Nested
    @DisplayName("setColumns()")
    class SetColumns {

        @Test
        @DisplayName("the map replaces the mapping as a sorted copy of its own")
        void map_replacesTheMapping() {
            FK fk = new FK(new Table());
            fk.addRefColumnName("old", "x");
            Map<String, String> columns = new LinkedHashMap<>();
            columns.put("z", "rz");
            columns.put("y", "ry");

            fk.setColumns(columns);
            columns.put("w", "rw");

            assertThat(fk.getColumns()).containsExactly(entry("y", "ry"), entry("z", "rz"));
        }

        @Test
        @DisplayName("null removes the mapping")
        void nullMap_removesTheMapping() {
            FK fk = declaredBThenA();

            fk.setColumns(null);

            assertThat(fk.getColumns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getColumnNames()")
    class GetColumnNames {

        @Test
        @DisplayName("changing the returned list leaves the mapping alone")
        void returnedList_isACopy() {
            FK fk = declaredBThenA();

            fk.getColumnNames().clear();

            assertThat(fk.getColumnNames()).hasSize(2);
        }

        @Test
        @DisplayName("the FK columns come sorted by name, not in the declared order")
        void columnNames_comeSortedByName() {
            // DEFECT: the TreeMap sorts the FK columns by name, so they no longer follow the
            // declared KEY_SEQ order, for the same reason as getCol2RefMapping()
            // - see FK.getColumnNames()
            assertThat(declaredBThenA().getColumnNames()).containsExactly("A", "B");
        }
    }

    @Nested
    @DisplayName("getColumns()")
    class GetColumns {

        @Test
        @DisplayName("the mapping comes sorted by column, as a copy")
        void mapping_comesAsASortedCopy() {
            FK fk = declaredBThenA();

            fk.getColumns().put("Q", "RQ");

            assertThat(fk.getColumns()).containsExactly(entry("A", "RA"), entry("B", "RB"));
        }
    }

    @Nested
    @DisplayName("copyFrom()")
    class CopyFrom {

        @Test
        @DisplayName("the name, mapping, rules and referenced table are copied")
        void definition_isCopied() {
            FK source = declaredBThenA();
            source.setName("fk1");
            source.setDeleteRule(FK.ON_DELETE_RESTRICT);
            source.setUpdateRule(FK.ON_UPDATE_SET_NULL);
            source.setReferencedTableName("ref");
            FK copy = new FK(new Table());

            copy.copyFrom(source);
            source.addRefColumnName("C", "RC");

            assertThat(copy.getName()).isEqualTo("fk1");
            assertThat(copy.getColumns()).containsExactly(entry("A", "RA"), entry("B", "RB"));
            assertThat(copy.getDeleteRule()).isEqualTo(FK.ON_DELETE_RESTRICT);
            assertThat(copy.getUpdateRule()).isEqualTo(FK.ON_UPDATE_SET_NULL);
            assertThat(copy.getReferencedTableName()).isEqualTo("ref");
        }

        @Test
        @DisplayName("the table, DDL and changed flag stay, and null is ignored")
        void tableDdlAndFlag_stay() {
            FK source = declaredBThenA();
            source.setDDL("source ddl");
            source.setChangedReferentialAction(true);
            Table table = new Table();
            FK copy = new FK(table);
            copy.setDDL("ddl");

            copy.copyFrom(source);
            copy.copyFrom(null);

            assertThat(copy.getTable()).isSameAs(table);
            assertThat(copy.getDDL()).isEqualTo("ddl");
            assertThat(copy.isChangedReferentialAction()).isFalse();
        }
    }

    @Nested
    @DisplayName("getFKString()")
    class GetFKString {

        @Test
        @DisplayName("an FK without a table fails with an NPE")
        void fkWithoutTable_throwsNullPointerException() {
            assertThatThrownBy(() -> new FK().getFKString())
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("the name, table columns and referenced columns read with two spaces before >")
        void fkString_hasTwoSpacesBeforeTheArrow() {
            // DEFECT: "] " and " > " are appended one after the other, so the arrow gets two
            // spaces in front of it
            // - see FK.getFKString()
            Table table = new Table();
            table.setName("emp");
            FK fk = new FK(table);
            fk.setName("fk_d");
            fk.setReferencedTableName("dept");
            fk.addRefColumnName("b", "rb");
            fk.addRefColumnName("a", "ra");

            assertThat(fk.getFKString()).isEqualTo("fk_d(emp[a,b]  > dept[ra,rb])");
            assertThat(new FK(table).getFKString()).isEqualTo("null(emp[]  > null[])");
        }
    }
}
