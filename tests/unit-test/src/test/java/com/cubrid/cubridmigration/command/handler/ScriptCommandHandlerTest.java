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
package com.cubrid.cubridmigration.command.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

@DisplayName("ScriptCommandHandler")
class ScriptCommandHandlerTest {

    @Test
    @DisplayName("disables create only when target owner schema contains the table")
    void applyTargetTableMapping_matchesTargetOwner() {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig source = addSourceTable(config, "CUBRID", "cmt_emp");

        Catalog catalog = new Catalog();
        addTargetTable(catalog, "DBA", "cmt_emp");
        addTargetTable(catalog, "CUBRID", "cmt_emp");

        ScriptCommandHandler.applyTargetTableMapping(config, catalog);

        assertThat(source.isCreateNewTable()).isFalse();
        assertThat(source.isReplace()).isFalse();
        assertThat(source.isCreatePK()).isFalse();
    }

    @Test
    @DisplayName("keeps create enabled when only another schema has the same table name")
    void applyTargetTableMapping_ignoresDifferentOwnerMatch() {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig source = addSourceTable(config, "CUBRID", "cmt_emp");

        Catalog catalog = new Catalog();
        addTargetTable(catalog, "DBA", "cmt_emp");

        ScriptCommandHandler.applyTargetTableMapping(config, catalog);

        assertThat(source.isCreateNewTable()).isTrue();
        assertThat(source.isReplace()).isTrue();
        assertThat(source.isCreatePK()).isTrue();
    }

    @Test
    @DisplayName("keeps create enabled when owner is blank and table name is ambiguous")
    void applyTargetTableMapping_skipsAmbiguousFallback() {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig source = addSourceTable(config, null, "cmt_emp");

        Catalog catalog = new Catalog();
        addTargetTable(catalog, "DBA", "cmt_emp");
        addTargetTable(catalog, "CUBRID", "cmt_emp");

        ScriptCommandHandler.applyTargetTableMapping(config, catalog);

        assertThat(source.isCreateNewTable()).isTrue();
        assertThat(source.isReplace()).isTrue();
        assertThat(source.isCreatePK()).isTrue();
    }

    @Test
    @DisplayName("disables create when owner is blank and table name is unique across schemas")
    void applyTargetTableMapping_allowsUniqueFallback() {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig source = addSourceTable(config, null, "cmt_emp");

        Catalog catalog = new Catalog();
        addTargetTable(catalog, "DBA", "cmt_emp");

        ScriptCommandHandler.applyTargetTableMapping(config, catalog);

        assertThat(source.isCreateNewTable()).isFalse();
        assertThat(source.isReplace()).isFalse();
        assertThat(source.isCreatePK()).isFalse();
    }

    @Test
    @DisplayName("defaults add schema to yes for online target options")
    void applyTargetOutputOptions_defaultsAddSchemaToYes() {
        MigrationConfiguration config = new MigrationConfiguration();
        Properties properties = new Properties();

        assertThat(config.isAddUserSchema()).isFalse();

        ScriptCommandHandler.applyTargetOutputOptions(config, properties, "target");

        assertThat(config.isAddUserSchema()).isTrue();
    }

    @Test
    @DisplayName("honors explicit add_schema no for target options")
    void applyTargetOutputOptions_honorsExplicitNo() {
        MigrationConfiguration config = new MigrationConfiguration();
        Properties properties = new Properties();
        properties.setProperty("target.add_schema", "no");

        ScriptCommandHandler.applyTargetOutputOptions(config, properties, "target");

        assertThat(config.isAddUserSchema()).isFalse();
    }

    @Test
    @DisplayName("applies file target defaults consistently")
    void applyFileTargetOptions_appliesDefaults() {
        MigrationConfiguration config = new MigrationConfiguration();
        Properties properties = new Properties();

        ScriptCommandHandler.applyFileTargetOptions(config, properties, "target");

        assertThat(config.isSplitSchema()).isTrue();
        assertThat(config.isOneTableOneFile()).isFalse();
    }

    @Test
    @DisplayName("applies explicit file target options")
    void applyFileTargetOptions_honorsExplicitValues() {
        MigrationConfiguration config = new MigrationConfiguration();
        Properties properties = new Properties();
        properties.setProperty("target.split_schema", "no");
        properties.setProperty("target.one_table_one_file", "yes");

        ScriptCommandHandler.applyFileTargetOptions(config, properties, "target");

        assertThat(config.isSplitSchema()).isFalse();
        assertThat(config.isOneTableOneFile()).isTrue();
    }

    private SourceEntryTableConfig addSourceTable(
            MigrationConfiguration config, String targetOwner, String targetName) {
        SourceEntryTableConfig setc = new SourceEntryTableConfig();
        setc.setOwner("SRC");
        setc.setName(targetName.toUpperCase());
        setc.setTargetOwner(targetOwner);
        setc.setTarget(targetName);
        setc.setCreateNewTable(true);
        setc.setReplace(true);
        setc.setCreatePK(true);
        config.addExpEntryTableCfg(setc);
        return setc;
    }

    private void addTargetTable(Catalog catalog, String schemaName, String tableName) {
        Schema schema = catalog.getSchemaByName(schemaName);
        if (schema == null) {
            schema = new Schema(catalog);
            schema.setName(schemaName);
            catalog.addSchema(schema);
        }

        Table table = new Table();
        table.setOwner(schemaName);
        table.setName(tableName);
        schema.addTable(table);
    }
}
