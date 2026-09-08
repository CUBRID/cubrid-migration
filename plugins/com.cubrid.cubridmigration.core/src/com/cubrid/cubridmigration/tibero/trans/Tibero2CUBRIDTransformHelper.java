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
package com.cubrid.cubridmigration.tibero.trans;

import com.cubrid.cubridmigration.core.common.CommonUtils;
import com.cubrid.cubridmigration.core.datatype.DataTypeConstant;
import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.core.trans.DBTransformHelper;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

public class Tibero2CUBRIDTransformHelper extends DBTransformHelper {

    private static final String[] TIBERO_DATETIME_FUNCTION = {
        "SYSDATE",
        "SYSTIME",
        "SYSTIMESTAMP",
        "CURRENT_DATE",
        "CURRENT_TIME",
        "CURRENT_TIMESTAMP",
        "LOCALTIMESTAMP",
        "DBTIMEZONE",
        "SESSIONTIMEZONE"
    };

    public Tibero2CUBRIDTransformHelper(
            AbstractDataTypeMappingHelper dataTypeMapping, ToCUBRIDDataConverterFacade cf) {
        super(dataTypeMapping, cf);
    }

    /**
     * adjust precision of a column
     *
     * @param srcColumn Column
     * @param cubColumn Column
     * @param config MigrationConfiguration
     */
    protected void adjustPrecision(
            Column srcColumn, Column cubColumn, MigrationConfiguration config) {

        CUBRIDDataTypeHelper cubDTHelper = CUBRIDDataTypeHelper.getInstance(null);
        long expectedPrecision = (long) cubColumn.getPrecision();
        if (cubDTHelper.isStrictNumeric(cubColumn.getDataType())) {
            Integer tarScale = cubColumn.getScale();
            int scale = tarScale == null ? 0 : tarScale;
            if (scale < 0) {
                expectedPrecision = expectedPrecision + Math.abs(scale);
                scale = 0;
                cubColumn.setScale(scale);
            }
            if (scale > expectedPrecision) {
                expectedPrecision = scale;
            }
            if (expectedPrecision <= DataTypeConstant.NUMERIC_MAX_PRECISIE_SIZE) {
                cubColumn.setPrecision((int) expectedPrecision);
                return;
            }
            if (scale == 0) {
                expectedPrecision = expectedPrecision + 1;
            } else if (scale < expectedPrecision) {
                expectedPrecision = expectedPrecision + 2;
            } else if (scale == expectedPrecision) {
                expectedPrecision = expectedPrecision + 3;
            }
            DataTypeInstance dti = new DataTypeInstance();
            dti.setName(DataTypeConstant.CUBRID_VARCHAR);
            dti.setPrecision((int) expectedPrecision);
            dti.setScale(null);

            cubColumn.setDataTypeInstance(dti);
            cubColumn.setJdbcIDOfDataType(DataTypeConstant.CUBRID_DT_VARCHAR);
            return;
        }
        if ("RAW".equalsIgnoreCase(srcColumn.getDataType())
                && cubDTHelper.isBinary(cubColumn.getDataType())) {
            expectedPrecision = Math.min(expectedPrecision * 8, DataTypeConstant.CUBRID_MAXSIZE);
            cubColumn.setPrecision((int) expectedPrecision);
        }
    }

    /**
     * return a cloned target view
     *
     * @param sourceView View source view
     * @param config MigrationConfiguration
     * @return View
     */
    public View getCloneView(View sourceView, MigrationConfiguration config) {
        View targetView = super.getCloneView(sourceView, config);

        String querySpec = targetView.getQuerySpec();
        String withReadOnly = "with read only";
        int index = querySpec.toLowerCase(Locale.ENGLISH).indexOf(withReadOnly);

        if (index != -1) {
            querySpec =
                    querySpec.substring(0, index)
                            + querySpec.substring(
                                    index + withReadOnly.length(), querySpec.length());
        }

        targetView.setQuerySpec(querySpec);

        return targetView;
    }

    /**
     * get CUBRID column from source column if scale < 0 and |scale|+ |Precision| > 38 convert it to
     * varchar(|scale|+ |Precision|+1) if scale > Precision and scale > 38 convert it to
     * varchar|scale+ 3)
     *
     * <p>if scale < 0 and |scale|+ |Precision| <= 38 numeric(|scale|+|Precision|,0) if scale >
     * Precision and scale <= 38 convert it to numeric(scale,scale) In convention of column,
     * additional information is needed like database charset, timezone and so on, these information
     * is stored in Catalog object.
     *
     * @param srcColumn Column
     * @param config MigrationConfiguration
     * @return Column
     */
    @Override
    public Column getCUBRIDColumn(Column srcColumn, MigrationConfiguration config) {
        Column cubCol = super.getCUBRIDColumn(srcColumn, config);

        cubCol.setDefaultValue(removeCommentsFromDefaultValue(cubCol.getDefaultValue()));

        // if char is char , add '' to default value
        CUBRIDDataTypeHelper dataTypeHelper = CUBRIDDataTypeHelper.getInstance(null);
        if (dataTypeHelper.isString(cubCol.getDataType())
                && StringUtils.isNotEmpty(cubCol.getDefaultValue())
                && !cubCol.isDefaultIsExpression()
                && !cubCol.getDefaultValue().startsWith("'")
                && !cubCol.getDefaultValue().startsWith("(")) {
            cubCol.setDefaultValue("'" + cubCol.getDefaultValue() + "'");
        }

        if (srcColumn.getComment() != null) {
            cubCol.setComment(srcColumn.getComment());
        }
        return cubCol;
    }

    /**
     * verify the char length
     *
     * @param sourceColumn Column
     * @param targetColumn Column
     * @param config MigrationConfiguration
     * @return VerifyInfo
     */
    protected VerifyInfo validateChar(
            Column sourceColumn, Column targetColumn, MigrationConfiguration config) {
        VerifyInfo info = new VerifyInfo(VerifyInfo.TYPE_NO_MATCH, "");
        CUBRIDDataTypeHelper dataTypeHelper = CUBRIDDataTypeHelper.getInstance(null);
        if (dataTypeHelper.isString(targetColumn.getDataType())) {
            int sourcePrecision = sourceColumn.getPrecision();
            int targetPrecision = targetColumn.getPrecision();
            int needPrecision = config.getCharsetFactor() * sourcePrecision;

            if (targetPrecision < needPrecision) {
                info =
                        new VerifyInfo(
                                VerifyInfo.TYPE_NOENOUGH_LENGTH,
                                "ERROR: The target precision should equal or greater than "
                                        + needPrecision);
            } else {
                // if success
                info = new VerifyInfo(VerifyInfo.TYPE_MATCH, "");
            }
        } else if (dataTypeHelper.isNString(targetColumn.getDataType())) {
            int sourcePrecision = sourceColumn.getPrecision();
            int targetPrecision = targetColumn.getPrecision();
            if (targetPrecision < sourcePrecision) {
                info =
                        new VerifyInfo(
                                VerifyInfo.TYPE_NOENOUGH_LENGTH,
                                "ERROR: The target precision should equal or greater than "
                                        + sourcePrecision);
            } else {
                // if success
                info = new VerifyInfo(VerifyInfo.TYPE_MATCH, "");
            }
        }

        return info;
    }

    /**
     * get the precision when Numeric convert to varchar
     *
     * @param sourceColumn Column
     * @return Integer
     */
    private Integer getPrecisionOfNumericToVarchar(Column sourceColumn) {
        int srcScale = sourceColumn.getScale();
        int srcPrecision = sourceColumn.getPrecision();
        if (srcScale < 0) {
            return Math.abs(srcScale) + Math.abs(srcPrecision) + 1;
        } else if (srcScale > srcPrecision && srcScale > 38) {
            return Math.abs(srcScale) + 3;
        }
        return getNumericToCharLength(sourceColumn);
    }

    /**
     * adjust default value of a column
     *
     * @param srcColumn Column
     * @param cubridColumn Column
     */
    protected void adjustDefaultValue(Column srcColumn, Column cubridColumn) {
        String dataType = srcColumn.getDataType();
        String defaultValue = srcColumn.getDefaultValue();
        if (defaultValue == null) {
            return;
        }

        if (isTimezoneFunctionDefault(defaultValue)) {
            defaultValue = convertFunctionInDefaultValue(defaultValue, cubridColumn.getDataType());
            cubridColumn.setDefaultIsExpression(true);
            cubridColumn.setDefaultValue(defaultValue);
            return;
        }

        if (isDateTimeSourceType(dataType) && isDefaultDateTimeFunction(defaultValue)) {
            defaultValue = convertFunctionInDefaultValue(defaultValue, cubridColumn.getDataType());
            cubridColumn.setDefaultIsExpression(true);
            cubridColumn.setDefaultValue(defaultValue);
            return;
        }

        if (isDefaultValueExpression(defaultValue)) {
            defaultValue = convertFunctionInDefaultValue(defaultValue, cubridColumn.getDataType());
            cubridColumn.setDefaultIsExpression(true);
            cubridColumn.setDefaultValue(defaultValue);
            return;
        }
    }

    /**
     * If there is no matched condition case, returns defaultValue as it is.
     *
     * @param defaultValue
     * @param dataType
     * @return
     */
    private String convertFunctionInDefaultValue(String defaultValue, String dataType) {
        String upperCaseDefaultValue = defaultValue.toUpperCase(Locale.US);
        String normalizedDataType = StringUtils.upperCase(dataType);

        if ("TIME".equals(normalizedDataType)) {
            return convertTimeDefaultValue(upperCaseDefaultValue);
        }

        String convertedFunction =
                convertCurrentDateTimeDefaultValue(upperCaseDefaultValue, normalizedDataType);
        if (convertedFunction != null) {
            return convertedFunction;
        }

        String convertedLiteral =
                convertDateTimeLiteralDefaultValue(upperCaseDefaultValue, normalizedDataType);
        if (convertedLiteral != null) {
            return convertedLiteral;
        }

        return defaultValue;
    }

    private String convertTimeDefaultValue(String upperCaseDefaultValue) {
        switch (upperCaseDefaultValue) {
            case "SYSTIME":
                return "SYS_TIME";
            case "CURRENT_TIME":
                return "CURRENT_TIME";
            default:
                return upperCaseDefaultValue;
        }
    }

    private String convertCurrentDateTimeDefaultValue(
            String upperCaseDefaultValue, String normalizedDataType) {
        switch (upperCaseDefaultValue) {
            case "SYSDATE":
                return convertCurrentDateTimeFunction(true, normalizedDataType);
            case "SYSTIME":
                return "SYS_TIME";
            case "SYSTIMESTAMP":
                return upperCaseDefaultValue;
            case "CURRENT_DATE":
                return convertCurrentDateTimeFunction(false, normalizedDataType);
            case "CURRENT_TIMESTAMP":
                return upperCaseDefaultValue;
            case "LOCALTIMESTAMP":
                return convertCurrentDateTimeFunction(false, normalizedDataType);
            default:
                return null;
        }
    }

    private String convertDateTimeLiteralDefaultValue(
            String upperCaseDefaultValue, String normalizedDataType) {
        if ("DATETIME".equals(normalizedDataType) || "DATETIMELTZ".equals(normalizedDataType)) {
            return convertDateTimeLiteral(upperCaseDefaultValue);
        }
        if ("DATETIMETZ".equals(normalizedDataType)) {
            return convertDateTimeTzLiteral(upperCaseDefaultValue);
        }
        return null;
    }

    private String convertDateTimeLiteral(String upperCaseDefaultValue) {
        if (upperCaseDefaultValue.startsWith("TO_DATE")) {
            return upperCaseDefaultValue.replaceFirst("(?i)TO_DATE", "TO_DATETIME");
        }
        if (upperCaseDefaultValue.startsWith("TO_TIMESTAMP_TZ")) {
            return upperCaseDefaultValue;
        }
        if (upperCaseDefaultValue.startsWith("TO_TIMESTAMP")) {
            return upperCaseDefaultValue.replaceFirst("(?i)TO_TIMESTAMP", "TO_DATETIME");
        }
        return null;
    }

    private String convertDateTimeTzLiteral(String upperCaseDefaultValue) {
        if (upperCaseDefaultValue.startsWith("TO_TIMESTAMP_TZ")) {
            return upperCaseDefaultValue;
        }
        String dateTimeLiteral = convertDateTimeLiteral(upperCaseDefaultValue);
        if (dateTimeLiteral == null) {
            return null;
        }
        return wrapWithFromTz(dateTimeLiteral, "SESSIONTIMEZONE()");
    }

    private String convertCurrentDateTimeFunction(boolean serverTime, String dataType) {
        if ("DATETIMETZ".equals(dataType)) {
            return serverTime
                    ? "FROM_TZ(SYS_DATETIME, DBTIMEZONE())"
                    : "FROM_TZ(CURRENT_DATETIME, SESSIONTIMEZONE())";
        }
        if ("DATETIME".equals(dataType) || "DATETIMELTZ".equals(dataType)) {
            return serverTime ? "SYS_DATETIME" : "CURRENT_DATETIME";
        }
        return serverTime ? "SYS_TIMESTAMP" : "CURRENT_TIMESTAMP";
    }

    private String wrapWithFromTz(String dateTimeExpression, String timezoneFunction) {
        return "FROM_TZ(" + dateTimeExpression + ", " + timezoneFunction + ")";
    }

    /**
     * isDefaultValueExpression
     *
     * @param defaultValue
     * @return
     */
    private boolean isDefaultValueExpression(String defaultValue) {
        String upperCaseDefaultValue = defaultValue.toUpperCase(Locale.US);

        // Function names should be upperCases
        String[] functions = {"(", "TO_CHAR", "TO_DATE", "TO_TIMESTAMP", "TO_TIMESTAMP_TZ", "CAST"};

        for (String function : functions) {
            if (upperCaseDefaultValue.startsWith(function)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tibero date/time function validation
     *
     * @param defaultValue
     * @return true: Tibero support; false: Tibero not support
     */
    private boolean isDefaultDateTimeFunction(String defaultValue) {
        String upperCaseDefaultValue = defaultValue.toUpperCase(Locale.US);

        for (String function : TIBERO_DATETIME_FUNCTION) {
            if (upperCaseDefaultValue.startsWith(function)) {
                return true;
            }
        }

        return false;
    }

    private boolean isTimezoneFunctionDefault(String defaultValue) {
        String upperCaseDefaultValue = defaultValue.toUpperCase(Locale.US);
        return upperCaseDefaultValue.startsWith("DBTIMEZONE")
                || upperCaseDefaultValue.startsWith("SESSIONTIMEZONE");
    }

    private boolean isDateTimeSourceType(String dataType) {
        if (dataType == null) {
            return false;
        }
        String upperCaseDataType = dataType.toUpperCase(Locale.US);
        return "DATE".equals(upperCaseDataType)
                || "TIME".equals(upperCaseDataType)
                || upperCaseDataType.contains("TIMESTAMP");
    }

    /**
     * Remove comments from default value
     *
     * @param defaultValue default value
     * @return default value without comments
     */
    private String removeCommentsFromDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        return removeLineComment(removeBlockComment(defaultValue));
    }

    /**
     * Block comment removal
     *
     * @param defaultValue default value
     * @return block comment removed string
     */
    private String removeBlockComment(String defaultValue) {
        String result = defaultValue.replaceAll("/\\*[\\s\\S]*?\\*/", " ");
        return result.trim().replaceAll("\\s+", " ");
    }

    /**
     * Line comment removal
     *
     * @param defaultValue default value
     * @return line comment removed string
     */
    private String removeLineComment(String defaultValue) {
        return defaultValue.replaceAll("\\s*--.*$", "");
    }

    /**
     * verify the length that numeric convert to varchar
     *
     * @param sourceColumn Column
     * @param targetColumn Column
     * @param config MigrationConfiguration
     * @return VerifyInfo
     */
    protected VerifyInfo validateNumericToVarchar(
            Column sourceColumn, Column targetColumn, MigrationConfiguration config) {
        Integer expectPrecision = getPrecisionOfNumericToVarchar(sourceColumn);
        if (targetColumn.getPrecision() < expectPrecision) {
            return new VerifyInfo(
                    VerifyInfo.TYPE_NOENOUGH_LENGTH,
                    "The precision should equal or greater than: " + expectPrecision);
        }
        return new VerifyInfo(VerifyInfo.TYPE_MATCH, "");
    }

    /**
     * Turn the DDL of source db to CUBRID DDL. For example: Tibero select "test" from "code" will
     * be turned to CUBRID select "test" from "code"
     *
     * @param table Source DDL
     * @return CUBRID DDL
     */
    public String getToCUBRIDPartitionDDL(Table table) {
        if (table == null || table.getPartitionInfo() == null) {
            return null;
        }

        PartitionInfo partInfo = table.getPartitionInfo();
        String partitionMethod = partInfo.getPartitionMethod();
        List<Column> partitionColumns = partInfo.getPartitionColumns();
        if (partitionColumns == null || partitionColumns.isEmpty()) {
            return null;
        }

        String cubridPartitionMethod = getCUBRIDPartitionMethod(partitionMethod);
        if (cubridPartitionMethod == null) {
            return null;
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("PARTITION BY ").append(cubridPartitionMethod).append(" ");
        appendPartitionColumns(ddl, partitionColumns);
        if (PartitionInfo.PARTITION_METHOD_HASH.equalsIgnoreCase(partitionMethod)) {
            return appendHashPartitionDDL(ddl, partInfo);
        }
        return appendRangeOrListPartitionDDL(ddl, partInfo, partitionMethod);
    }

    private String getCUBRIDPartitionMethod(String partitionMethod) {
        if (PartitionInfo.PARTITION_METHOD_RANGE.equalsIgnoreCase(partitionMethod)) {
            return "RANGE";
        }
        if (PartitionInfo.PARTITION_METHOD_LIST.equalsIgnoreCase(partitionMethod)) {
            return "LIST";
        }
        if (PartitionInfo.PARTITION_METHOD_HASH.equalsIgnoreCase(partitionMethod)) {
            return "HASH";
        }
        return null;
    }

    private void appendPartitionColumns(StringBuilder ddl, List<Column> partitionColumns) {
        ddl.append("(");
        for (int i = 0; i < partitionColumns.size(); i++) {
            if (i > 0) {
                ddl.append(",");
            }
            ddl.append(partitionColumns.get(i).getName());
        }
        ddl.append(") ");
    }

    private String appendHashPartitionDDL(StringBuilder ddl, PartitionInfo partInfo) {
        List<PartitionTable> partitions = partInfo.getPartitions();
        int actualPartitionCount = partitions == null ? 0 : partitions.size();
        int hashPartitionCount =
                partInfo.getPartitionCount() > 0
                        ? partInfo.getPartitionCount()
                        : actualPartitionCount;
        if (hashPartitionCount <= 0) {
            return null;
        }
        ddl.append(" PARTITIONS ").append(hashPartitionCount);
        return ddl.toString();
    }

    private String appendRangeOrListPartitionDDL(
            StringBuilder ddl, PartitionInfo partInfo, String partitionMethod) {
        List<PartitionTable> partitions = partInfo.getPartitions();
        if (partitions == null || partitions.isEmpty()) {
            return null;
        }
        ddl.append("(").append(CommonUtils.newLine);
        for (int i = 0; i < partitions.size(); i++) {
            if (i > 0) {
                ddl.append(",").append(CommonUtils.newLine);
            }
            appendPartitionDefinition(ddl, partitions.get(i), partitionMethod);
        }
        ddl.append(CommonUtils.newLine).append(")");
        return ddl.toString();
    }

    private void appendPartitionDefinition(
            StringBuilder ddl, PartitionTable partTable, String partitionMethod) {
        ddl.append("PARTITION ").append(partTable.getPartitionName());
        if (PartitionInfo.PARTITION_METHOD_RANGE.equalsIgnoreCase(partitionMethod)) {
            appendRangePartitionValues(ddl, partTable);
            return;
        }
        if (PartitionInfo.PARTITION_METHOD_LIST.equalsIgnoreCase(partitionMethod)) {
            ddl.append(" VALUES IN (").append(partTable.getPartitionDesc()).append(")");
        }
    }

    private void appendRangePartitionValues(StringBuilder ddl, PartitionTable partTable) {
        ddl.append(" VALUES LESS THAN ");
        if ("MAXVALUE".equalsIgnoreCase(partTable.getPartitionDesc())) {
            ddl.append(partTable.getPartitionDesc());
            return;
        }
        ddl.append("(").append(partTable.getPartitionDesc()).append(")");
    }
}
