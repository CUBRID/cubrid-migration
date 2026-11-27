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
package com.cubrid.cubridmigration.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@DisplayName("DBUtils")
@ResourceLock(Resources.TIME_ZONE)
class DBUtilsTest {

    private static final TimeZone ORIGINAL_TIME_ZONE = TimeZone.getDefault();

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(ORIGINAL_TIME_ZONE);
    }

    private static PartitionInfo partition(String expression, String... columnNames) {
        PartitionInfo partition = new PartitionInfo();
        partition.setPartitionExp(expression);
        for (String name : columnNames) {
            Column column = new Column();
            column.setName(name);
            partition.addPartitionColumn(column);
        }
        return partition;
    }

    private static Table table(String... columnNames) {
        Table table = new Table();
        for (String name : columnNames) {
            Column column = new Column(table);
            column.setName(name);
            table.addColumn(column);
        }
        return table;
    }

    private static Index uniqueIndex(String... columnNames) {
        Index index = new Index();
        index.setUnique(true);
        for (String name : columnNames) {
            index.addColumn(name, true);
        }
        return index;
    }

    private static PK pk(String... columnNames) {
        PK pk = new PK();
        for (String name : columnNames) {
            pk.addColumn(name);
        }
        return pk;
    }

    @Nested
    @DisplayName("getDateFormat()")
    class GetDateFormat {

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @DisplayName("the milliseconds follow a colon and the zone is the default time zone")
        @CsvSource({
            "Asia/Seoul,       1970-01-01 09:20:34:567 KST",
            "UTC,              1970-01-01 00:20:34:567 UTC",
            "America/New_York, 1969-12-31 19:20:34:567 EST",
        })
        void format_writesDefaultZone(String zoneId, String expected) {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId));

            assertThat(DBUtils.getDateFormat().format(new Date(1234567L))).isEqualTo(expected);
        }

        @Test
        @DisplayName("text carrying its own zone parses to that instant")
        void textWithZone_parsesToInstant() throws Exception {
            assertThat(DBUtils.getDateFormat().parse("2024-01-02 03:04:05:006 KST").getTime())
                    .isEqualTo(1704132245006L);
        }

        @Test
        @DisplayName("parsing is lenient, so month 13 rolls over into the next year")
        void outOfRangeMonth_rollsOver() throws Exception {
            assertThat(DBUtils.getDateFormat().parse("2024-13-01 00:00:00:000 UTC").getTime())
                    .isEqualTo(1735689600000L);
        }

        @Test
        @DisplayName("every call hands out a new formatter")
        void eachCall_returnsNewInstance() {
            assertThat(DBUtils.getDateFormat()).isNotSameAs(DBUtils.getDateFormat());
        }

        @Test
        @DisplayName("text without a zone does not parse")
        void textWithoutZone_throwsParseException() {
            assertThatThrownBy(() -> DBUtils.getDateFormat().parse("2024-01-02 03:04:05:006"))
                    .isInstanceOf(ParseException.class);
        }
    }

    @Nested
    @DisplayName("getCubridPartitionExp()")
    class GetCubridPartitionExp {

        @ParameterizedTest(name = "[{index}] {0} on c -> {1}")
        @DisplayName("a function CUBRID supports is rewritten around the first partition column")
        @CsvSource({
            "EXTRACT(YEAR FROM x),   EXTRACT(YEAR FROM c)",
            "EXTRACT(SECOND FROM x), EXTRACT(SECOND FROM c)",
            "CEILING(x),             CEIL(c)",
            "ABS(x),                 ABS(c)",
            "FLOOR(x),               FLOOR(c)",
        })
        void supportedFunction_isRewrittenForCubrid(String expression, String expected) {
            assertThat(DBUtils.getCubridPartitionExp(partition(expression, "c", "other")))
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("MOD keeps the source expression as written")
        void modExpression_isKeptAsWritten() {
            assertThat(DBUtils.getCubridPartitionExp(partition("MOD(x, 4)", "c")))
                    .isEqualTo("MOD(x, 4)");
        }

        @Test
        @DisplayName("any other function is applied to the first partition column")
        void otherFunction_isAppliedToFirstColumn() {
            assertThat(DBUtils.getCubridPartitionExp(partition("YEAR(x)", "c", "other")))
                    .isEqualTo("YEAR(c)");
        }

        @Test
        @DisplayName("an expression with no function writes null as the function name")
        void expressionWithoutFunction_writesNullFunction() {
            assertThat(DBUtils.getCubridPartitionExp(partition("c", "c"))).isEqualTo("null(c)");
        }

        @Test
        @DisplayName("a lower-case extract loses its unit")
        void lowerCaseExtract_dropsTheUnit() {
            // DEFECT: the EXTRACT test in parsePartitionFunc() is case-sensitive, so the function
            // comes back as plain "extract" and is written around the column without its unit
            // - see DBUtils.parsePartitionFunc()
            assertThat(DBUtils.getCubridPartitionExp(partition("extract(year from x)", "c")))
                    .isEqualTo("extract(c)");
        }

        @Test
        @DisplayName("the column name is written unquoted, even a reserved word")
        void reservedWordColumn_isWrittenUnquoted() {
            // DEFECT: the column goes into the expression without quoting, while
            // CUBRIDSQLHelper quotes it when there is no function, so a column named after a
            // reserved word such as order breaks the partition DDL
            // - see DBUtils.getCubridPartitionExp()
            assertThat(DBUtils.getCubridPartitionExp(partition("ABS(x)", "order")))
                    .isEqualTo("ABS(order)");
        }

        @Test
        @DisplayName("a partition without columns fails")
        void noPartitionColumn_throwsIndexOutOfBoundsException() {
            assertThatThrownBy(() -> DBUtils.getCubridPartitionExp(partition("ABS(x)")))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("supportedCubridPartition()")
    class SupportedCubridPartition {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("any DDL text counts, a blank one included")
        @ValueSource(strings = {"PARTITION BY RANGE(id)", "  "})
        void partitionWithDdl_returnsTrue(String ddl) {
            PartitionInfo partition = new PartitionInfo();
            partition.setDDL(ddl);

            assertThat(DBUtils.supportedCubridPartition(partition)).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("a partition with no DDL is not supported")
        @NullAndEmptySource
        void partitionWithoutDdl_returnsFalse(String ddl) {
            PartitionInfo partition = new PartitionInfo();
            partition.setDDL(ddl);

            assertThat(DBUtils.supportedCubridPartition(partition)).isFalse();
        }

        @Test
        @DisplayName("no partition is not supported")
        void nullPartition_returnsFalse() {
            assertThat(DBUtils.supportedCubridPartition(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("parsePartitionColumns()")
    class ParsePartitionColumns {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the column inside a function call is found, whatever its case")
        @CsvSource({
            "YEAR(sale_date),              sale_date",
            "EXTRACT(YEAR FROM sale_date), sale_date",
            "f(cast(id as int)),           id",
            "'mod(id, 4)',                 id",
            "ID,                           id",
        })
        void functionArgument_findsColumn(String expression, String expected) {
            assertThat(DBUtils.parsePartitionColumns(table("sale_date", "id"), expression))
                    .extracting(Column::getName)
                    .containsExactly(expected);
        }

        @Test
        @DisplayName("each comma-separated name is looked up and unknown ones are dropped")
        void commaSeparatedNames_findsEachKnownColumn() {
            assertThat(DBUtils.parsePartitionColumns(table("id", "c"), "id,unknown,c"))
                    .extracting(Column::getName)
                    .containsExactly("id", "c");
        }

        @Test
        @DisplayName("a name in square brackets is found whatever its case")
        void bracketedName_findsColumn() {
            Table table = new Table();
            Column column = new Column(table);
            column.setName("sale_date");
            table.addColumn(column);

            List<Column> columns = DBUtils.parsePartitionColumns(table, "[SALE_DATE]");

            assertThat(columns).containsExactly(column);
        }

        @Test
        @DisplayName("a name in double quotes is found whatever its case")
        void quotedName_findsColumn() {
            Table table = new Table();
            Column column = new Column(table);
            column.setName("log_msg");
            table.addColumn(column);

            List<Column> columns = DBUtils.parsePartitionColumns(table, "\"LOG_MSG\"");

            assertThat(columns).containsExactly(column);
        }

        @Test
        @DisplayName("quotes inside brackets are stripped too, but brackets inside quotes stay")
        void bracketsAroundQuotes_areStrippedInThatOrderOnly() {
            Table table = table("id");

            assertThat(DBUtils.parsePartitionColumns(table, "[\"id\"]"))
                    .extracting(Column::getName)
                    .containsExactly("id");
            assertThat(DBUtils.parsePartitionColumns(table, "\"[id]\"")).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] {0} -> no column")
        @DisplayName("lower-case extract, CAST, a nested call or MySQL backticks find no column")
        @ValueSource(
                strings = {
                    "extract(year from sale_date)",
                    "f(CAST(id as int))",
                    "cast(id as int)",
                    "f(g(id))",
                    "`id`"
                })
        void unparsedExpression_findsNoColumn(String expression) {
            // DEFECT: EXTRACT is matched only in upper case at the start and cast only in lower
            // case inside another call, a nested call is cut at the first ')', and backticks are
            // never stripped, so each of these partition expressions yields no column
            // - see DBUtils.parsePartitionColumns()
            assertThat(DBUtils.parsePartitionColumns(table("sale_date", "id"), expression))
                    .isEmpty();
        }

        @Test
        @DisplayName("a lone double quote cannot be stripped")
        void loneDoubleQuote_throwsStringIndexOutOfBoundsException() {
            // DEFECT: a one-character name both starts and ends with a double quote, and taking
            // the text between the pair cuts past its end
            // - see DBUtils.parsePartitionColumns()
            assertThatThrownBy(() -> DBUtils.parsePartitionColumns(table("id"), "\""))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("an expression that opens with a parenthesis is taken whole and finds nothing")
        void leadingParenthesis_findsNoColumn() {
            assertThat(DBUtils.parsePartitionColumns(table("id"), "(id)")).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("a blank expression gives null, not an empty list")
        @NullSource
        @ValueSource(strings = {"", "  "})
        void blankExpression_returnsNull(String expression) {
            assertThat(DBUtils.parsePartitionColumns(table("id"), expression)).isNull();
        }
    }

    @Nested
    @DisplayName("parsePartitionFunc()")
    class ParsePartitionFunc {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("a function call gives the text before its parenthesis, spaces kept")
        @CsvSource({"YEAR(d), YEAR", "'MOD(c, 4)', MOD", "'  year (d)', '  year '"})
        void functionCall_returnsTextBeforeParenthesis(String expression, String expected) {
            assertThat(DBUtils.parsePartitionFunc(expression)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("EXTRACT gives EXTRACT_ followed by its unit")
        @CsvSource({
            "EXTRACT(YEAR FROM d),        EXTRACT_YEAR",
            "'EXTRACT(  MONTH   FROM d)', EXTRACT_MONTH",
        })
        void extract_returnsExtractWithUnit(String expression, String expected) {
            assertThat(DBUtils.parsePartitionFunc(expression)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a lower-case extract is treated as an ordinary function")
        void lowerCaseExtract_returnsPlainName() {
            // DEFECT: the EXTRACT test is case-sensitive, so the unit is dropped and
            // getCubridPartitionExp() then writes extract(column)
            // - see DBUtils.parsePartitionFunc()
            assertThat(DBUtils.parsePartitionFunc("extract(year from d)")).isEqualTo("extract");
        }

        @Test
        @DisplayName("EXTRACT without a FROM clause cannot be cut")
        void extractWithoutFrom_throwsStringIndexOutOfBoundsException() {
            assertThatThrownBy(() -> DBUtils.parsePartitionFunc("EXTRACT(YEAR)"))
                    .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("text that is not a function call gives null")
        @NullSource
        @ValueSource(strings = {"d", "(d)", "f)(x"})
        void noFunctionCall_returnsNull(String expression) {
            assertThat(DBUtils.parsePartitionFunc(expression)).isNull();
        }
    }

    @Nested
    @DisplayName("isAdditionalUniqueIndex()")
    class IsAdditionalUniqueIndex {

        @Test
        @DisplayName("a unique index on other columns, another order or another case is additional")
        void uniqueIndexDifferingFromPk_returnsTrue() {
            PK pk = pk("a", "b");

            assertThat(DBUtils.isAdditionalUniqueIndex(pk, uniqueIndex("a", "c"))).isTrue();
            assertThat(DBUtils.isAdditionalUniqueIndex(pk, uniqueIndex("b", "a"))).isTrue();
            assertThat(DBUtils.isAdditionalUniqueIndex(pk, uniqueIndex("A", "b"))).isTrue();
        }

        @Test
        @DisplayName("a unique index on exactly the primary key columns is not additional")
        void uniqueIndexOnPkColumns_returnsFalse() {
            assertThat(DBUtils.isAdditionalUniqueIndex(pk("a", "b"), uniqueIndex("a", "b")))
                    .isFalse();
        }

        @Test
        @DisplayName("any unique index is additional when there is no primary key")
        void uniqueIndexWithoutPk_returnsTrue() {
            assertThat(DBUtils.isAdditionalUniqueIndex(null, uniqueIndex("a"))).isTrue();
        }

        @Test
        @DisplayName("a non-unique index is never additional")
        void nonUniqueIndex_returnsFalse() {
            Index index = new Index();
            index.addColumn("a", true);

            assertThat(DBUtils.isAdditionalUniqueIndex(pk("b"), index)).isFalse();
            assertThat(DBUtils.isAdditionalUniqueIndex(null, index)).isFalse();
        }
    }

    @Nested
    @DisplayName("getBitString()")
    class GetBitString {

        private final byte[] bytes = {0x0A, (byte) 0xFF, 0x00, 0x7F};

        @Test
        @DisplayName("each byte becomes two lower-case hex digits")
        void bytes_becomeTwoHexDigitsEach() {
            assertThat(DBUtils.getBitString(bytes, 4)).isEqualTo("0aff007f");
        }

        @ParameterizedTest(name = "[{index}] len {0} -> \"{1}\"")
        @DisplayName("a shorter length writes only the leading bytes")
        @CsvSource({"2, 0aff", "0, ''"})
        void shorterLength_writesLeadingBytes(int length, String expected) {
            assertThat(DBUtils.getBitString(bytes, length)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a length beyond the array fails")
        void lengthBeyondArray_throwsArrayIndexOutOfBoundsException() {
            assertThatThrownBy(() -> DBUtils.getBitString(bytes, 5))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("reader2String()")
    class Reader2String {

        @Test
        @DisplayName("the reader is read to the end and then closed")
        void reader_isReadToEndAndClosed() throws Exception {
            StringReader reader = new StringReader("abc");

            assertThat(DBUtils.reader2String(reader)).isEqualTo("abc");
            assertThatThrownBy(reader::read).isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("an empty reader gives an empty string")
        void emptyReader_returnsEmptyString() throws Exception {
            assertThat(DBUtils.reader2String(new StringReader(""))).isEmpty();
        }

        @Test
        @DisplayName("a failing read is passed on and the reader is left open")
        void failingRead_leavesReaderOpen() throws Exception {
            // DEFECT: the reader is closed only after a complete read, so when read() fails the
            // exception propagates past the close
            // - see DBUtils.reader2String()
            Reader reader = mock(Reader.class);
            when(reader.read()).thenThrow(new IOException("broken pipe"));

            assertThatThrownBy(() -> DBUtils.reader2String(reader))
                    .isInstanceOf(IOException.class)
                    .hasMessage("broken pipe");
            verify(reader, never()).close();
        }
    }

    @Nested
    @DisplayName("rollback()")
    class Rollback {

        @Test
        @DisplayName("the connection is rolled back")
        void connection_isRolledBack() throws Exception {
            Connection connection = mock(Connection.class);

            DBUtils.rollback(connection);

            verify(connection).rollback();
        }

        @Test
        @DisplayName("a failed rollback is swallowed")
        void failedRollback_isSwallowed() throws Exception {
            // DEFECT: a failed rollback is only printed to stderr, unlike commit() which
            // rethrows, so the caller carries on as if the work had been undone
            // - see DBUtils.rollback()
            Connection connection = mock(Connection.class);
            doThrow(new SQLException("connection lost")).when(connection).rollback();

            assertThatCode(() -> DBUtils.rollback(connection)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("no connection is ignored")
        void nullConnection_isIgnored() {
            assertThatCode(() -> DBUtils.rollback(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("commit()")
    class Commit {

        @Test
        @DisplayName("an open connection is committed")
        void openConnection_isCommitted() throws Exception {
            Connection connection = mock(Connection.class);

            DBUtils.commit(connection);

            verify(connection).commit();
        }

        @Test
        @DisplayName("a closed connection is left alone")
        void closedConnection_isNotCommitted() throws Exception {
            Connection connection = mock(Connection.class);
            when(connection.isClosed()).thenReturn(true);

            DBUtils.commit(connection);

            verify(connection, never()).commit();
        }

        @Test
        @DisplayName("a failed commit is rethrown as a RuntimeException carrying the cause")
        void failedCommit_throwsRuntimeException() throws Exception {
            Connection connection = mock(Connection.class);
            SQLException cause = new SQLException("disk full");
            doThrow(cause).when(connection).commit();

            assertThatThrownBy(() -> DBUtils.commit(connection))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Committed error:disk full")
                    .hasCause(cause);
        }

        @Test
        @DisplayName("a failing closed check is rethrown the same way")
        void failedClosedCheck_throwsRuntimeException() throws Exception {
            Connection connection = mock(Connection.class);
            when(connection.isClosed()).thenThrow(new SQLException("gone"));

            assertThatThrownBy(() -> DBUtils.commit(connection))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Committed error:gone");
        }

        @Test
        @DisplayName("no connection is ignored")
        void nullConnection_isIgnored() {
            assertThatCode(() -> DBUtils.commit(null)).doesNotThrowAnyException();
        }
    }
}
