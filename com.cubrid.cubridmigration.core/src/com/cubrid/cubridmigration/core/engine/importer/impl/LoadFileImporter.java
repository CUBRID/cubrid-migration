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
package com.cubrid.cubridmigration.core.engine.importer.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.DBObject;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.MigrationDirAndFilesManager;
import com.cubrid.cubridmigration.core.engine.MigrationStatusManager;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;
import com.cubrid.cubridmigration.core.engine.task.FileMergeRunnable;
import com.cubrid.cubridmigration.core.engine.task.RunnableResultHandler;
import com.cubrid.cubridmigration.cubrid.Data2StrTranslator;

/**
 * LoadDBImporter : Use LoadDB and CSQL commands to import database objects.
 * 
 * @author Kevin Cao
 * @version 1.0 - 2011-8-3 created by Kevin Cao
 */
public class LoadFileImporter extends
		OfflineImporter {
	/**
	 * ExportStatus records the export status
	 * 
	 * @author Kevin Cao
	 * @version 1.0 - 2013-3-5 created by Kevin Cao
	 */
	private static class CurrentDataFileInfo {
		String fileHeader;
		String fileFullName;
		String fileTableFullName;
		int currentFileNO = 1;
		String fileExt;

		public CurrentDataFileInfo(String fileFullName, String header, String prefix, String owner, String name, String ext) {
			this.fileHeader = header;
			this.fileExt = ext;
			this.fileTableFullName = header + File.separator 
					+ owner + File.separator
					+ prefix + "_" + owner + "_" + name
					+ ext;
			this.fileFullName = fileFullName;
		}

		/**
		 * Create next file.
		 * 
		 */
		public void nextFile() {
			final StringBuffer sb = new StringBuffer(fileHeader);
			currentFileNO++;
			sb.append("_").append(currentFileNO);
			fileTableFullName = sb.append(fileExt).toString();
			//If has old file ,remove it firstly
			PathUtils.deleteFile(new File(fileTableFullName));
		}
	}

	protected final static Logger LOGGER = LogUtil.getLogger(LoadFileImporter.class);

	private final Map<String, CurrentDataFileInfo> tableFiles = new HashMap<String, CurrentDataFileInfo>();
	private final Map<String, String> schemaFiles = new HashMap<String, String>();
	private final Map<String, String> tableSchemaFiles = new HashMap<String, String>();
	private final Map<String, String> viewFiles = new HashMap<String, String>();
	private final Map<String, String> pkFiles = new HashMap<String, String>();
	private final Map<String, String> fkFiles = new HashMap<String, String>();
	private final Map<String, String> indexFiles = new HashMap<String, String>();
	private final Map<String, String> sequenceFiles = new HashMap<String, String>();
	private final Map<String, String> synonymFiles = new HashMap<String, String>();
	private final Map<String, String> grantFiles = new HashMap<String, String>();

	private final Object lockObj = new Object();

	public LoadFileImporter(MigrationContext mrManager) {
		super(mrManager);
		unloadFileUtil = new Data2StrTranslator(mrManager.getDirAndFilesMgr().getMergeFilesDir(),
				config, config.getDestType());
	}

	/**
	 * 
	 * Execute merge file tasks
	 * 
	 * @param sourceFile is file to be read.
	 * @param targetFile specify schema or data
	 * @param listener call back method
	 * @param deleteFile delete file after task finished
	 * @param isSchemaFile true if file is schema file
	 */
	private void executeTask(String sourceFile, String targetFile, RunnableResultHandler listener,
			boolean deleteFile, boolean isSchemaFile) {
		cmTaskService.execute(new FileMergeRunnable(sourceFile, targetFile,
				config.getTargetCharSet(), listener, deleteFile, isSchemaFile
						|| !config.targetIsXLS()));
	}

	/**
	 * Send schema file and data file to server for loadDB command.
	 * 
	 * @param fileName the file to be sent.
	 * @param stc source table configuration.
	 * @param impCount the count of records in file.
	 * @param expCount exported record count
	 */
	protected void handleDataFile(String fileName, final SourceTableConfig stc, final int impCount,
			final int expCount) {
		synchronized (lockObj) {
			MigrationDirAndFilesManager mdfm = mrManager.getDirAndFilesMgr();
			
			if (!tableFiles.containsKey(stc.getTargetOwner() + stc.getName())) {
				tableFiles.put(stc.getTargetOwner() + stc.getName(), new CurrentDataFileInfo(config.getTargetDataFileName(stc.getTargetOwner()), 
						mdfm.getMergeFilesDir(),config.getTargetFilePrefix(), stc.getTargetOwner(), stc.getName(), config.getDataFileExt()));
			}
			
			CurrentDataFileInfo es = tableFiles.get(stc.getTargetOwner() + stc.getName());
			
			//If the target file is full. 
			if (mdfm.isDataFileFull(es.fileTableFullName)) {
				//Full name will be changed.
				es.nextFile();
			}
			final String fileTableFullName = es.fileTableFullName;
			final String fileFullName = es.fileFullName;
			mdfm.addDataFile(fileTableFullName, impCount);
			executeTask(fileName, fileTableFullName, new RunnableResultHandler() {

				public void success() {
					eventHandler.handleEvent(new ImportRecordsEvent(stc, impCount));
					final MigrationStatusManager sm = mrManager.getStatusMgr();
					sm.addImpCount(stc.getOwner(), stc.getName(), expCount);
					//CSV, XLS file will not be merged into one data file.
					if (config.targetIsCSV() || config.targetIsXLS()) {
						return;
					}
					if (config.isOneTableOneFile()) {
						return;
					}
					final Table st = config.getSrcTableSchema(stc.getOwner(), stc.getName());
					if (null == st) {
						return;
					}
					final long totalEc = sm.getExpCount(stc.getOwner(), stc.getName());
					final long totalIc = sm.getImpCount(stc.getOwner(), stc.getName());
					final boolean expEnd = sm.getExpFlag(stc.getOwner(), stc.getName());
					//If it is the last merging,Merge data files to one data file
					if (expEnd && totalEc == totalIc) {
						executeTask(fileTableFullName, fileFullName, null, true, false);
					}
				}

				public void failed(String error) {
					mrManager.getStatusMgr().addImpCount(stc.getOwner(), stc.getName(), expCount);
					eventHandler.handleEvent(new ImportRecordsEvent(stc, impCount,
							new NormalMigrationException(error), null));
				}
			}, config.isDeleteTempFile(), false);
		}
	}
	
	/**
	 * Send Schema file to server loadDB command.
	 * 
	 * @param owner
	 * @return schema file full path
	 */
	protected String handleSchemaFile(String owner) {
		if (!schemaFiles.containsKey(owner)) {
			schemaFiles.put(owner, config.getTargetSchemaFileName(owner));
		}
		return schemaFiles.get(owner);
	}
	
	/**
	 * Send Table file to server loadDB command.
	 * 
	 * @param owner
	 * @return table file full path
	 */
	protected String handleTableSchemaFile(String owner) {
		if (!tableSchemaFiles.containsKey(owner)) {
			tableSchemaFiles.put(owner, config.getTargetTableFileName(owner));
		}
		return tableSchemaFiles.get(owner);
	}
	
	/**
	 * Send View file to server loadDB command.
	 * 
	 * @param owner
	 * @return view file pull path
	 */
	protected String handleViewFile(String owner) {
		if (!viewFiles.containsKey(owner)) {
			viewFiles.put(owner, config.getTargetViewFileName(owner));
		}
		return viewFiles.get(owner);
	}
	
	protected String handlePkFile(String owner) {
		if (!pkFiles.containsKey(owner)) {
			pkFiles.put(owner, config.getTargetPkFileName(owner));
		}
		return pkFiles.get(owner);
	}
	
	protected String handleFkFile(String owner) {
		if (!fkFiles.containsKey(owner)) {
			fkFiles.put(owner, config.getTargetFkFileName(owner));
		}
		return fkFiles.get(owner);
	}
	
	/**
	 * Send Index file to server loadDB command.
	 * 
	 * @param owner
	 * @return index file full path
	 */
	protected String handleIndexFile(String owner) {
		if (!indexFiles.containsKey(owner)) {
			indexFiles.put(owner, config.getTargetIndexFileName(owner));
		}
		return indexFiles.get(owner);
	}
	
	/**
	 * Send Sequence file to server loadDB command.
	 * 
	 * @param owner
	 * @return sequence file full path
	 */
	protected String handleSequenceFile(String owner) {
		if (!sequenceFiles.containsKey(owner)) {
			sequenceFiles.put(owner, config.getTargetSerialFileName(owner));
		}
		return sequenceFiles.get(owner);
	}
	
	/**
	 * Send Synonym file to server loadDB command.
	 * 
	 * @param owner
	 * @return synonym file full path
	 */
	protected String handleSynonymFile(String owner) {
		if (!synonymFiles.containsKey(owner)) {
			synonymFiles.put(owner, config.getTargetSynonymFileName(owner));
		}
		return synonymFiles.get(owner);
	}
	
	/**
	 * Send Grant file to server loadDB command.
	 * 
	 * @param owner
	 * @return grant file full path
	 */
	protected String handleGrantFile(String owner) {
		if (!grantFiles.containsKey(owner)) {
			grantFiles.put(owner, config.getTargetGrantFileName(owner));
		}
		return grantFiles.get(owner);
	}
	
	/**
	 * Send schema file and data file to server for loadDB command.
	 * 
	 * @param fileName the file to be sent.
	 * @param tableName tableName
	 * 
	 */
	protected void sendLOBFile(String fileName, String tableName) {
		///home/cmt/CUBRID/database/mt/log/blob1/blob.xxxxxxx
		String targetFile = getLOBDir(tableName) + new File(fileName).getName();
		//Copy to target if it is local
		try {
			CUBRIDIOUtils.mergeFile(fileName, targetFile);
		} catch (IOException ex) {
			LOGGER.error("", ex);
		}
	}

	/**
	 * Send schema file and data file to server for loadDB command.
	 * 
	 * @param fileName the file to be sent.
	 * @param listener a call interface.
	 * @param isIndex true if the DDL is about index
	 */
	protected void sendSchemaFile(String fileName, RunnableResultHandler listener, String objectType, String owner) {
		executeTask(fileName, getFilePath(objectType, owner), listener, config.isDeleteTempFile(), true);
	}
	
	private String getFilePath(String objectType, String owner) {
		if (config.isSplitSchema()) {
			if (objectType.equals(DBObject.OBJ_TYPE_TABLE)) {
				return handleTableSchemaFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_VIEW)) {
				return handleViewFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_PK)) {
				return handlePkFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_FK)) {
				return handleFkFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_INDEX)) {
				return handleIndexFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_SEQUENCE)) {
				return handleSequenceFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_SYNONYM)) {
				return handleSynonymFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_GRANT)) {
				return handleGrantFile(owner);
			}
		} else {
			if (objectType.equals(DBObject.OBJ_TYPE_TABLE)
					|| objectType.equals(DBObject.OBJ_TYPE_VIEW)
					|| objectType.equals(DBObject.OBJ_TYPE_PK)
					|| objectType.equals(DBObject.OBJ_TYPE_FK)
					|| objectType.equals(DBObject.OBJ_TYPE_SEQUENCE)) {
				return handleSchemaFile(owner);
			} else if (objectType.equals(DBObject.OBJ_TYPE_INDEX)) {
				return handleIndexFile(owner);
			}
		}
		
		return handleSchemaFile(owner);
	}

	/**
	 * Get lob files directory
	 * 
	 * @param tableName string
	 * @return lob directory
	 */
	protected String getLOBDir(String tableName) {
		return mrManager.getDirAndFilesMgr().getLobFilesDir() + tableName + File.separatorChar;
	}
}
