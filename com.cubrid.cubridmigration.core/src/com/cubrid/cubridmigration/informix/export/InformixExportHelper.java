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

package com.cubrid.cubridmigration.informix.export;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.export.DBExportHelper;
import com.cubrid.cubridmigration.core.export.IExportDataHandler;
import com.cubrid.cubridmigration.core.export.handler.CharTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixBSONTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixBooleanTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixCustomTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixJSONTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixListTypeHandler;
import com.cubrid.cubridmigration.informix.export.handler.InformixSetTypeHandler;

/**
 * InformixExportHelper Description
 *
 * @author rathana
 * @version 1.0 
 * @created Aug 18, 2022
 */
public class InformixExportHelper extends DBExportHelper{
	public static final int CUSTOM = -1;
	
	public InformixExportHelper(){
		super();
		handlerMap2.put("json", new InformixJSONTypeHandler());
		handlerMap2.put("bson", new InformixBSONTypeHandler());
		handlerMap2.put("boolean", new InformixBooleanTypeHandler());
		handlerMap2.put("set", new InformixSetTypeHandler());
		handlerMap2.put("list", new InformixListTypeHandler());
		handlerMap2.put("multiset", new InformixListTypeHandler());
//		handlerMap1.put(CUSTOM, new InformixCustomTypeHandler());
	}
	
	/**
	 * get JDBC Object
	 * 
	 * @param rs ResultSet
	 * @param column Column
	 * 
	 * @return Object
	 * @throws SQLException e
	 */
	public Object getJdbcObject(final ResultSet rs, final Column column) throws SQLException {
		IExportDataHandler edh = handlerMap2.get(column.getDataType());
		if (edh != null) {
			return edh.getJdbcObject(rs, column);
		}
		return super.getJdbcObject(rs, column);
	}
	
	public DatabaseType getDBType() {
		// TODO Auto-generated method stub
		return DatabaseType.INFORMIX;
	}

	@Override
	protected String getQuotedObjName(String objectName) {
		// TODO Auto-generated method stub
//		return DatabaseType.INFORMIX.getSQLHelper(null).getQuotedObjName(objectName);
		return objectName;
	}

	@Override
	public String getPagedSelectSQL(String sql, long pageSize, long exportedRecords, PK pk) {
		// TODO Auto-generated method stub
		return sql;
	}

}
