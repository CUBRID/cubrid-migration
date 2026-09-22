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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.Locale;
import java.util.TimeZone;

@DisplayName("MigrationConfiguration")
class MigrationConfigurationTest {

    private static final String PROCEDURE_SOURCE_DDL =
            "CREATE OR REPLACE PROCEDURE TEST_PROC IS\n" + "BEGIN\n" + "    NULL;\n" + "END;";

    private static final String FUNCTION_SOURCE_DDL =
            "CREATE OR REPLACE FUNCTION TEST_FUNC RETURN NUMBER IS\n"
                    + "BEGIN\n"
                    + "    RETURN 1;\n"
                    + "END;";

    @Nested
    @DisplayName("parsingProcedureFunction()")
    class ParsingProcedureFunction {

        @Test
        @DisplayName("rebuilds procedure body when only header is present")
        void parsingProcedureFunction_rebuildsProcedureBodyWhenMissing() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpPlcsqlProcedureCfg(
                    "SRC",
                    "TAR",
                    "TEST_PROC",
                    "test_proc",
                    "DEFINER",
                    false,
                    PROCEDURE_SOURCE_DDL,
                    null,
                    null,
                    null);

            PlcsqlProcedure target = new PlcsqlProcedure();
            target.setOwner("SRC");
            target.setTargetOwner("TAR");
            target.setName("TEST_PROC");
            target.setTargetName("test_proc");
            target.setHeaderDDL("PROCEDURE [tar].[test_proc]");
            target.setBodyDDL("");
            config.addTargetPlcsqlProcedureSchema(target);

            config.parsingProcedureFunction(true);

            PlcsqlProcedure parsed = config.getTargetPlcsqlProcedureSchema("SRC", "TEST_PROC");
            assertThat(parsed.getHeaderDDL()).contains("PROCEDURE");
            assertThat(parsed.getBodyDDL()).isNotBlank();
            assertThat(parsed.getBodyDDL()).contains("BEGIN");
            assertThat(parsed.getBodyDDL()).contains("NULL;");
        }

        @Test
        @DisplayName("rebuilds function body when only header is present")
        void parsingProcedureFunction_rebuildsFunctionBodyWhenMissing() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpPlcsqlFunctionCfg(
                    "SRC",
                    "TAR",
                    "TEST_FUNC",
                    "test_func",
                    "DEFINER",
                    false,
                    FUNCTION_SOURCE_DDL,
                    null,
                    null,
                    null);

            PlcsqlFunction target = new PlcsqlFunction();
            target.setOwner("SRC");
            target.setTargetOwner("TAR");
            target.setName("TEST_FUNC");
            target.setTargetName("test_func");
            target.setHeaderDDL("FUNCTION [tar].[test_func] RETURN NUMERIC");
            target.setBodyDDL("   ");
            config.addTargetPlcsqlFunctionSchema(target);

            config.parsingProcedureFunction(true);

            PlcsqlFunction parsed = config.getTargetPlcsqlFunctionSchema("SRC", "TEST_FUNC");
            assertThat(parsed.getHeaderDDL()).contains("FUNCTION");
            assertThat(parsed.getBodyDDL()).isNotBlank();
            assertThat(parsed.getBodyDDL()).contains("BEGIN");
            assertThat(parsed.getBodyDDL()).contains("RETURN 1;");
        }
    }

    @Nested
    @DisplayName("source selection lookup")
    class SourceSelectionLookup {

        @ParameterizedTest(name = "[{index}] {0}: schema={1} name={2} -> {3}")
        @DisplayName(
                "an object is selected under one schema and name, and each kind decides for"
                        + " itself how exactly those have to be spelled")
        @CsvSource(
                nullValues = "null",
                value = {
                    // Spelled exactly as selected, every kind resolves.
                    "table,     hr,   EMP, true",
                    "serial,    hr,   EMP, true",
                    "synonym,   hr,   EMP, true",
                    "view,      hr,   EMP, true",
                    "grant,     hr,   EMP, true",
                    "procedure, hr,   EMP, true",
                    "function,  hr,   EMP, true",

                    // No schema asked for, so the first name match wins.
                    "table,     null, EMP, true",
                    "serial,    null, EMP, true",
                    "synonym,   null, EMP, true",
                    "view,      null, EMP, true",
                    "grant,     null, EMP, true",
                    "procedure, null, EMP, true",
                    "function,  null, EMP, true",

                    // DEFECT: the schema is compared with equalsIgnoreCase for most kinds but with
                    // equals for sequences and views, so the same schema spelled in upper case
                    // resolves a table and not a sequence
                    // - see MigrationConfiguration.getExpSerialCfg(), getExpViewCfg()
                    "table,     HR,   EMP, true",
                    "grant,     HR,   EMP, true",
                    "procedure, HR,   EMP, true",
                    "function,  HR,   EMP, true",
                    "synonym,   HR,   EMP, true",
                    "serial,    HR,   EMP, false",
                    "view,      HR,   EMP, false",

                    // DEFECT: the name is compared with equals for every kind except synonyms,
                    // which use equalsIgnoreCase
                    // - see MigrationConfiguration.getExpSynonymCfg()
                    "synonym,   hr,   emp, true",
                    "table,     hr,   emp, false",
                    "serial,    hr,   emp, false",
                    "view,      hr,   emp, false",
                    "grant,     hr,   emp, false",
                    "procedure, hr,   emp, false",
                    "function,  hr,   emp, false",
                })
        void selectedObject_resolvesOnlyForTheSpellingsItsKindAccepts(
                String kind, String schema, String name, boolean found) {
            MigrationConfiguration config = configWithOneObjectOfEachKind();

            assertThat(selected(config, kind, schema, name) != null).isEqualTo(found);
        }

        private Object selected(
                MigrationConfiguration config, String kind, String schema, String name) {
            switch (kind) {
                case "table":
                    return config.getExpEntryTableCfg(schema, name);
                case "serial":
                    return config.getExpSerialCfg(schema, name);
                case "synonym":
                    return config.getExpSynonymCfg(schema, name);
                case "view":
                    return config.getExpViewCfg(schema, name);
                case "grant":
                    return config.getExpGrantCfg(schema, name);
                case "procedure":
                    return config.getExpPlcsqlProcedureCfg(schema, name);
                default:
                    return config.getExpPlcsqlFunctionCfg(schema, name);
            }
        }

        /** One object of every kind, all named EMP under schema hr. */
        private MigrationConfiguration configWithOneObjectOfEachKind() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = new SourceEntryTableConfig();
            table.setName("EMP");
            table.setOwner("hr");
            table.setTarget("emp");
            config.addExpEntryTableCfg(table);
            config.addExpSerialCfg("hr", "EMP", "emp");
            config.addExpSynonymCfg("hr", "EMP", "hr", "emp", "hr", "obj", "hr", "obj");
            config.addExpViewCfg("hr", "EMP", "emp", "hr", "c");
            config.addExpGrantCfg(
                    "hr", "EMP", "gr", "ge", "obj", "hr", "SELECT", false, "hr", "gr", "hr");
            config.addExpPlcsqlProcedureCfg(
                    "hr", "hr", "EMP", "emp", "d", false, "s", "h", "b", "e");
            config.addExpPlcsqlFunctionCfg(
                    "hr", "hr", "EMP", "emp", "d", false, "s", "h", "b", "e");
            return config;
        }
    }

    @Nested
    @DisplayName("target schema lookup")
    class TargetSchemaLookup {

        @ParameterizedTest(name = "[{index}] {0}: owner={1} name={2} -> {3}")
        @DisplayName(
                "a target object is found by owner and name, both compared without regard to"
                        + " case")
        @CsvSource({
            "table,     hr, emp, true",
            "serial,    hr, emp, true",
            "synonym,   hr, emp, true",
            "grant,     hr, emp, true",
            "view,      hr, emp, true",
            "procedure, hr, emp, true",
            "function,  hr, emp, true",

            // Either side in another case still resolves.
            "table,     HR, emp, true",
            "serial,    HR, emp, true",
            "view,      HR, emp, true",
            "table,     hr, EMP, true",
            "serial,    hr, EMP, true",
            "view,      hr, EMP, true",
        })
        void ownerAndName_areComparedWithoutRegardToCase(
                String kind, String owner, String name, boolean found) {
            MigrationConfiguration config = configWithOneTargetOfEachKind();

            assertThat(target(config, kind, owner, name) != null).isEqualTo(found);
        }

        @ParameterizedTest(name = "[{index}] {0}: name={1} -> {2}")
        @DisplayName(
                "with no owner the lookup falls back to name alone, and there each kind"
                        + " decides for itself how it is spelled")
        @CsvSource(
                nullValues = "null",
                value = {
                    // Spelled exactly as added, every kind resolves.
                    "table,     emp, true",
                    "serial,    emp, true",
                    "synonym,   emp, true",
                    "grant,     emp, true",
                    "view,      emp, true",
                    "procedure, emp, true",
                    "function,  emp, true",

                    // DEFECT: the owner-less overload compares the name with equalsIgnoreCase for
                    // grants, procedures and functions but with equals for the rest, so the same
                    // name in upper case resolves a grant and not a table
                    // - see MigrationConfiguration.getTargetTableSchema(String)
                    "grant,     EMP, true",
                    "procedure, EMP, true",
                    "function,  EMP, true",
                    "table,     EMP, false",
                    "serial,    EMP, false",
                    "synonym,   EMP, false",
                    "view,      EMP, false",
                })
        void withoutOwner_theNameSpellingDependsOnTheKind(String kind, String name, boolean found) {
            MigrationConfiguration config = configWithOneTargetOfEachKind();

            assertThat(target(config, kind, null, name) != null).isEqualTo(found);
        }

        private Object target(
                MigrationConfiguration config, String kind, String owner, String name) {
            switch (kind) {
                case "table":
                    return config.getTargetTableSchema(owner, name);
                case "serial":
                    return config.getTargetSerialSchema(owner, name);
                case "synonym":
                    return config.getTargetSynonymSchema(owner, name);
                case "grant":
                    return config.getTargetGrantSchema(owner, name);
                case "view":
                    return config.getTargetViewSchema(owner, name);
                case "procedure":
                    return config.getTargetPlcsqlProcedureSchema(owner, name);
                default:
                    return config.getTargetPlcsqlFunctionSchema(owner, name);
            }
        }

        /** One target object of every kind, all named emp under owner hr. */
        private MigrationConfiguration configWithOneTargetOfEachKind() {
            MigrationConfiguration config = new MigrationConfiguration();
            Table table = new Table();
            table.setName("emp");
            table.setOwner("hr");
            config.addTargetTableSchema(table);
            Sequence sequence = new Sequence();
            sequence.setName("emp");
            sequence.setOwner("hr");
            config.addTargetSerialSchema(sequence);
            Synonym synonym = new Synonym();
            synonym.setName("emp");
            synonym.setOwner("hr");
            config.addTargetSynonymSchema(synonym);
            Grant grant = new Grant();
            grant.setName("emp");
            grant.setOwner("hr");
            config.addTargetGrantSchema(grant);
            View view = new View();
            view.setName("emp");
            view.setOwner("hr");
            config.addTargetViewSchema(view);
            PlcsqlProcedure procedure = new PlcsqlProcedure();
            procedure.setName("emp");
            procedure.setOwner("hr");
            config.addTargetPlcsqlProcedureSchema(procedure);
            PlcsqlFunction function = new PlcsqlFunction();
            function.setName("emp");
            function.setOwner("hr");
            config.addTargetPlcsqlFunctionSchema(function);
            return config;
        }
    }

    @Nested
    @DisplayName("selection guards")
    class SelectionGuards {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName(
                "once a source database is attached the selection is fixed, so adding to it"
                        + " is refused")
        @CsvSource({
            "expEntryTable",
            "expSerial",
            "expSynonym",
            "expView",
            "expGrant",
            "expFunction",
            "expProcedure",
            "expTrigger",
            "targetSerial",
            "targetSynonym",
            "targetGrant",
            "targetView",
            "targetProcedure",
            "targetFunction",
        })
        void afterASourceDatabaseIsAttached_addingIsRefused(String kind) {
            MigrationConfiguration config = configWithSourceCatalog();

            assertThatThrownBy(() -> add(config, kind))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Source database was specified.");
        }

        @Test
        @DisplayName("adding a target table is refused too, with a message of its own")
        void addingATargetTable_isRefusedWithItsOwnMessage() {
            MigrationConfiguration config = configWithSourceCatalog();

            // DEFECT: every other guard in this family reports "Source database was specified.",
            // so a caller matching on the message misses this one
            // - see MigrationConfiguration.addTargetTableSchema()
            assertThatThrownBy(() -> add(config, "targetTable"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Schema was specified.");
        }

        @Test
        @DisplayName("with no source database attached the same calls go through")
        void withoutASourceDatabase_addingGoesThrough() {
            MigrationConfiguration config = new MigrationConfiguration();

            add(config, "expEntryTable");
            add(config, "targetTable");

            assertThat(config.getExpEntryTableCfg()).hasSize(1);
            assertThat(config.getTargetTableSchema()).hasSize(1);
        }

        private MigrationConfiguration configWithSourceCatalog() {
            MigrationConfiguration config = new MigrationConfiguration();
            Catalog catalog = new Catalog();
            catalog.setName("db");
            config.setSrcCatalog(catalog, false);
            return config;
        }

        private void add(MigrationConfiguration config, String kind) {
            switch (kind) {
                case "expEntryTable":
                    SourceEntryTableConfig table = new SourceEntryTableConfig();
                    table.setName("X");
                    table.setTarget("x");
                    config.addExpEntryTableCfg(table);
                    return;
                case "expSerial":
                    config.addExpSerialCfg("s", "X", "x");
                    return;
                case "expSynonym":
                    config.addExpSynonymCfg("s", "X", "s", "x", "s", "o", "s", "o");
                    return;
                case "expView":
                    config.addExpViewCfg("s", "X", "x", "s", "c");
                    return;
                case "expGrant":
                    config.addExpGrantCfg(
                            "s", "X", "g", "e", "o", "s", "SELECT", false, "s", "g", "s");
                    return;
                case "expFunction":
                    config.addExpFunctionCfg("X");
                    return;
                case "expProcedure":
                    config.addExpProcedureCfg("X");
                    return;
                case "expTrigger":
                    config.addExpTriggerCfg("X");
                    return;
                case "targetTable":
                    Table targetTable = new Table();
                    targetTable.setName("x");
                    targetTable.setOwner("s");
                    config.addTargetTableSchema(targetTable);
                    return;
                case "targetSerial":
                    Sequence sequence = new Sequence();
                    sequence.setName("x");
                    sequence.setOwner("s");
                    config.addTargetSerialSchema(sequence);
                    return;
                case "targetSynonym":
                    Synonym synonym = new Synonym();
                    synonym.setName("x");
                    synonym.setOwner("s");
                    config.addTargetSynonymSchema(synonym);
                    return;
                case "targetGrant":
                    Grant grant = new Grant();
                    grant.setName("x");
                    grant.setOwner("s");
                    config.addTargetGrantSchema(grant);
                    return;
                case "targetView":
                    View view = new View();
                    view.setName("x");
                    view.setOwner("s");
                    config.addTargetViewSchema(view);
                    return;
                case "targetProcedure":
                    PlcsqlProcedure procedure = new PlcsqlProcedure();
                    procedure.setName("x");
                    procedure.setOwner("s");
                    config.addTargetPlcsqlProcedureSchema(procedure);
                    return;
                default:
                    PlcsqlFunction function = new PlcsqlFunction();
                    function.setName("x");
                    function.setOwner("s");
                    config.addTargetPlcsqlFunctionSchema(function);
            }
        }
    }

    @Nested
    @DisplayName("what there is to export")
    class WhatThereIsToExport {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("an online source has something to export once any object is selected")
        @CsvSource({
            // Registered but nothing ticked.
            "nothing,      false",
            "unselected,   false",

            // Either half of a table counts on its own.
            "schemaOnly,   true",
            "dataOnly,     true",

            // So does another kind of object.
            "viewOnly,     true",
        })
        void onlineSource_countsEverySelectedObject(String selection, boolean expected) {
            assertThat(configWith(selection).hasObjects2Export()).isEqualTo(expected);
        }

        @Test
        @DisplayName("a file source counts its files instead, whatever is selected")
        void fileSource_countsItsFiles() {
            MigrationConfiguration sql = new MigrationConfiguration();
            sql.setSourceType(MigrationConfiguration.SOURCE_TYPE_SQL);

            assertThat(sql.hasObjects2Export()).isFalse();

            sql.addSQLFile("a.sql");

            assertThat(sql.hasObjects2Export()).isTrue();
        }

        @Test
        @DisplayName("foreign keys and indexes are counted only when a table selects them")
        void keysAndIndexes_areCountedWhenSelected() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = selectedTable("EMP", true, true);
            config.addExpEntryTableCfg(table);

            assertThat(config.hasFKExports()).isFalse();
            assertThat(config.hasIndexExports()).isFalse();

            table.addFKConfig("fk", "fk", true);
            table.addIndexConfig("ix", "ix", true);

            assertThat(config.hasFKExports()).isTrue();
            assertThat(config.hasIndexExports()).isTrue();
        }

        @Test
        @DisplayName("a row wide enough to fill the heap a commit at a time is an OOM risk")
        void aVeryWideRow_isAnOomRisk() {
            // The estimate is row size times commit count times export threads, against a fifth of
            // the heap. One widest-possible column clears that on any heap this runs on.
            assertThat(configWithTargetColumn(Types.VARCHAR, 1073741823).checkOOMRisk()).isTrue();
        }

        @Test
        @DisplayName("an ordinary row is not, and neither is having no target tables at all")
        void anOrdinaryRow_isNoRisk() {
            assertThat(new MigrationConfiguration().checkOOMRisk()).isFalse();
            assertThat(configWithTargetColumn(Types.INTEGER, 11).checkOOMRisk()).isFalse();
        }

        @Test
        @DisplayName("a column with no JDBC type counts as nothing, however wide it was declared")
        void columnWithoutAJdbcType_countsAsNothing() {
            // DEFECT: the row size is looked up by JDBC type id, so a column whose id was never
            // filled in contributes zero and the widest table can pass the check
            // - see CUBRIDDataTypeHelper.getDataTypeByteSize()
            assertThat(configWithTargetColumn(null, 1073741823).checkOOMRisk()).isFalse();
        }

        private MigrationConfiguration configWithTargetColumn(Integer jdbcType, int precision) {
            Table table = new Table();
            table.setName("wide");
            Column column = new Column(table);
            column.setName("c");
            column.setDataType("varchar");
            column.setPrecision(precision);
            column.setJdbcIDOfDataType(jdbcType);
            table.addColumn(column);

            MigrationConfiguration config = new MigrationConfiguration();
            config.addTargetTableSchema(table);
            return config;
        }

        private MigrationConfiguration configWith(String selection) {
            MigrationConfiguration config = new MigrationConfiguration();
            switch (selection) {
                case "nothing":
                    return config;
                case "unselected":
                    config.addExpEntryTableCfg(selectedTable("EMP", false, false));
                    return config;
                case "schemaOnly":
                    config.addExpEntryTableCfg(selectedTable("EMP", true, false));
                    return config;
                case "dataOnly":
                    config.addExpEntryTableCfg(selectedTable("EMP", false, true));
                    return config;
                default:
                    config.addExpViewCfg("hr", "V", "v", "hr", "c");
                    return config;
            }
        }
    }

    @Nested
    @DisplayName("target name conflicts")
    class TargetNameConflicts {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a target name is in use only while a table is actually migrating to it")
        @CsvSource(
                nullValues = "null",
                value = {
                    // EMP is selected, DEPT is registered but migrating neither half.
                    "emp,  true",
                    "EMP,  true",
                    "dept, false",

                    // Never registered, or no name at all.
                    "none, false",
                    "'',   false",
                    "' ',  false",
                    "null, false",
                })
        void nameInUse_meansATableIsMigratingToIt(String name, boolean expected) {
            assertThat(configWithTwoTables().isTargetNameInUse(name)).isEqualTo(expected);
        }

        @Test
        @DisplayName("how many tables aim at a name, counted without regard to case")
        void tablesAimingAtAName_areCounted() {
            MigrationConfiguration config = configWithTwoTables();

            assertThat(config.getTargetRefedCount("emp")).isEqualTo(1);
            assertThat(config.getTargetRefedCount("EMP")).isEqualTo(1);
            assertThat(config.getTargetRefedCount("none")).isZero();
        }

        @Test
        @DisplayName("and which ones they are")
        void tablesAimingAtAName_areListed() {
            assertThat(configWithTwoTables().getSourceTableConfigByTarget("emp"))
                    .extracting(SourceTableConfig::getName)
                    .containsExactly("EMP");
        }

        private MigrationConfiguration configWithTwoTables() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpEntryTableCfg(selectedTable("EMP", true, false));
            config.addExpEntryTableCfg(selectedTable("DEPT", false, false));
            return config;
        }
    }

    @Nested
    @DisplayName("setAll()")
    class SetAll {

        @Test
        @DisplayName("everything a table can migrate is selected at once, and cleared at once")
        void everything_isSelectedAndClearedAtOnce() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = selectedTable("EMP", false, false);
            table.addFKConfig("fk", "fk", false);
            table.addIndexConfig("ix", "ix", false);
            config.addExpEntryTableCfg(table);

            config.setAll(true);

            assertThat(table.isCreateNewTable()).isTrue();
            assertThat(table.isMigrateData()).isTrue();
            assertThat(table.isCreatePK()).isTrue();
            assertThat(table.isCreatePartition()).isTrue();
            assertThat(table.getColumnConfig("c1").isCreate()).isTrue();
            assertThat(table.getFKConfig("fk").isCreate()).isTrue();
            assertThat(table.getIndexConfig("ix").isCreate()).isTrue();

            config.setAll(false);

            assertThat(table.isCreateNewTable()).isFalse();
            assertThat(table.getColumnConfig("c1").isCreate()).isFalse();
            assertThat(table.getFKConfig("fk").isCreate()).isFalse();
        }

        @Test
        @DisplayName("naming a schema leaves the tables of every other schema alone")
        void namingASchema_leavesTheOthersAlone() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig hr = selectedTable("EMP", false, false);
            SourceEntryTableConfig sales = selectedTable("ORDER", false, false);
            sales.setOwner("sales");
            config.addExpEntryTableCfg(hr);
            config.addExpEntryTableCfg(sales);

            config.setAll("hr", true);

            assertThat(hr.isCreateNewTable()).isTrue();
            assertThat(sales.isCreateNewTable()).isFalse();
        }

        @Test
        @DisplayName("the schema is matched without regard to case")
        void schemaName_isMatchedWithoutRegardToCase() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig hr = selectedTable("EMP", true, true);
            config.addExpEntryTableCfg(hr);

            config.setAll("HR", false);

            assertThat(hr.isCreateNewTable()).isFalse();
        }
    }

    /** A table under schema hr with one column, switched off first so the flags can be set. */
    private static SourceEntryTableConfig selectedTable(
            String name, boolean createNewTable, boolean migrateData) {
        SourceEntryTableConfig table = new SourceEntryTableConfig();
        table.setCreateNewTable(false);
        table.setMigrateData(false);
        table.setName(name);
        table.setOwner("hr");
        table.setTarget(name.toLowerCase(Locale.ENGLISH));
        table.addColumnConfig("c1", "c1", false);
        table.setCreateNewTable(createNewTable);
        table.setMigrateData(migrateData);
        return table;
    }

    @Nested
    @DisplayName("source and target kind")
    class SourceAndTargetKind {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("every database CMT connects to counts as an online source")
        @CsvSource({"CUBRID", "MYSQL", "ORACLE", "MSSQL", "MARIADB", "INFORMIX", "TIBERO"})
        void everyDatabase_countsAsOnline(String name) {
            // A source database added without being listed here migrates as if it were a file.
            MigrationConfiguration config = new MigrationConfiguration();
            config.setSourceType(name);

            assertThat(config.sourceIsOnline()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0} -> online={1} csv={2} sql={3} xml={4}")
        @DisplayName("a file source is named by its format, and reports no database")
        @CsvSource({
            "SQL, false, false, true,  false",
            "CSV, false, true,  false, false",
            "XML, false, false, false, true",
        })
        void fileSource_isNamedByItsFormat(
                String name, boolean online, boolean csv, boolean sql, boolean xml) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setSourceType(name);

            assertThat(config.sourceIsOnline()).isEqualTo(online);
            assertThat(config.sourceIsCSV()).isEqualTo(csv);
            assertThat(config.sourceIsSQL()).isEqualTo(sql);
            assertThat(config.sourceIsXMLDump()).isEqualTo(xml);
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("a file source still names a database, because the reader needs one")
        @CsvSource({
            "SQL, CUBRID",
            "CSV, CUBRID",

            // A MySQL dump is read with MySQL's own rules.
            "XML, MYSQL",
        })
        void fileSource_stillNamesADatabase(String name, String expected) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setSourceType(name);

            assertThat(config.getSourceDBType().getName()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> file={1}")
        @DisplayName("every format CMT writes counts as a file target")
        @CsvSource({
            "csv,    true",
            "sql,    true",
            "xls,    true",
            "xlsx,   true",
            "unload, true",

            // Only a live database is not.
            "cubrid, false",
        })
        void everyWrittenFormat_countsAsAFileTarget(String name, boolean expected) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName(name);

            assertThat(config.targetIsFile()).isEqualTo(expected);
            assertThat(config.targetIsOnline()).isEqualTo(!expected);
        }

        @Test
        @DisplayName(
                "the target format is named without regard to case, and an unknown one is"
                        + " refused")
        void targetFormatName_isCaseInsensitiveAndChecked() {
            MigrationConfiguration config = new MigrationConfiguration();

            config.setDestTypeName("CUBRID");

            assertThat(config.getDestType()).isEqualTo(MigrationConfiguration.DEST_ONLINE);
            assertThatThrownBy(() -> config.setDestTypeName("nosuchformat"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("an unknown source name is refused as well")
        void unknownSourceName_isRefused() {
            MigrationConfiguration config = new MigrationConfiguration();

            assertThatThrownBy(() -> config.setSourceType("nosuchdatabase"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("settings derived from the target")
    class SettingsDerivedFromTheTarget {

        @ParameterizedTest(name = "[{index}] {0} rows into {1} -> {2}")
        @DisplayName("a spreadsheet target cuts the commit down to what one sheet holds")
        @CsvSource({
            // A sheet holds 65536 rows, a modern one 1048576.
            "100000,  xls,  65536",
            "2000000, xlsx, 1048576",

            // A file format with no row limit commits what it was asked to.
            "100000,  csv,  100000",
        })
        void spreadsheetTarget_cutsTheCommitToOneSheet(
                int commitCount, String target, int expected) {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName(target);
            config.setCommitCount(commitCount);

            assertThat(config.getCommitCount()).isEqualTo(expected);
        }

        @Test
        @DisplayName("a smaller file size asked for wins over the sheet limit")
        void smallerFileSize_winsOverTheSheetLimit() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("xls");
            config.setCommitCount(100000);

            config.setMaxCountPerFile(1000);

            assertThat(config.getMaxCountPerFile()).isEqualTo(1000);
            assertThat(config.getCommitCount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("a file size larger than the sheet holds is cut down when it is stored")
        void tooLargeFileSize_isCutWhenStored() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("xls");

            config.setMaxCountPerFile(999999999);

            assertThat(config.getMaxCountPerFile()).isEqualTo(65536);
        }

        @Test
        @DisplayName(
                "a CSV target keeps its charset in the CSV settings, where the writer reads"
                        + " it")
        void csvTarget_keepsItsCharsetInTheCsvSettings() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("csv");

            assertThat(config.getTargetCharSet()).isEqualTo("UTF-8");

            config.setTargetCharSet("EUC-KR");

            assertThat(config.getTargetCharSet()).isEqualTo("EUC-KR");
            assertThat(config.getCsvSettings().getCharset()).isEqualTo("EUC-KR");
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the LOB directory is stored ending in a separator, ready to append a name to")
        @CsvSource(
                nullValues = "null",
                value = {
                    "/a/b,    /a/b/",

                    // Already ends in one, either way round.
                    "/a/b/,   /a/b/",
                    "/a/b\\,  /a/b\\",

                    // Nothing to store is an empty path, never null.
                    "null,    ''",
                })
        void lobDirectory_endsInASeparator(String path, String expected) {
            MigrationConfiguration config = new MigrationConfiguration();

            config.setTargetLOBRootPath(path);

            assertThat(config.getTargetLOBRootPath()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("time zones")
    @ResourceLock(Resources.TIME_ZONE)
    class TimeZones {

        private TimeZone defaultZone;

        @BeforeEach
        void pinTimeZone() {
            defaultZone = TimeZone.getDefault();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        }

        @AfterEach
        void restoreTimeZone() {
            TimeZone.setDefault(defaultZone);
        }

        @Test
        @DisplayName("a file target with no zone of its own migrates in the JVM's zone")
        void fileTargetWithoutAZone_usesTheJvmZone() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("csv");

            assertThat(config.getTargetDatabaseTimeZone().getID()).isEqualTo("GMT+00:00");
        }

        @Test
        @DisplayName("a zone set on the file target is the one used")
        void fileTargetZone_isUsed() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("csv");

            config.setTargetFileTimeZone("GMT+09:00");

            assertThat(config.getTargetDatabaseTimeZone().getID()).isEqualTo("GMT+09:00");
        }

        @Test
        @DisplayName("a file source reads in its own zone, or the JVM's when it has none")
        void fileSource_readsInItsOwnZone() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setSourceType("CSV");

            assertThat(config.getSourceDatabaseTimeZone().getID()).isEqualTo("GMT+00:00");

            config.setSourceFileTimeZone("GMT+05:00");

            assertThat(config.getSourceDatabaseTimeZone().getID()).isEqualTo("GMT+05:00");
        }
    }

    @Nested
    @DisplayName("cleanNoUsedConfigForStart()")
    class CleanNoUsedConfigForStart {

        @Test
        @DisplayName("a table migrating neither its schema nor its data is dropped before the run")
        void unselectedTable_isDropped() {
            MigrationConfiguration config = configReadyToClean();

            config.cleanNoUsedConfigForStart();

            assertThat(config.getExpEntryTableCfg())
                    .extracting(SourceTableConfig::getName)
                    .containsExactly("KEEP");
        }

        @Test
        @DisplayName("and inside the tables that stay, whatever was not selected goes too")
        void unselectedPartsOfAKeptTable_areDropped() {
            MigrationConfiguration config = configReadyToClean();
            SourceEntryTableConfig kept = config.getExpEntryTableCfg("hr", "KEEP");

            config.cleanNoUsedConfigForStart();

            assertThat(kept.getColumnConfigList())
                    .extracting(SourceColumnConfig::getName)
                    .containsExactly("keep");
            assertThat(kept.getFKConfigList())
                    .extracting(SourceFKConfig::getName)
                    .containsExactly("fkKeep");
            assertThat(kept.getIndexConfigList())
                    .extracting(SourceIndexConfig::getName)
                    .containsExactly("ixKeep");
        }

        @Test
        @DisplayName("an unselected view or sequence is dropped from the target schema as well")
        void unselectedObject_isDroppedFromTheTargetSchema() {
            MigrationConfiguration config = configReadyToClean();

            config.cleanNoUsedConfigForStart();

            assertThat(config.getExpViewCfg()).hasSize(1);
            assertThat(config.getTargetViewSchema()).hasSize(1);
            assertThat(config.getExpSerialCfg()).hasSize(1);
            assertThat(config.getTargetSerialSchema()).hasSize(1);
        }

        @Test
        @DisplayName("a file source is left alone, so an unselected table survives into the run")
        void fileSource_isLeftAlone() {
            // DEFECT: the whole body sits behind a check for an online or XML dump source, so a
            // CSV or SQL migration starts with the configuration it was given
            // - see MigrationConfiguration.cleanNoUsedConfigForStart()
            MigrationConfiguration config = new MigrationConfiguration();
            config.setSourceType("CSV");
            config.addExpEntryTableCfg(selectedTable("DROP", false, false));

            config.cleanNoUsedConfigForStart();

            assertThat(config.getExpEntryTableCfg()).hasSize(1);
        }

        /** One table to keep and one to drop, plus a view and a sequence of each kind. */
        private MigrationConfiguration configReadyToClean() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpEntryTableCfg(tableWithParts("KEEP", true));
            config.addExpEntryTableCfg(tableWithParts("DROP", false));

            config.addExpViewCfg("hr", "V1", "v1", "hr", "c");
            config.addExpViewCfg("hr", "V2", "v2", "hr", "c");
            config.getExpViewCfg("hr", "V2").setCreate(false);
            config.addTargetViewSchema(targetView("v1"));
            config.addTargetViewSchema(targetView("v2"));

            config.addExpSerialCfg("hr", "S1", "s1");
            config.addExpSerialCfg("hr", "S2", "s2");
            config.getExpSerialCfg("hr", "S2").setCreate(false);
            config.addTargetSerialSchema(targetSequence("s1"));
            config.addTargetSerialSchema(targetSequence("s2"));
            return config;
        }

        /**
         * Built without the shared helper: setCreateNewTable selects every column when none is
         * selected yet, so the parts have to be added after the flag is set.
         */
        private SourceEntryTableConfig tableWithParts(String name, boolean selected) {
            SourceEntryTableConfig table = new SourceEntryTableConfig();
            table.setCreateNewTable(false);
            table.setMigrateData(false);
            table.setName(name);
            table.setOwner("hr");
            table.setTarget(name.toLowerCase(Locale.ENGLISH));
            table.setCreateNewTable(selected);
            table.addColumnConfig("keep", "keep", true);
            table.addColumnConfig("drop", "drop", false);
            table.addFKConfig("fkKeep", "fkKeep", true);
            table.addFKConfig("fkDrop", "fkDrop", false);
            table.addIndexConfig("ixKeep", "ixKeep", true);
            table.addIndexConfig("ixDrop", "ixDrop", false);
            return table;
        }

        private View targetView(String name) {
            View view = new View();
            view.setName(name);
            view.setOwner("hr");
            return view;
        }

        private Sequence targetSequence(String name) {
            Sequence sequence = new Sequence();
            sequence.setName(name);
            sequence.setOwner("hr");
            return sequence;
        }
    }

    @Nested
    @DisplayName("schema selection")
    class SchemaSelectionGroup {

        @Test
        @DisplayName("the schemas being migrated are the owners of the tables, views and sequences")
        void migratedSchemas_areTheOwnersOfTheSelectedObjects() {
            assertThat(configWithTwoSchemas().getExpSchemaNames()).containsExactly("hr", "sales");
        }

        @Test
        @DisplayName("removing a schema removes its tables, views and sequences together")
        void removingASchema_removesEverythingUnderIt() {
            MigrationConfiguration config = configWithTwoSchemas();

            config.removeExpSchema("sales");

            assertThat(config.getExpSchemaNames()).containsExactly("hr");
            assertThat(config.getExpEntryTableCfg()).hasSize(1);
        }

        @Test
        @DisplayName("no schema named -> nothing is removed")
        void noSchemaNamed_removesNothing() {
            MigrationConfiguration config = configWithTwoSchemas();

            config.removeExpSchema(null);

            assertThat(config.getExpEntryTableCfg()).hasSize(2);
        }

        @Test
        @DisplayName("renaming a schema moves its tables but leaves its views and sequences behind")
        void renamingASchema_movesItsTablesOnly() {
            MigrationConfiguration config = configWithTwoSchemas();

            config.renameExpSchema("hr", "HR2");

            // DEFECT: only the tables are walked, so the views and sequences keep the old owner and
            // the migration then reports both schema names
            // - see MigrationConfiguration.renameExpSchema()
            assertThat(config.getExpSchemaNames()).containsExactly("HR2", "sales", "hr");
        }

        @Test
        @DisplayName("the schema being renamed is matched by its exact name")
        void renamedSchema_isMatchedExactly() {
            MigrationConfiguration config = configWithTwoSchemas();

            config.renameExpSchema("HR", "HR2");

            assertThat(config.getExpSchemaNames()).containsExactly("hr", "sales");
        }

        @Test
        @DisplayName("a selected source schema is trimmed and kept once")
        void selectedSourceSchema_isTrimmedAndKeptOnce() {
            MigrationConfiguration config = new MigrationConfiguration();

            config.addSelectedSrcSchema(" hr ");
            config.addSelectedSrcSchema("hr");
            config.addSelectedSrcSchema("  ");
            config.addSelectedSrcSchema(null);

            assertThat(config.getSelectedSrcSchemas()).containsExactly("hr");
        }

        @Test
        @DisplayName("removing one trims too, but matches the name exactly")
        void removingASelectedSchema_trimsButMatchesExactly() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addSelectedSrcSchema("hr");

            config.removeSelectedSrcSchema("HR");

            assertThat(config.getSelectedSrcSchemas()).containsExactly("hr");

            config.removeSelectedSrcSchema(" hr ");

            assertThat(config.getSelectedSrcSchemas()).isEmpty();
        }

        private MigrationConfiguration configWithTwoSchemas() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpEntryTableCfg(selectedTable("EMP", true, false));
            SourceEntryTableConfig sales = selectedTable("ORDER", true, false);
            sales.setOwner("sales");
            config.addExpEntryTableCfg(sales);
            config.addExpViewCfg("hr", "V", "v", "hr", "c");
            return config;
        }
    }

    @Nested
    @DisplayName("renaming a target")
    class RenamingATarget {

        @Test
        @DisplayName("a table and the target schema it built are renamed together")
        void tableAndItsTargetSchema_areRenamedTogether() {
            MigrationConfiguration config = configWithOneTable();
            SourceEntryTableConfig table = config.getExpEntryTableCfg("hr", "EMP");

            config.changeTarget(table, "emp2");

            assertThat(table.getTarget()).isEqualTo("emp2");
            assertThat(config.getTargetTableSchema("emp2")).isNotNull();
        }

        @Test
        @DisplayName("with no schema built yet only the setting moves")
        void withoutATargetSchema_onlyTheSettingMoves() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = selectedTable("DEPT", true, false);
            config.addExpEntryTableCfg(table);

            config.changeTarget(table, "dept2");

            assertThat(table.getTarget()).isEqualTo("dept2");
        }

        @Test
        @DisplayName("a column and its target column are renamed together")
        void columnAndItsTargetColumn_areRenamedTogether() {
            MigrationConfiguration config = configWithOneTable();
            SourceColumnConfig column =
                    config.getExpEntryTableCfg("hr", "EMP").getColumnConfig("C1");

            config.changeTarget(column, "c2");

            assertThat(column.getTarget()).isEqualTo("c2");
            assertThat(config.getTargetColumnSchema("emp", "c2")).isNotNull();
        }

        @Test
        @DisplayName("renaming a column of a table that was never built is refused")
        void columnOfAnUnbuiltTable_isRefused() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = selectedTable("EMP", true, false);
            config.addExpEntryTableCfg(table);

            assertThatThrownBy(() -> config.changeTarget(table.getColumnConfig("c1"), "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("emp");
        }

        /**
         * A table selected for migration, its target schema, and the source catalog it came from.
         */
        private MigrationConfiguration configWithOneTable() {
            MigrationConfiguration config = new MigrationConfiguration();
            SourceEntryTableConfig table = new SourceEntryTableConfig();
            table.setCreateNewTable(false);
            table.setMigrateData(false);
            table.setName("EMP");
            table.setOwner("hr");
            table.setTarget("emp");
            table.addColumnConfig("C1", "c1", true);
            table.setCreateNewTable(true);
            config.addExpEntryTableCfg(table);

            Table targetTable = new Table();
            targetTable.setName("emp");
            targetTable.setOwner("hr");
            targetTable.addColumn(varchar(targetTable, "c1"));
            config.addTargetTableSchema(targetTable);

            // The guard refuses more targets once the catalog is attached, so it goes on last.
            Catalog catalog = new Catalog();
            catalog.setName("db");
            Schema schema = new Schema();
            schema.setName("hr");
            Table sourceTable = new Table();
            sourceTable.setName("EMP");
            sourceTable.setOwner("hr");
            sourceTable.addColumn(varchar(sourceTable, "C1"));
            schema.addTable(sourceTable);
            catalog.addSchema(schema);
            config.setSrcCatalog(catalog, false);
            return config;
        }

        private Column varchar(Table table, String name) {
            Column column = new Column(table);
            column.setName(name);
            column.setDataType("varchar");
            column.setPrecision(10);
            column.setJdbcIDOfDataType(Types.VARCHAR);
            return column;
        }
    }

    @Nested
    @DisplayName("output file paths")
    class OutputFilePaths {

        @Test
        @DisplayName("every kind of output file is named from the directory, prefix and schema")
        void outputFiles_areNamedFromTheDirectoryPrefixAndSchema() {
            MigrationConfiguration config = configWritingToFiles();

            assertThat(config.getTargetFilePrefix()).isEqualTo("pre");
            assertThat(config.getFileRepositroyPath()).isEqualTo("/out");
            assertThat(config.getTargetSchemaFileName("hr")).isEqualTo("/out/pre/hr_schema");
            assertThat(config.getTargetDataFileName("hr")).isEqualTo("/out/pre/hr_objects.sql");
            assertThat(config.getTargetIndexFileName("hr")).isEqualTo("/out/pre/hr_indexes");
        }

        @Test
        @DisplayName("moving the output directory rewrites the names, with a separator too many")
        void movingTheOutputDirectory_rewritesTheNames() {
            MigrationConfiguration config = configWritingToFiles();

            config.changeTargetFilePath("/out2/");

            // DEFECT: the new directory is padded to end with a separator but the old one is cut
            // off by its raw length, so the remainder still starts with one
            // - see MigrationConfiguration.changeTargetFilePath()
            assertThat(config.getTargetSchemaFileName("hr")).isEqualTo("/out2//pre/hr_schema");
        }

        @Test
        @DisplayName("a data file is named after its schema and the object inside it")
        void dataFile_isNamedAfterItsSchemaAndObject() {
            MigrationConfiguration config = configWritingToFiles();

            assertThat(config.buildDataFileFullPath("hr", "emp"))
                    .isEqualTo("/out/hr/pre_hr_emp.sql");
            assertThat(config.getFullTargetFilePrefix()).isEqualTo("pre_");
            assertThat(config.getDefaultTargetSchemaFileExtName()).isEqualTo(".sql");
        }

        private MigrationConfiguration configWritingToFiles() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.setDestTypeName("sql");
            Catalog catalog = new Catalog();
            catalog.setName("db");
            Schema schema = new Schema();
            schema.setName("hr");
            catalog.addSchema(schema);
            config.setSrcCatalog(catalog, false);
            config.setAddUserSchema(true);
            config.setExp2FileOuput("pre", "/out", "UTF-8");
            return config;
        }
    }

    @Nested
    @DisplayName("CSV files")
    class CsvFiles {

        @Test
        @DisplayName("adding a file reads it, so its columns are ready to map")
        void addingAFile_readsItsColumns(@TempDir Path directory) throws Exception {
            MigrationConfiguration config = new MigrationConfiguration();
            String file = writeCsv(directory);

            config.addCSVFile(file, targetSchema());

            assertThat(config.getCSVConfigs()).hasSize(1);
            assertThat(config.getCSVConfigByFile(file)).isNotNull();
            assertThat(config.getCSVConfigs().get(0).getColumnConfigs()).hasSize(2);
        }

        @Test
        @DisplayName("the same file twice is still one file")
        void theSameFileTwice_isStillOneFile(@TempDir Path directory) throws Exception {
            MigrationConfiguration config = new MigrationConfiguration();
            String file = writeCsv(directory);

            config.addCSVFile(file, targetSchema());
            config.addCSVFile(file, targetSchema());

            assertThat(config.getCSVConfigs()).hasSize(1);
        }

        @Test
        @DisplayName("removing a file that is not there leaves the rest alone")
        void removingAFileThatIsNotThere_leavesTheRestAlone(@TempDir Path directory)
                throws Exception {
            MigrationConfiguration config = new MigrationConfiguration();
            String file = writeCsv(directory);
            config.addCSVFile(file, targetSchema());

            config.removeCSVFile(directory.resolve("other.csv").toString());

            assertThat(config.getCSVConfigs()).hasSize(1);

            config.removeCSVFile(file);

            assertThat(config.getCSVConfigs()).isEmpty();
        }

        private String writeCsv(Path directory) throws Exception {
            Path file = directory.resolve("data.csv");
            Files.write(file, "ID,Name\n1,a\n".getBytes(StandardCharsets.UTF_8));
            return file.toString();
        }

        private Schema targetSchema() {
            Schema schema = new Schema();
            schema.setName("hr");
            return schema;
        }
    }
}
