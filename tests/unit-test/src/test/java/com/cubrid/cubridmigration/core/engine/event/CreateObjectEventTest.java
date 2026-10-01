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
package com.cubrid.cubridmigration.core.engine.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.DBObject;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.View;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@DisplayName("CreateObjectEvent")
class CreateObjectEventTest {

    private static Table table(String owner, String name) {
        Table table = new Table();
        table.setOwner(owner);
        table.setName(name);
        return table;
    }

    private static View view(String name, String alterDDL) {
        View view = new View();
        view.setName(name);
        view.setAlterDDL(alterDDL);
        return view;
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @ParameterizedTest(name = "[{index}] {0} -> {2}")
        @DisplayName("a created object reads as Create, its type and its name, then successfully")
        @MethodSource(
                "com.cubrid.cubridmigration.core.engine.event.CreateObjectEventTest#createdObjects")
        void createdObject_readsAsCreated(String label, DBObject object, String expected) {
            assertThat(new CreateObjectEvent(object)).hasToString(expected);
        }

        @Test
        @DisplayName("a failure reads as unsuccessfully with the error message as the detail")
        void failure_readsTheErrorMessage() {
            CreateObjectEvent event =
                    new CreateObjectEvent(table(null, "test"), new RuntimeException("error"));

            assertThat(event).hasToString("Create table[test] unsuccessfully. Detail:error");
        }

        @Test
        @DisplayName("an error without a message reads as Detail:null")
        void errorWithoutMessage_readsAsDetailNull() {
            CreateObjectEvent event =
                    new CreateObjectEvent(table(null, "test"), new RuntimeException());

            assertThat(event).hasToString("Create table[test] unsuccessfully. Detail:null");
        }

        @Test
        @DisplayName(
                "a procedure or function whose rights changed gets a warning before the result")
        void changedRights_addAWarningLine() {
            Procedure procedure = new Procedure();
            procedure.setName("p2");
            procedure.setAuthidChanged(true);
            Function function = new Function();
            function.setName("f2");
            function.setAuthidChanged(true);

            assertThat(new CreateObjectEvent(procedure))
                    .hasToString(
                            "Create procedure[p2]"
                                    + System.lineSeparator()
                                    + "[PROC_WARNING] procedure: p2 Change rights"
                                    + " (CALLER RIGHTS -> OWNER RIGHTS) successfully.");
            assertThat(new CreateObjectEvent(function, new RuntimeException("boom")))
                    .hasToString(
                            "Create function[f2]"
                                    + System.lineSeparator()
                                    + "[FUNC_WARNING] function: f2 Change rights (CALLER RIGHTS ->"
                                    + " OWNER RIGHTS) unsuccessfully. Detail:boom");
        }

        @Test
        @DisplayName("a view with an alter DDL, even an empty one, reads as Alter")
        void viewWithAlterDdl_readsAsAlter() {
            assertThat(new CreateObjectEvent(view("v2", "alter view v2")))
                    .hasToString("Alter view[v2] successfully.");
            assertThat(new CreateObjectEvent(view("v3", "")))
                    .hasToString("Alter view[v3] successfully.");
        }

        @Test
        @DisplayName("a failed alter of a view reads with no space after Alter")
        void failedAlter_readsWithoutASpace() {
            // DEFECT: the failure path joins "Alter" to the rest without the space the success
            // path has, so the message reads "Alterview[...]"
            // - see CreateObjectEvent.toString()
            CreateObjectEvent event =
                    new CreateObjectEvent(
                            view("v2", "alter view v2"), new RuntimeException("boom"));

            assertThat(event).hasToString("Alterview[v2] unsuccessfully. Detail:boom");
        }

        @Test
        @DisplayName(
                "the wording follows the error, so a failure event without one reads as a success")
        void failureWithoutError_readsAsASuccess() {
            assertThat(new CreateObjectEvent(table(null, "test"), null))
                    .hasToString("Create table[test] successfully.");
        }
    }

    @Nested
    @DisplayName("getLevel()")
    class GetLevel {

        @Test
        @DisplayName(
                "a success event is level 2 and a failure event level 1, with or without an error")
        void failure_isLevelOne() {
            Table table = table(null, "test");

            assertThat(new CreateObjectEvent(table).getLevel()).isEqualTo(2);
            assertThat(new CreateObjectEvent(table, new RuntimeException("error")).getLevel())
                    .isEqualTo(1);
            assertThat(new CreateObjectEvent(table, null).getLevel()).isEqualTo(1);
        }
    }

    static Stream<Arguments> createdObjects() {
        Schema schema = new Schema();
        schema.setName("s1");
        Table table = table(null, "t2");
        PK pk = new PK(table);
        pk.setName("pk_t2");
        FK fk = new FK(table);
        fk.setName("fk_t2");
        Index index = new Index(table);
        index.setName("idx_t2");
        Procedure procedure = new Procedure();
        procedure.setName("testprocedure");
        PlcsqlProcedure plcsqlProcedure = new PlcsqlProcedure();
        plcsqlProcedure.setName("pp");
        Function function = new Function();
        function.setName("testfunction");
        PlcsqlFunction plcsqlFunction = new PlcsqlFunction();
        plcsqlFunction.setName("pf");
        Trigger trigger = new Trigger();
        trigger.setName("testtrigger");
        Sequence sequence = new Sequence();
        sequence.setName("testsequence");
        Synonym synonym = new Synonym();
        synonym.setName("sy1");
        synonym.setOwner("o2");
        Grant grant = new Grant();
        grant.setName("g1");
        return Stream.of(
                Arguments.of("schema", schema, "Create Schema[s1] successfully."),
                Arguments.of(
                        "table with an owner",
                        table("o1", "t1"),
                        "Create table[o1.t1] successfully."),
                Arguments.of("table", table, "Create table[t2] successfully."),
                Arguments.of(
                        "primary key, by its table", pk, "Create primary key[t2] successfully."),
                Arguments.of("foreign key", fk, "Create foreign key[fk_t2] successfully."),
                Arguments.of("index", index, "Create index[idx_t2] successfully."),
                Arguments.of(
                        "procedure", procedure, "Create procedure[testprocedure] successfully."),
                Arguments.of(
                        "PL/CSQL procedure", plcsqlProcedure, "Create procedure[pp] successfully."),
                Arguments.of("function", function, "Create function[testfunction] successfully."),
                Arguments.of(
                        "PL/CSQL function", plcsqlFunction, "Create function[pf] successfully."),
                Arguments.of("trigger", trigger, "Create trigger[testtrigger] successfully."),
                Arguments.of("view", view("v1", null), "Create view[v1] successfully."),
                Arguments.of("sequence", sequence, "Create sequence[testsequence] successfully."),
                Arguments.of(
                        "synonym with its owner", synonym, "Create synonym[o2.sy1] successfully."),
                Arguments.of("grant", grant, "Create grant[g1] successfully."));
    }
}
