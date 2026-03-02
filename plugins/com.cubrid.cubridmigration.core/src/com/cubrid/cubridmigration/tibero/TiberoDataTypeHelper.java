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

import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import java.util.List;
import java.util.Map;

public final class TiberoDataTypeHelper extends DBDataTypeHelper {
    /**
     * Get normalized data type name.
     *
     * @param dataType String
     * @param precision Integer
     * @param scale Integer
     * @return String normalized data type
     */
    public String getNormalizedDataType(String dataType, Integer precision, Integer scale) {
        if ("NVARCHAR".equalsIgnoreCase(dataType)) {
            return "NVARCHAR2";
        }
        if ("NUMBER".equalsIgnoreCase(dataType)
                && precision == null
                && Integer.valueOf(0).equals(scale)) {
            return "INTEGER";
        }
        return dataType;
    }

    private static final TiberoDataTypeHelper HELPER = new TiberoDataTypeHelper();

    /**
     * Singleton
     *
     * @param version of tibero database
     * @return DataTypeHelper
     */
    public static TiberoDataTypeHelper getInstance(String version) {
        return HELPER;
    }

    /**
     * return get Tibero data type key
     *
     * @param dataType String
     * @return String
     */
    public static String getTiberoDataTypeKey(String dataType) {
        String key = dataType;
        if ("NVARCHAR2".equalsIgnoreCase(dataType)) {
            return "NVARCHAR";
        }
        if ("VARCHAR2".equalsIgnoreCase(dataType)) {
            return "VARCHAR";
        }

        if (dataType.matches("TIMESTAMP\\(\\d*\\)")) {
            key = "TIMESTAMP";
        } else if (dataType.matches("TIMESTAMP\\(\\d*\\) WITH TIME ZONE")) {
            key = "TIMESTAMP WITH TIME ZONE";
        } else if (dataType.matches("TIMESTAMP\\(\\d*\\) WITH LOCAL TIME ZONE")) {
            key = "TIMESTAMP WITH LOCAL TIME ZONE";
        } else if (dataType.matches("INTERVAL DAY\\(\\d*\\) TO SECOND\\(\\d*\\)")) {
            key = "INTERVAL DAY TO SECOND";
        } else if (dataType.matches("INTERVAL YEAR\\(\\d*\\) TO MONTH")) {
            key = "INTERVAL YEAR TO MONTH";
        }
        return key;
    }

    private TiberoDataTypeHelper() {
        // Do nothing here.
    }

    /**
     * Retrieves the Database type.
     *
     * @return DatabaseType
     */
    public DatabaseType getDBType() {
        return DatabaseType.TIBERO;
    }

    /**
     * Get normalized data type name.
     *
     * @param dataType String
     * @param precision Integer
     * @param scale Integer
     * @return String normalized data type
     */
    public String getNormalizedDataType(String dataType, Integer precision, Integer scale) {
        if ("NVARCHAR".equalsIgnoreCase(dataType)) {
            return "NVARCHAR2";
        }
        if ("NUMBER".equalsIgnoreCase(dataType)
                && precision == null
                && Integer.valueOf(0).equals(scale)) {
            return "INTEGER";
        }
        return dataType;
    }

    /**
     * return data type id TODO: to be refactory
     *
     * @param catalog Catalog
     * @param dataType String
     * @param precision Integer
     * @param scale Integer
     * @return Integer
     */
    public Integer getJdbcDataTypeID(
            Catalog catalog, String dataType, Integer precision, Integer scale) {
        if ("NUMBER".equals(dataType)) {
            return TiberoJdbcTypeMapper.getNumberType(precision, scale);
        }

        Integer fixedType = TiberoJdbcTypeMapper.getFixedJdbcTypeId(dataType);
        if (fixedType != null) {
            return fixedType;
        }

        String key = getTiberoDataTypeKey(dataType);
        Map<String, List<DataType>> supportedDataType = catalog.getSupportedDataType();
        List<DataType> dataTypeList = supportedDataType.get(key);
        if (dataTypeList == null) {
            throw new IllegalArgumentException("Not supported Tibero data type(" + dataType + ")");
        }

        if (dataTypeList.size() == 1) {
            return dataTypeList.get(0).getJdbcDataTypeID();
        }

        throw new IllegalArgumentException(
                "Not supported Tibero data type("
                        + dataType
                        + ": p="
                        + precision
                        + ", s="
                        + scale
                        + ")");
    }

    /**
     * generate data type via JDBC meta data information of CUBRID
     *
     * @param column Column
     * @return String
     */
    public String getShownDataType(Column column) {
        return TiberoTypeFormatter.format(column, this);
    }

    /**
     * Retrieves if The data type of column is binary type such as blob/bit ...
     *
     * @param dataType Column
     * @return true or false
     */
    public boolean isBinary(String dataType) {
        return "blob".equalsIgnoreCase(dataType);
    }

    /**
     * Tibero server does not support collection type.
     *
     * @param dataType Integer
     * @return false
     */
    public boolean isCollection(String dataType) {
        return false;
    }
}
