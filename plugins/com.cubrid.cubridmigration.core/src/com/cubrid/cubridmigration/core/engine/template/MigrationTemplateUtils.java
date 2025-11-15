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
package com.cubrid.cubridmigration.core.engine.template;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class that provides common helper methods for migration template processing. */
public final class MigrationTemplateUtils {

    public static final List<String> FK_OPERATION =
            List.of("CASCADE", "RESTRICT", "SET NULL", "NO ACTION");
    private static final int DEFAULT_FK_OPTION_INDEX = FK_OPERATION.indexOf("RESTRICT");

    private MigrationTemplateUtils() {}

    public static boolean getBoolean(String value, boolean def) {
        if (isEmpty(value)) {
            return def;
        }
        return TemplateTags.VALUE_YES.equals(value);
    }

    public static String getBooleanString(boolean value) {
        return value ? TemplateTags.VALUE_YES : TemplateTags.VALUE_NO;
    }

    public static List<String> getStringList(String strings) {
        if (isBlank(strings)) {
            return Collections.emptyList();
        }

        return Arrays.stream(strings.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static String list2String(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }

    public static int getFKOptIndex(String opt) {
        int result = FK_OPERATION.indexOf(opt);
        return result < 0 ? DEFAULT_FK_OPTION_INDEX : result;
    }

    public static int convertVersionToInt(String version) {
        if (isEmpty(version)) {
            return 0;
        }

        String stringValue = version.replaceAll("\\.", "");
        return Integer.parseInt(stringValue);
    }
}
