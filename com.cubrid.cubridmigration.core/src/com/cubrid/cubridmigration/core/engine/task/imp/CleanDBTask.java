/*
 * Copyright (C) 2009 Search Solution Corporation. All rights reserved by Search Solution. 
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceFKConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceIndexConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceViewConfig;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

/**
 * CleanDBTask is to clear the database objects which were set to be replaced in
 * target database
 * 
 * @author Kevin Cao
 * @version 1.0 - 2012-9-5 created by Kevin Cao
 */
public class CleanDBTask extends
		ImportTask {

	private final static Logger LOG = LogUtil.getLogger(CleanDBTask.class);

	private final MigrationConfiguration config;
	
	public CleanDBTask(MigrationConfiguration config) {
		this.config = config;
	}

	/**
	 * Execute import
	 */
	protected void executeImport() {
		Map<String, List<String>> dropQueryBySchemaMap = new HashMap<String, List<String>>();
		Map<String, List<String>> fkDropQueryBySchemaMap = new HashMap<String, List<String>>();
		Map<String, List<String>> tbTruncateQueryBySchemaMap = new HashMap<String, List<String>>();
		
		for (SourceEntryTableConfig setc : config.getExpEntryTableCfg()) {
			if (!setc.isCreateNewTable()) {
				continue;
			}
			for (SourceFKConfig sfkc : setc.getFKConfigList()) {
				if (sfkc.isCreate() && sfkc.isReplace()) {
					
					StringBuffer query = new StringBuffer();
					query.append("ALTER TABLE ");
					query.append(addSchemaName(config.isAddUserSchema(), setc.getTargetOwner()));
					query.append(getBracketsObjName(setc.getTarget()) 
							+ " DROP CONSTRAINT " + getBracketsObjName(sfkc.getTarget()));
					query.append(";");
					
					LOG.info("drop foreign key : " + query.toString());
					
					divideQueryBySchema(fkDropQueryBySchemaMap, setc.getTargetOwner(), query.toString());
					execDDL(query.toString());
				}
			}
			for (SourceIndexConfig idx : setc.getIndexConfigList()) {
				if (idx.isCreate() && idx.isReplace()) {
					
					StringBuffer query = new StringBuffer();
					
					query.append("ALTER TABLE ");
					query.append(addSchemaName(config.isAddUserSchema(), setc.getTargetOwner()));
					query.append(getBracketsObjName(setc.getTarget()) 
							+ " DROP CONSTRAINT " + getBracketsObjName(idx.getTarget()));
					query.append(";");
					
					LOG.info("drop index : " + query.toString());
					
					divideQueryBySchema(dropQueryBySchemaMap, setc.getTargetOwner(), query.toString());
					execDDL(query.toString());
				}
			}
		}
		for (SourceEntryTableConfig setc : config.getExpEntryTableCfg()) {
			if (setc.isCreateNewTable() && setc.isReplace()) {
				StringBuffer query = new StringBuffer();
				
				query.append("DROP TABLE ");
				query.append(addSchemaName(config.isAddUserSchema(), setc.getTargetOwner()));
				query.append(getBracketsObjName(setc.getTarget()));
				query.append(";");
				
				LOG.info("drop table query : " + query.toString());
				
				divideQueryBySchema(dropQueryBySchemaMap, setc.getTargetOwner(), query.toString());
				divideQueryBySchema(tbTruncateQueryBySchemaMap, setc.getTargetOwner(), 
						query.toString().replaceAll("DROP", "TRUNCATE"));
				execDDL(query.toString());
			}
		}
		for (SourceSQLTableConfig sstc : config.getExpSQLCfg()) {
			if (sstc.isCreateNewTable() && sstc.isReplace()) {
				StringBuffer query = new StringBuffer();
				
				query.append("DROP TABLE ");
				query.append(addSchemaName(config.isAddUserSchema(), sstc.getTargetOwner()));
				query.append(getBracketsObjName(sstc.getTarget()));
				query.append(";");
				
				LOG.info("drop table query : " + query.toString());
				
				divideQueryBySchema(dropQueryBySchemaMap, sstc.getTargetOwner(), query.toString());
				divideQueryBySchema(tbTruncateQueryBySchemaMap, sstc.getTargetOwner(), 
						query.toString().replaceAll("DROP", "TRUNCATE"));
				execDDL(query.toString());
			}
		}
		for (SourceCSVConfig scc : config.getCSVConfigs()) {
			if (scc.isCreate() && scc.isReplace()) {
				StringBuffer query = new StringBuffer();
				
				query.append("DROP TABLE ");
				query.append(addSchemaName(config.isAddUserSchema(), scc.getTargetOwner()));	
				query.append(getBracketsObjName(scc.getTarget()));
				query.append(";");
				
				LOG.info("drop table query : " + query.toString());
				
				divideQueryBySchema(dropQueryBySchemaMap, scc.getTargetOwner(), query.toString());
				divideQueryBySchema(tbTruncateQueryBySchemaMap, scc.getTargetOwner(), 
						query.toString().replaceAll("DROP", "TRUNCATE"));
				execDDL(query.toString());
			}
		}
		for (SourceViewConfig sc : config.getExpViewCfg()) {
			if (sc.isCreate() && sc.isReplace()) {
				StringBuffer query = new StringBuffer();
				
				query.append("DROP VIEW ");
				query.append(addSchemaName(config.isAddUserSchema(), sc.getTargetOwner()));
				query.append(getBracketsObjName(sc.getTarget()));
				query.append(";");
				
				LOG.info("drop view query : " + query.toString());
				
				divideQueryBySchema(dropQueryBySchemaMap, sc.getTargetOwner(), query.toString());
				execDDL(query.toString());
			}
		}
		for (SourceSequenceConfig sc : config.getExpSerialCfg()) {
			if (sc.isCreate() && sc.isReplace()) {
				StringBuffer query = new StringBuffer();
				
				query.append("DROP SERIAL ");
				query.append(addSchemaName(config.isAddUserSchema(), sc.getTargetOwner()));
				query.append(getBracketsObjName(sc.getTarget()));
				query.append(";");
				
				LOG.info("drop serial query : " + query.toString());
				
				divideQueryBySchema(dropQueryBySchemaMap, sc.getTargetOwner(), query.toString());
				execDDL(query.toString());
				
			}
		}
		for (SourceSynonymConfig sc : config.getExpSynonymCfg()) {
			if (sc.isCreate() && sc.isReplace()) {
				StringBuffer query = new StringBuffer();

				query.append("DROP SYNONYM ");
				query.append(addSchemaName(config.isAddUserSchema(), sc.getTargetOwner()));
				query.append(getBracketsObjName(sc.getTarget()));
				query.append(";");

				LOG.info("drop syonym query : " + query.toString());

				divideQueryBySchema(dropQueryBySchemaMap, sc.getTargetOwner(), query.toString());
				execDDL(query.toString());
			}
		}
		
		if (config.targetIsFile()) {
			List<Schema> schemaList = null;
			if (config.getTargetSchemaList().size() > 0) {
				schemaList = config.getTargetSchemaList();
			} else {
				Collection<Schema> schemas = config.getScriptSchemaMapping().values();
				schemaList = new ArrayList<Schema>(schemas);
			}
			
			for (Schema schema : schemaList) {
				String ownerName = schema.getTargetSchemaName();
				writeFile(dropQueryBySchemaMap, ownerName, "_clear.sql");
				writeFile(fkDropQueryBySchemaMap, ownerName, "_drop_fk.sql");
				writeFile(tbTruncateQueryBySchemaMap, ownerName, "_truncate.sql");
			}
		}
	}
	
	/**
	 * Drop queries are written to a file
	 * 
	 * @param map Map<String, List<String>>
	 * @param ownerName String
	 * @param fileName String
	 */
	private void writeFile(Map<String, List<String>> map, String ownerName, String fileName) {
		if (!map.isEmpty()) {
			File clearFile = new File(config.getFileRepositroyPath() + File.separator
					+ ownerName + File.separator + ownerName + fileName);
			try {
				PathUtils.deleteFile(clearFile);
				if (map.containsKey(ownerName)) {
					PathUtils.createFile(clearFile);
					CUBRIDIOUtils.writeLines(clearFile, map.get(ownerName).toArray(new String[]{}),
							config.getTargetCharSet());
				}
			} catch (IOException e) {
				LOG.error("", e);
			}
		}
	}
	
	/**
	 * Divide drop queries by schema
	 * 
	 * @param targetOwner String
	 * @param query String
	 */
	private void divideQueryBySchema(Map<String, List<String>> map, String targetOwner, String query) {
		if (map.containsKey(targetOwner)) {
			map.get(targetOwner).add(query);
		} else {
			List<String> list = new ArrayList<String>();
			list.add(query);
			map.put(targetOwner, list);
		}
	}

	/**
	 * Return object names with square bracket
	 * 
	 * @param objName String
	 * @return object name with quotes
	 */
	private String getBracketsObjName(String objName) {
		return "[" + objName + "]";
	}

	/**
	 * If it is a multi schema, give the user schema name
	 * 
	 * @param isAddUserSchema boolean
	 * @param schemaName String
	 * @return User schema name String
	 */
	private String addSchemaName(boolean isAddUserSchema, String schemaName) {
		return isAddUserSchema ? "[" + schemaName + "]." : "";
	}

	/**
	 * Execute DDL
	 * 
	 * @param sql String
	 */
	private void execDDL(String sql) {
		if (config.targetIsFile()) {
			return;
		}
		try {
			importer.executeDDL(sql);
		} catch (Exception ex) {
			LOG.warn("Clearn SQL:" + sql, ex);
		}
	}
}
