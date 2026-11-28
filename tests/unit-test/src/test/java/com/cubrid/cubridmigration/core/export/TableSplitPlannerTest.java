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
package com.cubrid.cubridmigration.core.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.ParallelExportPlan;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.SkewResult;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.SplitBasis;
import com.cubrid.cubridmigration.core.export.TableSplitPlanner.SplitRange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@DisplayName("TableSplitPlanner")
class TableSplitPlannerTest {

    // Matches MigrationConfiguration's default so these tests reflect real behavior; individual
    // tests can pass a different value if they need to exercise the threshold itself.
    private static final double SKEW_RATIO_THRESHOLD = 3.0;

    private final TableSplitPlanner planner =
            new TableSplitPlanner(DatabaseType.MYSQL.getExportHelper());

    // ---------------------------------------------------------------
    // determineSplitColumn(): metadata-only, no DB round trip involved
    // ---------------------------------------------------------------

    @Test
    @DisplayName("single numeric PK column is used directly")
    void determineSplitColumn_singleNumericPk_returnsPkColumn() {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(id);
    }

    @Test
    @DisplayName("single non-numeric PK with no index anywhere yields no split column")
    void determineSplitColumn_singleNonNumericPkNoIndex_returnsNull() {
        Table table = createTable("orders");
        addColumn(table, "code", "VARCHAR");
        setSinglePk(table, "code");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    @Test
    @DisplayName("single non-numeric PK falls back to a usable index if one exists")
    void determineSplitColumn_singleNonNumericPkWithIndexFallback_returnsIndexColumn() {
        Table table = createTable("orders");
        addColumn(table, "code", "VARCHAR");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        setSinglePk(table, "code");
        addIndex(table, "ix_seq", true, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(seq);
    }

    @Test
    @DisplayName("composite PK: a usable index is preferred over the composite key itself")
    void determineSplitColumn_compositePkWithIndex_prefersIndexOverCompositePk() {
        Table table = createTable("order_items");
        addColumn(table, "tenant_id", "INT");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        setCompositePk(table, "tenant_id", "seq");
        addIndex(table, "ix_seq", true, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(seq);
    }

    @Test
    @DisplayName("composite PK with no usable index falls back to the leading PK column")
    void determineSplitColumn_compositePkNoIndex_returnsLeadingPkColumn() {
        Table table = createTable("order_items");
        Column tenantId = addColumn(table, "tenant_id", "INT");
        addColumn(table, "seq", "BIGINT");
        setCompositePk(table, "tenant_id", "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("composite PK whose leading column isn't splittable, and no index, yields nothing")
    void determineSplitColumn_compositePkLeadingColumnNotSplittable_returnsNull() {
        Table table = createTable("order_items");
        addColumn(table, "code", "VARCHAR");
        addColumn(table, "seq", "BIGINT");
        setCompositePk(table, "code", "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    @Test
    @DisplayName("no PK at all: a unique index is used")
    void determineSplitColumn_noPkWithUniqueIndex_returnsUniqueIndexColumn() {
        Table table = createTable("logs");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        addIndex(table, "ix_seq", true, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(seq);
    }

    @Test
    @DisplayName("no PK at all: falls back to a non-unique index if no unique one exists")
    void determineSplitColumn_noPkNonUniqueIndexOnly_returnsNonUniqueIndexColumn() {
        Table table = createTable("logs");
        Column createdAt = addNotNullColumn(table, "created_at", "DATE");
        addIndex(table, "ix_created_at", false, "created_at");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("no PK and no index at all: no usable split column")
    void determineSplitColumn_noPkNoIndex_returnsNull() {
        Table table = createTable("logs");
        addColumn(table, "message", "VARCHAR");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    @Test
    @DisplayName("a unique index is preferred over a non-unique one")
    void determineSplitColumn_prefersUniqueIndexOverNonUnique() {
        Table table = createTable("logs");
        addNotNullColumn(table, "created_at", "DATE");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        addIndex(table, "ix_created_at", false, "created_at");
        addIndex(table, "ix_seq", true, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(seq);
    }

    @Test
    @DisplayName("a composite (multi-column) index is skipped, same as a composite PK")
    void determineSplitColumn_compositeIndexIsSkipped() {
        Table table = createTable("logs");
        addColumn(table, "tenant_id", "INT");
        addColumn(table, "seq", "BIGINT");
        Column createdAt = addNotNullColumn(table, "created_at", "DATE");
        addIndex(table, "ux_tenant_seq", true, "tenant_id", "seq");
        addIndex(table, "ix_created_at", false, "created_at");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(createdAt);
    }

    @Test
    @DisplayName(
            "a nullable unique index column is skipped, since BETWEEN would drop its NULL rows")
    void determineSplitColumn_nullableUniqueIndexColumn_returnsNull() {
        Table table = createTable("logs");
        addColumn(table, "seq", "BIGINT"); // nullable by default
        addIndex(table, "ix_seq", true, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    @Test
    @DisplayName("a nullable non-unique index column is skipped as well")
    void determineSplitColumn_nullableNonUniqueIndexColumn_returnsNull() {
        Table table = createTable("logs");
        addColumn(table, "created_at", "DATE"); // nullable by default
        addIndex(table, "ix_created_at", false, "created_at");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    @Test
    @DisplayName("a nullable index column does not shadow a later NOT NULL one")
    void determineSplitColumn_nullableIndexBeforeNotNullIndex_returnsNotNullColumn() {
        Table table = createTable("logs");
        addColumn(table, "ref_id", "BIGINT"); // nullable by default
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        addIndex(table, "ix_ref_id", true, "ref_id");
        addIndex(table, "ix_seq", false, "seq");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(seq);
    }

    @Test
    @DisplayName("composite PK with only a nullable index falls back to the leading PK column")
    void determineSplitColumn_compositePkWithNullableIndexOnly_returnsLeadingPkColumn() {
        Table table = createTable("order_items");
        Column tenantId = addNotNullColumn(table, "tenant_id", "INT");
        addNotNullColumn(table, "seq", "BIGINT");
        addColumn(table, "ref_id", "BIGINT"); // nullable by default
        setCompositePk(table, "tenant_id", "seq");
        addIndex(table, "ix_ref_id", true, "ref_id");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("null source DB type yields no split column instead of guessing")
    void determineSplitColumn_nullDatabaseType_returnsNull() {
        Table table = createTable("orders");
        addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        assertThat(planner.determineSplitColumn(table, null)).isNull();
    }

    @Test
    @DisplayName(
            "regression: Oracle's INTERVAL YEAR TO MONTH is not misclassified as numeric just"
                    + " because its name contains \"INT\"")
    void determineSplitColumn_oracleIntervalTypeIsNotMisclassifiedAsNumeric() {
        Table table = createTable("contracts");
        addColumn(table, "duration", "INTERVAL YEAR TO MONTH");
        setSinglePk(table, "duration");

        assertThat(planner.determineSplitColumn(table, DatabaseType.ORACLE)).isNull();
    }

    @Test
    @DisplayName(
            "regression: MySQL's spatial POINT type is not misclassified as numeric just because"
                    + " its name contains \"INT\"")
    void determineSplitColumn_mysqlPointTypeIsNotMisclassifiedAsNumeric() {
        Table table = createTable("geo_features");
        addColumn(table, "location", "point");
        setSinglePk(table, "location");

        assertThat(planner.determineSplitColumn(table, DatabaseType.MYSQL)).isNull();
    }

    // ---------------------------------------------------------------
    // buildRangeCondition(): the WHERE predicate a scheduler ANDs onto a table's export SQL
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "numeric bounds are emitted as unquoted literals, lower inclusive and upper exclusive")
    void buildRangeCondition_numericBounds_emitsHalfOpenPredicateWithPlainLiterals() {
        Column id = new Column();
        id.setName("id");

        String condition = planner.buildRangeCondition(id, new SplitRange(1L, 1000L));

        assertThat(condition).isEqualTo("`id` >= 1 AND `id` < 1000");
    }

    @Test
    @DisplayName("an unbounded side is omitted from the predicate")
    void buildRangeCondition_unboundedSide_omitsThatComparison() {
        Column id = new Column();
        id.setName("id");

        assertThat(planner.buildRangeCondition(id, new SplitRange(null, 26L)))
                .isEqualTo("`id` < 26");
        assertThat(planner.buildRangeCondition(id, new SplitRange(76L, null)))
                .isEqualTo("`id` >= 76");
    }

    @Test
    @DisplayName("a range with no bounds at all matches every row")
    void buildRangeCondition_unboundedRange_matchesEverything() {
        Column id = new Column();
        id.setName("id");

        assertThat(planner.buildRangeCondition(id, SplitRange.unbounded())).isEqualTo("1 = 1");
    }

    @Test
    @DisplayName("BigDecimal bounds are written in plain notation, never exponent form")
    void buildRangeCondition_bigDecimalBounds_writesPlainNotation() {
        Column amount = new Column();
        amount.setName("amount");

        String condition =
                planner.buildRangeCondition(
                        amount, new SplitRange(new BigDecimal("1E+3"), new BigDecimal("2.50")));

        assertThat(condition).isEqualTo("`amount` >= 1000 AND `amount` < 2.50");
    }

    @Test
    @DisplayName("non-numeric bounds are quoted, and embedded quotes are escaped")
    void buildRangeCondition_nonNumericBounds_areQuotedAndEscaped() {
        Column code = new Column();
        code.setName("code");

        String condition = planner.buildRangeCondition(code, new SplitRange("A", "O'Brien"));

        assertThat(condition).isEqualTo("`code` >= 'A' AND `code` < 'O''Brien'");
    }

    // ---------------------------------------------------------------
    // calculateRanges(): MIN/MAX only, mocked JDBC objects
    // ---------------------------------------------------------------

    @Test
    @DisplayName("degree <= 1 short-circuits without touching the connection")
    void calculateRanges_degreeOne_returnsEmptyWithoutQuerying() throws Exception {
        Connection conn = mock(Connection.class);
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");

        assertThat(planner.calculateRanges(conn, table, id, 1)).isEmpty();
        verifyNoInteractions(conn);
    }

    @Test
    @DisplayName("null split column short-circuits without touching the connection")
    void calculateRanges_nullSplitColumn_returnsEmptyWithoutQuerying() throws Exception {
        Connection conn = mock(Connection.class);
        Table table = createTable("orders");

        assertThat(planner.calculateRanges(conn, table, null, 4)).isEmpty();
        verifyNoInteractions(conn);
    }

    @Test
    @DisplayName("integer MIN/MAX is split into contiguous half-open ranges with open outer ends")
    void calculateRanges_integerMinMax_splitsIntoRequestedRanges() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("BIGINT", 1L, 1000L, 4);

        assertThat(ranges).hasSize(4);
        assertThat(ranges.get(0).getLowerBound()).isNull();
        assertThat(ranges.get(ranges.size() - 1).getUpperBound()).isNull();
        // Each range starts exactly where the previous one ends - the shared boundary is what
        // guarantees neither a gap nor an overlap.
        for (int i = 1; i < ranges.size(); i++) {
            assertThat(ranges.get(i).getLowerBound()).isEqualTo(ranges.get(i - 1).getUpperBound());
        }
        assertThat(ranges.get(0).getUpperBound()).isEqualTo(251L);
        assertThat(ranges.get(1).getUpperBound()).isEqualTo(501L);
        assertThat(ranges.get(2).getUpperBound()).isEqualTo(751L);
    }

    @Test
    @DisplayName("[1, 100] in 4 ranges splits at 26, 51 and 76, the same cut points as before")
    void calculateRanges_oneToHundred_splitsAt26_51_76() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("INT", 1L, 100L, 4);

        assertThat(ranges)
                .extracting(range -> planner.buildRangeCondition(idColumn(), range))
                .containsExactly(
                        "`id` < 26",
                        "`id` >= 26 AND `id` < 51",
                        "`id` >= 51 AND `id` < 76",
                        "`id` >= 76");
    }

    @Test
    @DisplayName("fractional MIN/MAX is split on exact decimal boundaries instead of truncated")
    void calculateRanges_fractionalMinMax_splitsOnDecimalBoundaries() throws Exception {
        // Truncating through longValue() gave [0,2] [3,5] [6,8] [9,10] here, so 2.5, 5.5, 8.7
        // and 10.7 matched no range at all.
        List<SplitRange> ranges =
                calculateRangesFor("DECIMAL", new BigDecimal("0.5"), new BigDecimal("10.7"), 4);

        assertThat(ranges)
                .extracting(range -> planner.buildRangeCondition(idColumn(), range))
                .containsExactly(
                        "`id` < 3.05",
                        "`id` >= 3.05 AND `id` < 5.6",
                        "`id` >= 5.6 AND `id` < 8.15",
                        "`id` >= 8.15");
    }

    @Test
    @DisplayName(
            "every value, boundaries and out-of-MIN/MAX values included, is in exactly one range")
    void calculateRanges_anyValue_belongsToExactlyOneRange() throws Exception {
        List<SplitRange> ranges =
                calculateRangesFor("DECIMAL", new BigDecimal("0.5"), new BigDecimal("10.7"), 4);

        String[] samples = {
            "-1000", "0.5", "2.5", "3.05", "3.0499", "5.6", "5.5", "8.15", "8.7", "10.7", "10.71",
            "99999"
        };
        for (String sample : samples) {
            BigDecimal value = new BigDecimal(sample);
            long matches = ranges.stream().filter(range -> contains(range, value)).count();
            assertThat(matches).as("ranges containing %s", sample).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a BIGINT range spanning the whole long domain does not overflow")
    void calculateRanges_fullLongDomain_doesNotOverflow() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("BIGINT", Long.MIN_VALUE, Long.MAX_VALUE, 4);

        assertThat(ranges).hasSize(4);
        BigInteger previous = BigInteger.valueOf(Long.MIN_VALUE);
        for (int i = 0; i < ranges.size() - 1; i++) {
            BigInteger boundary = new BigInteger(String.valueOf(ranges.get(i).getUpperBound()));
            assertThat(boundary).isGreaterThan(previous);
            previous = boundary;
        }
        assertThat(previous).isLessThanOrEqualTo(BigInteger.valueOf(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("boundaries beyond the long range are BigDecimal, not BigInteger")
    void calculateRanges_boundsBeyondLong_returnsBigDecimalBounds() throws Exception {
        // e.g. an Oracle NUMBER(38) key: MIN/MAX arrive as BigDecimal and the cut points no longer
        // fit in a long.
        List<SplitRange> ranges =
                calculateRangesFor(
                        "NUMBER",
                        BigDecimal.ZERO,
                        new BigDecimal("100000000000000000000000000"),
                        4);

        assertThat(ranges).hasSize(4);
        assertThat(ranges.get(0).getUpperBound()).isInstanceOf(BigDecimal.class);
        assertThat(((BigDecimal) ranges.get(0).getUpperBound()).toPlainString())
                .isEqualTo("25000000000000000000000000");
        assertThat(planner.buildRangeCondition(idColumn(), ranges.get(1)))
                .isEqualTo(
                        "`id` >= 25000000000000000000000000 AND `id` < 50000000000000000000000000");
    }

    @Test
    @DisplayName("a span smaller than the degree yields fewer ranges, not empty ones")
    void calculateRanges_spanSmallerThanDegree_dropsEmptyRanges() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("INT", 1L, 3L, 4);

        assertThat(ranges)
                .extracting(range -> planner.buildRangeCondition(idColumn(), range))
                .containsExactly("`id` < 2", "`id` >= 2 AND `id` < 3", "`id` >= 3");
    }

    @Test
    @DisplayName("MIN equal to MAX (a single distinct value) is not split")
    void calculateRanges_singleDistinctValue_returnsOneUnboundedRange() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("INT", 7L, 7L, 4);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).getLowerBound()).isNull();
        assertThat(ranges.get(0).getUpperBound()).isNull();
    }

    @Test
    @DisplayName("a NaN MAX cannot be split and comes back as one unbounded range")
    void calculateRanges_nanBound_returnsOneUnboundedRange() throws Exception {
        List<SplitRange> ranges = calculateRangesFor("DOUBLE", 1.0d, Double.NaN, 4);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).getLowerBound()).isNull();
        assertThat(ranges.get(0).getUpperBound()).isNull();
    }

    @Test
    @DisplayName("an empty table (no MIN/MAX row) yields no ranges")
    void calculateRanges_noRows_returnsEmpty() throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertThat(planner.calculateRanges(conn, table, id, 4)).isEmpty();
    }

    @Test
    @DisplayName("non-numeric MIN/MAX (e.g. DATE) is not split, and comes back as one range")
    void calculateRanges_nonNumericMinMax_returnsSingleUnsplitRange() throws Exception {
        Table table = createTable("logs");
        Column createdAt = addColumn(table, "created_at", "DATE");

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getObject(1)).thenReturn("2020-01-01");
        when(rs.getObject(2)).thenReturn("2026-01-01");

        List<SplitRange> ranges = planner.calculateRanges(conn, table, createdAt, 4);

        assertThat(ranges).hasSize(1);
        assertThat(ranges.get(0).getLowerBound()).isNull();
        assertThat(ranges.get(0).getUpperBound()).isNull();
    }

    // ---------------------------------------------------------------
    // validateSkew(): one COUNT(*) per range, mocked JDBC objects
    // ---------------------------------------------------------------

    @Test
    @DisplayName("no ranges is trivially acceptable and never touches the connection")
    void validateSkew_emptyRanges_isAcceptable() throws Exception {
        Connection conn = mock(Connection.class);
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");

        SkewResult result =
                planner.validateSkew(
                        conn, table, id, Collections.emptyList(), SKEW_RATIO_THRESHOLD);

        assertThat(result.isAcceptable()).isTrue();
        assertThat(result.getRangeCounts()).isEmpty();
        verifyNoInteractions(conn);
    }

    @Test
    @DisplayName("evenly distributed row counts across ranges are acceptable")
    void validateSkew_uniformCounts_isAcceptable() throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");
        List<SplitRange> ranges =
                Arrays.asList(
                        new SplitRange(1L, 250L),
                        new SplitRange(251L, 500L),
                        new SplitRange(501L, 750L),
                        new SplitRange(751L, 1000L));

        ResultSet rs1 = countResultSet(25L);
        ResultSet rs2 = countResultSet(25L);
        ResultSet rs3 = countResultSet(25L);
        ResultSet rs4 = countResultSet(25L);

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2, rs3, rs4);

        SkewResult result = planner.validateSkew(conn, table, id, ranges, SKEW_RATIO_THRESHOLD);

        assertThat(result.isAcceptable()).isTrue();
        assertThat(result.getSkewRatio()).isCloseTo(1.0, offset(0.0001));
    }

    @Test
    @DisplayName("a heavily skewed distribution (90/2/2/6) is flagged as not acceptable")
    void validateSkew_skewedCounts_isNotAcceptable() throws Exception {
        Table table = createTable("order_items");
        Column tenantId = addColumn(table, "tenant_id", "INT");
        List<SplitRange> ranges =
                Arrays.asList(
                        new SplitRange(1, 25), new SplitRange(26, 50),
                        new SplitRange(51, 75), new SplitRange(76, 100));

        ResultSet rs1 = countResultSet(90L);
        ResultSet rs2 = countResultSet(2L);
        ResultSet rs3 = countResultSet(2L);
        ResultSet rs4 = countResultSet(6L);

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2, rs3, rs4);

        SkewResult result =
                planner.validateSkew(conn, table, tenantId, ranges, SKEW_RATIO_THRESHOLD);

        assertThat(result.isAcceptable()).isFalse();
        assertThat(result.getSkewRatio()).isCloseTo(3.6, offset(0.0001));
    }

    @Test
    @DisplayName("a stricter (lower) threshold can reject a split a looser one would accept")
    void validateSkew_stricterThreshold_rejectsSplitLooserThresholdWouldAccept() throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");
        List<SplitRange> ranges =
                Arrays.asList(new SplitRange(1L, 500L), new SplitRange(501L, 1000L));

        ResultSet rs1 = countResultSet(80L);
        ResultSet rs2 = countResultSet(20L);

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2);

        // ratio = 80 / 50 = 1.6: acceptable under the default 3.0 threshold, not under 1.5.
        SkewResult result = planner.validateSkew(conn, table, id, ranges, 1.5);

        assertThat(result.isAcceptable()).isFalse();
    }

    @Test
    @DisplayName(
            "each COUNT(*) uses the same half-open predicate as the export, bound as parameters")
    void validateSkew_rangesWithOpenSides_bindsOnlyPresentBounds() throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");
        List<SplitRange> ranges =
                Arrays.asList(
                        new SplitRange(null, 26L),
                        new SplitRange(26L, 51L),
                        new SplitRange(51L, null));

        ResultSet rs1 = countResultSet(10L);
        ResultSet rs2 = countResultSet(10L);
        ResultSet rs3 = countResultSet(10L);

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2, rs3);

        planner.validateSkew(conn, table, id, ranges, SKEW_RATIO_THRESHOLD);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn, org.mockito.Mockito.times(3)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues())
                .containsExactly(
                        "SELECT COUNT(*) FROM `owner`.`orders` WHERE `id` < ?",
                        "SELECT COUNT(*) FROM `owner`.`orders` WHERE `id` >= ? AND `id` < ?",
                        "SELECT COUNT(*) FROM `owner`.`orders` WHERE `id` >= ?");
        // First range binds only its upper bound, the last only its lower bound.
        verify(ps, org.mockito.Mockito.times(2)).setObject(1, 26L);
        verify(ps).setObject(2, 51L);
        verify(ps).setObject(1, 51L);
        verify(ps, never()).setObject(2, null);
    }

    // ---------------------------------------------------------------
    // plan(): the end-to-end entry point
    // ---------------------------------------------------------------

    @Test
    @DisplayName("requested degree <= 1 disables parallel export without touching the connection")
    void plan_requestedDegreeOne_disabled() throws Exception {
        Connection conn = mock(Connection.class);
        Table table = createTable("orders");
        addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 1, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isFalse();
        assertThat(plan.getDisabledReason()).isNotBlank();
        verifyNoInteractions(conn);
    }

    @Test
    @DisplayName("no usable split column disables parallel export without touching the connection")
    void plan_noSplitColumn_disabled() throws Exception {
        Connection conn = mock(Connection.class);
        Table table = createTable("logs");
        addColumn(table, "message", "VARCHAR");

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 4, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isFalse();
        assertThat(plan.getDisabledReason()).isNotBlank();
        verifyNoInteractions(conn);
    }

    @Test
    @DisplayName("a table with a single distinct split value can't be split, and is disabled")
    void plan_singleComputedRange_disabled() throws Exception {
        Table table = createTable("orders");
        addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        ResultSet minMaxRs = countResultSet(5L, 5L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 4, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isFalse();
        assertThat(plan.getDisabledReason()).isNotBlank();
        verify(conn, never()).prepareStatement(anyString());
    }

    @Test
    @DisplayName("a heavily skewed split disables parallel export, even though ranges exist")
    void plan_skewedRanges_disabled() throws Exception {
        Table table = createTable("orders");
        addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        ResultSet minMaxRs = countResultSet(1L, 1000L);
        ResultSet rs1 = countResultSet(900L);
        ResultSet rs2 = countResultSet(33L);
        ResultSet rs3 = countResultSet(33L);
        ResultSet rs4 = countResultSet(34L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2, rs3, rs4);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 4, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isFalse();
        assertThat(plan.getDisabledReason()).isNotBlank();
    }

    @Test
    @DisplayName("a well-distributed numeric PK produces an enabled plan with 4 ranges")
    void plan_allChecksPass_returnsEnabledPlan() throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", "BIGINT");
        setSinglePk(table, "id");

        ResultSet minMaxRs = countResultSet(1L, 1000L);
        ResultSet rs1 = countResultSet(25L);
        ResultSet rs2 = countResultSet(25L);
        ResultSet rs3 = countResultSet(25L);
        ResultSet rs4 = countResultSet(25L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2, rs3, rs4);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 4, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isTrue();
        assertThat(plan.getSplitColumn()).isEqualTo(id);
        assertThat(plan.getSplitBasis()).isEqualTo(SplitBasis.SINGLE_COLUMN_PK);
        assertThat(plan.getRanges()).hasSize(4);
        assertThat(plan.getSkewResult().isAcceptable()).isTrue();
        assertThat(plan.getDisabledReason()).isNull();
    }

    @Test
    @DisplayName("plan() reports UNIQUE_INDEX as the basis when that's what was used")
    void plan_unusedPkFallsBackToUniqueIndex_reportsUniqueIndexBasis() throws Exception {
        Table table = createTable("logs");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        addIndex(table, "ix_seq", true, "seq");

        ResultSet minMaxRs = countResultSet(1L, 100L);
        ResultSet rs1 = countResultSet(50L);
        ResultSet rs2 = countResultSet(50L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 2, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isTrue();
        assertThat(plan.getSplitColumn()).isEqualTo(seq);
        assertThat(plan.getSplitBasis()).isEqualTo(SplitBasis.UNIQUE_INDEX);
    }

    @Test
    @DisplayName("plan() reports NON_UNIQUE_INDEX as the basis when that's what was used")
    void plan_noPkNoUniqueIndex_reportsNonUniqueIndexBasis() throws Exception {
        Table table = createTable("logs");
        Column seq = addNotNullColumn(table, "seq", "BIGINT");
        addIndex(table, "ix_seq", false, "seq");

        ResultSet minMaxRs = countResultSet(1L, 100L);
        ResultSet rs1 = countResultSet(50L);
        ResultSet rs2 = countResultSet(50L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 2, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isTrue();
        assertThat(plan.getSplitColumn()).isEqualTo(seq);
        assertThat(plan.getSplitBasis()).isEqualTo(SplitBasis.NON_UNIQUE_INDEX);
    }

    @Test
    @DisplayName(
            "plan() reports COMPOSITE_PK_LEADING_COLUMN as the basis when no index was available")
    void plan_compositePkNoIndex_reportsCompositePkLeadingColumnBasis() throws Exception {
        Table table = createTable("order_items");
        Column tenantId = addColumn(table, "tenant_id", "INT");
        addColumn(table, "seq", "BIGINT");
        setCompositePk(table, "tenant_id", "seq");

        ResultSet minMaxRs = countResultSet(1L, 100L);
        ResultSet rs1 = countResultSet(50L);
        ResultSet rs2 = countResultSet(50L);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(minMaxRs);

        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs1, rs2);

        ParallelExportPlan plan =
                planner.plan(conn, table, DatabaseType.MYSQL, 2, SKEW_RATIO_THRESHOLD);

        assertThat(plan.isParallelizable()).isTrue();
        assertThat(plan.getSplitColumn()).isEqualTo(tenantId);
        assertThat(plan.getSplitBasis()).isEqualTo(SplitBasis.COMPOSITE_PK_LEADING_COLUMN);
    }

    // ---------------------------------------------------------------
    // identifier quoting: every name in the generated SQL goes through the dialect's quoting
    // ---------------------------------------------------------------

    /** Dialect, opening quote, closing quote - as the export queries quote identifiers. */
    private static Stream<Arguments> quotingDialects() {
        return Stream.of(
                Arguments.of(DatabaseType.ORACLE, "\"", "\""),
                Arguments.of(DatabaseType.TIBERO, "\"", "\""),
                Arguments.of(DatabaseType.MYSQL, "`", "`"),
                Arguments.of(DatabaseType.MARIADB, "`", "`"),
                Arguments.of(DatabaseType.MSSQL, "[", "]"),
                Arguments.of(DatabaseType.CUBRID, "[", "]"),
                // The Informix export helper deliberately leaves names unquoted.
                Arguments.of(DatabaseType.INFORMIX, "", ""));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("the split column in a range predicate is quoted the way the dialect quotes it")
    @MethodSource("quotingDialects")
    void buildRangeCondition_columnNeedingQuotes_isQuotedForTheDialect(
            DatabaseType dialect, String open, String close) {
        TableSplitPlanner dialectPlanner = new TableSplitPlanner(dialect.getExportHelper());
        Column column = new Column();
        column.setName("Order Id");
        String quoted = open + "Order Id" + close;

        assertThat(dialectPlanner.buildRangeCondition(column, new SplitRange(1L, 10L)))
                .isEqualTo(quoted + " >= 1 AND " + quoted + " < 10");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("MIN/MAX query quotes the split column, the owner and the table")
    @MethodSource("quotingDialects")
    void calculateRanges_namesNeedingQuotes_quotesColumnOwnerAndTable(
            DatabaseType dialect, String open, String close) throws Exception {
        TableSplitPlanner dialectPlanner = new TableSplitPlanner(dialect.getExportHelper());
        Table table = createTable("Order Items");
        table.setOwner("Sales Dept");
        Column column = addColumn(table, "order", "BIGINT");

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        ResultSet minMax = countResultSet(1L, 100L);
        when(stmt.executeQuery(anyString())).thenReturn(minMax);

        dialectPlanner.calculateRanges(conn, table, column, 4);

        String col = open + "order" + close;
        String tableRef = open + "Sales Dept" + close + "." + open + "Order Items" + close;
        verify(stmt).executeQuery("SELECT MIN(" + col + "), MAX(" + col + ") FROM " + tableRef);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("skew COUNT query quotes the split column, the owner and the table")
    @MethodSource("quotingDialects")
    void validateSkew_namesNeedingQuotes_quotesColumnOwnerAndTable(
            DatabaseType dialect, String open, String close) throws Exception {
        TableSplitPlanner dialectPlanner = new TableSplitPlanner(dialect.getExportHelper());
        Table table = createTable("Order Items");
        table.setOwner("Sales Dept");
        Column column = addColumn(table, "order", "BIGINT");

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        ResultSet count = countResultSet(10L);
        when(ps.executeQuery()).thenReturn(count);

        dialectPlanner.validateSkew(
                conn,
                table,
                column,
                Collections.singletonList(new SplitRange(null, 26L)),
                SKEW_RATIO_THRESHOLD);

        String tableRef = open + "Sales Dept" + close + "." + open + "Order Items" + close;
        verify(conn)
                .prepareStatement(
                        "SELECT COUNT(*) FROM "
                                + tableRef
                                + " WHERE "
                                + open
                                + "order"
                                + close
                                + " < ?");
    }

    @Test
    @DisplayName("a table without an owner is not prefixed with an empty qualifier")
    void calculateRanges_tableWithoutOwner_omitsTheQualifier() throws Exception {
        Table table = createTable("orders");
        table.setOwner(null);
        Column id = addColumn(table, "id", "BIGINT");

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        ResultSet minMax = countResultSet(1L, 100L);
        when(stmt.executeQuery(anyString())).thenReturn(minMax);

        planner.calculateRanges(conn, table, id, 4);

        verify(stmt).executeQuery("SELECT MIN(`id`), MAX(`id`) FROM `orders`");
    }

    // ---------------------------------------------------------------
    // fixture helpers
    // ---------------------------------------------------------------

    private static Column idColumn() {
        Column id = new Column();
        id.setName("id");
        return id;
    }

    /** Runs calculateRanges() against a mocked MIN/MAX row of the given values. */
    private List<SplitRange> calculateRangesFor(String dataType, Object min, Object max, int degree)
            throws Exception {
        Table table = createTable("orders");
        Column id = addColumn(table, "id", dataType);

        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getObject(1)).thenReturn(min);
        when(rs.getObject(2)).thenReturn(max);

        return planner.calculateRanges(conn, table, id, degree);
    }

    /** True if {@code value} satisfies the range's half-open predicate. */
    private static boolean contains(SplitRange range, BigDecimal value) {
        boolean aboveLower =
                range.getLowerBound() == null
                        || value.compareTo(new BigDecimal(String.valueOf(range.getLowerBound())))
                                >= 0;
        boolean belowUpper =
                range.getUpperBound() == null
                        || value.compareTo(new BigDecimal(String.valueOf(range.getUpperBound())))
                                < 0;
        return aboveLower && belowUpper;
    }

    private static Table createTable(String name) {
        Table table = new Table();
        table.setName(name);
        table.setOwner("owner");
        return table;
    }

    private static Column addColumn(Table table, String name, String dataType) {
        Column column = new Column();
        column.setName(name);
        column.setDataType(dataType);
        table.addColumn(column);
        return column;
    }

    private static Column addNotNullColumn(Table table, String name, String dataType) {
        Column column = addColumn(table, name, dataType);
        column.setNullable(false);
        return column;
    }

    private static void setSinglePk(Table table, String columnName) {
        PK pk = new PK(table);
        pk.addColumn(columnName);
        table.setPk(pk);
    }

    private static void setCompositePk(Table table, String... columnNames) {
        PK pk = new PK(table);
        for (String columnName : columnNames) {
            pk.addColumn(columnName);
        }
        table.setPk(pk);
    }

    private static void addIndex(
            Table table, String indexName, boolean unique, String... columnNames) {
        Index index = new Index(table);
        index.setName(indexName);
        index.setUnique(unique);
        for (String columnName : columnNames) {
            index.addColumn(columnName, true);
        }
        table.addIndex(index);
    }

    /** A ResultSet mock that returns a single row containing {@code value} in column 1. */
    private static ResultSet countResultSet(long value) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getLong(1)).thenReturn(value);
        return rs;
    }

    /** A ResultSet mock that returns a single row containing {@code min}/{@code max}. */
    private static ResultSet countResultSet(long min, long max) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getObject(1)).thenReturn(min);
        when(rs.getObject(2)).thenReturn(max);
        return rs;
    }
}
