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
package com.cubrid.cubridmigration.cubrid.trans.converter;

import com.cubrid.cubridmigration.core.common.TimeZoneConverterUtils;
import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.trans.AbstractDataConverter;

import java.time.OffsetDateTime;

public class TimestampTZConverter extends AbstractDataConverter {

    public Object convert(Object obj, DataTypeInstance dti, MigrationConfiguration config) {

        if (obj instanceof OffsetDateTime) {
            String result = TimeZoneConverterUtils.formatWithOffset((OffsetDateTime) obj);
            return result;
        }

        if (obj instanceof String && shouldPassThrough((String) obj)) {
            return obj;
        }

        return convertToFormattedString(obj, config, "TIMESTAMPTZ");
    }

    private Object convertToFormattedString(
            Object obj, MigrationConfiguration config, String targetType) {
        try {
            OffsetDateTime offsetDateTime =
                    TimeZoneConverterUtils.parseToOffsetDateTime(
                            obj, config.getSourceDatabaseTimeZone());
            if (offsetDateTime == null) {
                return null;
            }
            return TimeZoneConverterUtils.formatWithOffset(offsetDateTime);
        } catch (IllegalArgumentException ex) {
            String valueStr = obj != null ? obj.toString() : null;
            if (isZeroDatePattern(valueStr)) {
                return valueStr;
            }
            throw new IllegalStateException(
                    "ERROR: could not convert:" + obj + " to CUBRID type " + targetType, ex);
        }
    }

    private boolean shouldPassThrough(String valueStr) {
        boolean hasZoneId =
                valueStr.matches(".*\\s+[A-Za-z][A-Za-z0-9_/]+(?:\\s+[A-Z]{2,4})?\\s*$");
        boolean endsWithOffset = valueStr.matches(".*[+-]\\d{2}:\\d{2}\\s*$");
        return hasZoneId && !endsWithOffset;
    }

    private boolean isZeroDatePattern(String value) {
        if (value == null) {
            return false;
        }
        return value.matches(".*0{2,4}[/-]0{1,2}[/-]0{2,4}.*");
    }
}
