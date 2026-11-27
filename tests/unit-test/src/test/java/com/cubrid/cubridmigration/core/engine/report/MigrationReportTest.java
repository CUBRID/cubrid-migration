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
package com.cubrid.cubridmigration.core.engine.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.cubrid.cubridmigration.core.dbobject.DBObject;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ExportCSVEvent;
import com.cubrid.cubridmigration.core.engine.event.ExportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportCSVEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.event.ImportSQLsEvent;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@DisplayName("MigrationReport")
class MigrationReportTest {

    private static Table table(String owner, String name) {
        Table table = new Table();
        table.setOwner(owner);
        table.setName(name);
        return table;
    }

    private static View view(String owner, String name) {
        View view = new View();
        view.setOwner(owner);
        view.setName(name);
        return view;
    }

    private static SourceTableConfig tableConfig(String owner, String name) {
        SourceTableConfig config = new SourceTableConfig();
        config.setOwner(owner);
        config.setName(name);
        config.setTarget(name);
        return config;
    }

    private static SourceCSVConfig csv(String name) {
        SourceCSVConfig csv = new SourceCSVConfig();
        csv.setName(name);
        return csv;
    }

    private static DataFileImportResult dataFile(String name) {
        DataFileImportResult result = new DataFileImportResult();
        result.setFileName(name);
        return result;
    }

    private static DataFileImportResult dataFile(String name, long exported, long imported) {
        DataFileImportResult result = dataFile(name);
        result.setExportCount(exported);
        result.setImportCount(imported);
        return result;
    }

    private static DBObjMigrationResult objectResult(String name, String type, String owner) {
        DBObjMigrationResult result = new DBObjMigrationResult();
        result.setObjName(name);
        result.setObjType(type);
        result.setObjOwner(owner);
        return result;
    }

    private static RecordMigrationResult recordResult(String schema, String source) {
        RecordMigrationResult result = new RecordMigrationResult();
        result.setSrcSchema(schema);
        result.setSource(source);
        return result;
    }

    private static RecordMigrationResult recordResult(
            String source, long exported, long imported, long total) {
        RecordMigrationResult result = recordResult(null, source);
        result.setExpCount(exported);
        result.setImpCount(imported);
        result.setTotalCount(total);
        return result;
    }

    private static String overview(MigrationOverviewResult result) {
        return result.getObjType()
                + " "
                + result.getExpCount()
                + "/"
                + result.getImpCount()
                + "/"
                + result.getTotalCount();
    }

    @Nested
    @DisplayName("addExpCSVEvent()")
    class AddExpCSVEvent {

        @Test
        @DisplayName(
                "the count goes to the first result with the same file name, compared with case")
        void count_goesToTheFirstSameName() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(
                    Arrays.asList(dataFile("A.csv"), dataFile("a.csv"), dataFile("a.csv")));

            report.addExpCSVEvent(new ExportCSVEvent(csv("a.csv"), 5));

            assertThat(report.getDataFileResults())
                    .extracting(DataFileImportResult::getExportCount)
                    .containsExactly(0L, 5L, 0L);
        }

        @Test
        @DisplayName("an event for another file changes nothing")
        void otherFile_changesNothing() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.csv")));

            report.addExpCSVEvent(new ExportCSVEvent(csv("b.csv"), 5));

            assertThat(report.getDataFileResults())
                    .extracting(DataFileImportResult::getExportCount)
                    .containsExactly(0L);
        }
    }

    @Nested
    @DisplayName("addExpMigRecResult()")
    class AddExpMigRecResult {

        @Test
        @DisplayName("an event for a new table adds its result, the count raising the total")
        void newTable_addsItsResult() {
            MigrationReport report = new MigrationReport();

            report.addExpMigRecResult(new ExportRecordsEvent(tableConfig(null, "game"), 100));

            assertThat(report.getRecMigResults())
                    .extracting(
                            RecordMigrationResult::getSource,
                            RecordMigrationResult::getTarget,
                            RecordMigrationResult::getExpCount,
                            RecordMigrationResult::getTotalCount)
                    .containsExactly(tuple("game", "game", 100L, 100L));
        }

        @Test
        @DisplayName("counts add up, and the total rises to the count but never falls")
        void counts_addUp() {
            MigrationReport report = new MigrationReport();
            RecordMigrationResult result = report.getRecMigResults(null, "game", "game");
            result.setTotalCount(120);

            report.addExpMigRecResult(new ExportRecordsEvent(tableConfig(null, "game"), 100));
            assertThat(result.getExpCount()).isEqualTo(100);
            assertThat(result.getTotalCount()).isEqualTo(120);

            report.addExpMigRecResult(new ExportRecordsEvent(tableConfig(null, "game"), 50));
            assertThat(result.getExpCount()).isEqualTo(150);
            assertThat(result.getTotalCount()).isEqualTo(150);
        }

        @Test
        @DisplayName("the end time moves to a later event and never back")
        void endTime_movesForwardOnly() {
            MigrationReport report = new MigrationReport();
            ExportRecordsEvent event = new ExportRecordsEvent(tableConfig(null, "game"), 1);
            report.addExpMigRecResult(event);
            RecordMigrationResult result = report.getRecMigResults().get(0);
            assertThat(result.getEndExportTime()).isEqualTo(event.getEventTime().getTime());

            result.setEndExportTime(Long.MAX_VALUE);
            report.addExpMigRecResult(new ExportRecordsEvent(tableConfig(null, "game"), 1));

            assertThat(result.getEndExportTime()).isEqualTo(Long.MAX_VALUE);
            assertThat(result.getStartExportTime()).isZero();
        }
    }

    @Nested
    @DisplayName("addImpMigRecResult()")
    class AddImpMigRecResult {

        @Test
        @DisplayName("a success adds its count and sets the start and end times to the event")
        void success_addsItsCount() {
            MigrationReport report = new MigrationReport();
            ImportRecordsEvent event = new ImportRecordsEvent(tableConfig(null, "game"), 100);

            report.addImpMigRecResult(event);

            RecordMigrationResult result = report.getRecMigResults().get(0);
            long time = event.getEventTime().getTime();
            assertThat(result.getImpCount()).isEqualTo(100);
            assertThat(result.getStartImportTime()).isEqualTo(time);
            assertThat(result.getEndImportTime()).isEqualTo(time);
        }

        @Test
        @DisplayName("a failure adds no count but records its error file once")
        void failure_recordsItsErrorFileOnce() {
            MigrationReport report = new MigrationReport();
            SourceTableConfig game = tableConfig(null, "game");
            NormalMigrationException error = new NormalMigrationException("error");

            report.addImpMigRecResult(new ImportRecordsEvent(game, 3, error, "err.sql"));
            report.addImpMigRecResult(new ImportRecordsEvent(game, 3, error, "err.sql"));

            assertThat(report.getRecMigResults().get(0).getImpCount()).isZero();
            assertThat(report.getErrorSQLFiles()).containsExactly("err.sql");
        }

        @Test
        @DisplayName("the start time is set only once and the end time never moves back")
        void times_neverMoveBack() {
            MigrationReport report = new MigrationReport();
            RecordMigrationResult result = report.getRecMigResults(null, "game", "game");
            result.setStartImportTime(123);
            result.setEndImportTime(Long.MAX_VALUE);

            report.addImpMigRecResult(new ImportRecordsEvent(tableConfig(null, "game"), 1));

            assertThat(result.getStartImportTime()).isEqualTo(123);
            assertThat(result.getEndImportTime()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("a blank error file is not recorded")
        void blankErrorFile_isNotRecorded() {
            MigrationReport report = new MigrationReport();
            NormalMigrationException error = new NormalMigrationException("error");

            report.addImpMigRecResult(
                    new ImportRecordsEvent(tableConfig(null, "game"), 3, error, "  "));

            assertThat(report.getErrorSQLFiles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addImportCSVEvent()")
    class AddImportCSVEvent {

        @Test
        @DisplayName("a success adds its count to the first result with the CSV name, with case")
        void success_addsToTheFirstSameName() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(
                    Arrays.asList(dataFile("A.csv"), dataFile("a.csv"), dataFile("a.csv")));

            report.addImportCSVEvent(new ImportCSVEvent(csv("a.csv"), 3, 1));

            assertThat(report.getDataFileResults())
                    .extracting(
                            DataFileImportResult::getExportCount,
                            DataFileImportResult::getImportCount)
                    .containsExactly(tuple(0L, 0L), tuple(0L, 3L), tuple(0L, 0L));
        }

        @Test
        @DisplayName("a failure adds no count but records its error file")
        void failure_recordsItsErrorFile() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.csv")));
            NormalMigrationException error = new NormalMigrationException("error");

            report.addImportCSVEvent(new ImportCSVEvent(csv("a.csv"), 3, 1, error, "e.sql"));

            assertThat(report.getDataFileResults())
                    .extracting(DataFileImportResult::getImportCount)
                    .containsExactly(0L);
            assertThat(report.getErrorSQLFiles()).containsExactly("e.sql");
        }

        @Test
        @DisplayName("an event for another file changes nothing and its error file is dropped")
        void otherFile_dropsItsErrorFile() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.csv")));
            NormalMigrationException error = new NormalMigrationException("error");

            report.addImportCSVEvent(new ImportCSVEvent(csv("b.csv"), 3, 1, error, "e.sql"));

            assertThat(report.getDataFileResults())
                    .extracting(DataFileImportResult::getImportCount)
                    .containsExactly(0L);
            assertThat(report.getErrorSQLFiles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("addSQLImportEvent()")
    class AddSQLImportEvent {

        @Test
        @DisplayName("a success adds its count to the export and import counts of the first match")
        void success_addsToBothCounts() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.sql"), dataFile("a.sql")));

            report.addSQLImportEvent(new ImportSQLsEvent("a.sql", 4, 1));

            assertThat(report.getDataFileResults())
                    .extracting(
                            DataFileImportResult::getExportCount,
                            DataFileImportResult::getImportCount)
                    .containsExactly(tuple(4L, 4L), tuple(0L, 0L));
        }

        @Test
        @DisplayName("a failure adds to the export count only and records its error file")
        void failure_addsToTheExportCountOnly() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.sql")));
            NormalMigrationException error = new NormalMigrationException("error");

            report.addSQLImportEvent(new ImportSQLsEvent("a.sql", 2, 1, error, "a.err"));

            assertThat(report.getDataFileResults())
                    .extracting(
                            DataFileImportResult::getExportCount,
                            DataFileImportResult::getImportCount)
                    .containsExactly(tuple(2L, 0L));
            assertThat(report.getErrorSQLFiles()).containsExactly("a.err");
        }

        @Test
        @DisplayName("an event for another file, the name compared with case, changes nothing")
        void otherFile_changesNothing() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("a.sql")));
            NormalMigrationException error = new NormalMigrationException("error");

            report.addSQLImportEvent(new ImportSQLsEvent("A.sql", 2, 1, error, "a.err"));

            assertThat(report.getDataFileResults())
                    .extracting(
                            DataFileImportResult::getExportCount,
                            DataFileImportResult::getImportCount)
                    .containsExactly(tuple(0L, 0L));
            assertThat(report.getErrorSQLFiles()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDataFileResults()")
    class GetDataFileResults {

        @Test
        @DisplayName("changing the returned list leaves the report alone, the results being shared")
        void returnedList_isACopy() {
            MigrationReport report = new MigrationReport();
            DataFileImportResult result = dataFile("a.csv");
            report.setDataFileResults(Arrays.asList(result));

            report.getDataFileResults().add(dataFile("b.csv"));

            assertThat(report.getDataFileResults()).singleElement().isSameAs(result);
        }
    }

    @Nested
    @DisplayName("getDbObjectsResult()")
    class GetDbObjectsResult {

        @Test
        @DisplayName("changing the returned list leaves the report alone, the results being shared")
        void returnedList_isACopy() {
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult result = report.getDBObjResult(table(null, "game"));

            report.getDbObjectsResult().clear();

            assertThat(report.getDbObjectsResult()).singleElement().isSameAs(result);
        }
    }

    @Nested
    @DisplayName("getObjNameResult()")
    class GetObjNameResult {

        @Test
        @DisplayName("changing the returned list leaves the report alone, the results being shared")
        void returnedList_isACopy() {
            MigrationReport report = new MigrationReport();
            ObjNameMigrationResult result = new ObjNameMigrationResult("table", "game", "t_game");
            report.setObjNameResult(Arrays.asList(result));

            report.getObjNameResult().clear();

            assertThat(report.getObjNameResult()).singleElement().isSameAs(result);
        }
    }

    @Nested
    @DisplayName("getDBObjResult()")
    class GetDBObjResult {

        @ParameterizedTest(name = "[{index}] {0} -> {2}, {3}, owner {4}")
        @DisplayName("a new object gets a result with its display name, its type and its owner")
        @MethodSource(
                "com.cubrid.cubridmigration.core.engine.report.MigrationReportTest#namedObjects")
        void newObject_getsANamedResult(
                String label, DBObject object, String name, String type, String owner) {
            MigrationReport report = new MigrationReport();

            DBObjMigrationResult result = report.getDBObjResult(object);

            assertThat(result.getObjName()).isEqualTo(name);
            assertThat(result.getObjType()).isEqualTo(type);
            assertThat(result.getObjOwner()).isEqualTo(owner);
            assertThat(report.getDbObjectsResult()).singleElement().isSameAs(result);
        }

        @Test
        @DisplayName(
                "a primary key without a name, or named primary, reads as the key of its table")
        void unnamedPrimaryKey_readsAsTheKeyOfItsTable() {
            MigrationReport report = new MigrationReport();
            Table table = table("own", "t");
            PK primary = new PK(table);
            primary.setName("PRIMARY");
            PK blank = new PK(table);
            blank.setName("  ");

            DBObjMigrationResult result = report.getDBObjResult(new PK(table));

            assertThat(result.getObjName()).isEqualTo("primary key of t");
            assertThat(report.getDBObjResult(primary)).isSameAs(result);
            assertThat(report.getDBObjResult(blank)).isSameAs(result);
        }

        @Test
        @DisplayName("the same object gets the same result, added only once")
        void sameObject_getsTheSameResult() {
            MigrationReport report = new MigrationReport();
            Table table = table("own", "game");

            DBObjMigrationResult result = report.getDBObjResult(table);

            assertThat(report.getDBObjResult(table)).isSameAs(result);
            assertThat(report.getDbObjectsResult()).hasSize(1);
        }

        @Test
        @DisplayName("names compare with case, and the type has to match too")
        void nameAndType_haveToMatch() {
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult result = report.getDBObjResult(table(null, "T"));

            assertThat(report.getDBObjResult(table(null, "t"))).isNotSameAs(result);
            assertThat(report.getDBObjResult(view(null, "T"))).isNotSameAs(result);
            assertThat(report.getDbObjectsResult()).hasSize(3);
        }

        @Test
        @DisplayName("owners compare ignoring case, and another owner gets its own result")
        void owners_compareIgnoringCase() {
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult result = report.getDBObjResult(view("OWN", "v"));

            assertThat(report.getDBObjResult(view("own", "v"))).isSameAs(result);
            assertThat(report.getDBObjResult(view("other", "v"))).isNotSameAs(result);
        }

        @Test
        @DisplayName(
                "an ownerless result matches any owner, an owned one misses an ownerless object")
        void resultWithoutOwner_matchesAnyOwner() {
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult anyOwner = objectResult("v", "view", null);
            DBObjMigrationResult owned = objectResult("w", "view", "own");
            report.setDbObjectsResult(Arrays.asList(anyOwner, owned));

            assertThat(report.getDBObjResult(view("own", "v"))).isSameAs(anyOwner);
            assertThat(report.getDBObjResult(view(null, "w"))).isNotSameAs(owned);
        }
    }

    @Nested
    @DisplayName("getOverviewResults()")
    class GetOverviewResults {

        @Test
        @DisplayName(
                "each listed type counts its results, a success as imported, records adding up")
        void listedTypes_countTheirResults() {
            MigrationReport report = new MigrationReport();
            report.getDBObjResult(table(null, "t1")).setSucceed(true);
            report.getDBObjResult(table(null, "t2"));
            PlcsqlProcedure procedure = new PlcsqlProcedure();
            procedure.setName("p");
            report.getDBObjResult(procedure).setSucceed(true);
            report.setRecMigResults(
                    Arrays.asList(recordResult("t1", 10, 8, 12), recordResult("t2", 1, 1, 1)));

            assertThat(report.getOverviewResults())
                    .extracting(MigrationReportTest::overview)
                    .containsExactly(
                            "schema 0/0/0",
                            "table 2/1/2",
                            "view 0/0/0",
                            "primary key 0/0/0",
                            "foreign key 0/0/0",
                            "index 0/0/0",
                            "sequence 0/0/0",
                            "synonym 0/0/0",
                            "trigger 0/0/0",
                            "plcsql_function 0/0/0",
                            "plcsql_procedure 1/1/1",
                            "grant 0/0/0",
                            "record 11/9/13");
        }

        @Test
        @DisplayName("a type outside the list is left out")
        void otherType_isLeftOut() {
            MigrationReport report = new MigrationReport();
            report.addDbObjectsResult(objectResult("c", "column", null));

            assertThat(report.getOverviewResults())
                    .hasSize(13)
                    .extracting(MigrationOverviewResult::getObjType)
                    .doesNotContain("column");
        }
    }

    @Nested
    @DisplayName("getRecMigResults(String, String, String)")
    class GetRecMigResultsByName {

        @Test
        @DisplayName("without an owner the first result with the name, ignoring case, matches")
        void noOwner_matchesTheFirstSameName() {
            MigrationReport report = new MigrationReport();
            RecordMigrationResult first = recordResult("other", "game");
            report.setRecMigResults(Arrays.asList(first, recordResult("own", "GAME")));

            assertThat(report.getRecMigResults(null, "Game", "anything")).isSameAs(first);
        }

        @Test
        @DisplayName("with an owner the schema and the name match ignoring case, the target unused")
        void owner_matchesTheSchemaAndTheName() {
            MigrationReport report = new MigrationReport();
            RecordMigrationResult owned = recordResult("own", "GAME");
            report.setRecMigResults(Arrays.asList(recordResult("other", "game"), owned));

            assertThat(report.getRecMigResults("OWN", "game", "anything")).isSameAs(owned);
        }

        @Test
        @DisplayName("a missing result is added with the name and the target only")
        void missingResult_isAdded() {
            MigrationReport report = new MigrationReport();

            RecordMigrationResult result = report.getRecMigResults("own", "game", "t_game");

            assertThat(report.getRecMigResults()).singleElement().isSameAs(result);
            assertThat(result.getSource()).isEqualTo("game");
            assertThat(result.getTarget()).isEqualTo("t_game");
            assertThat(result.getSrcSchema()).isNull();
        }

        @Test
        @DisplayName("a result added for an owner is not found for that owner again")
        void resultAddedForAnOwner_isAddedAgain() {
            // DEFECT: the new result gets no schema, so a later lookup with the same owner misses
            // it and adds one more result each time
            // - see MigrationReport.getRecMigResults(String, String, String)
            MigrationReport report = new MigrationReport();

            RecordMigrationResult first = report.getRecMigResults("own", "game", "game");
            RecordMigrationResult second = report.getRecMigResults("own", "game", "game");

            assertThat(second).isNotSameAs(first);
            assertThat(report.getRecMigResults()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("hasError()")
    class HasError {

        @Test
        @DisplayName("an object error, or export and import counts that differ, mean an error")
        void errorOrDifferentCounts_meanAnError() {
            MigrationReport objects = new MigrationReport();
            objects.getDBObjResult(table(null, "game")).setError("error");
            MigrationReport records = new MigrationReport();
            records.setRecMigResults(Arrays.asList(recordResult("game", 5, 4, 5)));
            MigrationReport files = new MigrationReport();
            files.setDataFileResults(Arrays.asList(dataFile("a.csv", 3, 2)));

            assertThat(objects.hasError()).isTrue();
            assertThat(records.hasError()).isTrue();
            assertThat(files.hasError()).isTrue();
        }

        @Test
        @DisplayName("a blank error and matching counts mean no error, whatever the total")
        void blankErrorAndMatchingCounts_meanNoError() {
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult object = report.getDBObjResult(table(null, "game"));
            object.setSucceed(true);
            object.setError("  ");
            report.setRecMigResults(Arrays.asList(recordResult("game", 5, 5, 100)));
            report.setDataFileResults(Arrays.asList(dataFile("a.csv", 3, 3)));

            assertThat(report.hasError()).isFalse();
            assertThat(new MigrationReport().hasError()).isFalse();
        }

        @Test
        @DisplayName("an object that failed without an error message is no error")
        void failureWithoutMessage_isNoError() {
            // DEFECT: only the error text counts, and the reporter stores getMessage() of the
            // exception, so an object that failed with an exception without a message leaves the
            // report without an error
            // - see MigrationReport.hasError()
            MigrationReport report = new MigrationReport();
            DBObjMigrationResult object = report.getDBObjResult(table(null, "game"));
            object.setSucceed(false);
            object.setError(null);

            assertThat(report.hasError()).isFalse();
        }
    }

    @Nested
    @DisplayName("setDataFileResults()")
    class SetDataFileResults {

        @Test
        @DisplayName("the results replace the old ones as a copy, and null leaves none")
        void results_replaceTheOldOnes() {
            MigrationReport report = new MigrationReport();
            report.setDataFileResults(Arrays.asList(dataFile("old.csv")));
            List<DataFileImportResult> results = new ArrayList<>(Arrays.asList(dataFile("a.csv")));

            report.setDataFileResults(results);
            results.add(dataFile("b.csv"));

            assertThat(report.getDataFileResults())
                    .extracting(DataFileImportResult::getFileName)
                    .containsExactly("a.csv");
            report.setDataFileResults(null);
            assertThat(report.getDataFileResults()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setDbObjectsResult()")
    class SetDbObjectsResult {

        @Test
        @DisplayName("the results replace the old ones as a copy, and null leaves none")
        void results_replaceTheOldOnes() {
            MigrationReport report = new MigrationReport();
            report.getDBObjResult(table(null, "old"));
            List<DBObjMigrationResult> results =
                    new ArrayList<>(Arrays.asList(objectResult("game", "table", null)));

            report.setDbObjectsResult(results);
            results.clear();

            assertThat(report.getDbObjectsResult())
                    .extracting(DBObjMigrationResult::getObjName)
                    .containsExactly("game");
            report.setDbObjectsResult(null);
            assertThat(report.getDbObjectsResult()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setRecMigResults()")
    class SetRecMigResults {

        @Test
        @DisplayName("the results replace the old ones as a copy, and null leaves none")
        void results_replaceTheOldOnes() {
            MigrationReport report = new MigrationReport();
            report.getRecMigResults(null, "old", "old");
            List<RecordMigrationResult> results =
                    new ArrayList<>(Arrays.asList(recordResult(null, "game")));

            report.setRecMigResults(results);
            results.clear();

            assertThat(report.getRecMigResults())
                    .extracting(RecordMigrationResult::getSource)
                    .containsExactly("game");
            report.setRecMigResults(null);
            assertThat(report.getRecMigResults()).isEmpty();
        }

        @Test
        @DisplayName("the getter hands out the list itself, unlike the setter")
        void getter_handsOutTheListItself() {
            MigrationReport report = new MigrationReport();
            report.setRecMigResults(Arrays.asList(recordResult(null, "game")));

            report.getRecMigResults().clear();

            assertThat(report.getRecMigResults()).isEmpty();
        }
    }

    @Nested
    @DisplayName("setObjNameResult(List)")
    class SetObjNameResult {

        @Test
        @DisplayName("the results replace the old ones as a copy, and null leaves none")
        void results_replaceTheOldOnes() {
            MigrationReport report = new MigrationReport();
            report.setObjNameResult(
                    Arrays.asList(new ObjNameMigrationResult("table", "old", "t_old")));
            List<ObjNameMigrationResult> results =
                    new ArrayList<>(
                            Arrays.asList(new ObjNameMigrationResult("table", "game", "t_game")));

            report.setObjNameResult(results);
            results.clear();

            assertThat(report.getObjNameResult())
                    .extracting(ObjNameMigrationResult::getObjSourceName)
                    .containsExactly("game");
            report.setObjNameResult(null);
            assertThat(report.getObjNameResult()).isEmpty();
        }
    }

    @Nested
    @DisplayName("save2ReportFile()")
    class Save2ReportFile {

        @Test
        @DisplayName("the report is saved without its brief, which stays on the report")
        void report_isSavedWithoutItsBrief(@TempDir Path dir) {
            MigrationReport report = new MigrationReport();
            MigrationBriefReport brief = new MigrationBriefReport();
            report.setBrief(brief);
            report.setConfigSummary("test config summary");
            report.setTotalStartTime(1L);
            report.setTotalEndTime(2L);
            report.getDBObjResult(table("own", "game")).setSucceed(true);
            report.getRecMigResults(null, "game", "game").setExpCount(100);
            report.setDataFileResults(Arrays.asList(dataFile("a.csv")));
            report.setObjNameResult(
                    Arrays.asList(new ObjNameMigrationResult("table", "game", "t_game")));
            report.addErrorSQLFile("err.sql");
            String file = dir.resolve("report.xml").toString();

            report.save2ReportFile(file);

            MigrationReport saved = MigrationReport.loadFromReportFile(file);
            assertThat(report.getBrief()).isSameAs(brief);
            assertThat(saved.getBrief()).isNull();
            assertThat(saved.getConfigSummary()).isEqualTo("test config summary");
            assertThat(saved.getTotalStartTime()).isEqualTo(1L);
            assertThat(saved.getTotalEndTime()).isEqualTo(2L);
            assertThat(saved.getDbObjectsResult())
                    .extracting(DBObjMigrationResult::getObjName, DBObjMigrationResult::isSucceed)
                    .containsExactly(tuple("own.game", true));
            assertThat(saved.getRecMigResults())
                    .extracting(
                            RecordMigrationResult::getSource, RecordMigrationResult::getExpCount)
                    .containsExactly(tuple("game", 100L));
            assertThat(saved.getDataFileResults())
                    .extracting(DataFileImportResult::getFileName)
                    .containsExactly("a.csv");
            assertThat(saved.getObjNameResult())
                    .extracting(ObjNameMigrationResult::getObjTargetName)
                    .containsExactly("t_game");
            assertThat(saved.getErrorSQLFiles()).containsExactly("err.sql");
        }

        @Test
        @DisplayName("a path that cannot be opened throws RuntimeException around the cause")
        void unopenablePath_throwsRuntimeException(@TempDir Path dir) {
            MigrationReport report = new MigrationReport();

            assertThatThrownBy(() -> report.save2ReportFile(dir.toString()))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(FileNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("addErrorSQLFile()")
    class AddErrorSQLFile {

        @Test
        @DisplayName("a new name goes at the end and one already there is skipped, null included")
        void sameName_isSkipped() {
            MigrationReport report = new MigrationReport();

            report.addErrorSQLFile("a.sql");
            report.addErrorSQLFile("b.sql");
            report.addErrorSQLFile("a.sql");
            report.addErrorSQLFile("A.sql");
            report.addErrorSQLFile(null);
            report.addErrorSQLFile(null);

            assertThat(report.getErrorSQLFiles()).containsExactly("a.sql", "b.sql", "A.sql", null);
        }
    }

    @Nested
    @DisplayName("getErrorSQLFiles()")
    class GetErrorSQLFiles {

        @Test
        @DisplayName("changing the returned list leaves the report alone")
        void returnedList_isACopy() {
            MigrationReport report = new MigrationReport();
            report.addErrorSQLFile("a.sql");

            report.getErrorSQLFiles().add("b.sql");

            assertThat(report.getErrorSQLFiles()).containsExactly("a.sql");
        }
    }

    @Nested
    @DisplayName("setErrorSQLFiles()")
    class SetErrorSQLFiles {

        @Test
        @DisplayName("the names replace the files, duplicates merged, and null leaves none")
        void names_replaceTheFiles() {
            MigrationReport report = new MigrationReport();
            report.addErrorSQLFile("old.sql");

            report.setErrorSQLFiles(Arrays.asList("a.sql", "b.sql", "a.sql"));
            assertThat(report.getErrorSQLFiles()).containsExactly("a.sql", "b.sql");

            report.setErrorSQLFiles(null);
            assertThat(report.getErrorSQLFiles()).isEmpty();
        }
    }

    static Stream<Arguments> namedObjects() {
        Table table = table("own", "t");
        PK pk = new PK(table);
        pk.setName("pk_t");
        Index index = new Index(table);
        index.setName("idx");
        FK fk = new FK(table);
        fk.setName("fk");
        Sequence sequence = new Sequence();
        sequence.setName("s");
        sequence.setOwner("sown");
        Synonym synonym = new Synonym();
        synonym.setName("sy");
        synonym.setOwner("syown");
        Grant grant = new Grant();
        grant.setName("g");
        grant.setOwner("gown");
        Trigger trigger = new Trigger();
        trigger.setName("tr");
        trigger.setOwner("trown");
        Schema schema = new Schema();
        schema.setName("sc");
        return Stream.of(
                Arguments.of("table with an owner", table, "own.t", "table", "own"),
                Arguments.of("table", table(null, "t"), "t", "table", null),
                Arguments.of("named primary key", pk, "[t]pk_t", "primary key", "own"),
                Arguments.of("index", index, "[t]idx", "index", "own"),
                Arguments.of("foreign key", fk, "fk", "foreign key", "own"),
                Arguments.of("view", view("vown", "v"), "v", "view", "vown"),
                Arguments.of("sequence", sequence, "s", "sequence", "sown"),
                Arguments.of("synonym", synonym, "syown.sy", "synonym", null),
                Arguments.of("grant", grant, "g", "grant", null),
                Arguments.of("trigger", trigger, "tr", "trigger", null),
                Arguments.of("schema", schema, "sc", "schema", null));
    }
}
