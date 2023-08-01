/*
 * Copyright 2016 CUBRID Corporation. All rights reserved by Search Solution. 
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
package com.cubrid.cubridmigration.core.engine.task.imp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

/**
 * Output dump file with create user query
 * 
 * @author CUBRID
 */
public class CreateUserSQLTask 
		extends ImportTask {
	
	private final static Logger LOG = LogUtil.getLogger(CreateUserSQLTask.class);
	private final MigrationConfiguration config;
	
	public CreateUserSQLTask(MigrationConfiguration config) {
		this.config = config;
	}
	
	/**
	 * Execute import
	 */
	@Override
	protected void executeImport() {
		File createUserSQLFile = new File(config.getFileRepositroyPath() 
				+ File.separator + "create_user.sql");
		LOG.debug("Write to create user sql file");
		
		try {
			List<String> sb = new ArrayList<String>();
			List<Schema> schemaList = null;
			if (config.getTargetSchemaList().size() > 0) {
				schemaList = config.getTargetSchemaList();
			} else {
				Collection<Schema> schemas = config.getScriptSchemaMapping().values();
				schemaList = new ArrayList<Schema>(schemas);
			}
			
			for (Schema schema : schemaList) {
				String schemaTargetName = schema.getTargetSchemaName();
				if (schemaTargetName.equalsIgnoreCase("DBA")) {
					continue;
				}
				sb.add(getCreateUserSQL(schemaTargetName));
			}
			
			PathUtils.deleteFile(createUserSQLFile);
			PathUtils.createFile(createUserSQLFile);
			CUBRIDIOUtils.writeLines(createUserSQLFile, sb.toArray(new String[]{}));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Get create user query
	 * 
	 * @param targetSchemaName String
	 * @return create user query String
	 */
	private String getCreateUserSQL(String targetSchemaName) {
		return "CREATE USER " + targetSchemaName + ";";
	}
}
