/*
 * Copyright (c) 2016 CUBRID Corporation.
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

package com.cubrid.cubridmigration.informix;

import java.util.List;
import java.util.Map;

import com.cubrid.cubridmigration.core.datatype.DBDataTypeHelper;
import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

/**
 * InformixDataTypeHelper Description
 *
 * @author rathana
 * @version 1.0 
 * @created Sep 6, 2022
 */
public class InformixDataTypeHelper extends DBDataTypeHelper{
	
	
	private static final String INFORMIX_BIN_TYPES = "/byte/bson/blob/";
	private static final String INFORMIX_NVCHAR_TYPES = "/nchar/";
	private static final String INFOMRIX_STR_TYPES = "/text/char/varchar/clob/";
	private static final String INFOMRIX_COLLECTION_TYPES = "/set/list/multiset/";
	private static final InformixDataTypeHelper HELPER = new InformixDataTypeHelper();
	
	private InformixDataTypeHelper() {
		//prevent instantiate
	}
	
	public static InformixDataTypeHelper getInstance(String version){
		return HELPER;
	}
	
	public DatabaseType getDBType() {
		// TODO Auto-generated method stub
		return DatabaseType.INFORMIX;
	}

	@Override
	public Integer getJdbcDataTypeID(Catalog catalog, String dataType, Integer precision, Integer scale) {
		String key = dataType;
		Map<String, List<DataType>> supportedDataType = catalog.getSupportedDataType();

		List<DataType> dataTypeList = supportedDataType.get(key);
		if (dataTypeList == null) {
			throw new IllegalArgumentException(
					"Not supported Informix data type(" + dataType + ")");
		}
		if (dataTypeList.size() == 1) {
			return dataTypeList.get(0).getJdbcDataTypeID();
		}
		throw new IllegalArgumentException(
				"Not supported  Informix data type(" + dataType + ": p="
						+ precision + ", s=" + scale + ")");
	}

	@Override
	public String getShownDataType(Column column) {
		// TODO Auto-generated method stub
		String colType = column.getDataType() == null ? ""
				: column.getDataType();
		Integer precision = column.getPrecision();
		Integer scale = column.getScale();
		colType = colType.replace("identity", "").trim();
		if (checkType("/text/xml/", colType)) {
			return colType;
		} else if (isGenericString(colType)) {
			return colType + "(" + precision + ")";
		} else if (checkType("/char/varchar/nchar/serial/", colType)) {
			return colType + "(" + precision + ")";
		} else if (checkType("/decimal/numeric/money/", colType)) {
			return colType + "(" + precision + "," + scale + ")";
		}
		return colType;
	}

	@Override
	public boolean isBinary(String dataType) {
		// TODO Auto-generated method stub
		return checkType(INFORMIX_BIN_TYPES, dataType);
	}

	@Override
	public boolean isCollection(String dataType) {
		// TODO Auto-generated method stub
//		return checkType(INFOMRIX_COLLECTION_TYPES, dataType);
		return false;
	}
	
	public boolean isEnum(String dataType) {
		return checkType("/enum/", dataType);
	}
	

}
