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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SourceTableConfig")
class SourceTableConfigTest {

    @Nested
    @DisplayName("setCreateNewTable()")
    class SetCreateNewTable {

        @Test
        @DisplayName("turning it on selects every column, if none was selected yet")
        void nothingSelectedYet_selectsEveryColumn() {
            SourceTableConfig config = configWithNothingSelected();

            config.setCreateNewTable(true);

            assertThat(config.getColumnConfigList())
                    .extracting(SourceColumnConfig::getName, SourceColumnConfig::isCreate)
                    .containsExactly(tuple("f1", true), tuple("f2", true));
        }

        @Test
        @DisplayName("a column already selected means the rest are left as they are")
        void somethingAlreadySelected_leavesTheRestAlone() {
            SourceTableConfig config = new SourceTableConfig();
            config.setCreateNewTable(false);
            config.addColumnConfig("f1", "f1", true);
            config.addColumnConfig("f2", "f2", false);

            config.setCreateNewTable(true);

            assertThat(config.getColumnConfig("f1").isCreate()).isTrue();
            assertThat(config.getColumnConfig("f2").isCreate()).isFalse();
        }

        @Test
        @DisplayName("turning it off selects nothing, whatever the columns were")
        void turningItOff_selectsNothing() {
            SourceTableConfig config = configWithNothingSelected();

            config.setCreateNewTable(false);

            assertThat(config.getColumnConfig("f1").isCreate()).isFalse();
        }
    }

    @Nested
    @DisplayName("setMigrateData()")
    class SetMigrateData {

        @Test
        @DisplayName("turning it on selects every column, by the same rule")
        void nothingSelectedYet_selectsEveryColumn() {
            SourceTableConfig config = configWithNothingSelected();

            config.setMigrateData(true);

            assertThat(config.getColumnConfig("f1").isCreate()).isTrue();
            assertThat(config.getColumnConfig("f2").isCreate()).isTrue();
        }

        @Test
        @DisplayName("a column already selected means the rest are left as they are")
        void somethingAlreadySelected_leavesTheRestAlone() {
            SourceTableConfig config = new SourceTableConfig();
            config.setMigrateData(false);
            config.addColumnConfig("f1", "f1", true);
            config.addColumnConfig("f2", "f2", false);

            config.setMigrateData(true);

            assertThat(config.getColumnConfig("f2").isCreate()).isFalse();
        }
    }

    /**
     * createNewTable, migrateData and replace all start out on, so a config has to be switched off
     * before switching it on does anything at all.
     */
    private static SourceTableConfig configWithNothingSelected() {
        SourceTableConfig config = new SourceTableConfig();
        config.setCreateNewTable(false);
        config.setMigrateData(false);
        config.addColumnConfig("f1", "f1", false);
        config.addColumnConfig("f2", "f2", false);
        return config;
    }
}
