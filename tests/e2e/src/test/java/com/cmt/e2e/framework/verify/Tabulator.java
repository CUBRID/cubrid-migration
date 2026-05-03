package com.cmt.e2e.framework.verify;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats tabular data as a {@code psql}-style aligned text table — the
 * canonical snapshot format (see ARCHITECTURE.md §7). Caller must
 * {@code ORDER BY} for determinism. SQL {@code NULL} renders as
 * {@code <NULL>}; trailing whitespace is stripped.
 */
public final class Tabulator {

    public static final String NULL_VALUE = "<NULL>";

    private Tabulator() {}

    public static String format(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();

        List<String> headers = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            headers.add(md.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                String v = rs.getString(i);
                row.add(v == null ? NULL_VALUE : v);
            }
            rows.add(row);
        }
        return format(headers, rows);
    }

    /** Caller must coerce null cells to {@link #NULL_VALUE} — this method does not. */
    public static String format(List<String> headers, List<List<String>> rows) {
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("headers must not be empty");
        }
        int n = headers.size();
        for (List<String> row : rows) {
            if (row.size() != n) {
                throw new IllegalArgumentException(
                    "row size " + row.size() + " != header size " + n + ": " + row);
            }
        }
        int[] widths = computeWidths(headers, rows, n);

        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers, widths);
        appendSeparator(sb, widths);
        for (List<String> row : rows) {
            appendRow(sb, row, widths);
        }
        return sb.toString();
    }

    private static int[] computeWidths(List<String> headers, List<List<String>> rows, int n) {
        int[] widths = new int[n];
        for (int i = 0; i < n; i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < n; i++) {
                int len = row.get(i).length();
                if (len > widths[i]) widths[i] = len;
            }
        }
        return widths;
    }

    private static void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) line.append('|');
            line.append(' ').append(padRight(cells.get(i), widths[i])).append(' ');
        }
        sb.append(stripTrailing(line)).append('\n');
    }

    private static void appendSeparator(StringBuilder sb, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) line.append('+');
            line.append("-".repeat(widths[i] + 2));
        }
        sb.append(stripTrailing(line)).append('\n');
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String stripTrailing(CharSequence s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.subSequence(0, end).toString();
    }
}
