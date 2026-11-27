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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

@DisplayName("PK")
class PKTest {

    @Nested
    @DisplayName("PK()")
    class Constructor {

        @Test
        @DisplayName("the PK belongs to the given table, and null is rejected")
        void table_isRequired() {
            Table table = new Table();

            assertThat(new PK(table).getTable()).isSameAs(table);
            assertThatThrownBy(() -> new PK(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("setTable()")
    class SetTable {

        @Test
        @DisplayName("a table replaces the current one, and null is rejected")
        void table_replacesTheCurrentOne() {
            PK pk = new PK(new Table());
            Table other = new Table();

            pk.setTable(other);

            assertThat(pk.getTable()).isSameAs(other);
            assertThatThrownBy(() -> pk.setTable(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("table can't be NULL.");
        }
    }

    @Nested
    @DisplayName("getPkColumns()")
    class GetPkColumns {

        @Test
        @DisplayName("changing the returned list leaves the PK columns alone")
        void returnedList_isACopy() {
            PK pk = new PK();
            pk.addColumn("a");

            pk.getPkColumns().add("x");

            assertThat(pk.getPkColumns()).containsExactly("a");
        }
    }

    @Nested
    @DisplayName("addColumn()")
    class AddColumn {

        @Test
        @DisplayName(
                "a name already there is skipped, names compare with case and null counts once")
        void sameName_isSkipped() {
            PK pk = new PK();

            pk.addColumn("ID");
            pk.addColumn("id");
            pk.addColumn("ID");
            pk.addColumn(null);
            pk.addColumn(null);

            assertThat(pk.getPkColumns()).containsExactly("ID", "id", null);
        }
    }

    @Nested
    @DisplayName("setPkColumns()")
    class SetPkColumns {

        @Test
        @DisplayName("the list replaces the columns, keeping the first of each name")
        void list_replacesTheColumns() {
            PK pk = new PK();
            pk.addColumn("old");

            pk.setPkColumns(Arrays.asList("b", "a", "b"));

            assertThat(pk.getPkColumns()).containsExactly("b", "a");
        }

        @Test
        @DisplayName("null or an empty list removes every column")
        void nullOrEmptyList_removesEveryColumn() {
            PK pk = new PK();
            pk.addColumn("a");
            pk.setPkColumns(null);
            assertThat(pk.getPkColumns()).isEmpty();

            pk.addColumn("a");
            pk.setPkColumns(new ArrayList<String>());

            assertThat(pk.getPkColumns()).isEmpty();
        }
    }
}
