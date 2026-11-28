/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.core.export;

import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides whether a table's records can be exported by multiple parallel range workers instead of
 * the current one-thread-per-table model, and computes the value ranges those workers would use.
 *
 * <p>Every identifier the planner puts into SQL (table, owner, split column) is quoted by the
 * source database's {@link DBExportHelper}, the same way the export queries quote them, so names
 * that are reserved words, contain spaces or are case-sensitive work as they do in a normal export.
 *
 * <p>The skew ratio threshold used by {@link #validateSkew} and {@link #plan} is intentionally not
 * a constant here - callers should read it from {@code MigrationConfiguration
 * #getParallelExportSkewRatioThreshold()} so it stays a single user/ops-tunable knob instead of a
 * value baked into this class.
 *
 * <p>Split key priority, cheapest/safest first:
 *
 * <ol>
 *   <li>Single-column PK
 *   <li>If the PK is composite, a single-column index (unique preferred over non-unique)
 *   <li>If no usable index exists either, the composite PK's leading column (accepted even though
 *       it may distribute unevenly)
 * </ol>
 */
public class TableSplitPlanner {

    /**
     * Column type names (already stripped of precision/scale, lowercased) accepted as range-split
     * keys alongside whatever {@link DBDataTypeHelper#isGeneralizedNumeric} accepts. There is no
     * shared date/time classifier in {@link DBDataTypeHelper} the way there is for numerics, so
     * this is an explicit allow-list rather than a substring match - see the false positives a
     * naive {@code contains("INT")}/{@code contains("DATE")} check produces (Oracle's "INTERVAL
     * YEAR TO MONTH" contains "INT"; MySQL's spatial "POINT" type also contains "INT").
     */
    private static final Set<String> DATE_TIME_TYPES =
            new HashSet<String>(
                    Arrays.asList(
                            "date",
                            "time",
                            "datetime",
                            "datetime2",
                            "smalldatetime",
                            "timestamp",
                            "timestamptz",
                            "datetimetz",
                            "timestampltz",
                            "datetimeltz"));

    private final DBExportHelper exportHelper;

    /**
     * @param exportHelper the source database's export helper, used to quote identifiers in the SQL
     *     this planner generates
     */
    public TableSplitPlanner(DBExportHelper exportHelper) {
        this.exportHelper = exportHelper;
    }

    /**
     * Picks the column to split on, following the priority described in the class doc. Metadata
     * only - no DB round trip - so this is cheap enough to call for every table in a table list UI.
     *
     * @param table source table metadata
     * @param sourceDbType the migration's source database type, used to obtain the right {@link
     *     DBDataTypeHelper} for classifying this table's raw column type strings
     * @return the column to split on, or {@code null} if this table has no usable split key
     */
    public Column determineSplitColumn(Table table, DatabaseType sourceDbType) {
        SplitColumnChoice choice = chooseSplitColumn(table, sourceDbType);
        return choice == null ? null : choice.getColumn();
    }

    /**
     * Same decision as {@link #determineSplitColumn}, but also reports *why* this column was picked
     * (PK vs. index vs. composite-PK fallback) - see {@link SplitBasis}. {@link #plan} uses this so
     * the basis can be logged/shown alongside the split column.
     *
     * @param table source table metadata
     * @param sourceDbType the migration's source database type
     * @return the chosen column plus its basis, or {@code null} if no usable split key exists
     */
    private SplitColumnChoice chooseSplitColumn(Table table, DatabaseType sourceDbType) {
        if (table == null || sourceDbType == null) {
            return null;
        }
        DBDataTypeHelper dtHelper = sourceDbType.getDataTypeHelper(null);

        PK pk = table.getPk();
        if (pk == null || CollectionUtils.isEmpty(pk.getPkColumns())) {
            // No PK at all: the only remaining candidates are indexes.
            return findIndexColumnChoice(table, dtHelper);
        }

        List<String> pkColumns = pk.getPkColumns();
        if (pkColumns.size() == 1) {
            Column pkColumn = table.getColumnByName(pkColumns.get(0));
            if (isSplittableType(pkColumn, dtHelper)) {
                return new SplitColumnChoice(pkColumn, SplitBasis.SINGLE_COLUMN_PK);
            }
            // Single PK column exists but isn't a splittable type (e.g. VARCHAR PK) - fall
            // through and see if an index gives us a better candidate.
            return findIndexColumnChoice(table, dtHelper);
        }

        // Composite PK: prefer an index over the composite key itself, per the agreed priority.
        SplitColumnChoice indexChoice = findIndexColumnChoice(table, dtHelper);
        if (indexChoice != null) {
            return indexChoice;
        }

        // Last resort: the composite PK's leading column, accepting possible skew.
        Column leadingColumn = table.getColumnByName(pkColumns.get(0));
        return isSplittableType(leadingColumn, dtHelper)
                ? new SplitColumnChoice(leadingColumn, SplitBasis.COMPOSITE_PK_LEADING_COLUMN)
                : null;
    }

    /**
     * Finds the best single-column index to split on: unique indexes are preferred over non-unique
     * ones. Composite indexes are skipped for the same reason composite PKs are skipped - a
     * multi-column range predicate is out of scope for this planner.
     *
     * <p>Columns that allow NULL are skipped as well: the range predicate ({@code col BETWEEN lo
     * AND hi}) never matches a NULL, so splitting on such a column would silently drop every row
     * whose value is NULL. PK columns are NOT NULL by definition, so only this index path needs the
     * check.
     *
     * <p>TODO: index cardinality/statistics aren't tracked anywhere in this codebase's metadata, so
     * this can't distinguish a high-cardinality index from a low-cardinality one. {@link
     * #validateSkew} is the actual safety net for that - this method only filters by shape
     * (single-column, splittable type).
     *
     * @param table source table metadata
     * @param dtHelper the source dialect's data type helper, used to classify column types
     * @return the best candidate column plus its basis (unique vs. non-unique index), or {@code
     *     null} if none qualify
     */
    private SplitColumnChoice findIndexColumnChoice(Table table, DBDataTypeHelper dtHelper) {
        Column uniqueCandidate = null;
        Column nonUniqueCandidate = null;

        for (Index index : table.getIndexes()) {
            List<String> columnNames = index.getColumnNames();
            if (columnNames.size() != 1) {
                continue;
            }
            Column column = table.getColumnByName(columnNames.get(0));
            if (!isSplittableType(column, dtHelper)) {
                continue;
            }
            if (column.isNullable()) {
                continue;
            }
            if (index.isUnique()) {
                if (uniqueCandidate == null) {
                    uniqueCandidate = column;
                }
            } else if (nonUniqueCandidate == null) {
                nonUniqueCandidate = column;
            }
        }

        if (uniqueCandidate != null) {
            return new SplitColumnChoice(uniqueCandidate, SplitBasis.UNIQUE_INDEX);
        }
        if (nonUniqueCandidate != null) {
            return new SplitColumnChoice(nonUniqueCandidate, SplitBasis.NON_UNIQUE_INDEX);
        }
        return null;
    }

    /**
     * Checks "does this column's type make sense as a range-split key": sequential/auto-increment
     * integers and date/time columns are accepted; low-cardinality or unordered types (VARCHAR,
     * CHAR, BOOLEAN, ENUM, spatial types, ...) are rejected.
     *
     * <p>Delegates the numeric check to {@link DBDataTypeHelper#isGeneralizedNumeric}, which does
     * an exact match against a per-dialect type list rather than a substring search - this matters
     * because a naive {@code contains("INT")} check misclassifies e.g. Oracle's "INTERVAL YEAR TO
     * MONTH" (contains "INT") and MySQL's spatial "POINT"/"MULTIPOINT" types (contain "INT") as
     * numeric. The date/time check below uses the same exact-match discipline via {@link
     * #DATE_TIME_TYPES}, since {@link DBDataTypeHelper} has no shared date/time classifier.
     *
     * @param column candidate column, may be {@code null}
     * @param dtHelper the source dialect's data type helper
     * @return true if this column's type is a reasonable range-split key
     */
    private boolean isSplittableType(Column column, DBDataTypeHelper dtHelper) {
        if (column == null || StringUtils.isBlank(column.getDataType())) {
            return false;
        }
        String rawType = column.getDataType();
        if (dtHelper.isGeneralizedNumeric(rawType)) {
            return true;
        }
        String mainType = dtHelper.getMainDataType(rawType).toLowerCase(Locale.US);
        return DATE_TIME_TYPES.contains(mainType);
    }

    /**
     * Computes up to {@code degree} non-overlapping, gap-free value ranges that together cover
     * every value of the split column (see {@link SplitRange} for the half-open convention).
     * MIN/MAX only - no COUNT(*) - so this stays cheap regardless of table size.
     *
     * <p>Only numeric split columns are actually split here; DATE/TIMESTAMP columns need
     * dialect-specific arithmetic and currently fall back to a single unsplit range (see {@link
     * #splitIntoRanges}).
     *
     * @param conn a short-lived connection to the source database, owned by the caller
     * @param table source table metadata
     * @param splitColumn column returned by {@link #determineSplitColumn(Table, DatabaseType)}
     * @param degree how many ranges to produce (must be &gt;= 2)
     * @return the computed ranges, or an empty list if the table has no rows / degree &lt;= 1
     * @throws SQLException if the MIN/MAX query fails
     */
    public List<SplitRange> calculateRanges(
            Connection conn, Table table, Column splitColumn, int degree) throws SQLException {
        if (degree <= 1 || splitColumn == null) {
            return Collections.emptyList();
        }

        String quotedColumn = quotedColumnName(splitColumn);
        String sql =
                "SELECT MIN("
                        + quotedColumn
                        + "), MAX("
                        + quotedColumn
                        + ") FROM "
                        + qualifiedTableName(table);

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            Object min = rs.getObject(1);
            Object max = rs.getObject(2);
            if (min == null || max == null) {
                return Collections.emptyList();
            }
            return splitIntoRanges(min, max, degree);
        }
    }

    /**
     * Splits [min, max] into up to {@code degree} equal-width ranges by value, not by row count -
     * see the earlier discussion on why MIN/MAX splitting is cheap but can be skewed if the
     * column's values aren't uniformly distributed. {@link #validateSkew} exists to catch that.
     *
     * <p>Boundaries are computed with {@link BigInteger}/{@link BigDecimal}, never {@code long}:
     * truncating through {@code longValue()} dropped every fractional value (DECIMAL/FLOAT/NUMBER
     * columns), and {@code max - min + 1} overflowed for very wide BIGINT ranges. The resulting
     * ranges are half-open and the outer two are unbounded (see {@link SplitRange}), so no value -
     * including one inserted after MIN/MAX was read - can fall between ranges.
     */
    private List<SplitRange> splitIntoRanges(Object min, Object max, int degree) {
        if (!(min instanceof Number) || !(max instanceof Number)) {
            // TODO: DATE/TIMESTAMP range splitting isn't implemented - treat as unsplittable
            // for now rather than risk an incorrect boundary.
            return Collections.singletonList(SplitRange.unbounded());
        }

        BigDecimal lo = toBigDecimal((Number) min);
        BigDecimal hi = toBigDecimal((Number) max);
        if (lo == null || hi == null || hi.compareTo(lo) <= 0) {
            // NaN/Infinity, or a single distinct value: nothing to split.
            return Collections.singletonList(SplitRange.unbounded());
        }

        List<Object> boundaries =
                (isIntegral(lo) && isIntegral(hi))
                        ? integerBoundaries(lo.toBigIntegerExact(), hi.toBigIntegerExact(), degree)
                        : decimalBoundaries(lo, hi, degree);
        if (boundaries.isEmpty()) {
            return Collections.singletonList(SplitRange.unbounded());
        }

        List<SplitRange> ranges = new ArrayList<SplitRange>();
        Object lower = null;
        for (Object boundary : boundaries) {
            ranges.add(new SplitRange(lower, boundary));
            lower = boundary;
        }
        ranges.add(new SplitRange(lower, null));
        return ranges;
    }

    /**
     * Boundaries for integer-valued MIN/MAX: {@code lo + i * (hi - lo + 1) / degree}, so e.g. [1,
     * 100] in 4 ranges splits at 26, 51 and 76. Boundaries that would not advance (a span smaller
     * than the degree) are dropped rather than producing empty ranges.
     */
    private List<Object> integerBoundaries(BigInteger lo, BigInteger hi, int degree) {
        BigInteger span = hi.subtract(lo).add(BigInteger.ONE);
        BigInteger degreeValue = BigInteger.valueOf(degree);
        List<Object> boundaries = new ArrayList<Object>();
        BigInteger previous = lo;
        for (int i = 1; i < degree; i++) {
            BigInteger boundary = lo.add(span.multiply(BigInteger.valueOf(i)).divide(degreeValue));
            if (boundary.compareTo(previous) > 0 && boundary.compareTo(hi) <= 0) {
                boundaries.add(narrow(boundary));
                previous = boundary;
            }
        }
        return boundaries;
    }

    /**
     * Boundaries for fractional MIN/MAX: {@code lo + i * (hi - lo) / degree}, rounded to two more
     * decimal places than the inputs carry. Rounding only moves a boundary slightly; correctness
     * does not depend on it because every range shares its neighbour's exact boundary value.
     */
    private List<Object> decimalBoundaries(BigDecimal lo, BigDecimal hi, int degree) {
        BigDecimal width = hi.subtract(lo);
        BigDecimal degreeValue = BigDecimal.valueOf(degree);
        int scale = Math.max(Math.max(lo.scale(), hi.scale()), 0) + 2;
        List<Object> boundaries = new ArrayList<Object>();
        BigDecimal previous = lo;
        for (int i = 1; i < degree; i++) {
            BigDecimal boundary =
                    lo.add(
                                    width.multiply(BigDecimal.valueOf(i))
                                            .divide(degreeValue, scale, RoundingMode.HALF_UP))
                            .stripTrailingZeros();
            if (boundary.compareTo(previous) > 0 && boundary.compareTo(hi) <= 0) {
                boundaries.add(boundary);
                previous = boundary;
            }
        }
        return boundaries;
    }

    /**
     * Keeps boundaries that fit in a {@code long} as {@link Long}; wider ones become {@link
     * BigDecimal}, not {@link BigInteger}: BigDecimal is the standard JDBC mapping for NUMERIC
     * values, while {@code setObject(BigInteger)} is not supported by every driver.
     */
    private static Object narrow(BigInteger value) {
        return value.bitLength() < Long.SIZE
                ? (Object) Long.valueOf(value.longValue())
                : new BigDecimal(value);
    }

    private static boolean isIntegral(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    /**
     * @return the exact decimal value of {@code number}, or {@code null} for NaN, infinities and
     *     Number types whose text form is not a plain decimal
     */
    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        }
        if (number instanceof Long
                || number instanceof Integer
                || number instanceof Short
                || number instanceof Byte) {
            return BigDecimal.valueOf(number.longValue());
        }
        try {
            // Double/Float: go through the shortest text form (0.1 -> "0.1"), not the binary
            // expansion, so the boundaries stay readable. NaN/Infinity fail to parse -> null.
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Runs one lightweight {@code COUNT(*)} per range (an index range scan, not a full table scan)
     * and reports how skewed the row distribution across ranges is.
     *
     * <p>Cost note: summed across all ranges this is roughly one index scan's worth of work, paid
     * once as a validation step - see the earlier discussion on why this is acceptable even though
     * using COUNT(*) to *build* ranges in the first place would not be.
     *
     * @param conn a short-lived connection to the source database, owned by the caller
     * @param table source table metadata
     * @param splitColumn column the ranges were computed against
     * @param ranges ranges from {@link #calculateRanges}
     * @param skewRatioThreshold above this max/ideal row-count ratio a split is considered too
     *     skewed to be worth parallelizing; read this from {@code MigrationConfiguration
     *     #getParallelExportSkewRatioThreshold()} rather than hardcoding it at the call site
     * @return per-range counts plus a skew verdict
     * @throws SQLException if a COUNT(*) query fails
     */
    public SkewResult validateSkew(
            Connection conn,
            Table table,
            Column splitColumn,
            List<SplitRange> ranges,
            double skewRatioThreshold)
            throws SQLException {
        if (CollectionUtils.isEmpty(ranges)) {
            return new SkewResult(Collections.<Long>emptyList(), 0d, true);
        }

        // The predicate comes from the same builder the export tasks use, so each COUNT(*) is
        // exactly the set of rows that range's task will read. Bounds are bound as parameters; a
        // range's unbounded side has no placeholder, so the statement differs per range.
        String countPrefix = "SELECT COUNT(*) FROM " + qualifiedTableName(table) + " WHERE ";
        String quotedColumn = quotedColumnName(splitColumn);

        List<Long> counts = new ArrayList<Long>();
        long total = 0;
        for (SplitRange range : ranges) {
            String sql = countPrefix + rangePredicate(quotedColumn, range, b -> "?");
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                if (range.getLowerBound() != null) {
                    ps.setObject(index++, range.getLowerBound());
                }
                if (range.getUpperBound() != null) {
                    ps.setObject(index, range.getUpperBound());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    long count = rs.next() ? rs.getLong(1) : 0;
                    counts.add(count);
                    total += count;
                }
            }
        }

        double idealPerRange = (double) total / ranges.size();
        double maxCount = Collections.max(counts);
        double skewRatio = idealPerRange <= 0 ? 0 : maxCount / idealPerRange;
        boolean acceptable = skewRatio <= skewRatioThreshold;
        return new SkewResult(counts, skewRatio, acceptable);
    }

    /**
     * Runs the full decision: pick a split column, compute ranges, validate skew. This is the entry
     * point the runtime scheduler should call right before creating export tasks for a table; the
     * UI's per-table badge should call only {@link #determineSplitColumn(Table, DatabaseType)}
     * instead, since it doesn't need (and shouldn't pay for) a DB round trip for every table in the
     * list.
     *
     * @param conn a short-lived connection to the source database, owned by the caller
     * @param table source table metadata
     * @param sourceDbType the migration's source database type
     * @param requestedDegree how many parallel workers the user asked for
     * @param skewRatioThreshold see {@link #validateSkew}; read this from {@code
     *     MigrationConfiguration#getParallelExportSkewRatioThreshold()}
     * @return a plan that is either enabled with computed ranges, or disabled with a reason
     * @throws SQLException if any of the underlying queries fail
     */
    public ParallelExportPlan plan(
            Connection conn,
            Table table,
            DatabaseType sourceDbType,
            int requestedDegree,
            double skewRatioThreshold)
            throws SQLException {
        if (requestedDegree <= 1) {
            return ParallelExportPlan.disabled(table, "Requested degree of parallelism is <= 1.");
        }

        SplitColumnChoice choice = chooseSplitColumn(table, sourceDbType);
        if (choice == null) {
            return ParallelExportPlan.disabled(
                    table, "No usable split column (PK / unique index / index) was found.");
        }
        Column splitColumn = choice.getColumn();

        List<SplitRange> ranges = calculateRanges(conn, table, splitColumn, requestedDegree);
        if (ranges.size() <= 1) {
            return ParallelExportPlan.disabled(
                    table, "Could not compute more than one range (empty table or single value).");
        }

        SkewResult skew = validateSkew(conn, table, splitColumn, ranges, skewRatioThreshold);
        if (!skew.isAcceptable()) {
            return ParallelExportPlan.disabled(
                    table,
                    "Row distribution across ranges is too skewed (ratio="
                            + skew.getSkewRatio()
                            + ").");
        }

        return ParallelExportPlan.enabled(table, splitColumn, choice.getBasis(), ranges, skew);
    }

    /** The table reference the export query reads from, quoted for the source dialect. */
    private String qualifiedTableName(Table table) {
        return exportHelper.getQualifiedTableName(table.getOwner(), table.getName());
    }

    private String quotedColumnName(Column column) {
        return exportHelper.getQuotedColumnName(column.getName());
    }

    /**
     * Builds the half-open predicate ({@code <column> >= <lo> AND <column> < <hi>}, with an
     * unbounded side simply omitted) a scheduler should combine with a table's existing WHERE
     * condition (e.g. via {@code JDBCExporter#exportTableRecords(SourceTableConfig, String,
     * RecordExportedListener)}) to actually read only this range.
     *
     * <p>Not {@code BETWEEN}: that is inclusive on both ends, so a boundary value would be read by
     * two neighbouring ranges, and for fractional values the integer-only ranges used to leave
     * gaps. With one shared boundary per pair of neighbours every value lands in exactly one range.
     *
     * <p>The column name is quoted for the source dialect, as in the export query it is ANDed onto.
     *
     * @param splitColumn the column ranges were computed against (from {@link
     *     #determineSplitColumn})
     * @param range one range from {@link #calculateRanges}
     * @return a standalone boolean SQL predicate, safe to AND onto an existing condition
     */
    public String buildRangeCondition(Column splitColumn, SplitRange range) {
        return rangePredicate(
                quotedColumnName(splitColumn), range, TableSplitPlanner::toSqlLiteral);
    }

    /**
     * The one place the range predicate is spelled out, shared by the export condition (bounds as
     * SQL literals) and the skew check (bounds as {@code ?} placeholders).
     *
     * @param columnName the split column, already quoted for the source dialect
     */
    private static String rangePredicate(
            String columnName, SplitRange range, Function<Object, String> renderBound) {
        Object lower = range.getLowerBound();
        Object upper = range.getUpperBound();
        if (lower == null && upper == null) {
            return "1 = 1";
        }
        StringBuilder predicate = new StringBuilder();
        if (lower != null) {
            predicate.append(columnName).append(" >= ").append(renderBound.apply(lower));
        }
        if (lower != null && upper != null) {
            predicate.append(" AND ");
        }
        if (upper != null) {
            predicate.append(columnName).append(" < ").append(renderBound.apply(upper));
        }
        return predicate.toString();
    }

    private static String toSqlLiteral(Object bound) {
        if (bound instanceof BigDecimal) {
            // toString() may switch to exponent form (1E+3), which not every database accepts.
            return ((BigDecimal) bound).toPlainString();
        }
        if (bound instanceof Number) {
            return bound.toString();
        }
        return "'" + String.valueOf(bound).replace("'", "''") + "'";
    }

    /**
     * One value range of the split column: {@code lowerBound <= value < upperBound}. The lower
     * bound is inclusive and the upper bound exclusive, so neighbouring ranges share a boundary and
     * never overlap or leave a gap. A {@code null} bound means unbounded on that side: the first
     * range has no lower bound and the last has no upper bound, which also catches values outside
     * the MIN/MAX that were read at planning time (e.g. rows inserted since).
     */
    public static final class SplitRange {
        private final Object lowerBound;
        private final Object upperBound;

        /**
         * @param lowerBound inclusive lower bound, or {@code null} for unbounded
         * @param upperBound exclusive upper bound, or {@code null} for unbounded
         */
        public SplitRange(Object lowerBound, Object upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        /**
         * @return a range with no bounds at all, i.e. the whole table as one range
         */
        public static SplitRange unbounded() {
            return new SplitRange(null, null);
        }

        public Object getLowerBound() {
            return lowerBound;
        }

        public Object getUpperBound() {
            return upperBound;
        }

        @Override
        public String toString() {
            return (lowerBound == null ? "(-inf" : "[" + lowerBound)
                    + ", "
                    + (upperBound == null ? "+inf)" : upperBound + ")");
        }
    }

    /** Per-range row counts and the resulting skew verdict from {@link #validateSkew}. */
    public static final class SkewResult {
        private final List<Long> rangeCounts;
        private final double skewRatio;
        private final boolean acceptable;

        public SkewResult(List<Long> rangeCounts, double skewRatio, boolean acceptable) {
            this.rangeCounts = rangeCounts;
            this.skewRatio = skewRatio;
            this.acceptable = acceptable;
        }

        public List<Long> getRangeCounts() {
            return rangeCounts;
        }

        public double getSkewRatio() {
            return skewRatio;
        }

        public boolean isAcceptable() {
            return acceptable;
        }
    }

    /**
     * Which kind of column {@link #chooseSplitColumn} picked, in priority order - lets callers log
     * or display *why* a table was (or wasn't) split on a particular column, not just which column.
     */
    public enum SplitBasis {
        SINGLE_COLUMN_PK("single-column primary key"),
        UNIQUE_INDEX("unique index"),
        NON_UNIQUE_INDEX("non-unique index"),
        COMPOSITE_PK_LEADING_COLUMN("composite primary key's leading column");

        private final String description;

        SplitBasis(String description) {
            this.description = description;
        }

        /**
         * @return a short, human-readable label suitable for logs/UI, e.g. "unique index".
         */
        public String getDescription() {
            return description;
        }
    }

    /** A split column paired with the reason (see {@link SplitBasis}) it was chosen. */
    private static final class SplitColumnChoice {
        private final Column column;
        private final SplitBasis basis;

        SplitColumnChoice(Column column, SplitBasis basis) {
            this.column = column;
            this.basis = basis;
        }

        Column getColumn() {
            return column;
        }

        SplitBasis getBasis() {
            return basis;
        }
    }

    /**
     * Result of {@link #plan(Connection, Table, DatabaseType, int, double)}: either a usable set of
     * ranges, or a reason parallel export was disabled for this table. The UI can show {@link
     * #getDisabledReason()} directly as a tooltip.
     */
    public static final class ParallelExportPlan {
        private final Table table;
        private final Column splitColumn;
        private final SplitBasis splitBasis;
        private final List<SplitRange> ranges;
        private final SkewResult skewResult;
        private final boolean parallelizable;
        private final String disabledReason;

        private ParallelExportPlan(
                Table table,
                Column splitColumn,
                SplitBasis splitBasis,
                List<SplitRange> ranges,
                SkewResult skewResult,
                boolean parallelizable,
                String disabledReason) {
            this.table = table;
            this.splitColumn = splitColumn;
            this.splitBasis = splitBasis;
            this.ranges = ranges;
            this.skewResult = skewResult;
            this.parallelizable = parallelizable;
            this.disabledReason = disabledReason;
        }

        public static ParallelExportPlan enabled(
                Table table,
                Column splitColumn,
                SplitBasis splitBasis,
                List<SplitRange> ranges,
                SkewResult skewResult) {
            return new ParallelExportPlan(
                    table, splitColumn, splitBasis, ranges, skewResult, true, null);
        }

        public static ParallelExportPlan disabled(Table table, String reason) {
            return new ParallelExportPlan(
                    table, null, null, Collections.<SplitRange>emptyList(), null, false, reason);
        }

        public Table getTable() {
            return table;
        }

        public Column getSplitColumn() {
            return splitColumn;
        }

        /**
         * @return why {@link #getSplitColumn()} was chosen, or {@code null} if disabled.
         */
        public SplitBasis getSplitBasis() {
            return splitBasis;
        }

        public List<SplitRange> getRanges() {
            return ranges;
        }

        public SkewResult getSkewResult() {
            return skewResult;
        }

        public boolean isParallelizable() {
            return parallelizable;
        }

        public String getDisabledReason() {
            return disabledReason;
        }
    }
}
