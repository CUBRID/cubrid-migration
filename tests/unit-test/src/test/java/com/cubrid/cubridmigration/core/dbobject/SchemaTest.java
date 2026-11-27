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

@DisplayName("Schema")
class SchemaTest {

    private static Schema schema(String name) {
        Schema schema = new Schema();
        schema.setName(name);
        return schema;
    }

    private static View view(String name) {
        View view = new View();
        view.setName(name);
        return view;
    }

    private static Grant grant() {
        return new Grant("old", "grantor", "grantee", "t", "co", "SELECT", false, "ddl");
    }

    private static Synonym synonym(String name) {
        return new Synonym(name, "old", false, "obj", "oo", null, null);
    }

    @Nested
    @DisplayName("addTable()")
    class AddTable {

        @Test
        @DisplayName("a table is added and owned")
        void table_isAddedAndOwned() {
            Schema schema = schema("s");
            Table table = TestTableFactory.createTable("t");

            schema.addTable(table);

            assertThat(schema.getTables()).containsExactly(table);
            assertThat(table.getSchema()).isSameAs(schema);
        }

        @Test
        @DisplayName("the same table added twice is listed twice")
        void sameTableTwice_isListedTwice() {
            Schema schema = schema("s");
            Table table = TestTableFactory.createTable("t");

            schema.addTable(table);
            schema.addTable(table);

            assertThat(schema.getTables()).containsExactly(table, table);
        }

        @Test
        @DisplayName("a table of another schema is taken over but stays listed there")
        void tableOfAnotherSchema_isTakenOver() {
            Schema first = schema("s1");
            Schema second = schema("s2");
            Table table = TestTableFactory.createTable("t");
            first.addTable(table);

            second.addTable(table);

            assertThat(table.getSchema()).isSameAs(second);
            assertThat(first.getTables()).containsExactly(table);
        }

        @Test
        @DisplayName("a missing table list is created first")
        void missingList_isCreated() {
            Schema schema = schema("s");
            schema.setTables(null);

            schema.addTable(TestTableFactory.createTable("t"));

            assertThat(schema.getTables()).hasSize(1);
        }

        @Test
        @DisplayName("null is ignored")
        void nullTable_isIgnored() {
            Schema schema = schema("s");

            schema.addTable(null);

            assertThat(schema.getTables()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addView()")
    class AddView {

        @Test
        @DisplayName("a view is added and owned, twice if added twice")
        void view_isAddedAndOwned() {
            Schema schema = schema("s");
            View view = view("v");

            schema.addView(view);
            schema.addView(view);

            assertThat(schema.getViews()).containsExactly(view, view);
            assertThat(view.getSchema()).isSameAs(schema);
        }

        @Test
        @DisplayName("a missing view list is created first")
        void missingList_isCreated() {
            Schema schema = schema("s");
            schema.setViews(null);

            schema.addView(view("v"));

            assertThat(schema.getViews()).hasSize(1);
        }

        @Test
        @DisplayName("null is stored before it fails")
        void nullView_isStoredBeforeItFails() {
            // DEFECT: unlike addTable(), null is not checked, so it goes into the list and only
            // then fails on setSchema(), leaving the null behind
            // - see Schema.addView()
            Schema schema = schema("s");

            assertThatThrownBy(() -> schema.addView(null)).isInstanceOf(NullPointerException.class);
            assertThat(schema.getViews()).containsExactly((View) null);
        }
    }

    @Nested
    @DisplayName("getTableByName()")
    class GetTableByName {

        @Test
        @DisplayName("the name matches with case")
        void name_matchesWithCase() {
            Schema schema = schema("s");
            Table table = TestTableFactory.createTable("T1");
            schema.addTable(table);

            assertThat(schema.getTableByName("T1")).isSameAs(table);
            assertThat(schema.getTableByName("t1")).isNull();
        }

        @Test
        @DisplayName("an unknown or null name gives null")
        void unknownOrNullName_returnsNull() {
            Schema schema = schema("s");
            schema.addTable(TestTableFactory.createTable("T1"));

            assertThat(schema.getTableByName("zz")).isNull();
            assertThat(schema.getTableByName(null)).isNull();
        }

        @Test
        @DisplayName("a table without a name fails the lookup")
        void unnamedTable_throwsNullPointerException() {
            Schema schema = schema("s");
            schema.addTable(new Table());

            assertThatThrownBy(() -> schema.getTableByName("t"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getViewByName()")
    class GetViewByName {

        @Test
        @DisplayName("the name matches with case, and an unknown one gives null")
        void name_matchesWithCase() {
            Schema schema = schema("s");
            View view = view("V1");
            schema.addView(view);

            assertThat(schema.getViewByName("V1")).isSameAs(view);
            assertThat(schema.getViewByName("v1")).isNull();
        }

        @Test
        @DisplayName("a null name fails once there is a view, and gives null otherwise")
        void nullName_failsOnceThereIsAView() {
            Schema schema = schema("s");
            assertThat(schema.getViewByName(null)).isNull();

            schema.addView(view("V1"));

            assertThatThrownBy(() -> schema.getViewByName(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getSequenceByName()")
    class GetSequenceByName {

        @Test
        @DisplayName("the name matches with case, and an unknown one gives null")
        void name_matchesWithCase() {
            Schema schema = schema("s");
            Sequence sequence = TestTableFactory.createSequence("SQ", "o");
            schema.addSequence(sequence);

            assertThat(schema.getSequenceByName("SQ")).isSameAs(sequence);
            assertThat(schema.getSequenceByName("sq")).isNull();
        }

        @Test
        @DisplayName("a null name fails once there is a sequence, and gives null otherwise")
        void nullName_failsOnceThereIsASequence() {
            Schema schema = schema("s");
            assertThat(schema.getSequenceByName(null)).isNull();

            schema.addSequence(TestTableFactory.createSequence("SQ", "o"));

            assertThatThrownBy(() -> schema.getSequenceByName(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("addGrant()")
    class AddGrant {

        @Test
        @DisplayName("a grant is added once and takes the schema name as its owner")
        void grant_isAddedOnceAndOwned() {
            Schema schema = schema("owner1");
            Grant grant = grant();

            schema.addGrant(grant);
            schema.addGrant(grant);

            assertThat(schema.getGrantList()).containsExactly(grant);
            assertThat(grant.getOwner()).isEqualTo("owner1");
        }

        @Test
        @DisplayName("another grant with the same content is added too")
        void equalContent_isAddedToo() {
            Schema schema = schema("owner1");

            schema.addGrant(grant());
            schema.addGrant(grant());

            assertThat(schema.getGrantList()).hasSize(2);
        }

        @Test
        @DisplayName("a missing list is created first, and null is ignored")
        void missingList_isCreatedAndNullIgnored() {
            Schema schema = schema("owner1");
            schema.setGrantList(null);

            schema.addGrant(null);
            schema.addGrant(grant());

            assertThat(schema.getGrantList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("addSequence()")
    class AddSequence {

        @Test
        @DisplayName("a sequence is added once and takes the schema name as its owner")
        void sequence_isAddedOnceAndOwned() {
            Schema schema = schema("owner1");
            Sequence sequence = TestTableFactory.createSequence("SQ", "old");

            schema.addSequence(sequence);
            schema.addSequence(sequence);

            assertThat(schema.getSequenceList()).containsExactly(sequence);
            assertThat(sequence.getOwner()).isEqualTo("owner1");
        }

        @Test
        @DisplayName("another sequence with the same content is added too")
        void equalContent_isAddedToo() {
            Schema schema = schema("owner1");

            schema.addSequence(TestTableFactory.createSequence("SQ", "old"));
            schema.addSequence(TestTableFactory.createSequence("SQ", "old"));

            assertThat(schema.getSequenceList()).hasSize(2);
        }

        @Test
        @DisplayName("a missing list is created first, and null is ignored")
        void missingList_isCreatedAndNullIgnored() {
            Schema schema = schema("owner1");
            schema.setSequenceList(null);

            schema.addSequence(null);
            schema.addSequence(TestTableFactory.createSequence("SQ", "old"));

            assertThat(schema.getSequenceList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("addSynonym()")
    class AddSynonym {

        @Test
        @DisplayName("a synonym is added once and takes the schema name as its owner")
        void synonym_isAddedOnceAndOwned() {
            Schema schema = schema("owner1");
            Synonym synonym = synonym("syn");

            schema.addSynonym(synonym);
            schema.addSynonym(synonym);

            assertThat(schema.getSynonymList()).containsExactly(synonym);
            assertThat(synonym.getOwner()).isEqualTo("owner1");
        }

        @Test
        @DisplayName("another synonym with the same content is added too")
        void equalContent_isAddedToo() {
            Schema schema = schema("owner1");

            schema.addSynonym(synonym("syn"));
            schema.addSynonym(synonym("syn"));

            assertThat(schema.getSynonymList()).hasSize(2);
        }

        @Test
        @DisplayName("a missing list is created first, and null is ignored")
        void missingList_isCreatedAndNullIgnored() {
            Schema schema = schema("owner1");
            schema.setSynonymList(null);

            schema.addSynonym(null);
            schema.addSynonym(synonym("syn"));

            assertThat(schema.getSynonymList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("schemas with the same catalog identity and name hash alike")
        void sameCatalogAndName_hashAlike() {
            Schema first = schema("testSchema");
            Schema second = schema("testSchema");
            assertThat(first.hashCode()).isEqualTo(second.hashCode());

            Catalog catalog = new Catalog();
            catalog.setName("catalog1");
            first.setCatalog(catalog);
            second.setCatalog(catalog);

            assertThat(first.hashCode()).isEqualTo(second.hashCode());
            second.setName("testSchema2");
            assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
        }

        @Test
        @DisplayName("the target name and the objects leave the hash alone")
        void targetNameAndObjects_leaveTheHashAlone() {
            Schema schema = schema("s");
            int hash = schema.hashCode();

            schema.setTargetSchemaName("t");
            schema.addTable(TestTableFactory.createTable("x"));

            assertThat(schema.hashCode()).isEqualTo(hash);
        }
    }

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("the catalog and the name decide equality")
        void catalogAndName_decideEquality() {
            Schema first = new Schema();
            Schema second = new Schema();
            assertThat(first)
                    .isEqualTo(first)
                    .isEqualTo(second)
                    .isNotEqualTo(12)
                    .isNotEqualTo(null);

            first.setName("testSchema");
            assertThat(second).isNotEqualTo(first);
            second.setName("testSchema");
            assertThat(first).isEqualTo(second);

            Catalog catalog = new Catalog();
            first.setCatalog(catalog);
            assertThat(second).isNotEqualTo(first);
            second.setCatalog(catalog);
            second.setName("testSchema2");
            assertThat(first).isNotEqualTo(second);

            catalog.setName("catalog1");
            second.setCatalog(new Catalog());
            second.setName("testSchema");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("two catalog objects with the same host, name and port count as the same")
        void equalCatalogs_countAsTheSame() {
            Catalog catalog = new Catalog();
            catalog.setName("c");
            Catalog sameCatalog = new Catalog();
            sameCatalog.setName("c");
            Schema first = schema("s");
            first.setCatalog(catalog);
            Schema second = schema("s");
            second.setCatalog(sameCatalog);

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("the name compares with case, and the target name and objects are ignored")
        void nameComparesWithCase_otherFieldsIgnored() {
            Schema first = schema("s");
            Schema second = schema("s");
            first.setTargetSchemaName("t1");
            second.setTargetSchemaName("t2");
            first.addTable(TestTableFactory.createTable("x"));

            assertThat(first).isEqualTo(second);
            assertThat(schema("S")).isNotEqualTo(schema("s"));
        }
    }

    @Nested
    @DisplayName("getGrant()")
    class GetGrant {

        @Test
        @DisplayName("the owner and the name, built or set, match ignoring case")
        void ownerAndName_matchIgnoringCase() {
            Schema schema = schema("own");
            Grant grant = grant();
            schema.addGrant(grant);

            assertThat(schema.getGrant("OWN", "select on co.t to GRANTEE")).isSameAs(grant);
            assertThat(schema.getGrant("own", "zz")).isNull();
        }

        @Test
        @DisplayName("a grant without an owner fails the lookup")
        void grantWithoutOwner_throwsNullPointerException() {
            Schema schema = schema("own");
            schema.getGrantList()
                    .add(new Grant(null, "grantor", "grantee", "t", "co", "SELECT", false, "ddl"));

            assertThatThrownBy(() -> schema.getGrant("own", "x"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getSynonym()")
    class GetSynonym {

        @Test
        @DisplayName("the owner and the name match ignoring case")
        void ownerAndName_matchIgnoringCase() {
            Schema schema = schema("own");
            Synonym synonym = synonym("SYN");
            schema.addSynonym(synonym);

            assertThat(schema.getSynonym("OWN", "syn")).isSameAs(synonym);
            assertThat(schema.getSynonym("own", "zz")).isNull();
        }

        @Test
        @DisplayName("a synonym without a name fails the lookup")
        void synonymWithoutName_throwsNullPointerException() {
            Schema schema = schema("own");
            schema.getSynonymList().add(new Synonym(null, "own", false, "obj", "oo", null, null));

            assertThatThrownBy(() -> schema.getSynonym("own", "x"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getFunction()")
    class GetFunction {

        @Test
        @DisplayName("the name matches with case, and an unknown one gives null")
        void name_matchesWithCase() {
            Schema schema = schema("own");
            Function function = new Function();
            function.setName("F");
            schema.getFunctions().add(function);

            assertThat(schema.getFunction("F")).isSameAs(function);
            assertThat(schema.getFunction("f")).isNull();
        }

        @Test
        @DisplayName("a function without a name fails the lookup")
        void unnamedFunction_throwsNullPointerException() {
            Schema schema = schema("own");
            schema.getFunctions().add(new Function());

            assertThatThrownBy(() -> schema.getFunction("f"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getProcedure()")
    class GetProcedure {

        @Test
        @DisplayName("the name matches with case, and an unknown one gives null")
        void name_matchesWithCase() {
            Schema schema = schema("own");
            Procedure procedure = new Procedure();
            procedure.setName("P");
            schema.getProcedures().add(procedure);

            assertThat(schema.getProcedure("P")).isSameAs(procedure);
            assertThat(schema.getProcedure("p")).isNull();
        }

        @Test
        @DisplayName("a procedure without a name fails the lookup")
        void unnamedProcedure_throwsNullPointerException() {
            Schema schema = schema("own");
            schema.getProcedures().add(new Procedure());

            assertThatThrownBy(() -> schema.getProcedure("p"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getTrigger()")
    class GetTrigger {

        @Test
        @DisplayName("the name matches with case, and an unknown one gives null")
        void name_matchesWithCase() {
            Schema schema = schema("own");
            Trigger trigger = new Trigger();
            trigger.setName("T");
            schema.getTriggers().add(trigger);

            assertThat(schema.getTrigger("T")).isSameAs(trigger);
            assertThat(schema.getTrigger("t")).isNull();
        }

        @Test
        @DisplayName("a trigger without a name fails the lookup")
        void unnamedTrigger_throwsNullPointerException() {
            Schema schema = schema("own");
            schema.getTriggers().add(new Trigger());

            assertThatThrownBy(() -> schema.getTrigger("t"))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
