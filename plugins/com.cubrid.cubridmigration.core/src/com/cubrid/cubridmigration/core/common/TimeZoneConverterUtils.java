/*
 * Copyright (C) 2008 Search Solution Corporation.
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
 */
package com.cubrid.cubridmigration.core.common;

import com.cubrid.cubridmigration.cubrid.CUBRIDTimeUtil;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class TimeZoneConverterUtils {

    private static final DateTimeFormatter OUTPUT_WITH_OFFSET =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                    .appendLiteral(' ')
                    .appendOffset("+HH:MM", "+00:00")
                    .toFormatter(Locale.US);

    private static final DateTimeFormatter OUTPUT_WITHOUT_OFFSET =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                    .toFormatter(Locale.US);

    private static final DateTimeFormatter[] OFFSET_INPUT_FORMATTERS =
            new DateTimeFormatter[] {
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .appendLiteral(' ')
                        .appendOffset("+HH:MM", "+00:00")
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .appendLiteral(' ')
                        .appendOffset("+HH:MM", "+00:00")
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy/MM/dd HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .appendLiteral(' ')
                        .appendOffset("+HH:MM", "+00:00")
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .appendOffset("+HHMM", "+0000")
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .appendOffset("+HHMM", "+0000")
                        .toFormatter(Locale.US)
            };

    private static final DateTimeFormatter[] LOCAL_INPUT_FORMATTERS =
            new DateTimeFormatter[] {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy/MM/dd HH:mm:ss")
                        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 6, true)
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy-MM-dd HH:mm")
                        .toFormatter(Locale.US),
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("yyyy/MM/dd HH:mm")
                        .toFormatter(Locale.US)
            };

    private TimeZoneConverterUtils() {}

    public static OffsetDateTime parseToOffsetDateTime(Object value, TimeZone defaultTimeZone) {
        OffsetDateTime resolved = resolveDirectValue(value, defaultTimeZone);
        if (resolved != null || value == null) {
            return resolved;
        }

        String text = normalizeToText(value);
        if (text == null) {
            return null;
        }

        OffsetDateTime parsed = parseTextualValue(text, defaultTimeZone);
        if (parsed != null) {
            return parsed;
        }

        throw new IllegalArgumentException("Unable to parse timezone aware value: " + text);
    }

    private static OffsetDateTime resolveDirectValue(Object value, TimeZone defaultTimeZone) {
        if (value == null) {
            return null;
        }
        OffsetDateTime directTemporal = resolveTemporalInstances(value, defaultTimeZone);
        if (directTemporal != null) {
            return directTemporal;
        }
        OffsetDateTime cubridTemporal = resolveCubridTemporal(value, defaultTimeZone);
        if (cubridTemporal != null) {
            return cubridTemporal;
        }
        return resolveNumericInstant(value, defaultTimeZone);
    }

    private static OffsetDateTime resolveTemporalInstances(Object value, TimeZone defaultTimeZone) {
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toOffsetDateTime();
        }
        if (value instanceof Timestamp) {
            return toOffsetDateTime(((Timestamp) value).toInstant(), defaultTimeZone);
        }
        if (value instanceof Date) {
            return toOffsetDateTime(((Date) value).toInstant(), defaultTimeZone);
        }
        if (value instanceof Calendar) {
            Calendar calendar = (Calendar) value;
            return OffsetDateTime.ofInstant(
                    calendar.toInstant(), calendar.getTimeZone().toZoneId());
        }
        return null;
    }

    private static OffsetDateTime resolveCubridTemporal(Object value, TimeZone defaultTimeZone) {
        OffsetDateTime cubridTz = tryConvertCUBRIDTimestamptz(value);
        if (cubridTz != null) {
            return cubridTz;
        }
        return tryConvertCUBRIDTimestamp(value, defaultTimeZone);
    }

    private static OffsetDateTime resolveNumericInstant(Object value, TimeZone defaultTimeZone) {
        if (!(value instanceof Number)) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(((Number) value).longValue());
        return toOffsetDateTime(instant, defaultTimeZone);
    }

    private static String normalizeToText(Object value) {
        String text = value.toString();
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }

    private static OffsetDateTime parseTextualValue(String text, TimeZone defaultTimeZone) {
        OffsetDateTime parsed = tryParseOffsetDateTime(text);
        if (parsed != null) {
            return parsed;
        }

        OffsetDateTime zoneIdParsed = tryParseWithZoneId(text);
        if (zoneIdParsed != null) {
            return zoneIdParsed;
        }

        return tryParseLocalDateTime(text, defaultTimeZone);
    }

    public static String formatWithOffset(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return OUTPUT_WITH_OFFSET.format(dateTime);
    }

    public static String formatWithoutOffset(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return OUTPUT_WITHOUT_OFFSET.format(dateTime);
    }

    public static OffsetDateTime toUtc(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public static OffsetDateTime applyTargetTimeZone(
            OffsetDateTime dateTime, TimeZone targetTimeZone) {
        if (dateTime == null) {
            return null;
        }
        ZoneId zoneId = toZoneId(targetTimeZone);
        return dateTime.atZoneSameInstant(zoneId).toOffsetDateTime();
    }

    private static OffsetDateTime tryParseOffsetDateTime(String text) {
        String normalized = normalizeIsoSpacing(text);

        for (DateTimeFormatter formatter : OFFSET_INPUT_FORMATTERS) {
            try {
                return OffsetDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignore) {
            }
        }

        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignore) {
        }
        return null;
    }

    private static OffsetDateTime tryParseWithZoneId(String text) {
        String normalized = normalizeIsoSpacing(text);
        int idx = normalized.lastIndexOf(' ');
        if (idx <= 0 || idx + 1 >= normalized.length()) {
            return null;
        }

        String zoneIdStr = normalized.substring(idx + 1);
        if (!isValidZoneId(zoneIdStr)) {
            return null;
        }
        String dateTimePart = normalized.substring(0, idx);
        for (DateTimeFormatter formatter : LOCAL_INPUT_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dateTimePart, formatter);
                return ldt.atZone(ZoneId.of(zoneIdStr)).toOffsetDateTime();
            } catch (DateTimeParseException ignore) {
            }
        }
        try {
            long timestamp =
                    CUBRIDTimeUtil.parseTimestamp(dateTimePart, TimeZone.getTimeZone("GMT"));
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of(zoneIdStr));
        } catch (Exception ignore) {
        }
        return null;
    }

    private static OffsetDateTime tryParseLocalDateTime(String text, TimeZone defaultTimeZone) {
        String normalized = normalizeIsoSpacing(text);
        ZoneId zoneId = toZoneId(defaultTimeZone);
        for (DateTimeFormatter formatter : LOCAL_INPUT_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(normalized, formatter);
                return ldt.atZone(zoneId).toOffsetDateTime();
            } catch (DateTimeParseException ignore) {
            }
        }

        try {
            long timestamp =
                    CUBRIDTimeUtil.parseTimestamp(
                            normalized, defaultTimeZone == null ? null : defaultTimeZone);
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId);
        } catch (ParseException ex) {
        }
        return null;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant, TimeZone timeZone) {
        ZoneId zoneId = toZoneId(timeZone);
        return OffsetDateTime.ofInstant(instant, zoneId);
    }

    private static ZoneId toZoneId(TimeZone timeZone) {
        if (timeZone == null) {
            return ZoneId.systemDefault();
        }
        return timeZone.toZoneId();
    }

    private static boolean isValidZoneId(String zoneId) {
        try {
            ZoneId.of(zoneId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String normalizeIsoSpacing(String text) {
        String normalized = text;
        if (!normalized.contains("T")) {
            normalized = normalized.replaceFirst(" ", "T");
        }
        normalized = normalized.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
        return normalized;
    }

    private static OffsetDateTime tryConvertCUBRIDTimestamptz(Object value) {
        if (value == null) {
            return null;
        }
        if (!"cubrid.sql.CUBRIDTimestamptz".equals(value.getClass().getName())) {
            return null;
        }
        try {
            Method getUnixTime = value.getClass().getMethod("getUnixTime");
            Method getTimezone = value.getClass().getMethod("getTimezone");

            long utcMillis = ((Number) getUnixTime.invoke(value)).longValue();
            OffsetDateTime utc =
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(utcMillis), ZoneOffset.UTC);
            return applyTimezone(utc, getTimezone.invoke(value));
        } catch (ReflectiveOperationException | ClassCastException ex) {
            return null;
        }
    }

    private static OffsetDateTime applyTimezone(OffsetDateTime utc, Object tzObj) {
        String timezone = tzObj == null ? null : tzObj.toString();
        if (timezone == null || timezone.isEmpty()) {
            return utc;
        }
        OffsetDateTime offsetAdjusted = tryApplyOffset(utc, timezone);
        return offsetAdjusted == null ? utc : offsetAdjusted;
    }

    private static OffsetDateTime tryApplyOffset(OffsetDateTime utc, String timezone) {
        try {
            ZoneOffset offset = ZoneOffset.of(timezone);
            return utc.withOffsetSameInstant(offset);
        } catch (DateTimeException ex) {
            try {
                ZoneId zoneId = ZoneId.of(timezone);
                return utc.atZoneSameInstant(zoneId).toOffsetDateTime();
            } catch (DateTimeException ignored) {
                return null;
            }
        }
    }

    private static OffsetDateTime tryConvertCUBRIDTimestamp(
            Object value, TimeZone defaultTimeZone) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Timestamp)) {
            return null;
        }
        if (!"cubrid.sql.CUBRIDTimestamp".equals(value.getClass().getName())) {
            return null;
        }
        OffsetDateTime result = toOffsetDateTime(((Timestamp) value).toInstant(), defaultTimeZone);
        try {
            Method method = value.getClass().getMethod("isDatetime");
            Object flag = method.invoke(value);
            if (flag instanceof Boolean && !((Boolean) flag)) {
                return result.withOffsetSameInstant(ZoneOffset.UTC);
            }
        } catch (ReflectiveOperationException ex) {
            return result;
        }
        return result;
    }
}
