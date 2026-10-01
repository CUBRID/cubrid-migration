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

import com.cubrid.cubridmigration.testutil.TestTableFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@DisplayName("Table")
class TableTest {

    private static FK fk(Table table, String name) {
        FK fk = new FK(table);
        fk.setName(name);
        return fk;
    }

    @Nested
    @DisplayName("addFK()")
    class AddFK {

        @Test
        @DisplayName("an FK is added once and owned by the table")
        void fk_isAddedOnceAndOwned() {
            Table table = new Table();
            FK fk = fk(table, "fk");

            table.addFK(fk);
            table.addFK(fk);

            assertThat(table.getFks()).containsExactly(fk);
            assertThat(table.getFKByName("fk")).isSameAs(fk);
            assertThat(fk.getTable()).isSameAs(table);
        }

        @Test
        @DisplayName("a second FK with the same name, whatever the case, is dropped")
        void sameNameIgnoringCase_keepsTheOldFK() {
            // DEFECT: the Javadoc says the old FK is removed, but the new one with the same
            // name is dropped without notice and the old one stays
            // - see Table.addFK()
            Table table = new Table();
            FK old = fk(table, "fk");
            table.addFK(old);

            table.addFK(fk(table, "FK"));

            assertThat(table.getFks()).containsExactly(old);
        }

        @Test
        @DisplayName("a missing FK or one of another table is reported as an index")
        void missingOrForeignFK_isReportedAsAnIndex() {
            // DEFECT: the messages were copied from addIndex(), so a bad FK is reported as an
            // index
            // - see Table.addFK()
            Table table = new Table();

            assertThatThrownBy(() -> table.addFK(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Index can't be NULL");
            assertThatThrownBy(() -> table.addFK(fk(new Table(), "fk")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Index was set into a wrong table.");
        }
    }

    @Nested
    @DisplayName("addIndex()")
    class AddIndex {

        @Test
        @DisplayName("an index is added and owned by the table")
        void index_isAddedAndOwned() {
            Table table = new Table();
            Index index = TestTableFactory.createIndex("idx", "c");

            table.addIndex(index);

            assertThat(table.getIndexes()).containsExactly(index);
            assertThat(index.getTable()).isSameAs(table);
        }

        @Test
        @DisplayName("a missing index or one of another table is rejected")
        void missingOrForeignIndex_isRejected() {
            Table table = new Table();
            Index foreign = TestTableFactory.createIndex("idx", "c");
            foreign.setTable(new Table());

            assertThatThrownBy(() -> table.addIndex(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Index can't be NULL");
            assertThatThrownBy(() -> table.addIndex(foreign))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Index was set into a wrong table.");
        }

        @Test
        @DisplayName("a second index with the same name is dropped")
        void sameName_keepsTheOldIndex() {
            // DEFECT: the Javadoc says the old index is removed, but the new one with the same
            // name is dropped without notice and never gets its table
            // - see Table.addIndex()
            Table table = new Table();
            Index old = TestTableFactory.createIndex("idx", "c");
            Index later = TestTableFactory.createIndex("idx", "d");
            table.addIndex(old);

            table.addIndex(later);

            assertThat(table.getIndexes()).containsExactly(old);
            assertThat(later.getTable()).isNull();
        }
    }

    @Nested
    @DisplayName("getFKByName()")
    class GetFKByName {

        @Test
        @DisplayName("the name matches ignoring case")
        void name_matchesIgnoringCase() {
            Table table = new Table();
            FK fk = fk(table, "fk");
            table.addFK(fk);

            assertThat(table.getFKByName("FK")).isSameAs(fk);
        }

        @Test
        @DisplayName("a null or unknown name gives null")
        void nullOrUnknownName_returnsNull() {
            Table table = new Table();
            table.addFK(fk(table, "fk"));

            assertThat(table.getFKByName(null)).isNull();
            assertThat(table.getFKByName("fkno")).isNull();
        }
    }

    @Nested
    @DisplayName("getFks()")
    class GetFks {

        @Test
        @DisplayName("changing the returned list leaves the FKs alone, which are shared")
        void returnedList_isACopy() {
            Table table = new Table();
            FK fk = fk(table, "fk");
            table.addFK(fk);

            table.getFks().clear();

            assertThat(table.getFks()).containsExactly(fk);
        }
    }

    @Nested
    @DisplayName("getIndexByName()")
    class GetIndexByName {

        @Test
        @DisplayName("the name matches with case")
        void name_matchesWithCase() {
            Table table = new Table();
            Index index = TestTableFactory.createIndex("idx", "c");
            table.addIndex(index);

            assertThat(table.getIndexByName("idx")).isSameAs(index);
            assertThat(table.getIndexByName("IDX")).isNull();
        }

        @Test
        @DisplayName("a null or unknown name gives null")
        void nullOrUnknownName_returnsNull() {
            Table table = new Table();
            table.addIndex(TestTableFactory.createIndex("idx", "c"));

            assertThat(table.getIndexByName(null)).isNull();
            assertThat(table.getIndexByName("zz")).isNull();
        }

        @Test
        @DisplayName("names differing in case make two indexes, and one cannot remove the other")
        void namesDifferingInCase_makeTwoIndexes() {
            // DEFECT: unlike the FK lookup, index names compare with case, so idx and IDX are
            // both added and removeIndex("IDX") leaves idx in place
            // - see Table.getIndexByName()
            Table table = new Table();
            Index lower = TestTableFactory.createIndex("idx", "c");
            table.addIndex(lower);
            table.addIndex(TestTableFactory.createIndex("IDX", "d"));
            assertThat(table.getIndexes()).hasSize(2);

            table.removeIndex("IDX");

            assertThat(table.getIndexes()).containsExactly(lower);
        }
    }

    @Nested
    @DisplayName("getIndexes()")
    class GetIndexes {

        @Test
        @DisplayName("removing from the returned list leaves the indexes alone")
        void returnedList_isACopy() {
            Table table = new Table();
            Index index = TestTableFactory.createIndex("idx", "c");
            table.addIndex(index);

            table.getIndexes().removeIf(each -> true);

            assertThat(table.getIndexes()).containsExactly(index);
        }
    }

    @Nested
    @DisplayName("removeFK()")
    class RemoveFK {

        @Test
        @DisplayName("the FK is removed by name ignoring case, and an unknown name does nothing")
        void fk_isRemovedIgnoringCase() {
            Table table = new Table();
            table.addFK(fk(table, "fk"));

            table.removeFK("fkno");
            table.removeFK(null);
            assertThat(table.getFks()).hasSize(1);

            table.removeFK("FK");

            assertThat(table.getFKByName("fk")).isNull();
        }
    }

    @Nested
    @DisplayName("removeIndex()")
    class RemoveIndex {

        @Test
        @DisplayName("the index is removed by its exact name, and an unknown name does nothing")
        void index_isRemovedByExactName() {
            Table table = new Table();
            table.addIndex(TestTableFactory.createIndex("idx", "c"));

            table.removeIndex("zz");
            table.removeIndex(null);
            assertThat(table.getIndexes()).hasSize(1);

            table.removeIndex("idx");

            assertThat(table.getIndexes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setPk()")
    class SetPk {

        @Test
        @DisplayName("the PK is owned by the table, even one made for another table")
        void pk_isOwnedByTheTable() {
            Table table = new Table();
            PK pk = new PK(new Table());

            table.setPk(pk);

            assertThat(table.getPk()).isSameAs(pk);
            assertThat(pk.getTable()).isSameAs(table);
        }

        @Test
        @DisplayName("null removes the PK")
        void nullPk_removesThePK() {
            Table table = new Table();
            table.setPk(new PK(table));

            table.setPk(null);

            assertThat(table.getPk()).isNull();
        }
    }

    @Nested
    @DisplayName("setTableRowCount()")
    class SetTableRowCount {

        @Test
        @DisplayName("zero or more rows are stored")
        void nonNegativeCount_isStored() {
            Table table = new Table();

            table.setTableRowCount(0);
            table.setTableRowCount(5);

            assertThat(table.getTableRowCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("a negative count is rejected")
        void negativeCount_isRejected() {
            Table table = new Table();

            assertThatThrownBy(() -> table.setTableRowCount(-1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Table Row count can't be negative.");
        }
    }

    @Nested
    @DisplayName("setFKs()")
    class SetFKs {

        @Test
        @DisplayName("the list replaces the FKs, keeping the first of a name in any case")
        void list_replacesTheFKs() {
            Table table = new Table();
            table.addFK(fk(table, "old"));
            FK first = fk(table, "a");

            table.setFKs(Arrays.asList(first, fk(table, "A")));

            assertThat(table.getFks()).containsExactly(first);
        }

        @Test
        @DisplayName("null or an empty list removes every FK")
        void nullOrEmptyList_removesEveryFK() {
            Table table = new Table();
            table.addFK(fk(table, "a"));
            table.setFKs(null);
            assertThat(table.getFks()).isEmpty();

            table.addFK(fk(table, "a"));
            table.setFKs(new ArrayList<FK>());

            assertThat(table.getFks()).isEmpty();
        }

        @Test
        @DisplayName("a bad FK in the list fails after the old FKs are gone")
        void badFK_failsAfterTheOldFKsAreGone() {
            Table table = new Table();
            table.addFK(fk(table, "keep"));
            List<FK> list = Arrays.asList(fk(new Table(), "wrong"));

            assertThatThrownBy(() -> table.setFKs(list)).isInstanceOf(RuntimeException.class);
            assertThat(table.getFks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setIndexes()")
    class SetIndexes {

        @Test
        @DisplayName("the list replaces the indexes, keeping the first of an exact name")
        void list_replacesTheIndexes() {
            Table table = new Table();
            table.addIndex(TestTableFactory.createIndex("old", "c"));
            Index first = TestTableFactory.createIndex("i", "c");
            Index upper = TestTableFactory.createIndex("I", "e");

            table.setIndexes(Arrays.asList(first, TestTableFactory.createIndex("i", "d"), upper));

            assertThat(table.getIndexes()).containsExactly(first, upper);
        }

        @Test
        @DisplayName("null or an empty list removes every index")
        void nullOrEmptyList_removesEveryIndex() {
            Table table = new Table();
            table.addIndex(TestTableFactory.createIndex("i", "c"));
            table.setIndexes(null);
            assertThat(table.getIndexes()).isEmpty();

            table.addIndex(TestTableFactory.createIndex("i", "c"));
            table.setIndexes(new ArrayList<Index>());

            assertThat(table.getIndexes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasPK()")
    class HasPK {

        @Test
        @DisplayName("only a PK with a column counts")
        void pkWithAColumn_counts() {
            Table table = new Table();
            assertThat(table.hasPK()).isFalse();
            PK pk = new PK(table);
            table.setPk(pk);
            assertThat(table.hasPK()).isFalse();

            pk.addColumn("c");

            assertThat(table.hasPK()).isTrue();
        }
    }
}
