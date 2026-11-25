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

/**
 * Decides whether a table's records can be exported by multiple parallel range workers instead of
 * the current one-thread-per-table model, and computes the value ranges those workers would use.
 *
 * <p>This is a design skeleton: {@link #determineSplitColumn(Table, DatabaseType)} is fully
 * implemented since it only needs metadata that is already fetched. {@link #calculateRanges(
 * Connection, Table, Column, int)} and {@link #validateSkew(Connection, Table, Column, List,
 * double)} contain a generic, dialect-agnostic SQL implementation that must be reviewed per source
 * database (identifier quoting) before being relied on.
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
     * <p>Columns that allow NULL are skipped as well: the range predicate ({@code col BETWEEN lo AND
     * hi}) never matches a NULL, so splitting on such a column would silently drop every row whose
     * value is NULL. PK columns are NOT NULL by definition, so only this index path needs the check.
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
     * Computes {@code degree} non-overlapping value ranges covering [MIN(splitColumn),
     * MAX(splitColumn)]. MIN/MAX only - no COUNT(*) - so this stays cheap regardless of table size.
     *
     * <p>TODO: identifier quoting/escaping is not dialect-aware yet - reuse the quoting helpers
     * already in {@link DBExportHelper} and its per-DB subclasses instead of the naive {@code
     * owner.table} concatenation below. Only numeric split columns are actually split here;
     * DATE/TIMESTAMP columns need dialect-specific arithmetic and currently fall back to a single
     * unsplit range (see {@link #splitIntoRanges}).
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

        String sql =
                "SELECT MIN("
                        + splitColumn.getName()
                        + "), MAX("
                        + splitColumn.getName()
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
     */
    private List<SplitRange> splitIntoRanges(Object min, Object max, int degree) {
        if (!(min instanceof Number) || !(max instanceof Number)) {
            // TODO: DATE/TIMESTAMP range splitting isn't implemented - treat as unsplittable
            // for now rather than risk an incorrect boundary.
            return Collections.singletonList(new SplitRange(min, max));
        }

        long lo = ((Number) min).longValue();
        long hi = ((Number) max).longValue();
        if (hi <= lo) {
            return Collections.singletonList(new SplitRange(min, max));
        }

        long span = hi - lo + 1;
        long chunkSize = Math.max(1, span / degree);

        List<SplitRange> ranges = new ArrayList<SplitRange>();
        long cursor = lo;
        while (cursor <= hi) {
            boolean isLastRange = ranges.size() == degree - 1;
            long rangeHi = isLastRange ? hi : Math.min(hi, cursor + chunkSize - 1);
            ranges.add(new SplitRange(cursor, rangeHi));
            cursor = rangeHi + 1;
        }
        return ranges;
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

        String sql =
                "SELECT COUNT(*) FROM "
                        + qualifiedTableName(table)
                        + " WHERE "
                        + splitColumn.getName()
                        + " BETWEEN ? AND ?";

        List<Long> counts = new ArrayList<Long>();
        long total = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (SplitRange range : ranges) {
                ps.setObject(1, range.getLowerBound());
                ps.setObject(2, range.getUpperBound());
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

    /**
     * TODO: naive owner.table concatenation with no identifier quoting. Replace with the
     * quoting/escaping already implemented per source dialect in {@link DBExportHelper} and its
     * subclasses before this is used against a real connection.
     */
    private String qualifiedTableName(Table table) {
        String owner = table.getOwner();
        return (owner == null || owner.isEmpty()) ? table.getName() : owner + "." + table.getName();
    }

    /**
     * Builds the {@code <column> BETWEEN <lo> AND <hi>} predicate a scheduler should combine with a
     * table's existing WHERE condition (e.g. via {@code JDBCExporter#exportTableRecords(
     * SourceTableConfig, String, RecordExportedListener)}) to actually read only this range.
     *
     * <p>TODO: same identifier-quoting caveat as {@link #qualifiedTableName} - the column name is
     * emitted as-is rather than through a dialect-specific quoting helper.
     *
     * @param splitColumn the column ranges were computed against (from {@link
     *     #determineSplitColumn})
     * @param range one range from {@link #calculateRanges}
     * @return a standalone boolean SQL predicate, safe to AND onto an existing condition
     */
    public String buildRangeCondition(Column splitColumn, SplitRange range) {
        return splitColumn.getName()
                + " BETWEEN "
                + toSqlLiteral(range.getLowerBound())
                + " AND "
                + toSqlLiteral(range.getUpperBound());
    }

    private String toSqlLiteral(Object bound) {
        if (bound instanceof Number) {
            return bound.toString();
        }
        return "'" + String.valueOf(bound).replace("'", "''") + "'";
    }

    /** A single [lowerBound, upperBound] value range, inclusive on both ends. */
    public static final class SplitRange {
        private final Object lowerBound;
        private final Object upperBound;

        public SplitRange(Object lowerBound, Object upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public Object getLowerBound() {
            return lowerBound;
        }

        public Object getUpperBound() {
            return upperBound;
        }

        @Override
        public String toString() {
            return "[" + lowerBound + ", " + upperBound + "]";
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
