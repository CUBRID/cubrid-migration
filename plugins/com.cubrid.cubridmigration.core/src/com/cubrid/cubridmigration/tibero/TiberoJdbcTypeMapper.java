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
package com.cubrid.cubridmigration.tibero;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** TiberoJdbcTypeMapper is responsible for mapping Tibero data types to Java JDBC Types. */
public final class TiberoJdbcTypeMapper {

    private static final Map<String, Integer> FIXED_JDBC_TYPE_IDS = createFixedJdbcTypeIds();

    private TiberoJdbcTypeMapper() {
        // Prevent instantiation
    }

    /**
     * Get the JDBC Type ID for fixed/known Tibero data types.
     *
     * @param dataType String
     * @return Integer JDBC Type ID or null if not a fixed type
     */
    public static Integer getFixedJdbcTypeId(String dataType) {
        return FIXED_JDBC_TYPE_IDS.get(dataType);
    }

    /**
     * Get the JDBC Type ID for NUMBER type based on precision and scale.
     *
     * @param precision Integer
     * @param scale Integer
     * @return Integer JDBC Type ID
     */
    public static Integer getNumberType(Integer precision, Integer scale) {
        if (precision == null) {
            if (scale == null) {
                return Types.NUMERIC;
            } else if (scale == 0) {
                return Types.BIGINT;
            }
        } else if (scale == null || scale == 0) {
            if (precision == 1) {
                return Types.BIT;
            } else if (precision == 3) {
                return Types.TINYINT;
            } else if (precision == 5) {
                return Types.SMALLINT;
            } else if (precision <= 10) {
                return Types.INTEGER;
            } else if (precision <= 38) {
                return Types.BIGINT;
            }
        }
        return Types.NUMERIC;
    }

    private static Map<String, Integer> createFixedJdbcTypeIds() {
        Map<String, Integer> fixedTypes = new HashMap<String, Integer>();
        fixedTypes.put("LONG", Types.CLOB);
        fixedTypes.put("XMLTYPE", Types.CLOB);
        fixedTypes.put("JSON", Types.CLOB);
        fixedTypes.put("BINARY_FLOAT", Types.FLOAT);
        fixedTypes.put("BINARY_DOUBLE", Types.DOUBLE);
        return Collections.unmodifiableMap(fixedTypes);
    }
}
