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
package com.cubrid.cubridmigration.tibero.trans;

import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.tibero.TiberoDataTypeHelper;

public class TiberoDataTypeMappingHelper extends AbstractDataTypeMappingHelper {

    /**
     * @param databaseTypeID
     */
    public TiberoDataTypeMappingHelper() {
        super("TIBERO2CUBRID", "/com/cubrid/cubridmigration/tibero/trans/Tibero2CUBRID.xml");
    }

    /**
     * get the config map key
     *
     * @param datatype String
     * @param precision String
     * @param scale String
     * @return key String
     */
    @Override
    public String getMapKey(String datatype, String precision, String scale) {
        return new MappingKey(datatype, precision).generate();
    }

    /** MappingKey encapsulates the logic for generating a unique mapping key from Tibero types. */
    private final class MappingKey {
        private final String dataType;
        private final String precision;

        private MappingKey(String dataType, String precision) {
            this.dataType = dataType == null ? "" : dataType.toUpperCase();
            this.precision = precision;
        }

        private String generate() {
            if ("NUMBER".equals(dataType)) {
                return generateNumberKey();
            }
            if (dataType.matches("INTERVAL DAY\\(\\d*\\) TO SECOND\\(\\d*\\)")) {
                return "INTERVAL DAY TO SECOND";
            }
            if (dataType.matches("INTERVAL YEAR\\(\\d*\\) TO MONTH")) {
                return "INTERVAL YEAR TO MONTH";
            }
            return TiberoDataTypeHelper.getTiberoDataTypeKey(dataType);
        }

        private String generateNumberKey() {
            if (isPOrNumeric(precision)) {
                return "NUMBER" + MAP_KEY_SEPARATOR + "p" + MAP_KEY_SEPARATOR + "s";
            }
            return "NUMBER";
        }

        private boolean isPOrNumeric(String str) {
            if (str == null) {
                return false;
            }
            return "p".equalsIgnoreCase(str) || str.matches("^-?\\d+$");
        }
    }
}
