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
package com.cubrid.cubridmigration.informix.meta;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.attachPreparedQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.MissingFormatArgumentException;

@DisplayName("InformixSchemaFetcher")
class InformixSchemaFetcherTest {

    private static final InformixSchemaFetcher FETCHER = new InformixSchemaFetcher();

    // Informix spells a timestamp as a range, "datetime year to second". CUBRID has no such form,
    // so the qualifier is cut and the plain type name is what reaches the transform stage.
    @Test
    @DisplayName("a datetime range -> the bare datetime type")
    void datetimeRange_losesItsQualifier() throws SQLException {
        Table table =
                FETCHER.buildSQLTable(oneColumn("datetime year to second", Types.TIMESTAMP, 0));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("datetime");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("datetime");
    }

    @Test
    @DisplayName("any other type keeps its name and gains how it is shown")
    void otherType_keepsItsName() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("varchar", Types.VARCHAR, 20));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("varchar");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("varchar(20)");
    }

    // DEFECT: the same shape as getSequences() above
    // - see InformixSchemaFetcher.getFunctions()
    @Test
    @DisplayName("two function rows -> two references to one object holding the last row")
    void twoFunctionRows_collapseIntoOne() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 2);
        when(rs.getString("name")).thenReturn("f1", "f2");
        when(rs.getString("data")).thenReturn("body1", "body2");

        List<Function> functions = invokeRowReader("getFunctions", conn);

        assertThat(functions).hasSize(2);
        assertThat(functions.get(0)).isSameAs(functions.get(1));
        assertThat(functions.get(0).getName()).isEqualTo("f2");
    }

    // DEFECT: the same shape again
    // - see InformixSchemaFetcher.getProcedures()
    @Test
    @DisplayName("two procedure rows -> two references to one object holding the last row")
    void twoProcedureRows_collapseIntoOne() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 2);
        when(rs.getString("name")).thenReturn("p1", "p2");
        when(rs.getString("data")).thenReturn("body1", "body2");

        List<Procedure> procedures = invokeRowReader("getProcedures", conn);

        assertThat(procedures).hasSize(2);
        assertThat(procedures.get(0)).isSameAs(procedures.get(1));
        assertThat(procedures.get(0).getName()).isEqualTo("p2");
    }

    @Test
    @DisplayName("buildViewDDL() keeps what follows \" as\" and strips the quoted owner prefixes")
    void buildViewDDL_keepsWhatFollowsAs() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("viewtext")).thenReturn("create view \"hr\".v as select a from \"hr\".t");

        assertThat(FETCHER.buildViewDDL(conn, "hr", "v")).isEqualTo(" select a from t");
    }

    // DEFECT: the text is split on " as" and the second piece taken without checking that the
    // split produced one, so a view whose text carries no such word aborts the schema build
    // - see InformixSchemaFetcher.buildViewDDL()
    @Test
    @DisplayName("view text without \" as\" -> ArrayIndexOutOfBoundsException")
    void viewTextWithoutAs_throwsArrayIndexOutOfBounds() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("viewtext")).thenReturn("create view v (select a from t)");

        assertThatThrownBy(() -> FETCHER.buildViewDDL(conn, "hr", "v"))
                .isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }

    // The emptiness check compares references rather than contents. It happens to hold here only
    // because nothing was appended, so the field still points at the same literal.
    @Test
    @DisplayName("no view row -> null")
    void noViewRow_returnsNull() throws Exception {
        Connection conn = mock(Connection.class);
        attachPreparedQuery(conn, 0);

        assertThat(FETCHER.buildViewDDL(conn, "hr", "v")).isNull();
    }

    // DEFECT: the Sequence is built once, before the loop, and every row overwrites that same
    // object before adding it again. A schema with N sequences migrates as N copies of the last
    // one - the same shape applies to getProcedures() and getFunctions()
    // - see InformixSchemaFetcher.getSequences()
    @Test
    @DisplayName("two sequence rows -> two references to one object holding the last row")
    void twoSequenceRows_collapseIntoOne() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 2);
        when(rs.getString("tabname")).thenReturn("seq_a", "seq_b");
        when(rs.getString("max_val")).thenReturn("100", "200");
        when(rs.getString("min_val")).thenReturn("1", "2");
        when(rs.getString("inc_val")).thenReturn("1", "5");
        when(rs.getString("start_val")).thenReturn("1", "10");
        when(rs.getString("cycle")).thenReturn("1", "0");
        when(rs.getInt("cache")).thenReturn(20, 30);

        List<Sequence> sequences = invokeRowReader("getSequences", conn);

        assertThat(sequences).hasSize(2);
        assertThat(sequences.get(0)).isSameAs(sequences.get(1));
        assertThat(sequences.get(0).getName()).isEqualTo("seq_b");
        assertThat(sequences.get(0).getMaxValue()).isEqualTo(new java.math.BigInteger("200"));
    }

    // The format failure pinned above surfaces here: one trigger with a known event is enough to
    // abort the whole trigger catalog for the schema.
    @Test
    @DisplayName("one trigger with a known event aborts the whole trigger read")
    void triggerWithKnownEvent_abortsTheRead() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("trigname")).thenReturn("trg1");
        when(rs.getString("event")).thenReturn("U");
        when(rs.getString("data")).thenReturn("trigger body");

        assertThatThrownBy(() -> invokeRowReader("getTriggers", conn))
                .isInstanceOf(MissingFormatArgumentException.class);
    }

    @Test
    @DisplayName("a trigger whose event has no mapping is read with its body as the DDL")
    void triggerWithUnknownEvent_keepsItsBody() throws Exception {
        Connection conn = mock(Connection.class);
        ResultSet rs = attachPreparedQuery(conn, 1);
        when(rs.getString("trigname")).thenReturn("trg1");
        when(rs.getString("event")).thenReturn("X");
        when(rs.getString("data")).thenReturn("trigger body");

        List<Trigger> triggers = invokeRowReader("getTriggers", conn);

        assertThat(triggers).hasSize(1);
        assertThat(triggers.get(0).getDDL()).isEqualTo("trigger body");
    }

    // DEFECT: the template carries four placeholders but only three arguments are supplied, so
    // every trigger Informix reports raises instead of producing DDL. buildTriggers() is where
    // it surfaces, and the schema build loses the whole trigger catalog with it
    // - see InformixSchemaFetcher.buildTriggerDDL()
    @ParameterizedTest(name = "[{index}] event {0} -> raises")
    @DisplayName("every event Informix names raises instead of producing DDL")
    @ValueSource(strings = {"U", "I", "S", "D"})
    void knownEvent_raisesInsteadOfProducingDdl(String event) {
        assertThatThrownBy(() -> buildTriggerDDL("trigger body", "trg1", event))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(MissingFormatArgumentException.class);
    }

    // An event the method does not know skips the template entirely, which is the only path that
    // returns anything at all.
    @Test
    @DisplayName("an event with no mapping -> the body returned untouched")
    void unknownEvent_returnsTheBodyUntouched() throws Exception {
        assertThat(buildTriggerDDL("trigger body", "trg1", "X")).isEqualTo("trigger body");
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> invokeRowReader(String method, Connection conn) throws Exception {
        Method m =
                InformixSchemaFetcher.class.getDeclaredMethod(
                        method, Connection.class, String.class);
        m.setAccessible(true);
        try {
            return (List<T>) m.invoke(FETCHER, conn, "hr");
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static ResultSetMetaData oneColumn(String typeName, int jdbcType, int precision)
            throws SQLException {
        ResultSetMetaData rsm = mock(ResultSetMetaData.class);
        when(rsm.getColumnCount()).thenReturn(1);
        when(rsm.getColumnLabel(1)).thenReturn("C1");
        when(rsm.getColumnName(1)).thenReturn("C1");
        when(rsm.getColumnType(1)).thenReturn(jdbcType);
        when(rsm.getColumnTypeName(1)).thenReturn(typeName);
        when(rsm.getPrecision(1)).thenReturn(precision);
        when(rsm.getScale(1)).thenReturn(0);
        return rsm;
    }

    private static Object buildTriggerDDL(String body, String name, String event) throws Exception {
        Method method =
                InformixSchemaFetcher.class.getDeclaredMethod(
                        "buildTriggerDDL", String.class, String.class, String.class);
        method.setAccessible(true);
        return method.invoke(FETCHER, body, name, event);
    }
}
