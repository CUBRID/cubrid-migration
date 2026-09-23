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
package com.cubrid.cubridmigration.oracle.meta;

import static com.cubrid.cubridmigration.testutil.JdbcMockFactory.resultSetOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.TimeZone;

@DisplayName("OracleSchemaFetcher")
@ResourceLock(Resources.TIME_ZONE)
class OracleSchemaFetcherTest {

    private static final OracleSchemaFetcher FETCHER = new OracleSchemaFetcher();

    private static TimeZone defaultZone;

    @BeforeAll
    static void pinTimeZone() {
        defaultZone = TimeZone.getDefault();
    }

    @AfterAll
    static void restoreTimeZone() {
        TimeZone.setDefault(defaultZone);
    }

    // A query whose result set names no type is still migrated, as Oracle's own widest text type.
    @Test
    @DisplayName("buildSQLTable() gives a column with no type name VARCHAR2")
    void buildSQLTable_namesUntypedColumnsVarchar2() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("", Types.OTHER, 10));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("VARCHAR2");
        assertThat(table.getColumns().get(0).getJdbcIDOfDataType()).isEqualTo(Types.VARCHAR);
    }

    @Test
    @DisplayName("buildSQLTable() leaves a named type alone and fills in how it is shown")
    void buildSQLTable_keepsNamedTypes() throws SQLException {
        Table table = FETCHER.buildSQLTable(oneColumn("VARCHAR2", Types.VARCHAR, 20));

        assertThat(table.getColumns().get(0).getDataType()).isEqualTo("VARCHAR2");
        assertThat(table.getColumns().get(0).getShownDataType()).isEqualTo("VARCHAR2(20)");
    }

    @Test
    @DisplayName("a table grant is filtered and its name rewritten for CUBRID")
    void tableGrant_isFilteredAndRewritten() throws Exception {
        Schema schema = buildGrantsFrom("ALL", "SELECT");

        assertThat(schema.getGrantList())
                .filteredOn(grant -> "T1".equals(grant.getClassName()))
                .extracting(Grant::getAuthType)
                .containsExactly("ALL PRIVILEGES");
    }

    @Test
    @DisplayName("a table grant CUBRID cannot express is dropped")
    void unsupportedTableGrant_isDropped() throws Exception {
        Schema schema = buildGrantsFrom("REFERENCES", "SELECT");

        assertThat(schema.getGrantList()).extracting(Grant::getClassName).containsExactly("V1");
    }

    // DEFECT: the view half of the method runs neither the privilege filter nor the name
    // rewrite that the table half runs, so a view grant CUBRID has no word for is migrated
    // verbatim and ALL is never turned into ALL PRIVILEGES
    // - see OracleSchemaFetcher.buildGrant()
    @Test
    @DisplayName("a view grant skips both the filter and the rewrite the table half applies")
    void viewGrant_skipsFilterAndRewrite() throws Exception {
        Schema unsupported = buildGrantsFrom("SELECT", "REFERENCES");
        Schema notRewritten = buildGrantsFrom("SELECT", "ALL");

        assertThat(unsupported.getGrantList())
                .filteredOn(grant -> "V1".equals(grant.getClassName()))
                .extracting(Grant::getAuthType)
                .containsExactly("REFERENCES");
        assertThat(notRewritten.getGrantList())
                .filteredOn(grant -> "V1".equals(grant.getClassName()))
                .extracting(Grant::getAuthType)
                .containsExactly("ALL");
    }

    // Oracle reports no numeric precision for its text and raw types, so the length is taken from
    // whichever of the two length columns suits that family.
    @Test
    @DisplayName("a text type with no precision takes its character length")
    void textTypeWithoutPrecision_takesCharLength() throws Exception {
        Column column = columnOf("CHAR", null, 30, 60);

        invokePrivate("resetOracleColumnPrecision", new Class[] {Column.class}, column);

        assertThat(column.getPrecision()).isEqualTo(30);
    }

    @Test
    @DisplayName("a precision of zero counts as none at all")
    void zeroPrecision_countsAsNone() throws Exception {
        Column column = columnOf("VARCHAR2", 0, 30, 60);

        invokePrivate("resetOracleColumnPrecision", new Class[] {Column.class}, column);

        assertThat(column.getPrecision()).isEqualTo(30);
    }

    @Test
    @DisplayName("a raw type takes its byte length instead")
    void rawType_takesByteLength() throws Exception {
        Column column = columnOf("RAW", null, 30, 60);

        invokePrivate("resetOracleColumnPrecision", new Class[] {Column.class}, column);

        assertThat(column.getPrecision()).isEqualTo(60);
    }

    @Test
    @DisplayName("a type that reports its own precision is left alone")
    void typeWithPrecision_isLeftAlone() throws Exception {
        Column column = columnOf("NUMBER", 10, 30, 60);

        invokePrivate("resetOracleColumnPrecision", new Class[] {Column.class}, column);

        assertThat(column.getPrecision()).isEqualTo(10);
    }

    @Test
    @DisplayName("a type in neither family is left without a precision")
    void typeInNeitherFamily_getsNoPrecision() throws Exception {
        Column column = columnOf("CLOB", null, 30, 60);

        invokePrivate("resetOracleColumnPrecision", new Class[] {Column.class}, column);

        assertThat(column.getPrecision()).isZero();
    }

    // Oracle's own session time zone is never read: the catalog is stamped with the zone of the
    // machine running CMT, so the same database yields a different stamp elsewhere.
    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("the catalog is stamped with the migrating machine's time zone, not Oracle's")
    @CsvSource({
        "UTC,              GMT+00:00",
        "Asia/Seoul,       GMT+09:00",
        "America/New_York, GMT-05:00",
    })
    void catalogTimezone_comesFromTheLocalMachine(String zoneId, String expected) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        Catalog catalog = new Catalog();

        invokePrivate("setCatalogTimezone", new Class[] {Catalog.class}, catalog);

        assertThat(catalog.getTimezone()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> kept")
    @DisplayName("only the eight privileges CUBRID can express are carried over")
    @ValueSource(
            strings = {"SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "INDEX", "EXECUTE", "ALL"})
    void supportedPrivilege_isCarriedOver(String privilege) throws Exception {
        assertThat(invokePrivate("isSupportPrivilege", new Class[] {String.class}, privilege))
                .isEqualTo(true);
    }

    // The comparison is exact, so an Oracle catalog that reports a privilege in any other
    // spelling - or one CUBRID has no grant for - is dropped rather than migrated.
    @ParameterizedTest(name = "[{index}] {0} -> dropped")
    @DisplayName("anything else, including a lower-case spelling, is dropped")
    @ValueSource(strings = {"REFERENCES", "DROP", "select", "Select", ""})
    void unsupportedPrivilege_isDropped(String privilege) throws Exception {
        assertThat(invokePrivate("isSupportPrivilege", new Class[] {String.class}, privilege))
                .isEqualTo(false);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("ALL is the one privilege whose name CUBRID spells differently")
    @CsvSource({
        "ALL,    ALL PRIVILEGES",
        // Everything else is passed through untouched, and the match is case-sensitive.
        "SELECT, SELECT",
        "all,    all",
    })
    void privilegeName_isRewrittenOnlyForAll(String oracle, String expected) throws Exception {
        assertThat(
                        invokePrivate(
                                "convertPrivilegeOracle2Cubrid",
                                new Class[] {String.class},
                                oracle))
                .isEqualTo(expected);
    }

    /**
     * These rules live in private helpers, reached the way TiberoSchemaFetcherTest reaches its own.
     */

    /** buildGrant() runs two queries in turn: table grants first, then view grants. */
    private static Schema buildGrantsFrom(String tablePrivilege, String viewPrivilege)
            throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement tableStmt = mock(PreparedStatement.class);
        PreparedStatement viewStmt = mock(PreparedStatement.class);
        ResultSet tableRows = resultSetOf(1);
        ResultSet viewRows = resultSetOf(1);
        when(conn.prepareStatement(anyString())).thenReturn(tableStmt, viewStmt);
        when(tableStmt.executeQuery()).thenReturn(tableRows);
        when(viewStmt.executeQuery()).thenReturn(viewRows);
        stubGrantRow(tableRows, "T1", tablePrivilege);
        stubGrantRow(viewRows, "V1", viewPrivilege);

        Catalog catalog = new Catalog();
        catalog.setName("ORCL");
        Schema schema = new Schema(catalog);
        schema.setName("HR");
        catalog.addSchema(schema);
        FETCHER.buildGrant(conn, catalog, schema, null);
        return schema;
    }

    private static void stubGrantRow(ResultSet rs, String objectName, String privilege)
            throws Exception {
        when(rs.getString("GRANTEE")).thenReturn("HR");
        when(rs.getString("OWNER")).thenReturn("SCOTT");
        when(rs.getString("TABLE_NAME")).thenReturn(objectName);
        when(rs.getString("GRANTOR")).thenReturn("SYS");
        when(rs.getString("GRANTABLE")).thenReturn("YES");
        when(rs.getString("PRIVILEGE")).thenReturn(privilege);
    }

    private static Object invokePrivate(String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = OracleSchemaFetcher.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(FETCHER, args);
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

    private static Column columnOf(
            String dataType, Integer precision, int charLength, int byteLength) {
        Column column = new Column();
        column.setName("C1");
        column.setDataType(dataType);
        column.setPrecision(precision);
        column.setCharLength(charLength);
        column.setByteLength(byteLength);
        return column;
    }
}
