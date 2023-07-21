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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.log4j.Logger;

import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

/**
 * SchemaFileListTask creates a list of schema file names to use with loaddb.
 * 
 * @author Dongmin Kim
 * @version 1.0 - 2023-5-4 created by Dongmin Kim
 */
public class SchemaFileListTask extends ImportTask {
	
	private final static Logger LOG = LogUtil.getLogger(SchemaFileListTask.class);
	
	private final MigrationConfiguration config;
	
	public SchemaFileListTask(MigrationConfiguration config) {
		this.config = config;
	}
	
	/**
	 * Execute import
	 */
	@Override
	protected void executeImport() {
		String lineSeparator = System.getProperty("line.separator");
		List<Schema> schemaList = config.getTargetSchemaList();
		for (Schema schema : schemaList) {
			String schemaName = schema.getTargetSchemaName();
			LOG.debug("Write to schema list file for " + schemaName);
			
			StringBuilder sb = new StringBuilder();
			OutputStream os = null;
			boolean isCreateSchemaListFile = false;
			File schemaFileListFile = new File(config.getTargetSchemaFileListName(schemaName));
			try {
				// table
				String tableFileRepository = config.getTargetTableFileName(schemaName);
				if (checkFileRepository(tableFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(tableFileRepository));
					sb.append(lineSeparator);
				}
				
				// synonym
				String synonymFileRepository = config.getTargetSynonymFileName(schemaName);
				if (checkFileRepository(synonymFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(synonymFileRepository));
					sb.append(lineSeparator);
				}
				
				// view
				String viewFileRepository = config.getTargetViewFileName(schemaName);
				if (checkFileRepository(viewFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(viewFileRepository));
					sb.append(lineSeparator);
				}
				
				// serial
				String serialFileRepository = config.getTargetSerialFileName(schemaName);
				if (checkFileRepository(serialFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(serialFileRepository));
					sb.append(lineSeparator);
				}
				
				// pk
				String pkFileRepository = config.getTargetPkFileName(schemaName);
				if (checkFileRepository(pkFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(pkFileRepository));
					sb.append(lineSeparator);
				}
				
				// fk
				String fkFileRepository = config.getTargetFkFileName(schemaName);
				if (checkFileRepository(fkFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(fkFileRepository));
					sb.append(lineSeparator);
				}
				
				// grant
				String grantFileRepository = config.getTargetGrantFileName(schemaName);
				if (checkFileRepository(grantFileRepository)) {
					isCreateSchemaListFile = true;
					sb.append(getFileName(grantFileRepository));
					sb.append(lineSeparator);
				}
				
				if (isCreateSchemaListFile) {
					os = new BufferedOutputStream(new FileOutputStream(schemaFileListFile, true));
					os.write(sb.toString().getBytes());
					os.flush();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				if (os != null) {
					Closer.close(os);
				}
			}
		}
	}
	
	/**
	 * Extract file names from file repository
	 * 
	 * @param fileRepository
	 * @return fileName String
	 */
	private String getFileName(String fileRepository) {
		File file = new File(fileRepository);
		return file.getName();
	}
	
	/**
	 * Check if a File Repository Exists
	 * 
	 * @param fileRepository
	 * @return file exist true, no file false boolean
	 */
	private boolean checkFileRepository(String fileRepository) {
		File file = new File(fileRepository);
		return file.exists();
	}
}
