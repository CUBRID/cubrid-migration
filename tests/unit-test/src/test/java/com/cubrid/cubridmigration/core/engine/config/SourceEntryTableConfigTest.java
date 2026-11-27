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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SourceEntryTableConfig")
class SourceEntryTableConfigTest {

    @Nested
    @DisplayName("setCreateNewTable()")
    class SetCreateNewTable {

        @Test
        @DisplayName("partition follows create table when enabled")
        void turningItOn_enablesThePartition() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();
            config.setCreatePartition(false);
            config.setCreateNewTable(false);

            config.setCreateNewTable(true);

            assertThat(config.isCreateNewTable()).isTrue();
            assertThat(config.isCreatePartition()).isTrue();
        }

        @Test
        @DisplayName("partition is cleared when create table is disabled")
        void turningItOff_disablesThePartition() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();
            config.setCreatePartition(true);
            config.setCreateNewTable(true);

            config.setCreateNewTable(false);

            assertThat(config.isCreateNewTable()).isFalse();
            assertThat(config.isCreatePartition()).isFalse();
        }

        @Test
        @DisplayName("turning it on selects every foreign key and index, if none was selected yet")
        void nothingSelectedYet_selectsEveryForeignKeyAndIndex() {
            SourceEntryTableConfig config = configWithNothingSelected();

            config.setCreateNewTable(true);

            assertThat(config.getColumnConfig("f1").isCreate()).isTrue();
            assertThat(config.getFKConfig("fk1").isCreate()).isTrue();
            assertThat(config.getFKConfig("fk1").isReplace()).isTrue();
            assertThat(config.getIndexConfig("ix1").isCreate()).isTrue();
        }

        @Test
        @DisplayName("a foreign key already selected means the rest are left as they are")
        void somethingAlreadySelected_leavesTheRestAlone() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();
            config.setCreateNewTable(false);
            config.addFKConfig("fk1", "fk1", true);
            config.addFKConfig("fk2", "fk2", false);

            config.setCreateNewTable(true);

            assertThat(config.getFKConfig("fk1").isCreate()).isTrue();
            assertThat(config.getFKConfig("fk2").isCreate()).isFalse();
        }

        @Test
        @DisplayName("turning it off again keeps the selection and only drops the partition")
        void turningItOff_keepsTheSelection() {
            SourceEntryTableConfig config = configWithNothingSelected();
            config.setCreateNewTable(true);

            config.setCreateNewTable(false);

            assertThat(config.getColumnConfig("f1").isCreate()).isTrue();
            assertThat(config.getFKConfig("fk1").isCreate()).isTrue();
            assertThat(config.isCreatePartition()).isFalse();
        }

        /**
         * createNewTable and migrateData both start out on, so a config has to be switched off
         * before switching it on does anything at all.
         */
        private SourceEntryTableConfig configWithNothingSelected() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();
            config.setCreateNewTable(false);
            config.setMigrateData(false);
            config.addColumnConfig("f1", "f1", false);
            config.addFKConfig("fk1", "fk1", false);
            config.addIndexConfig("ix1", "ix1", false);
            return config;
        }
    }

    @Nested
    @DisplayName("setMigrateData()")
    class SetMigrateData {

        @Test
        @DisplayName("the columns are selected but the keys and indexes are not")
        void nothingSelectedYet_selectsTheColumnsOnly() {
            SourceEntryTableConfig config = configWithNothingSelected();

            config.setMigrateData(true);

            assertThat(config.getColumnConfig("f1").isCreate()).isTrue();
            assertThat(config.getFKConfig("fk1").isCreate()).isFalse();
            assertThat(config.getIndexConfig("ix1").isCreate()).isFalse();
        }

        /** Same shape as the sibling group: nothing is selected until the flag is switched off. */
        private SourceEntryTableConfig configWithNothingSelected() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();
            config.setCreateNewTable(false);
            config.setMigrateData(false);
            config.addColumnConfig("f1", "f1", false);
            config.addFKConfig("fk1", "fk1", false);
            config.addIndexConfig("ix1", "ix1", false);
            return config;
        }
    }

    @Nested
    @DisplayName("getCondition()")
    class GetCondition {

        @Test
        @DisplayName("no condition is an empty one, so it can be appended to a WHERE clause")
        void noCondition_isEmpty() {
            SourceEntryTableConfig config = new SourceEntryTableConfig();

            assertThat(config.getCondition()).isEmpty();

            config.setCondition(null);

            assertThat(config.getCondition()).isEmpty();
        }
    }
}
