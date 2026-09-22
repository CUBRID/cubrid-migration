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
package com.cubrid.cubridmigration.core.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

@DisplayName("SourceCSVConfig")
class SourceCSVConfigTest {

    private static final List<String[]> TWO_ROWS =
            Arrays.asList(new String[] {"ID", "Name"}, new String[] {"1", "a"});

    @Nested
    @DisplayName("setPreviewData()")
    class SetPreviewData {

        @Test
        @DisplayName("the first row is data by default, so the columns are named by position")
        void firstRowIsData_namesTheColumnsByPosition() {
            SourceCSVConfig config = new SourceCSVConfig();

            config.setPreviewData(TWO_ROWS);

            assertThat(config.isImportFirstRow()).isTrue();
            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getName, SourceCSVColumnConfig::getTarget)
                    .containsExactly(tuple("col1", "col1"), tuple("col2", "col2"));
        }

        @Test
        @DisplayName(
                "with the first row read as a header, it names the columns and the targets are"
                        + " lowercased")
        void firstRowIsHeader_namesTheColumnsAfterIt() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setImportFirstRow(false);

            config.setPreviewData(TWO_ROWS);

            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getName, SourceCSVColumnConfig::getTarget)
                    .containsExactly(tuple("ID", "id"), tuple("Name", "name"));
        }

        @Test
        @DisplayName("no data leaves no columns to migrate")
        void noData_leavesNoColumns() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setPreviewData(TWO_ROWS);

            config.setPreviewData(null);

            assertThat(config.getColumnConfigs()).isEmpty();
            assertThat(config.getPreviewData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setImportFirstRow()")
    class SetImportFirstRow {

        @Test
        @DisplayName(
                "changing how the first row is read renames the columns that were already"
                        + " built")
        void changingTheFlag_rebuildsTheColumnNames() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setPreviewData(TWO_ROWS);

            config.setImportFirstRow(false);

            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getName)
                    .containsExactly("ID", "Name");
        }
    }

    @Nested
    @DisplayName("changeTarget()")
    class ChangeTarget {

        @Test
        @DisplayName("with no header to match on, the columns take the target's in order")
        void noHeader_mapsByPosition() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setPreviewData(TWO_ROWS);

            config.changeTarget(targetTable());

            assertThat(config.getTarget()).isEqualTo("tgt");
            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getTarget)
                    .containsExactly("name", "id");
        }

        @Test
        @DisplayName("with a header, a column that matches a target name by lower case takes it")
        void header_mapsByNameFirst() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setImportFirstRow(false);
            config.setPreviewData(TWO_ROWS);

            config.changeTarget(targetTable());

            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getName, SourceCSVColumnConfig::getTarget)
                    .containsExactly(tuple("ID", "id"), tuple("Name", "name"));
        }

        @Test
        @DisplayName("no target table -> nothing is remapped")
        void noTargetTable_changesNothing() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setPreviewData(TWO_ROWS);

            config.changeTarget(null);

            assertThat(config.getTarget()).isNull();
            assertThat(config.getColumnConfigs())
                    .extracting(SourceCSVColumnConfig::getTarget)
                    .containsExactly("col1", "col2");
        }

        /** The columns are deliberately out of the CSV's order, so a positional map is visible. */
        private Table targetTable() {
            Table table = new Table();
            table.setName("tgt");
            for (String name : new String[] {"name", "id"}) {
                Column column = new Column(table);
                column.setName(name);
                table.addColumn(column);
            }
            return table;
        }
    }

    @Nested
    @DisplayName("getTargetColumn()")
    class GetTargetColumn {

        @Test
        @DisplayName("an index past the last column -> null rather than an error")
        void indexPastTheEnd_returnsNull() {
            SourceCSVConfig config = new SourceCSVConfig();
            config.setPreviewData(TWO_ROWS);

            assertThat(config.getTargetColumn(0).getName()).isEqualTo("col1");
            assertThat(config.getTargetColumn(9)).isNull();
        }
    }
}
