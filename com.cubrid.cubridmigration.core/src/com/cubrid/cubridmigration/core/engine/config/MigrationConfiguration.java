/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.core.engine.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import au.com.bytecode.opencsv.CSVReader;

import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.CharsetUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.TimeZoneUtils;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.DBSchemaInfoFetcherFactory;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSchemaInfoFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSource;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Procedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.Trigger;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.trans.DBTransformHelper;
import com.cubrid.cubridmigration.core.trans.MigrationTransFactory;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.cubrid.CUBRIDSQLHelper;
import com.cubrid.cubridmigration.mysql.MysqlXmlDumpSource;

/**
 * 
 * MigrationConfiguration Description
 * 
 * @author Kevin Cao fulei
 * @version 1.0 - 2011-9-8 created by Kevin Cao
 */
public class MigrationConfiguration {

	private static final Logger LOG = LogUtil.getLogger(MigrationConfiguration.class);

	public static final int XLS_MAX_COUNT = 65536;
	public static final char CSV_NO_CHAR = '\u0000';

	public static final int DEST_DB_UNLOAD = 0;
	public static final int DEST_CSV = 1;
	public static final int DEST_SQL = 2;
	public static final int DEST_XLS = 3;
	public static final int DEST_ONLINE = 4;

	public static final int SOURCE_TYPE_CUBRID = DatabaseType.CUBRID.getID();
	public static final int SOURCE_TYPE_MYSQL = DatabaseType.MYSQL.getID();
	public static final int SOURCE_TYPE_ORACLE = DatabaseType.ORACLE.getID();
	public static final int SOURCE_TYPE_MSSQL = DatabaseType.MSSQL.getID();
	public static final int SOURCE_TYPE_MARIADB = DatabaseType.MARIADB.getID();
	public static final int SOURCE_TYPE_INFORMIX = DatabaseType.INFORMIX.getID();
	
	public static final int SOURCE_TYPE_XML_1 = 101;
	public static final int SOURCE_TYPE_SQL = 102;
	public static final int SOURCE_TYPE_CSV = 103;

	public static final String XML = "xml";
	public static final String SQL = "sql";
	public static final String CSV = "csv";
	public static final String CUBRID = "cubrid";
	public static final String MYSQL = "mysql";
	public static final String ORACLE = "oracle";

	public static final int RPT_LEVEL_BRIEF = 0;
	public static final int RPT_LEVEL_ERROR = 1;
	public static final int RPT_LEVEL_INFO = 2;
	public static final int RPT_LEVEL_DEBUG = 3;

	private static final String[] DATA_FORMAT_EXT = new String[] { ".txt", ".csv", ".sql", ".xls",
			"", "" };
	private static final String[] DATA_FORMAT_LABEL = new String[] { "LoadDB", "CSV", "SQL", "XLS" };

	/**
	 * Retrieves all fomrat exts
	 * 
	 * @return {.txt,.csv,.sql}
	 */
	public static String[] getDataFileFormatExts() {
		return Arrays.copyOf(DATA_FORMAT_EXT, DATA_FORMAT_EXT.length);
	}

	private int commitCount = 1000;
	private int maxCountPerFile = 0;
	private int exportThreadCount = 2;
	private int importThreadCount = 2;

	private boolean deleteTempFile = true;

	private boolean exportNoSupportObjects = true;
	private final List<String> expFunctions = new ArrayList<String>();

	private final List<String> expProcedures = new ArrayList<String>();
	private final List<SourceGrantConfig> expGrants = new ArrayList<SourceGrantConfig>();
	private final List<SourceSynonymConfig> expSynonyms = new ArrayList<SourceSynonymConfig>();
	private final List<SourceSequenceConfig> expSerials = new ArrayList<SourceSequenceConfig>();
	private final List<SourceSQLTableConfig> expSQLTables = new ArrayList<SourceSQLTableConfig>();
	private final List<SourceEntryTableConfig> expTables = new ArrayList<SourceEntryTableConfig>();
	private final List<String> expTriggers = new ArrayList<String>();
	private final List<SourceViewConfig> expViews = new ArrayList<SourceViewConfig>();
	private String fileRepositroyPath;
	private final List<SourceCSVConfig> csvFiles = new ArrayList<SourceCSVConfig>();

	private Map<String, String> targetSchemaFileName = new HashMap<String, String>();
	private Map<String, String> targetTableFileName = new HashMap<String, String>();
	private Map<String, String> targetViewFileName = new HashMap<String, String>();
	private Map<String, String> targetViewQuerySpecFileName = new HashMap<String, String>();
	private Map<String, String> targetPkFileName = new HashMap<String, String>();
	private Map<String, String> targetFkFileName = new HashMap<String, String>();
	private Map<String, String> targetIndexFileName = new HashMap<String, String>();
	private Map<String, String> targetSerialFileName = new HashMap<String, String>();
	private Map<String, String> targetDataFileName = new HashMap<String, String>();
	private Map<String, String> targetUpdateStatisticFileName = new HashMap<String, String>();
	private Map<String, String> targetSchemaFileListName = new HashMap<String, String>();
	private Map<String, String> targetSynonymFileName = new HashMap<String, String>();
	private Map<String, String> targetGrantFileName = new HashMap<String, String>();
	private String targetFilePrefix;
	private String targetCharSet = "UTF-8";
	private String targetLOBRootPath = "";
	private boolean addUserSchema;
	private boolean splitSchema;
	private boolean targetDBAGroup;
	private boolean createUserSQL;

	private final CSVSettings csvSettings = new CSVSettings();

	private ConnParameters sourceConParams;

	private int sourceType = SOURCE_TYPE_CUBRID;
	private Catalog srcCatalog;
	private int tarSchemaSize;
	private Catalog offlineSrcCatalog;

	private final List<Table> srcSQLSchemas = new ArrayList<Table>();

	private boolean createConstrainsBeforeData = false;

	//default is utf-8
	private String sourceFileEncoding;
	private String sourceFileName;
	private String sourceFileTimeZone;
	private String sourceFileVersion;
	private int destType;
	private ConnParameters targetConParams;

	private List<String> newTargetSchema = new ArrayList<String>();
	private List<Schema> targetSchemaList = new ArrayList<Schema>();
	
	private String targetDBVersion;
	private final List<Table> targetTables = new ArrayList<Table>();
	private final List<View> targetViews = new ArrayList<View>();
	private final List<Sequence> targetSequences = new ArrayList<Sequence>();
	private final List<Synonym> targetSynonyms = new ArrayList<Synonym>();
	private final List<Grant> targetGrants = new ArrayList<Grant>();
	private String targetFileTimeZone = "Default";

	//Used by database unload file migration, 
	//if it is true, the data will be saved into several files.
	private boolean oneTableOneFile;
	private boolean isWriteErrorRecords;

	private final List<String> sqlFiles = new ArrayList<String>();

	private final Map<String, String> otherParams = new HashMap<String, String>();

	private int pageFetchCount = 1000;
	private int reportLevel = RPT_LEVEL_INFO;

	private boolean implicitEstimate = false;

	private String name;
	//True by default
	private boolean updateStatistics = true;
	
	private Map<String, Schema> scriptSchemaMapping = new HashMap<String, Schema>();
	
	private boolean isOldScript = false;
	
	private boolean isTarSchemaDuplicate = false;
	
	/**
	 * Add a CSV file to exporting list.
	 * 
	 * @param csvFile full name of the CSV file
	 */
	public void addCSVFile(SourceCSVConfig csvFile) {
		csvFiles.add(csvFile);
	}

	/**
	 * Add a CSV file to exporting list.
	 * 
	 * @param csvFile full name of the CSV file
	 * @param ts target table, may be null
	 */
	public void addCSVFile(String csvFile, Schema ts) {
		final SourceCSVConfig csvConfig = getCSVConfigByFile(csvFile);
		if (csvConfig != null) {
			return;
		}
		SourceCSVConfig scc = new SourceCSVConfig();
		scc.setName(csvFile);
		String fileName = new File(csvFile).getName();
		scc.setTarget(StringUtils.lowerCase(fileName.substring(0, fileName.length() - 4)));
		parsingCSVFile(scc);
		//create target table schema
		final Table tblInTargetDB = ts.getTableByName(scc.getTarget());
		if (tblInTargetDB != null) {
			scc.setCreate(false);
			scc.setReplace(false);
			scc.changeTarget(tblInTargetDB);
		}
		createTargetCSVTable(scc, tblInTargetDB);
		csvFiles.add(scc);
	}

	/**
	 * Add a SourceEntryTableConfig to configuration
	 * 
	 * @param stc SourceEntryTableConfig
	 */
	public void addExpEntryTableCfg(SourceEntryTableConfig stc) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		expTables.add(stc);
	}

	/**
	 * Add an export function.
	 * 
	 * @param name function name
	 */
	public void addExpFunctionCfg(String name) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		if (expFunctions.indexOf(name) < 0) {
			expFunctions.add(name);
		}
	}

	/**
	 * Add an export procedure.
	 * 
	 * @param name procedure name
	 */
	public void addExpProcedureCfg(String name) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		if (expProcedures.indexOf(name) < 0) {
			expProcedures.add(name);
		}
	}

	/**
	 * Add sequence to export configuration
	 * 
	 * @param schema Schema name
	 * @param sourceName String
	 * @param target String name
	 * @return Retrieves the new SourceSequenceConfig has been added.
	 */
	public SourceSequenceConfig addExpSerialCfg(String schema, String sourceName, String target) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		SourceSequenceConfig sc = getExpSerialCfg(schema, sourceName);
		if (sc == null) {
			sc = new SourceSequenceConfig();
			expSerials.add(sc);
		}
		sc.setName(sourceName);
		sc.setTarget(StringUtils.lowerCase(target));
		sc.setOwner(schema);
		return sc;
	}
	
	/**
	 * Get exporting synonym
	 * 
	 * @param schema name
	 * @param name of the object
	 * @return synonym
	 */
	public Synonym getExpSynonym(String schema, String name) {
		Schema sc = srcCatalog.getSchemaByName(schema);
		if (sc != null) {
			return sc.getSynonym(sc.getName(), name);
		}
		return null;
	}
	
	/**
	 * Add synonym to export configuration
	 * 
	 * @param schema Schema name
	 * @param sourceName String
	 * @param target String name
	 * @return Retrieves the new SourceSynonymConfig has been added.
	 */
	public SourceSynonymConfig addExpSynonymCfg(String schema, String name, 
			String targetOwner, String target, String objectOwner, String object, 
			String objectTargetOwner, String objectTarget) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		SourceSynonymConfig sc = getExpSynonymCfg(schema, name);
		if (sc == null) {
			sc = new SourceSynonymConfig();
			expSynonyms.add(sc);
		}
		sc.setOwner(schema);
		sc.setName(name);
		sc.setTargetOwner(targetOwner);
		sc.setTarget(target);
		sc.setObjectOwner(objectOwner);
		sc.setObjectName(object);
		sc.setObjectTargetOwner(objectTargetOwner);
		sc.setObjectTargetName(objectTarget);
		return sc;
	}
	
	/**
	 * Add grant to export configuration
	 * 
	 * @param schema
	 * @param name
	 * @param grantor
	 * @param grantee
	 * @param object
	 * @param objectOwner
	 * @param authType
	 * @param grantable
	 * @return Retrieves the new SourceGrantConfig has been added.
	 */
	public SourceGrantConfig addExpGrantCfg(String schema, String name, 
			String grantor, String grantee, String object, String objectOwner, 
			String authType, boolean grantable, String targetOwner, String sourceGrantorName) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		SourceGrantConfig sc = getExpGrantCfg(schema, name);
		if (sc == null) {
			sc = new SourceGrantConfig();
			expGrants.add(sc);
		}
		sc.setOwner(schema);
		sc.setName(name);
		sc.setGrantorName(grantor);
		sc.setGranteeName(grantee);
		sc.setClassName(object);
		sc.setClassOwner(objectOwner);
		sc.setAuthType(authType);
		sc.setGrantable(grantable);
		sc.setTargetOwner(targetOwner);
		sc.setSourceGrantorName(sourceGrantorName);
		return sc;
	}

	/**
	 * Add export SQL table configuration.
	 * 
	 * @param stc SourceSQLTableConfig
	 */
	public void addExpSQLTableCfg(SourceSQLTableConfig stc) {
		expSQLTables.add(stc);
	}

	/**
	 * Add export SQL table configuration.
	 * 
	 * @param sstc SourceSQLTableConfig
	 */
	public void addExpSQLTableCfgWithST(SourceSQLTableConfig sstc) {
		//Build source schema
		final Table sqlSchema = getSourceDBType().getMetaDataBuilder().buildSQLTableSchema(
				sourceConParams, sstc.getSql());
		sqlSchema.setName(sstc.getName());
		for (Column col : sqlSchema.getColumns()) {
			sstc.addColumnConfig(col.getName(), StringUtils.lowerCase(col.getName()), true);
		}
		//Build target schema
		Table tt = getDBTransformHelper().createCUBRIDTable(sstc, sqlSchema, this);
		srcSQLSchemas.add(sqlSchema);
		targetTables.add(tt);
		expSQLTables.add(sstc);
	}

	/**
	 * Add export SQL table schema
	 * 
	 * @param sourceTable SourceTable
	 */
	public void addExpSQLTableSchema(Table sourceTable) {
		srcSQLSchemas.add(sourceTable);
	}

	/**
	 * Add an export trigger.
	 * 
	 * @param name trigger name
	 */
	public void addExpTriggerCfg(String name) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		if (expTriggers.indexOf(name) < 0) {
			expTriggers.add(name);
		}
	}

	/**
	 * Add export view
	 * 
	 * @param schema of the view.
	 * @param viewName String
	 * @param target String
	 */
	public void addExpViewCfg(String schema, String viewName, String target) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		SourceViewConfig sc = getExpViewCfg(schema, viewName);
		if (sc == null) {
			sc = new SourceViewConfig();
			sc.setName(viewName);
			sc.setOwner(schema);
			expViews.add(sc);
		}
		sc.setTarget(StringUtils.lowerCase(target));
	}

	/**
	 * Add SQL file to importing list
	 * 
	 * @param value full name of the file
	 */
	public void addSQLFile(String value) {
		if (StringUtils.isBlank(value)) {
			return;
		}
		sqlFiles.add(value);
	}
	
	/**
	 * Add add target synonym
	 * 
	 * @param syn Sequence
	 */
	public void addTargetSynonymSchema(Synonym syn) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		targetSynonyms.add(syn);
	}
	
	/**
	 * Add target grant
	 * 
	 * @param grn Grant
	 */
	public void addTargetGrantSchema(Grant grn) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		targetGrants.add(grn);
	}

	/**
	 * Add add target sequence
	 * 
	 * @param se Sequence
	 */
	public void addTargetSerialSchema(Sequence se) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		targetSequences.add(se);
	}

	/**
	 * Add target table
	 * 
	 * @param tt TargetTable
	 */
	public void addTargetTableSchema(Table tt) {
		if (srcCatalog != null) {
			throw new RuntimeException("Schema was specified.");
		}
		targetTables.add(tt);
	}

	/**
	 * Add target view
	 * 
	 * @param view View
	 */
	public void addTargetViewSchema(View view) {
		if (srcCatalog != null) {
			throw new RuntimeException("Source database was specified.");
		}
		View vw = getTargetViewSchema(view.getOwner(), view.getName());
		if (vw == null) {
			targetViews.add(view);
		} else {
			targetViews.remove(vw);
			targetViews.add(view);
		}
	}

	/**
	 * Clean up the settings, remove the configurations which are not in source
	 * schema.
	 * 
	 * @param isReset true if reset configuration
	 */
	public void buildConfigAndTargetSchema(boolean isReset) {
		//Reset schema information for building configuration.
		resetSchemaInfo();
		buildSchemaCfg(isReset);
		buildTableCfg(isReset);
		buildViewCfg(isReset);
		buildSerialCfg(isReset);
		buildSynonymCfg(isReset);
		buildGrantCfg(isReset);
		List<Schema> schemas = srcCatalog.getSchemas();
		for (Schema sourceDBSchema : schemas) {
			String prefix = "";
			if (StringUtils.isNotBlank(sourceDBSchema.getName())) {
				prefix = sourceDBSchema.getName() + ".";
			}
			for (Function fun : sourceDBSchema.getFunctions()) {
				if (expFunctions.indexOf(fun.getName()) < 0) {
					expFunctions.add(prefix + fun.getName());
				}
			}
			for (Procedure pro : sourceDBSchema.getProcedures()) {
				if (expProcedures.indexOf(pro.getName()) < 0) {
					expProcedures.add(prefix + pro.getName());
				}
			}
			for (Trigger tri : sourceDBSchema.getTriggers()) {
				if (expTriggers.indexOf(tri.getName()) < 0) {
					expTriggers.add(prefix + tri.getName());
				}
			}
		}
		//Reset again after building finished.
		resetSchemaInfo();
		clearInvalidSchema();
	}

	/**
	 * Build source database's schema for migration. Only the referenced schemas
	 * in the source can be build.
	 * 
	 * @return Schema, maybe NULL
	 */
	public Catalog buildRequiredSourceSchema() {
		IDBSource ds = getDBSource();
		if (ds == null) {
			return null;
		}
		IDBSchemaInfoFetcher bcf = DBSchemaInfoFetcherFactory.createFetcher(ds);
		Catalog cl = bcf.fetchSchema(ds, new IBuildSchemaFilter() {

			public boolean filter(String schema, String objName) {
				//If database don't support multi schema, the schema name will be pass as null.
				String tmpSchema = null;
				if (sourceIsOnline() && getSourceDBType().isSupportMultiSchema()) {
					tmpSchema = schema;
				}
				if (getExpEntryTableCfg(tmpSchema, objName) != null
						|| getExpViewCfg(tmpSchema, objName) != null
						|| getExpSerialCfg(tmpSchema, objName) != null
						|| getExpSynonymCfg(tmpSchema, objName) != null) {
					return false;
				}
				return true;
			}
		});
		if (cl == null || cl.getSchemas().isEmpty()) {
			return null;
		}
		return cl;
	}
	
	/**
	 * Build schema to migrate 
	 * 
	 * @param isReset
	 */
	private void buildSchemaCfg(boolean isReset) {
		for (String schemaName : newTargetSchema) {
			Schema dummySchema = new Schema();
			dummySchema.setName(schemaName);
			dummySchema.setNewTargetSchema(true);
			
			targetSchemaList.add(dummySchema);
		}
	}

	/**
	 * copy All Sequences
	 * 
	 * @param isReset boolean
	 * 
	 */
	private void buildSerialCfg(boolean isReset) {
		List<SourceSequenceConfig> tempList = new ArrayList<SourceSequenceConfig>();
		List<Sequence> tempSerials = new ArrayList<Sequence>();
		final CUBRIDSQLHelper cubridddlUtil = CUBRIDSQLHelper.getInstance(null);
		List<Schema> schemas = srcCatalog.getSchemas();
		Map<String, Integer> allSequencesCountMap = srcCatalog.getAllSequencesCountMap();
		for (Schema sourceDBSchema : schemas) {
			for (Sequence seq : sourceDBSchema.getSequenceList()) {
				SourceSequenceConfig sc = getExpSerialCfg(seq.getOwner(), seq.getName());
				if (sc == null) {
					sc = new SourceSequenceConfig();
					sc.setOwner(sourceDBSchema.getName());
					sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					sc.setName(seq.getName());
					sc.setTarget(getTargetName(allSequencesCountMap, seq.getOwner(), seq.getName()));
					sc.setCreate(false);
					sc.setReplace(false);
					sc.setComment(seq.getComment());
				} else if (sourceDBSchema.getTargetSchemaName() != null) {
					if (!sourceDBSchema.getTargetSchemaName().equals(sc.getTargetOwner())) {
						sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					}
				}
				tempList.add(sc);
				Sequence tseq = null;
				if (sc.getOwner() == null) {
					tseq = getTargetSerialSchema(sc.getTarget());
				} else {
					tseq = getTargetSerialSchema(sc.getTargetOwner(), sc.getTarget());
				}
				if (tseq == null) {
					tseq = (Sequence) seq.clone();
					tseq.setName(sc.getTarget());
					tseq.setOwner(sc.getTargetOwner());
					tseq.setDDL(cubridddlUtil.getSequenceDDL(tseq, this.addUserSchema));
					tseq.setComment(seq.getComment());
				}
				tempSerials.add(tseq);
			}
		}
		expSerials.clear();
		expSerials.addAll(tempList);
		targetSequences.clear();
		targetSequences.addAll(tempSerials);
	}
	
	/**
	 * copy All Grant
	 * 
	 * @param isReset boolean
	 */
	private void buildGrantCfg(boolean isReset) {
		List<SourceGrantConfig> tempList = new ArrayList<SourceGrantConfig>();
		List<Grant> tempGrants = new ArrayList<Grant>();
		final CUBRIDSQLHelper cubridddlUtil = CUBRIDSQLHelper.getInstance(null);
		List<Schema> schemas = srcCatalog.getSchemas();
		for (Schema sourceDBSchema : schemas) {
			for (Grant grant : sourceDBSchema.getGrantList()) {
				SourceGrantConfig sc = getExpGrantCfg(grant.getOwner(), grant.getName());
				if (sc == null) {
					sc = new SourceGrantConfig();
					sc.setName(grant.getName());
					sc.setOwner(grant.getOwner());
					sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					sc.setSourceGrantorName(grant.getGrantorName());
					sc.setGrantorName(getTargetOwner(schemas, grant.getGrantorName()));
					sc.setGranteeName(getTargetOwner(schemas, grant.getGranteeName()));
					sc.setAuthType(grant.getAuthType());
					sc.setClassName(grant.getClassName());
					sc.setClassOwner(getTargetOwner(schemas, grant.getClassOwner()));
					sc.setGrantable(grant.isGrantable());
					sc.setCreate(false);
				} else if(sourceDBSchema.getTargetSchemaName() != null) {
					if (!sourceDBSchema.getTargetSchemaName().equals(sc.getTargetOwner())) {
						sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					}
				}
				tempList.add(sc);
				
				Grant tgrant = getTargetGrantSchema(sc.getName());
				if (tgrant == null) {
					tgrant = (Grant) grant.clone();
					tgrant.setOwner(sc.getTargetOwner());
					tgrant.setGrantorName(sc.getGrantorName());
					tgrant.setGranteeName(sc.getGranteeName());
					tgrant.setAuthType(sc.getAuthType());
					tgrant.setClassName(sc.getClassName());
					tgrant.setClassOwner(sc.getClassOwner());
					tgrant.setGrantable(sc.isGrantable());
					tgrant.setName(tgrant.getName());
					sc.setTarget(tgrant.getName());
					tgrant.setDDL(cubridddlUtil.getGrantDDL(tgrant, this.addUserSchema));
				}
				tempGrants.add(tgrant);
			}
		}
		expGrants.clear();
		expGrants.addAll(tempList);
		targetGrants.clear();
		targetGrants.addAll(tempGrants);
	}
	
	/**
	 * copy All Synonym
	 * 
	 * @param isReset boolean
	 * 
	 */
	private void buildSynonymCfg(boolean isReset) {
		List<SourceSynonymConfig> tempList = new ArrayList<SourceSynonymConfig>();
		List<Synonym> tempSynonyms = new ArrayList<Synonym>();
		final CUBRIDSQLHelper cubridddlUtil = CUBRIDSQLHelper.getInstance(null);
		List<Schema> schemas = srcCatalog.getSchemas();
		Map<String, Integer> allSynonymsCountMap = srcCatalog.getAllSequencesCountMap();
		for (Schema sourceDBSchema : schemas) {
			for (Synonym synonym : sourceDBSchema.getSynonymList()) {
				SourceSynonymConfig sc = getExpSynonymCfg(synonym.getOwner(), synonym.getName());
				if (sc == null) {
					sc = new SourceSynonymConfig();
					sc.setName(synonym.getName());
					sc.setOwner(synonym.getOwner());
					sc.setObjectName(synonym.getObjectName());
					sc.setObjectOwner(synonym.getObjectOwner());
					sc.setTarget(getTargetName(allSynonymsCountMap, synonym.getOwner(), synonym.getName()));
					sc.setTargetOwner(getTargetOwner(schemas, synonym.getOwner()));
					sc.setObjectTargetName(synonym.getObjectName());
					sc.setObjectTargetOwner(synonym.getObjectOwner());
					sc.setCreate(false);
					sc.setReplace(false);
					sc.setComment(synonym.getComment());
				} else if (sourceDBSchema.getTargetSchemaName() != null) {
					if (!sourceDBSchema.getTargetSchemaName().equals(sc.getTargetOwner())) {
						sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					}
				}
				tempList.add(sc);
				Synonym tsynonym = null;
				if (sc.getOwner() == null) {
					tsynonym = getTargetSynonymSchema(sc.getTarget());
				} else {
					tsynonym = getTargetSynonymSchema(sc.getTargetOwner(), sc.getTarget());
				}
				if (tsynonym == null) {
					tsynonym = (Synonym) synonym.clone();
					tsynonym.setOwner(sourceDBSchema.getTargetSchemaName());
					tsynonym.setObjectOwner(getTargetOwner(schemas, synonym.getObjectOwner()));
					tsynonym.setDDL(cubridddlUtil.getSynonymDDL(tsynonym, this.addUserSchema));
				}
				tempSynonyms.add(tsynonym);
			}
		}
		expSynonyms.clear();
		expSynonyms.addAll(tempList);
		targetSynonyms.clear();
		targetSynonyms.addAll(tempSynonyms);
	}
	
	public void createDumpfile(boolean isSplit) {
		Iterator<String> keys = scriptSchemaMapping.keySet().iterator();
		while (keys.hasNext()) {
			Schema schema = scriptSchemaMapping.get(keys.next());
			String schemaName = schema.getTargetSchemaName();
			if (isSplit) {
				this.addTargetTableFileName(schemaName, getTableFullName(schemaName));
				this.addTargetViewFileName(schemaName, getViewFullName(schemaName));
				this.addTargetViewQuerySpecFileName(schemaName, getViewQuerySpecFullName(schemaName));
				this.addTargetPkFileName(schemaName, getPkFullName(schemaName));
				this.addTargetFkFileName(schemaName, getFkFullName(schemaName));
				this.addTargetSerialFileName(schemaName, getSequenceFullName(schemaName));
				this.addTargetSynonymFileName(schemaName, getSynonymFullName(schemaName));
				this.addTargetGrantFileName(schemaName, getGrantFullName(schemaName));
				this.addTargetSchemaFileListName(schemaName, getSchemaFileListFullName(schemaName));
			} else {
				this.addTargetSchemaFileName(schemaName, getSchemaFullName(schemaName));
			}
			this.addTargetDataFileName(schemaName, getDataFullName(schemaName));
			this.addTargetIndexFileName(schemaName, getIndexFullName(schemaName));
			this.addTargetUpdateStatisticFileName(schemaName, getUpdateStatisticFullName(schemaName));
		}
	}
	
	private String getTargetOwner(List<Schema> schemas, String owner) {
		for (Schema schema : schemas) {
			if (schema.getName().equalsIgnoreCase(owner)) {
				return schema.getTargetSchemaName();
			}
		}
		
		return owner;
	}
	
	private boolean isDuplicatedObject(Map<String, Integer> allObjectsMap, String objectName) {
		if (allObjectsMap == null) {
			return false;
		}
		if (allObjectsMap.get(objectName) == null) {
			return false;
		}
		return allObjectsMap.get(objectName) > 1;
	}

	/**
	 * Build source database's schema for migration. Only the referenced schemas
	 * in the source can be build.
	 * 
	 * @return Schema, maybe NULL
	 */
	public Catalog buildSourceSchema() {
		IDBSource ds = getDBSource();
		if (ds == null) {
			return null;
		}
		IDBSchemaInfoFetcher bcf = DBSchemaInfoFetcherFactory.createFetcher(ds);
		Catalog cl = bcf.fetchSchema(ds, null);
		if (cl == null || cl.getSchemas().isEmpty()) {
			return null;
		}
		return cl;
	}

	/**
	 * 
	 * Only build the Source Schema's table columns which will be used in the
	 * migration process
	 * 
	 * @return Catalog
	 */
	public Catalog buildSourceSchemaForDataMigration() {
		this.resetSchemaInfo();
		String dbName = sourceConParams.getDbName();
		String catalogName;
		String defSchemaName;
		DatabaseType databaseType = sourceConParams.getDatabaseType();
		if (DatabaseType.ORACLE == databaseType) {
			//If DB name is SID/schemaName pattern
			if (dbName.startsWith("/")) {
				dbName = dbName.substring(1, dbName.length());
			}
			String[] strs = dbName.toUpperCase(Locale.ENGLISH).split("/");
			catalogName = strs[0];
			if (strs.length == 1) {
				defSchemaName = sourceConParams.getConUser().toUpperCase(Locale.ENGLISH);
			} else {
				defSchemaName = strs[1].toUpperCase(Locale.US);
			}
		} else {
			catalogName = dbName;
			defSchemaName = dbName;
		}

		Catalog catalog = new Catalog();
		final DatabaseType srcDBType = getSourceDBType();
		catalog.setDatabaseType(srcDBType);
		catalog.setHost(sourceConParams.getHost());
		catalog.setName(catalogName);
		catalog.setPort(sourceConParams.getPort());
		catalog.setConnectionParameters(sourceConParams.clone());
		catalog.setCharset(getSourceCharset());
		if (targetConParams != null) {
			catalog.setTimezone(targetConParams.getTimeZone());
		} else if (getTargetFileTimeZone() != null) {
			catalog.setTimezone(getTargetFileTimeZone());
		}

		List<SourceEntryTableConfig> tableList = getExpEntryTableCfg();
		for (SourceEntryTableConfig tableCfg : tableList) {
			tableCfg.setCreateNewTable(false);

			String schemaName = tableCfg.getOwner() == null ? defSchemaName : tableCfg.getOwner();
			Schema schema = catalog.getSchemaByName(schemaName);
			if (schema == null) {
				schema = new Schema(catalog);
				schema.setName(schemaName);
				catalog.addSchema(schema);
			}
			tableCfg.setOwner(schemaName);

			Table dstTable = getTargetTableSchema(tableCfg.getTarget());
			if (dstTable == null) {
				LOG.error("Target table is not found : " + tableCfg.getTarget());
				continue;
			}
			String sql = srcDBType.getExportHelper().getSelectSQL(tableCfg);
			try {
				Table table = srcDBType.getMetaDataBuilder().buildSQLTableSchema(sourceConParams,
						sql);
				table.setName(tableCfg.getName());
				schema.addTable(table);
			} catch (Exception ex) {
				LOG.error("Fetching schema error:" + tableCfg.getOwner() + "." + tableCfg.getName());
			}
		}

		List<SourceSQLTableConfig> sqlList = getExpSQLCfg();
		if (sqlList != null) {
			for (SourceSQLTableConfig sqlCfg : sqlList) {
				sqlCfg.setCreateNewTable(false);
			}
		}

		List<SourceViewConfig> viewsList = getExpViewCfg();
		for (SourceConfig vw : viewsList) {
			vw.setCreate(false);
		}

		List<SourceSequenceConfig> seqList = getExpSerialCfg();
		for (SourceConfig seq : seqList) {
			seq.setCreate(false);
		}
		return catalog;
	}

	/**
	 * Build table configuration and target table schema
	 * 
	 * @param isReset boolean
	 * 
	 */
	private void buildTableCfg(boolean isReset) {
		List<SourceEntryTableConfig> tempExpEntryTables = new ArrayList<SourceEntryTableConfig>();
		Map<String, Table> tempTarTables = new TreeMap<String, Table>();
		List<Schema> schemas = srcCatalog.getSchemas();
		Map<String, Integer> allTablesCountMap = srcCatalog.getAllTablesCountMap();
		for (Schema sourceDBSchema : schemas) {
			String prefix = "";
			if (StringUtils.isNotBlank(sourceDBSchema.getName())) {
				prefix = sourceDBSchema.getTargetSchemaName() + ".";
			}
			for (Table srcTable : sourceDBSchema.getTables()) {
				SourceEntryTableConfig setc = getExpEntryTableCfg(sourceDBSchema.getName(),
						srcTable.getName());
				if (setc == null) {
					setc = new SourceEntryTableConfig();
					setc.setOwner(sourceDBSchema.getName());
					setc.setName(srcTable.getName());
					setc.setComment(srcTable.getComment());
					setc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					setc.setTarget(getTargetName(allTablesCountMap, srcTable.getOwner(), srcTable.getName()));
					
					setc.setCreateNewTable(false);
					setc.setCreatePartition(false);
					setc.setCreatePK(false);
					setc.setMigrateData(false);
					setc.setReplace(false);
					setc.setEnableExpOpt(srcTable.getPk() != null);
				} else if (sourceDBSchema.getTargetSchemaName() != null) {
					if (!sourceDBSchema.getTargetSchemaName().equals(setc.getTargetOwner())) {
						setc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					}
				}
						
				tempExpEntryTables.add(setc);

				Table tt = null;
				
				tt = getTargetTableSchema(setc.getTargetOwner(), setc.getTarget());
				
				if (tt == null) {
					//If there is invalid information in source database, the target table will be NULL
					try {
						tt = getDBTransformHelper().createCUBRIDTable(setc, srcTable, this);
					} catch (Exception ex) {
						LOG.error("Building migration configuration error", ex);
						tt = null;
					}
					if (tt == null) {
						continue;
					}
					tt.setName(setc.getTarget());
				}
				tempTarTables.put(prefix + tt.getName(), tt);
				buildTableColumnCfg(setc, srcTable, tt, isReset);
				buildTablePKCfg(setc, srcTable, tt);
				buildTableFKCfg(setc, srcTable, tt, isReset);
				buildTableIndexeCfg(setc, srcTable, tt, isReset);
				buildTablePartitionCfg(setc, srcTable, tt);
			}

			//Only JDBC source migration can support SQL table
			if (sourceIsOnline() && sourceConParams != null) {
				//Refresh srcSQLSchemas, if source can't be connected, use old schema information
				try {
					sourceConParams.createConnection().close();
					srcSQLSchemas.clear();
					for (SourceSQLTableConfig sstc : expSQLTables) {
						try {
							Table st = getSourceDBType().getMetaDataBuilder().buildSQLTableSchema(
									sourceConParams, sstc.getSql());
							st.setName(sstc.getName());
							srcSQLSchemas.add(st);
						} catch (Exception ex) {
							LOG.error("", ex);
						}
					}
				} catch (Exception ex) {
					LOG.info("JDBC can't be connected. Old schema will be used.");
				}
				//Refresh target sql tables
				for (SourceSQLTableConfig sstc : expSQLTables) {
					try {
						Table st = getSrcSQLSchema(sstc.getName());
						if (st == null) {
							continue;
						}
						Table tt = getTargetTableSchema(sstc.getTarget());
						if (tt == null) {
							tt = getDBTransformHelper().createCUBRIDTable(sstc, st, this);
							tt.setName(sstc.getTarget());
						}
						tempTarTables.put(prefix + tt.getName(), tt);

						buildTableColumnCfg(sstc, st, tt, isReset);
					} catch (Exception ex) {
						LOG.error("", ex);
					}
				}
			}
		}
		expTables.clear();
		expTables.addAll(tempExpEntryTables);
		targetTables.clear();
		targetTables.addAll(tempTarTables.values());

		if (isTarSchemaDuplicate) {
			repareN21MigrationSetting();			
		}
	}

	/**
	 * The column will be clear and rebuilt
	 * 
	 * @param setc boolean
	 * @param srcTable Table
	 * @param tarTable Table
	 * @param isReset boolean
	 */
	private void buildTableColumnCfg(SourceTableConfig setc, Table srcTable, Table tarTable,
			boolean isReset) {
		if (setc == null || srcTable == null || tarTable == null) {
			throw new IllegalArgumentException("Parameter can't be null.");
		}
		List<SourceColumnConfig> sccs = new ArrayList<SourceColumnConfig>();
		List<Column> tcols = new ArrayList<Column>();
		List<String> targetNames = new ArrayList<String>();
		for (Column scol : srcTable.getColumns()) {
			SourceColumnConfig scc = setc.getColumnConfig(scol.getName());
			if (scc == null) {
				scc = new SourceColumnConfig();
				scc.setName(scol.getName());
				scc.setComment(scol.getComment());
				//If target is duplicated, auto append a number after the target.
				String newTar = StringUtils.lowerCase(scol.getName());
				String newTar2 = newTar;
				int i = 1;
				while (targetNames.contains(newTar2)) {
					newTar2 = newTar + "_" + i;
					i++;
				}
				scc.setTarget(newTar2);
				scc.setCreate(isReset);
				scc.setReplace(isReset);
				scc.setParent(setc);
			}
			sccs.add(scc);
			targetNames.add(scc.getTarget());

			Column tcol = tarTable.getColumnByName(scc.getParent().getTargetOwner(), scc.getParent().getName(), scc.getTarget());
			if (tcol == null) {
				tcol = getDBTransformHelper().getCUBRIDColumn(scol, this);
				tcol.setName(scc.getTarget());
			}
			setNullableForLobDataType(tcol);
			tcols.add(tcol);
		}
		setc.clearColumnList();
		setc.addAllColumnList(sccs);
		tarTable.setColumns(tcols);
	}

	/**
	 * setNullableForLobDataType
	 * @param column
	 */
	private void setNullableForLobDataType(Column column) {
		String shownDataType = column.getShownDataType();
		if ("CLOB".equalsIgnoreCase(shownDataType) || "BLOB".equalsIgnoreCase(shownDataType)) {
			if (!column.isNullable()) {
				column.setNullable(true);
			}
		}
	}

	/**
	 * 
	 * 
	 * @param setc SourceEntryTableConfig
	 * @param srcTable Table
	 * @param tarTable Table
	 * @param isReset boolean
	 */
	private void buildTableFKCfg(SourceEntryTableConfig setc, Table srcTable, Table tarTable,
			boolean isReset) {
		List<SourceFKConfig> sFKCfgs = new ArrayList<SourceFKConfig>();
		List<FK> tfks = new ArrayList<FK>();

		for (FK fk : srcTable.getFks()) {
			SourceFKConfig sfc = setc.getFKConfig(fk.getName());
			if (sfc == null) {
				sfc = new SourceFKConfig();
				sfc.setName(fk.getName());
				sfc.setCreate(isReset);
				sfc.setReplace(isReset);
				sfc.setParent(setc);
				sfc.setTarget(StringUtils.lowerCase(fk.getName()));
			}
			sFKCfgs.add(sfc);

			FK tfk = tarTable.getFKByName(sfc.getTarget());
				
			if (tfk == null) {
				tfk = new FK(tarTable);
				tfk.setName(sfc.getTarget());
				
				String referencedTableName = fk.getReferencedTableName();
				Map<String, Integer> allTablesCountMap = srcCatalog.getAllTablesCountMap();
				Integer integer = allTablesCountMap.get(referencedTableName);

				if (integer != null && integer > 1 && !addUserSchema) {
					String owner = fk.getTable().getOwner();
					tfk.setReferencedTableName(owner + "_" + referencedTableName);
				} else {
					tfk.setReferencedTableName(referencedTableName);
				}

				Map<String, String> fkcolumns = fk.getColumns();
				for (Map.Entry<String, String> entry : fkcolumns.entrySet()) {
					tfk.addRefColumnName(StringUtils.lowerCase(entry.getKey()),
							StringUtils.lowerCase(entry.getValue()));
				}

				//tfk.setDeferability(fk.getDeferability());
				tfk.setDeleteRule(fk.getDeleteRule());
				//tfk.setOnCacheObject(fk.getOnCacheObject());
				tfk.setUpdateRule(fk.getUpdateRule());
			}
			tfks.add(tfk);
		}
		setc.setFKs(sFKCfgs);
		tarTable.setFKs(tfks);
	}

	/**
	 * 
	 * 
	 * @param setc SourceEntryTableConfig
	 * @param srcTable Table
	 * @param tarTable Table
	 * @param isReset boolean
	 */
	private void buildTableIndexeCfg(SourceEntryTableConfig setc, Table srcTable, Table tarTable,
			boolean isReset) {
		List<SourceIndexConfig> sics = new ArrayList<SourceIndexConfig>();
		List<Index> tidxs = new ArrayList<Index>();

		for (Index idx : srcTable.getIndexes()) {
			SourceIndexConfig sic = setc.getIndexConfig(idx.getName());
			if (sic == null) {
				sic = new SourceIndexConfig();
				sic.setName(idx.getName());
				sic.setCreate(isReset);
				sic.setReplace(isReset);
				sic.setParent(setc);
				sic.setTarget(StringUtils.lowerCase(idx.getName()));
				sic.setComment(idx.getComment());
			}
			sics.add(sic);

			Index tidx = tarTable.getIndexByName(sic.getTarget());
			if (tidx == null) {
				tidx = new Index(tarTable);
				tidx.setName(sic.getTarget());
				Map<String, Boolean> indexColumns = idx.getIndexColumns();
				for (Map.Entry<String, Boolean> entry : indexColumns.entrySet()) {
					String key = DBTransformHelper.replaceStrValue(entry.getKey());
					tidx.addColumn(key, entry.getValue());
				}
				tidx.setReverse(idx.isReverse());
				tidx.setUnique(idx.isUnique());
				tidx.setIndexType(idx.getIndexType());
			}
			
			if (idx.getComment() != null) {
				tidx.setComment(idx.getComment());
			}
			tidxs.add(tidx);
		}
		setc.setIndexes(sics);
		tarTable.setIndexes(tidxs);
	}

	/**
	 * Build table partition configuration
	 * 
	 * @param setc SourceEntryTableConfig
	 * @param srcTable Table of source
	 * @param tt Table of target
	 */
	private void buildTablePartitionCfg(SourceEntryTableConfig setc, Table srcTable, Table tt) {
		if (srcTable.getPartitionInfo() == null) {
			setc.setCreatePartition(false);
			return;
		}
		if (tt.getPartitionInfo() == null) {
			PartitionInfo pi = new PartitionInfo();
			DBTransformHelper tranformHelper = getDBTransformHelper();
			pi.setDDL(tranformHelper.getToCUBRIDPartitionDDL(srcTable));
			tt.setPartitionInfo(pi);
		}
	}

	/**
	 * Build table primary key configuration
	 * 
	 * @param setc SourceEntryTableConfig
	 * @param srcTable Table
	 * @param tt Table
	 */
	private void buildTablePKCfg(SourceEntryTableConfig setc, Table srcTable, Table tt) {
		if (srcTable.getPk() == null) {
			setc.setCreatePK(false);
			return;
		}
		if (tt.getPk() == null) {
			PK pk = new PK(tt);
			pk.setName(StringUtils.lowerCase(srcTable.getPk().getName()));
			for (String scol : srcTable.getPk().getPkColumns()) {
				SourceColumnConfig scc = setc.getColumnConfig(scol);
				if (scc == null) {
					continue;
				}
				pk.addColumn(scc.getTarget());
			}
			tt.setPk(pk);
		} else {
			//Remove invalidate PK column configurations
			for (String col : tt.getPk().getPkColumns()) {
				if (tt.getColumnByName(col) == null) {
					tt.getPk().removeColumn(col);
				}
			}
		}
	}

	/**
	 * @param isReset boolean
	 */
	private void buildViewCfg(boolean isReset) {
		List<SourceViewConfig> tempSCList = new ArrayList<SourceViewConfig>();
		List<View> tempTarList = new ArrayList<View>();
		List<Schema> schemas = srcCatalog.getSchemas();
		Map<String, Integer> allViewsCountMap = srcCatalog.getAllViewsCountMap();
		for (Schema sourceDBSchema : schemas) {
			for (View vw : sourceDBSchema.getViews()) {
				SourceViewConfig sc = getExpViewCfg(sourceDBSchema.getName(), vw.getName());
				List<String> referenceTableNameList = new ArrayList<String>();
				if (sc == null) {
					for (Table table : sourceDBSchema.getTables()) {
							referenceTableNameList.add(table.getName());
					}
					sc = new SourceViewConfig();
					sc.setReferenceTableNames(referenceTableNameList);
					sc.setName(vw.getName());
					sc.setOwner(vw.getOwner());
					sc.setTarget(getTargetName(allViewsCountMap, vw.getOwner(), vw.getName()));
					sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					sc.setCreate(false);
					sc.setReplace(false);
					sc.setComment(vw.getComment());
				} else if (sourceDBSchema.getTargetSchemaName() != null) {
					if (!sourceDBSchema.getTargetSchemaName().equals(sc.getTargetOwner())) {
						sc.setTargetOwner(sourceDBSchema.getTargetSchemaName());
					}
				}
				tempSCList.add(sc);
				View tVw = getTargetViewSchema(sc.getTargetOwner(), sc.getTarget());
				if (tVw == null) {
					tVw = getDBTransformHelper().getCloneView(vw, this);
					tVw.setReferenceTableNames(referenceTableNameList);
					tVw.setName(sc.getTarget());
					tVw.setOwner(sc.getTargetOwner());
					tVw.setComment(vw.getComment());
				}
				tempTarList.add(tVw);
			}
		}
		expViews.clear();
		expViews.addAll(tempSCList);
		targetViews.clear();
		targetViews.addAll(tempTarList);
	}

	private String getTargetName(Map<String, Integer> map, String owner, String name) {
		if (isTarSchemaDuplicate || (!addUserSchema)) {
			if (isDuplicatedObject(map, name)) {
				return owner + "_" + StringUtils.lowerCase(name);
			}
		}
		
		return StringUtils.lowerCase(name);
	}

	/**
	 * Change column's target
	 * 
	 * @param sccc SourceCSVColumnConfig
	 * @param targetName to be changed
	 * @param tcol tcol
	 */
	public void changeCSVTarget(SourceCSVColumnConfig sccc, String targetName, Column tcol) {
		int count = 0;
		for (SourceCSVConfig scc : csvFiles) {
			if (!scc.getTarget().equals(tcol.getTableOrView().getName())) {
				continue;
			}
			for (SourceCSVColumnConfig colc : scc.getColumnConfigs()) {
				if (colc.getTarget().equals(tcol.getName())) {
					count++;
				}
			}
		}
		if (count == 1) {
			sccc.setTarget(targetName);
			tcol.setName(targetName);
			return;
		} else if (count > 1) {
			Column newCol = tcol.cloneCol();
			newCol.setName(targetName);
			sccc.setTarget(targetName);
			tcol.getTableOrView().addColumn(newCol);
		}
	}

	/**
	 * Change CSV's target table name.
	 * 
	 * @param scc SourceCSVConfig
	 * @param targetName SourceCSVConfig
	 * @param tschema Schema of target DB
	 * @param remapCols re-map the columns
	 */
	public void changeCSVTarget(SourceCSVConfig scc, String targetName, Schema tschema,
			boolean remapCols) {
		if (StringUtils.equalsIgnoreCase(targetName, scc.getTarget())) {
			return;
		}
		int oldCnt = 0, newCnt = 0;
		for (SourceCSVConfig scfg : csvFiles) {
			//Count of old name referenced
			if (scfg.getTarget().equalsIgnoreCase(scc.getTarget())) {
				oldCnt++;
			}
			//Count of new name referenced.
			if (scfg.getTarget().equalsIgnoreCase(targetName)) {
				newCnt++;
			}
		}
		// if old target is not referenced by others and new one is not referenced.
		if (oldCnt == 1 && newCnt == 0) {
			if (remapCols) {
				Table tblInTar = tschema.getTableByName(targetName);
				scc.changeTarget(tblInTar);
				createTargetCSVTable(scc, tblInTar);
			} else {
				Table tt = getTargetTableSchema(scc.getTarget());
				scc.setTarget(targetName);
				tt.setName(targetName);
			}
		} else if (oldCnt > 1 && newCnt == 0) {
			Table tblInTar = tschema.getTableByName(targetName);
			if (tblInTar == null) {
				scc.setTarget(targetName);
			} else {
				scc.changeTarget(tblInTar);
			}
			createTargetCSVTable(scc, tblInTar);
		} else if (oldCnt == 1 && newCnt > 0) {
			Table tt = getTargetTableSchema(targetName);
			scc.changeTarget(tt);
		} else if (oldCnt > 1 && newCnt > 0) {
			Table tt = getTargetTableSchema(targetName);
			scc.changeTarget(tt);
		}
	}

	/**
	 * Change column configuration's target
	 * 
	 * @param scc SourceColumnConfig
	 * @param newTarget String
	 */
	public void changeTarget(SourceColumnConfig scc, String newTarget) {
		if (scc.getTarget().equalsIgnoreCase(newTarget)) {
			return;
		}
		SourceTableConfig setc = scc.getParent();
		Table tt = getTargetTableSchema(setc.getTarget());
		if (tt == null) {
			throw new IllegalArgumentException("Cant't find target table:" + setc.getTarget());
		}
		Column col = tt.getColumnByName(scc.getTarget());
		Column col2 = tt.getColumnByName(newTarget);
		//If target column is not exists, create a new target column
		if (col != null) {
			scc.setTarget(newTarget);
			col.setName(newTarget);
			return;
		}
		if (col2 == null) {
			Table st = getSrcTableSchema(setc.getOwner(), setc.getName());
			if (st == null) {
				throw new IllegalArgumentException("Cant't find source table:" + setc.getName());
			}
			Column scol = st.getColumnByName(scc.getName());
			if (scol == null) {
				throw new IllegalArgumentException("Cant't find source table column:"
						+ scc.getName());
			}
			col = getDBTransformHelper().getCUBRIDColumn(scol, this);
			tt.addColumn(col);
			scc.setTarget(newTarget);
			col.setName(newTarget);
		} else {
			scc.setTarget(newTarget);
		}
	}

	/**
	 * Change table configuration's target
	 * 
	 * @param stc SourceTableConfig
	 * @param newTarget String
	 */
	public void changeTarget(SourceTableConfig stc, String newTarget) {
		if (stc.getTarget().equalsIgnoreCase(newTarget)) {
			return;
		}
		Table st = getSrcTableSchema(stc.getOwner(), stc.getName());
		Table tt = getTargetTableSchema(stc.getTarget());
		if (tt == null || st == null) {
			stc.setTarget(newTarget);
			return;
		}
		final int oldRef = getTargetRefedCount(tt.getName());
		final int newRef = getTargetRefedCount(newTarget);
		if (oldRef == 1 && newRef == 0) {
			tt.setName(newTarget);
		} else if (oldRef > 1 && newRef == 0) {
			Table tt2 = getDBTransformHelper().createCUBRIDTable(stc, st, this);
			tt2.setName(newTarget);
			targetTables.add(tt2);
		} else if (newRef > 0) {
			//Auto add columns which is not in the target table
			Table newtt = getTargetTableSchema(newTarget);
			for (SourceColumnConfig scc : stc.getColumnConfigList()) {
				Column tcol = newtt.getColumnByName(scc.getTarget());
				Column scol = st.getColumnByName(scc.getName());
				if (tcol == null) {
					tcol = getDBTransformHelper().getCUBRIDColumn(scol, this);
					newtt.addColumn(tcol);
				}
			}
		}
		stc.setTarget(newTarget);
	}

	/**
	 * Change the root path of the target files
	 * 
	 * @param path String
	 */
	public void changeTargetFilePath(String path) {
		String tempPath = getFileRepositroyPath();
		String path2 = path;
		if (!path.endsWith(File.separator)) {
			path2 = path + File.separator;
		}
		
		for (Schema schema : srcCatalog.getSchemas()) {
			addTargetSchemaFileName(schema.getName(), path2 + targetSchemaFileName.get(schema.getName().substring(tempPath.length())));
			addTargetTableFileName(schema.getName(), path2 + targetTableFileName.get(schema.getName().substring(tempPath.length())));
			addTargetViewFileName(schema.getName(), path2 + targetViewFileName.get(schema.getName().substring(tempPath.length())));
			addTargetViewQuerySpecFileName(schema.getName(), path2 + targetViewQuerySpecFileName.get(schema.getName().substring(tempPath.length())));
			addTargetPkFileName(schema.getName(), path2 + targetPkFileName.get(schema.getName().substring(tempPath.length())));
			addTargetFkFileName(schema.getName(), path2 + targetFkFileName.get(schema.getName().substring(tempPath.length())));
			addTargetDataFileName(schema.getName(), path2 + targetDataFileName.get(schema.getName().substring(tempPath.length())));
			addTargetIndexFileName(schema.getName(), path2 + targetIndexFileName.get(schema.getName().substring(tempPath.length())));
			addTargetSerialFileName(schema.getName(), path2 + targetSerialFileName.get(schema.getName().substring(tempPath.length())));
			addTargetUpdateStatisticFileName(schema.getName(), path2 + targetUpdateStatisticFileName.get(schema.getName().substring(tempPath.length())));
			addTargetSchemaFileListName(schema.getName(), path2 + targetSchemaFileListName.get(schema.getName().substring(tempPath.length())));
			addTargetSynonymFileName(schema.getName(), path2 + targetSynonymFileName.get(schema.getName().substring(tempPath.length())));
			addTargetGrantFileName(schema.getName(), path2 + targetGrantFileName.get(schema.getName().substring(tempPath.length())));
		}
	}

	/**
	 * Check the OOM risk of migration settings
	 * 
	 * @return true if there is an OOM risk.
	 */
	public boolean checkOOMRisk() {
		long maxSize = 0;
		CUBRIDDataTypeHelper dthelper = CUBRIDDataTypeHelper.getInstance(null);
		for (Table tt : targetTables) {
			long tmpSize = 0;
			final List<Column> columns = tt.getColumns();
			for (Column col : columns) {
				tmpSize = tmpSize + dthelper.getDataTypeByteSize(col);
			}
			if (tmpSize > maxSize) {
				maxSize = tmpSize;
			}
		}
		if (maxSize * getCommitCount() * getExportThreadCount() > Runtime.getRuntime().maxMemory() / 5) {
			return true;
		}
		return false;
	}

	/**
	 * Clean useless objects and build N:1 configurations
	 * 
	 */
	private void cleanN21Tables() {
		final Iterator<Table> ttIt = targetTables.iterator();
		List<SourceTableConfig> stcs = new ArrayList<SourceTableConfig>();
		stcs.addAll(expTables);
		stcs.addAll(expSQLTables);
		while (ttIt.hasNext()) {
			Table tt = ttIt.next();
			if (getTargetRefedCount(tt.getName()) == 0) {
				ttIt.remove();
				continue;
			}
			//Remove no used columns
			for (Column col : tt.getColumns()) {
				boolean create = false;
				for (SourceTableConfig stc : stcs) {
					if (!stc.getTarget().equalsIgnoreCase(tt.getName())) {
						continue;
					}
					for (SourceColumnConfig scc : stc.getColumnConfigList()) {
						if (!scc.getTarget().equalsIgnoreCase(col.getName())) {
							continue;
						}
						create = true;
						break;
					}
					if (create) {
						break;
					}
				}
				if (!create) {
					tt.removeColumn(col);
				}
			}
			boolean createPk = false;
			for (SourceEntryTableConfig setc : expTables) {
				if (!setc.getTarget().equalsIgnoreCase(tt.getName())) {
					continue;
				}
				if (setc.isCreatePK()) {
					createPk = true;
					break;
				}
			}
			if (!createPk) {
				tt.setPk(null);
			}

			for (FK fk : tt.getFks()) {
				boolean create = false;
				for (SourceEntryTableConfig setc : expTables) {
					if (!setc.getTarget().equalsIgnoreCase(tt.getName())) {
						continue;
					}
					for (SourceFKConfig sc : setc.getFKConfigList()) {
						if (!sc.getTarget().equalsIgnoreCase(fk.getName())) {
							continue;
						}
						create = true;
						break;
					}
				}
				if (!create) {
					tt.removeFK(fk.getName());
				}
			}

			for (Index idx : tt.getIndexes()) {
				boolean create = false;
				for (SourceEntryTableConfig setc : expTables) {
					if (!setc.getTarget().equalsIgnoreCase(tt.getName())) {
						continue;
					}
					for (SourceIndexConfig sc : setc.getIndexConfigList()) {
						if (!sc.getTarget().equalsIgnoreCase(idx.getName())) {
							continue;
						}
						create = true;
						break;
					}
				}
				if (!create) {
					tt.removeIndex(idx.getName());
				}
			}
		}
	}

	/**
	 * 
	 * Clean all which were not set to migration. Must call this method before
	 * start migration process.
	 * 
	 */
	public void cleanNoUsedConfigForStart() {
		if (sourceIsOnline() || sourceIsXMLDump()) {
			final Iterator<SourceEntryTableConfig> it = expTables.iterator();
			while (it.hasNext()) {
				SourceEntryTableConfig setc = it.next();

				if (!setc.isCreateNewTable() && !setc.isMigrateData()) {
					it.remove();
					continue;
				}
				for (SourceColumnConfig scc : setc.getColumnConfigList()) {
					if (!scc.isCreate()) {
						setc.removeColumnConfig(scc.getName());
					}
				}
				for (SourceFKConfig sfkc : setc.getFKConfigList()) {
					if (!sfkc.isCreate()) {
						setc.removeFKConfig(sfkc.getName());
					}
				}
				for (SourceIndexConfig sic : setc.getIndexConfigList()) {
					if (!sic.isCreate()) {
						setc.removeIndexConfig(sic.getName());
					}
				}
			}

			for (SourceConfig sc : getExpViewCfg()) {
				if (!sc.isCreate()) {
					targetViews.remove(getTargetViewSchema(sc.getTarget()));
					expViews.remove(sc);
				}
			}

			for (SourceConfig sc : getExpSerialCfg()) {
				if (!sc.isCreate()) {
					targetSequences.remove(getTargetSerialSchema(sc.getTarget()));
					expSerials.remove(sc);
				}
			}
			
			for (SourceConfig sc : getExpSynonymCfg()) {
				if (!sc.isCreate()) {
					targetSynonyms.remove(getTargetSynonymSchema(sc.getTarget()));
					expSynonyms.remove(sc);
				}
			}
			
			for (SourceConfig sc : getExpGrantCfg()) {
				if (!sc.isCreate()) {
					targetGrants.remove(getTargetGrantSchema(sc.getTarget()));
					expGrants.remove(sc);
				}
			}
			cleanN21Tables();
		} else if (sourceType == SOURCE_TYPE_CSV) {
			final Iterator<Table> it = targetTables.iterator();
			List<SourceCSVConfig> refed = new ArrayList<SourceCSVConfig>();
			List<String> colALL = new ArrayList<String>();
			while (it.hasNext()) {
				refed.clear();
				final Table nextT = it.next();
				for (SourceCSVConfig scc : csvFiles) {
					if (scc.getTarget().equalsIgnoreCase(nextT.getName())) {
						refed.add(scc);
					}
				}
				if (refed.isEmpty()) {
					it.remove();
					continue;
				}
				colALL.clear();
				for (SourceCSVConfig scc : refed) {
					for (SourceCSVColumnConfig sccc : scc.getColumnConfigs()) {
						colALL.add(sccc.getTarget());
					}
				}
				for (Column col : nextT.getColumns()) {
					if (colALL.indexOf(col.getName()) < 0) {
						nextT.removeColumn(col);
					}
				}
			}
		}
	}

	/**
	 * Clear all configurations and target schemas including SQL schemas
	 * 
	 */
	private void clearAll() {
		srcSQLSchemas.clear();

		expTables.clear();
		expSQLTables.clear();
		expViews.clear();
		expSerials.clear();
		expFunctions.clear();
		expProcedures.clear();
		expTriggers.clear();
		expSynonyms.clear();
		expGrants.clear();

		targetSequences.clear();
		targetTables.clear();
		targetViews.clear();
		targetSynonyms.clear();
	}

	/**
	 * Clear all sql tables
	 * 
	 */
	public void clearAllSQLTables() {
		expSQLTables.clear();
	}

	/**
	 * Clear configurations with invalid schema in the source
	 * 
	 */
	private void clearInvalidSchema() {
		if (!srcCatalog.getDatabaseType().isSupportMultiSchema()) {
			return;
		}
		Iterator<SourceEntryTableConfig> setcit = expTables.iterator();
		while (setcit.hasNext()) {
			SourceEntryTableConfig setc = setcit.next();
			if (setc.getOwner() == null) {
				continue;
			}
			if (srcCatalog.getSchemaByName(setc.getOwner()) == null) {
				setcit.remove();
			}
		}
		Iterator<SourceViewConfig> svcit = expViews.iterator();
		while (svcit.hasNext()) {
			SourceViewConfig svc = svcit.next();
			if (svc.getOwner() == null) {
				continue;
			}
			if (srcCatalog.getSchemaByName(svc.getOwner()) == null) {
				setcit.remove();
			}
		}
		Iterator<SourceSequenceConfig> sscit = expSerials.iterator();
		while (sscit.hasNext()) {
			SourceSequenceConfig ssc = sscit.next();
			if (ssc.getOwner() == null) {
				continue;
			}
			if (srcCatalog.getSchemaByName(ssc.getOwner()) == null) {
				setcit.remove();
			}
		}
	}

	/**
	 * Create target csv tables
	 * 
	 * @param scc SourceCSVConfig
	 * @param tblInTargetDB tblInTargetDB
	 */
	private void createTargetCSVTable(SourceCSVConfig scc, Table tblInTargetDB) {
		Table tt = getTargetTableSchema(scc.getTarget());
		CUBRIDDataTypeHelper dthelper = CUBRIDDataTypeHelper.getInstance(null);
		if (tt == null) {
			tt = new Table();
			tt.setName(scc.getTarget());
			for (SourceCSVColumnConfig sccc : scc.getColumnConfigs()) {
				Column col = new Column(tt);
				col.setName(sccc.getTarget());
				Column colInTarget = tblInTargetDB == null ? null
						: tblInTargetDB.getColumnByName(sccc.getTarget());
				if (colInTarget == null) {
					dthelper.setColumnDataType("string", col);
				} else {
					dthelper.setColumnDataType(colInTarget.getShownDataType(), col);
				}
				tt.addColumn(col);
			}
			targetTables.add(tt);
		} else {
			for (SourceCSVColumnConfig sccc : scc.getColumnConfigs()) {
				Column col = tt.getColumnByName(sccc.getTarget());
				if (col != null) {
					continue;
				}
				col = new Column(tt);
				col.setName(sccc.getTarget());
				Column colInTarget = tblInTargetDB == null ? null
						: tblInTargetDB.getColumnByName(sccc.getTarget());
				if (colInTarget == null) {
					dthelper.setColumnDataType("string", col);
				} else {
					dthelper.setColumnDataType(colInTarget.getShownDataType(), col);
				}
				tt.addColumn(col);
			}
		}
	}

	/**
	 * Get charset factor
	 * 
	 * @return charsetFactor
	 */
	public Integer getCharsetFactor() {
		int sourceFactor = CharsetUtils.getCharsetByte(getSourceCharset());
		int targetFactor = CharsetUtils.getCharsetByte(getTargetCharSet());
		return targetFactor == 1 ? sourceFactor : targetFactor;
	}

	/**
	 * Commit count
	 * 
	 * @return the commitCount default 500
	 */
	public int getCommitCount() {
		return commitCount;
	}

	/**
	 * getCSVConfigByFile
	 * 
	 * @param file name
	 * @return SourceCSVConfig
	 */
	public SourceCSVConfig getCSVConfigByFile(String file) {
		for (SourceCSVConfig sc : csvFiles) {
			if (sc.getName().equals(file)) {
				return sc;
			}
		}
		return null;
	}

	public List<SourceCSVConfig> getCSVConfigs() {
		return new ArrayList<SourceCSVConfig>(csvFiles);
	}

	public CSVSettings getCsvSettings() {
		return csvSettings;
	}

	/**
	 * Retrieves the data file's ext name.
	 * 
	 * @return 0:.txt 1:.csv 2:.sql
	 */
	public String getDataFileExt() {
		return DATA_FORMAT_EXT[destType];
	}

	/**
	 * If source is online, JDBC parameter will be returned; if source is XML,
	 * <MYSQLXMLDumpSource> will be returned; Other (SQL or CSV) will return a
	 * IDBSource object.
	 * 
	 * @return IDBSource for fetching source schema
	 */
	private IDBSource getDBSource() {
		if (sourceIsOnline()) {
			return sourceConParams;
		} else if (sourceIsXMLDump()) {
			return new MysqlXmlDumpSource(sourceFileName, sourceFileEncoding);
		} else {
			return new IDBSource() {
			};
		}
	}

	/**
	 * Retrieves the migration DBTransformHelper.
	 * 
	 * @return DBTranformHelper
	 */
	public DBTransformHelper getDBTransformHelper() {
		return MigrationTransFactory.getTransformHelper(getSourceDBType(), DatabaseType.CUBRID);
	}

	public int getDestType() {
		return destType;
	}

	/**
	 * Get deta raw offset
	 * 
	 * @return the deta raw offset between source and target time zone
	 */
	public Integer getDetaRawOffset() {
		int sourceRawOffset = getSourceDatabaseTimeZone().getRawOffset();
		int targetRawOffset = getTargetDatabaseTimeZone().getRawOffset();
		return sourceRawOffset - targetRawOffset;
	}

	/**
	 * getExpColumnCfg by table name and column name, including SQL table's
	 * columns
	 * 
	 * @param schema name of the object
	 * @param sourceTableName String
	 * @param columnName String
	 * @return SourceColumnConfig
	 */
	public SourceColumnConfig getExpColumnCfg(String schema, String sourceTableName,
			String columnName) {
		SourceTableConfig setc = getExpEntryTableCfg(schema, sourceTableName);
		if (setc == null) {
			setc = getExpSQLCfgByName(sourceTableName);
		}
		if (setc == null) {
			return null;
		}
		SourceColumnConfig sourceColumnConfig = setc.getColumnConfig(columnName);
		return sourceColumnConfig;
	}

	/**
	 * Retrieves all export tables.
	 * 
	 * @return the exportTables
	 */
	public List<SourceEntryTableConfig> getExpEntryTableCfg() {
		return new ArrayList<SourceEntryTableConfig>(expTables);
	}

	/**
	 * get export entry table
	 * 
	 * @param schema the schema name of table
	 * @param tableName String
	 * @return SourceEntryTableConfig
	 */
	public SourceEntryTableConfig getExpEntryTableCfg(String schema, String tableName) {
		SourceEntryTableConfig result = null;
		for (SourceEntryTableConfig setc : expTables) {
			if (setc.getName().equals(tableName)) {
				if (schema == null) {
					return setc;
				}
				if (schema.equals(setc.getOwner())) {
					return setc;
				}
				if (setc.getOwner() == null) {
					result = setc;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * getSourceFKConfigByTableAndFKName
	 * 
	 * @param schema name of the object.
	 * @param sourceTableName String
	 * @param fkName String
	 * @return SourceFKConfig
	 */
	public SourceFKConfig getExpFKCfg(String schema, String sourceTableName, String fkName) {
		SourceEntryTableConfig sourceEntryTableConfig = getExpEntryTableCfg(schema, sourceTableName);
		if (sourceEntryTableConfig == null) {
			return null;
		}
		return sourceEntryTableConfig.getFKConfig(fkName);
	}

	/**
	 * Get exporting function
	 * 
	 * @param schema name
	 * @param name of the object
	 * @return Function
	 */
	public Function getExpFunction(String schema, String name) {
		Schema sc = srcCatalog.getSchemaByName(schema);
		if (sc != null) {
			return sc.getFunction(name);
		}
		return null;
	}

	public List<String> getExpFunctionCfg() {
		return new ArrayList<String>(expFunctions);
	}

	/**
	 * getExportFunction
	 * 
	 * @param name String
	 * @return String
	 */
	public String getExpFunctionCfg(String name) {
		return expFunctions.indexOf(name) < 0 ? null : name;
	}

	/**
	 * getSourceIndexConfigByTableAndIndexName
	 * 
	 * @param schema name of the object
	 * @param sourceTableName String
	 * @param indexName String
	 * @return SourceIndexConfig
	 */
	public SourceIndexConfig getExpIdxCfg(String schema, String sourceTableName, String indexName) {
		SourceEntryTableConfig setc = getExpEntryTableCfg(schema, sourceTableName);
		if (setc == null) {
			return null;
		}
		return setc.getIndexConfig(indexName);
	}

	/**
	 * Retrieves the object count of this configuration
	 * 
	 * @return int
	 */
	public int getExpObjCount() {
		int iCount = expTables.size() + expSQLTables.size() + expSerials.size() + expViews.size();
		for (SourceEntryTableConfig setc : expTables) {
			iCount = iCount + setc.getFKConfigList().size();
			iCount = iCount + setc.getIndexConfigList().size();
		}
		return iCount;
	}

	/**
	 * @return the exportThreadCount
	 */
	public int getExportThreadCount() {
		return exportThreadCount;
	}

	/**
	 * Get exporting procedure
	 * 
	 * @param schema name
	 * @param name of the object
	 * @return procedure
	 */
	public Procedure getExpProcedure(String schema, String name) {
		Schema sc = srcCatalog.getSchemaByName(schema);
		if (sc != null) {
			return sc.getProcedure(name);
		}
		return null;
	}

	public List<String> getExpProcedureCfg() {
		return new ArrayList<String>(expProcedures);
	}

	/**
	 * getExportProcedure
	 * 
	 * @param name String
	 * @return String
	 */
	public String getExpProcedureCfg(String name) {
		return expProcedures.indexOf(name) < 0 ? null : name;
	}

	/**
	 * Retrieves all the schema names to be exported.
	 * 
	 * @return schema names.
	 */
	public List<String> getExpSchemaNames() {
		List<String> result = new ArrayList<String>();
		for (SourceEntryTableConfig setc : expTables) {
			if (setc.getOwner() == null) {
				continue;
			}
			if (result.contains(setc.getOwner())) {
				continue;
			}
			result.add(setc.getOwner());
		}
		for (SourceViewConfig setc : expViews) {
			if (setc.getOwner() == null) {
				continue;
			}
			if (result.contains(setc.getOwner())) {
				continue;
			}
			result.add(setc.getOwner());
		}
		for (SourceSequenceConfig setc : expSerials) {
			if (setc.getOwner() == null) {
				continue;
			}
			if (result.contains(setc.getOwner())) {
				continue;
			}
			result.add(setc.getOwner());
		}
		return result;
	}

	/**
	 * Return the export sequence configurations
	 * 
	 * @return List<SourceConfig>
	 */
	public List<SourceSequenceConfig> getExpSerialCfg() {
		return new ArrayList<SourceSequenceConfig>(expSerials);
	}

	/**
	 * getExportSequences
	 * 
	 * @param schema name of the object
	 * @param sourceName String
	 * @return SourceConfig
	 */
	public SourceSequenceConfig getExpSerialCfg(String schema, String sourceName) {
		SourceSequenceConfig result = null;
		for (SourceSequenceConfig config : expSerials) {
			if (config.getName().equals(sourceName)) {
				if (schema == null) {
					return config;
				}
				if (schema.equals(config.getOwner())) {
					return config;
				}
				if (config.getOwner() == null) {
					result = config;
					break;
				}
			}
		}
		return result;
	}
	
	/**
	 * Return the export synonym configurations
	 * 
	 * @return List<SourceConfig>
	 */
	public List<SourceSynonymConfig> getExpSynonymCfg() {
		return new ArrayList<SourceSynonymConfig>(expSynonyms);
	}
	
	/**
	 * getExportSynonyms
	 * 
	 * @param schema name of the object
	 * @param sourceName String
	 * @return SourceConfig
	 */
	public SourceSynonymConfig getExpSynonymCfg(String schema, String sourceName) {
		SourceSynonymConfig result = null;
		for (SourceSynonymConfig config : expSynonyms) {
			if (config.getName().equals(sourceName)) {
				if (schema == null) {
					return config;
				}
				if (schema.equalsIgnoreCase(config.getOwner())) {
					return config;
				}
				if (config.getOwner() == null) {
					result = config;
					break;
				}
			}
		}
		return result;
	}
	
	/**
	 * Return the export grant configurations
	 * 
	 * @return List<SourceGrantConfig>
	 */
	public List<SourceGrantConfig> getExpGrantCfg() {
		return new ArrayList<SourceGrantConfig>(expGrants);
	}
	
	/**
	 * getExportGrants
	 * 
	 * @param schema String
	 * @param sourceName String
	 * @return SourceGrantConfig
	 */
	public SourceGrantConfig getExpGrantCfg(String schema, String sourceName) {
		SourceGrantConfig result = null;
		for (SourceGrantConfig config : expGrants) {
			if (config.getName().equals(sourceName)) {
				if (schema == null) {
					return config;
				}
				if (schema.equalsIgnoreCase(config.getOwner())) {
					return config;
				}
				if (config.getOwner() == null) {
					result = config;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Retrieves all export tables.
	 * 
	 * @return the exportTables
	 */
	public List<SourceSQLTableConfig> getExpSQLCfg() {
		return new ArrayList<SourceSQLTableConfig>(expSQLTables);
	}

	/**
	 * 
	 * getExportSQLTableByTableName
	 * 
	 * @param tableName of SQL table
	 * @return SourceSQLTableConfig
	 */
	public SourceSQLTableConfig getExpSQLCfgByName(String tableName) {
		for (SourceSQLTableConfig config : expSQLTables) {
			if (config.getName().equals(tableName)) {
				return config;
			}
		}
		return null;
	}

	/**
	 * getExportSQLTableBySql
	 * 
	 * @param sql String
	 * @return SourceSQLTableConfig
	 */
	public SourceSQLTableConfig getExpSQLCfgBySql(String sql) {
		for (SourceSQLTableConfig config : expSQLTables) {
			if (config.getSql().equals(sql)) {
				return config;
			}
		}
		return null;
	}

	/**
	 * Get exporting trigger
	 * 
	 * @param schema name
	 * @param name of the object
	 * @return trigger
	 */
	public Trigger getExpTrigger(String schema, String name) {
		Schema sc = srcCatalog.getSchemaByName(schema);
		if (sc != null) {
			return sc.getTrigger(name);
		}
		return null;
	}

	public List<String> getExpTriggerCfg() {
		return new ArrayList<String>(expTriggers);
	}

	/**
	 * Get export trigger by name.
	 * 
	 * @param name trigger name
	 * @return trigger name
	 */
	public String getExpTriggerCfg(String name) {
		return expTriggers.indexOf(name) < 0 ? null : name;
	}

	public List<SourceViewConfig> getExpViewCfg() {
		return new ArrayList<SourceViewConfig>(expViews);
	}

	/**
	 * getExportView
	 * 
	 * @param schema schema name of the view
	 * @param viewName String
	 * @return SourceConfig
	 */
	public SourceViewConfig getExpViewCfg(String schema, String viewName) {
		SourceViewConfig result = null;
		for (SourceViewConfig sc : expViews) {
			if (sc.getName().equals(viewName)) {
				if (schema == null) {
					return sc;
				}
				if (schema.equals(sc.getOwner())) {
					return sc;
				}
				if (sc.getOwner() == null) {
					result = sc;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Get the dictionary to be exported.
	 * 
	 * @return dictionary string
	 */
	public String getFileRepositroyPath() {
		return fileRepositroyPath;
	}

	/**
	 * Retrieves the full prefix of the output files.
	 * 
	 * @return full prefix
	 */
	public String getFullTargetFilePrefix() {
		String filePrefix = getTargetFilePrefix();
		if (filePrefix == null) {
			filePrefix = srcCatalog.getName();
		}
		if (StringUtils.isNotBlank(filePrefix)) {
			filePrefix = filePrefix + "_";
		}
		return filePrefix;
	}

	/**
	 * @return the importThreadCount
	 */
	public int getImportThreadCount() {
		return importThreadCount;
	}

	public int getMaxCountPerFile() {
		return maxCountPerFile;
	}

	public String getName() {
		return name;
	}

	public Catalog getOfflineSrcCatalog() {
		return offlineSrcCatalog;
	}

	/**
	 * Get other parameter from configuration
	 * 
	 * @param key String
	 * @return value
	 */
	public String getOtherParam(String key) {
		return otherParams.get(key);
	}

	public int getPageFetchCount() {
		return pageFetchCount;
	}

	public int getReportLevel() {
		return reportLevel;
	}

	/**
	 * Get the source database charset;Empty string will not be returned.
	 * 
	 * @return charset
	 */
	public String getSourceCharset() {
		String charset;

		if (sourceIsOnline()) {
			charset = sourceConParams.getCharset();
		} else {
			charset = sourceFileEncoding;
		}

		if (StringUtils.isEmpty(charset)) {
			charset = "UTF-8";
		}
		return charset;
	}

	//	/**
	//	 * @return the sourceDBSchema
	//	 */
	//	public Schema getSourceDBSchema() {
	//		return sourceDBSchema;
	//	}

	/**
	 * 
	 * @return the sourceConParams
	 */
	public ConnParameters getSourceConParams() {
		return sourceConParams;
	}

	/**
	 * Get source database timezone
	 * 
	 * @return String
	 */
	public TimeZone getSourceDatabaseTimeZone() {
		String tzID;

		if (sourceIsOnline()) {
			tzID = TimeZoneUtils.getGMTByDisplay(sourceConParams.getTimeZone());
		} else {
			tzID = TimeZoneUtils.getGMTByDisplay(sourceFileTimeZone);
		}

		if (tzID == null || "default".equals(tzID)) {
			return TimeZone.getDefault();
		}
		TimeZone tz = TimeZone.getTimeZone(tzID);
		return tz == null ? TimeZone.getDefault() : tz;
	}

	/**
	 * 
	 * @return the getSourceDBType()
	 */
	public DatabaseType getSourceDBType() {
		if (sourceIsCSV() || sourceIsSQL()) {
			return DatabaseType.CUBRID;
		}
		if (sourceIsXMLDump()) {
			return DatabaseType.MYSQL;
		}
		return DatabaseType.getDatabaseTypeByID(sourceType);
	}

	/**
	 * 
	 * @return the sourceFileEncoding
	 */
	public String getSourceFileEncoding() {
		return sourceFileEncoding == null ? "" : sourceFileEncoding;
	}

	/**
	 * 
	 * @return the sourceFileName
	 */
	public String getSourceFileName() {
		return sourceFileName == null ? "" : sourceFileName;
	}

	/**
	 * 
	 * @return the sourceFileTimeZone
	 */
	public String getSourceFileTimeZone() {
		return sourceFileTimeZone;
	}

	/**
	 * 
	 * @return the sourceFileVersion
	 */
	public String getSourceFileVersion() {
		return sourceFileVersion;
	}

	/**
	 * Get source table configurations by target name.
	 * 
	 * @param targetName String
	 * @return List<SourceTableConfig>
	 */
	public List<SourceTableConfig> getSourceTableConfigByTarget(String targetName) {
		List<SourceTableConfig> result = new ArrayList<SourceTableConfig>();
		for (SourceEntryTableConfig setc : expTables) {
			if (targetName.equalsIgnoreCase(setc.getTarget())) {
				result.add(setc);
			}
		}
		for (SourceSQLTableConfig sstc : expSQLTables) {
			if (targetName.equalsIgnoreCase(sstc.getTarget())) {
				result.add(sstc);
			}
		}
		return result;
	}

	public int getSourceType() {
		return sourceType;
	}

	/**
	 * Retrieves the type name of data source
	 * 
	 * @return xml,sql,mysql,cubrid,oracle one of them
	 */
	public String getSourceTypeName() {
		if (sourceIsSQL()) {
			return SQL;
		} else if (sourceIsXMLDump()) {
			return XML;
		} else if (sourceIsCSV()) {
			return CSV;
		}
		return this.getSourceDBType().getName();
	}

	/**
	 * Retrieves the SQL files to be exectued.
	 * 
	 * @return a copy of List<String>
	 */
	public List<String> getSqlFiles() {
		return new ArrayList<String>(sqlFiles);
	}

	public Catalog getSrcCatalog() {
		return srcCatalog;
	}

	/**
	 * Retrieves the source column information
	 * 
	 * @param schema table's owner name
	 * @param tableName String
	 * @param columnName String
	 * @return Column
	 */
	public Column getSrcColumnSchema(String schema, String tableName, String columnName) {
		Table tbl = getSrcTableSchema(schema, tableName);
		if (tbl != null) {
			return tbl.getColumnByName(columnName);
		}
		return null;
	}

	/**
	 * get Export Foreign Key by table name and FK name
	 * 
	 * @param schema table's owner name
	 * @param tableName String
	 * @param fkName String
	 * @return FK
	 */
	public FK getSrcFKSchema(String schema, String tableName, String fkName) {
		Table table = getSrcTableSchema(schema, tableName);
		if (table == null) {
			return null;
		}
		return table.getFKByName(fkName);
	}

	/**
	 * getExportFksByTableNames
	 * 
	 * @param schema table's owner name
	 * @param tableName String
	 * @return String
	 */
	public List<FK> getSrcFkSchemaByTable(String schema, String tableName) {
		List<FK> fkList = new ArrayList<FK>();
		Table table = getSrcTableSchema(schema, tableName);
		if (table == null) {
			return fkList;
		}
		return table.getFks();
	}

	/**
	 * Get source index by table name and index name
	 * 
	 * @param schema table's owner name
	 * @param tableName source table name
	 * @param indexName source index name
	 * @return Index
	 */
	public Index getSrcIdxSchema(String schema, String tableName, String indexName) {
		Table table = getSrcTableSchema(schema, tableName);
		if (table == null) {
			return null;
		}
		return table.getIndexByName(indexName);
	}

	/**
	 * Get all export sequences
	 * 
	 * @return List<Sequence>
	 */
	public List<Sequence> getSrcSerialSchema() {
		List<Sequence> list = new ArrayList<Sequence>();
		List<Schema> schemas = srcCatalog.getSchemas();
		for (Schema sourceDBSchema : schemas) {
			list.addAll(sourceDBSchema.getSequenceList());
		}
		return list;
	}

	/**
	 * getSourceSequenceByName
	 * 
	 * @param schema sequence schema name
	 * @param sequenceName String
	 * @return Sequence
	 */
	public Sequence getSrcSerialSchema(String schema, String sequenceName) {
		Schema sourceDBSchema = srcCatalog.getSchemaByName(schema);
		if (sourceDBSchema == null) {
			return null;
		}
		return sourceDBSchema.getSequenceByName(sequenceName);
	}

	/**
	 * getExportSQLSourceTableBySourceTableName
	 * 
	 * @param name String
	 * @return SourceTable
	 */
	public Table getSrcSQLSchema(String name) {
		for (Table sourceTable : srcSQLSchemas) {
			if (sourceTable.getName().equals(name)) {
				return sourceTable;
			}
		}
		return null;
	}

	/**
	 * get all export SQL schemas
	 * 
	 * @return List<SourceTable>
	 */
	public List<Table> getSrcSQLSchema2Exp() {
		return new ArrayList<Table>(srcSQLSchemas);
	}

	/**
	 * getExportSQLSourceTableBySourceTableSql
	 * 
	 * @param sql String
	 * @return SourceTable
	 */
	public Table getSrcSQLSchemaBySql(String sql) {
		SourceSQLTableConfig sstc = getExpSQLCfgBySql(sql);
		if (sstc == null) {
			return null;
		}
		for (Table sourceTable : srcSQLSchemas) {
			if (sstc.getName().equals(sourceTable.getName())) {
				return sourceTable;
			}
		}
		return null;
	}

	/**
	 * Retrieves the source table object including SQL table object.
	 * 
	 * @param schema Schema
	 * @param name of the source table
	 * @return source table
	 */
	public Table getSrcTableSchema(String schema, String name) {
		if (srcCatalog == null) {
			return null;
		}
		if (srcCatalog.getSchemas().isEmpty()) {
			return null;
		}
		final Schema sc;
		if (schema == null) {
			//retrieves default schema.
			sc = srcCatalog.getSchemas().get(0);
		} else {
			sc = srcCatalog.getSchemaByName(schema);
		}
		if (sc == null) {
			return null;
		}
		Table table = sc.getTableByName(name);
		if (table == null) {
			table = getSrcSQLSchema(name);
		}
		return table;
	}

	/**
	 * getSourceView
	 * 
	 * @param schema of the view
	 * @param viewName viewName
	 * @return View
	 */
	public View getSrcViewSchema(String schema, String viewName) {
		if (srcCatalog == null) {
			return null;
		}
		Schema sc = srcCatalog.getSchemaByName(schema);
		if (sc == null) {
			return null;
		}
		return sc.getViewByName(viewName);
	}
	
	public void clearNewTargetShemaList() {
		newTargetSchema.clear();
	}

	public List<String> getNewTargetSchema() {
		return this.newTargetSchema;
	}
	
	public void setNewTargetSchema(String schemaName) {
		newTargetSchema.add(schemaName);
	}
	
	public void setNewTargetSchema(List<String> schemaNameList) {
		clearNewTargetShemaList();
		newTargetSchema = schemaNameList;
	}
	
	public List<Schema> getTargetSchemaList() {
		return targetSchemaList;
	}

	public void setTargetSchemaList(List<Schema> targetSchemaList) {
		this.targetSchemaList.addAll(targetSchemaList);
	}
	
	/**
	 * Retrieves the target charset;UTF-8 will be returned by default.
	 * 
	 * @return charset
	 */
	public String getTargetCharSet() {
		String result = null;
		if (targetIsCSV()) {
			result = this.csvSettings.getCharset();
		} else if (targetIsFile()) {
			result = targetCharSet;
		} else if (targetIsOnline()) {
			result = this.targetConParams.getCharset();
		}
		return result == null ? "UTF-8" : result;
	}

	/**
	 * Get the export column by source table name and column name
	 * 
	 * @param tableName target table name
	 * @param columnName target column name
	 * @return Column of target table
	 */
	public Column getTargetColumnSchema(String tableName, String columnName) {
		for (SourceEntryTableConfig setc : expTables) {
			if (!setc.getTarget().equals(tableName)) {
				continue;
			}
			Table tt = getTargetTableSchema(tableName);
			if (tt == null) {
				return null;
			}
			Column col = tt.getColumnByName(columnName);
			return col;
		}
		for (SourceSQLTableConfig sstc : expSQLTables) {
			if (!sstc.getTarget().equals(tableName)) {
				continue;
			}
			Table tt = getTargetTableSchema(tableName);
			if (tt == null) {
				return null;
			}
			Column col = tt.getColumnByName(columnName);
			return col;
		}
		return null;
	}

	/**
	 * 
	 * @return the targetConParams
	 */
	public ConnParameters getTargetConParams() {
		return targetConParams;
	}

	/**
	 * Get target database timezone
	 * 
	 * @return target db time zone string
	 */
	public TimeZone getTargetDatabaseTimeZone() {
		String tzID = null;

		if (targetIsOnline()) {
			tzID = TimeZoneUtils.getGMTByDisplay(targetConParams.getTimeZone());
		} else if (targetIsFile()) {
			tzID = TimeZoneUtils.getGMTByDisplay(targetFileTimeZone);
		}

		if (tzID == null || "default".equals(tzID)) {
			return TimeZone.getDefault();
		}

		TimeZone tz = TimeZone.getTimeZone(tzID);

		return tz == null ? TimeZone.getDefault() : tz;
	}

	/**
	 * Return the target data file format label.
	 * 
	 * @return Target label of target data file's format
	 */
	public String getTargetDataFileFormatLabel() {
		return DATA_FORMAT_LABEL[destType];
	}

	public Map<String, String> getTargetDataFileName() {
		return new HashMap<String, String>(this.targetDataFileName);
	}
	
	public String getTargetDataFileName(String schemaName) {
		return this.targetDataFileName.get(schemaName);
	}

	/**
	 * 
	 * @return the targetDBVersion
	 */
	public String getTargetDBVersion() {
		return targetDBVersion;
	}

	public String getTargetFilePrefix() {
		return targetFilePrefix;
	}

	public String getTargetFileTimeZone() {
		return targetFileTimeZone;
	}

	public String getTargetLOBRootPath() {
		return targetLOBRootPath;
	}
	
	public boolean isAddUserSchema() {
		return addUserSchema;
	}
	
	public boolean isSplitSchema() {
		return splitSchema;
	}
	
	public boolean isTargetDBAGroup() {
		return targetDBAGroup;
	}
	
	public boolean isCreateUserSQL() {
		return createUserSQL;
	}

	/**
	 * Retrieves the referenced count of target table name
	 * 
	 * @param targetName to be check
	 * @return int
	 */
	public int getTargetRefedCount(String targetName) {
		int count = 0;
		for (SourceEntryTableConfig setc : expTables) {
			if (setc.getTarget().equalsIgnoreCase(targetName)) {
				count++;
			}
		}
		for (SourceSQLTableConfig setc : expSQLTables) {
			if (setc.getTarget().equalsIgnoreCase(targetName)) {
				count++;
			}
		}
		return count;
	}
	
	public Map<String, String> getTargetSchemaFileName() {
		return new HashMap<String, String>(this.targetSchemaFileName);
	}
	
	public String getTargetSchemaFileName(String schemaName) {
		return this.targetSchemaFileName.get(schemaName);
	}

	public Map<String, String> getTargetTableFileName() {
		return new HashMap<String, String>(this.targetTableFileName);
	}
	
	public String getTargetTableFileName(String schemaName) {
		return this.targetTableFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetViewFileName() {
		return new HashMap<String, String>(this.targetViewFileName);
	}
	
	public String getTargetViewFileName(String schemaName) {
		return this.targetViewFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetViewQuerySpecFileName() {
		return new HashMap<String, String>(this.targetViewQuerySpecFileName);
	}
	
	public String getTargetViewQuerySpecFileName(String schemaName) {
		return this.targetViewQuerySpecFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetPkFileName() {
		return new HashMap<String, String>(this.targetPkFileName);
	}
	
	public String getTargetPkFileName(String schemaName) {
		return this.targetPkFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetFkFileName() {
		return new HashMap<String, String>(this.targetFkFileName);
	}
	
	public String getTargetFkFileName(String schemaName) {
		return this.targetFkFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetIndexFileName() {
		return new HashMap<String, String>(this.targetIndexFileName);
	}
	
	public String getTargetIndexFileName(String schemaName) {
		return this.targetIndexFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetSerialFileName() {
		return new HashMap<String, String>(this.targetSerialFileName);
	}
	
	public String getTargetSerialFileName(String schemaName) {
		return this.targetSerialFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetUpdateStatisticFileName() {
		return new HashMap<String, String>(this.targetUpdateStatisticFileName);
	}
	
	public String getTargetUpdateStatisticFileName(String schemaName) {
		return this.targetUpdateStatisticFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetSchemaFileListName() {
		return new HashMap<String, String>(this.targetSchemaFileListName);
	}
	
	public String getTargetSchemaFileListName(String schemaName) {
		return this.targetSchemaFileListName.get(schemaName);
	}
	
	public Map<String, String> getTargetSynonymFileName() {
		return new HashMap<String, String>(this.targetSynonymFileName);
	}
	
	public String getTargetSynonymFileName(String schemaName) {
		return this.targetSynonymFileName.get(schemaName);
	}
	
	public Map<String, String> getTargetGrantFileName1() {
		return new HashMap<String, String>(this.targetGrantFileName);
	}
	
	public String getTargetGrantFileName(String schemaName) {
		return this.targetGrantFileName.get(schemaName);
	}

	/**
	 * getTargetSerialList
	 * 
	 * @return List<Sequence>
	 */
	public List<Sequence> getTargetSerialSchema() {
		return new ArrayList<Sequence>(targetSequences);
	}

	/**
	 * get target sequence by sequence name
	 * 
	 * @param target String name
	 * @return target sequence
	 */
	public Sequence getTargetSerialSchema(String target) {
		for (Sequence seq : this.targetSequences) {
			if (seq.getName().equals(target)) {
				return seq;
			}
		}
		return null;
	}
	
	/**
	 * get target sequence by sequence name and sequence owner
	 * 
	 * @param String owner, String target
	 * @return target sequence
	 */
	public Sequence getTargetSerialSchema(String owner, String target) {
		if (owner == null) {
			return getTargetSerialSchema(target);
		}
		
		for (Sequence seq : this.targetSequences) {
			if (seq.getName().equalsIgnoreCase(target) && seq.getOwner().equalsIgnoreCase(owner)) {
				return seq;
			}
		}
		return null;
	}
	
	/**
	 * getTargetSynonymList
	 * 
	 * @return List<Synonym>
	 */
	public List<Synonym> getTargetSynonymSchema() {
		return new ArrayList<Synonym>(targetSynonyms);
	}
	
	/**
	 * get target synonym by synonym name
	 * 
	 * @param target String name
	 * @return target synonym
	 */
	public Synonym getTargetSynonymSchema(String target) {
		for (Synonym synonym : this.targetSynonyms) {
			if (synonym.getName().equals(target)) {
				return synonym;
			}
		}
		return null;
	}
	
	/**
	 * get target synonym by synonym name and synonym owner
	 * 
	 * @param String owner, String target
	 * @return target synonym
	 */
	public Synonym getTargetSynonymSchema(String owner, String target) {
		if (owner == null) {
			return getTargetSynonymSchema(target);
		}
		
		for (Synonym synonym : this.targetSynonyms) {
			if (synonym.getName().equalsIgnoreCase(target) && synonym.getOwner().equalsIgnoreCase(owner)) {
				return synonym;
			}
		}
		return null;
	}
	
	/**
	 * get target grant by grant name
	 * 
	 * @param target String name
	 * @return target grant
	 */
	public Grant getTargetGrantSchema(String target) {
		for (Grant grant : this.targetGrants) {
			if (grant.getName().equalsIgnoreCase(target)) {
				return grant;
			}
		}
		return null;
	}
	
	/**
	 * get target grant by grant name and grant owner
	 * 
	 * @param Stromg owner, String target
	 * @return target grant
	 */
	public Grant getTargetGrantSchema(String owner, String target) {
		if (owner == null) {
			return getTargetGrantSchema(target);
		}
		
		for (Grant grant : this.targetGrants) {
			if (grant.getName().equalsIgnoreCase(target) && grant.getOwner().equalsIgnoreCase(owner)) {
				return grant;
			}
		}
		return null;
	}

	/**
	 * getTargetTables
	 * 
	 * @return List<TargetTable>
	 */
	public List<Table> getTargetTableSchema() {
		return new ArrayList<Table>(targetTables);
	}

	/**
	 * get table in target table by target table's name
	 * 
	 * @param name of the target table
	 * @return TargetTable
	 */
	public Table getTargetTableSchema(String name) {
		for (Table tt : this.targetTables) {
			if (tt.getName().equals(name)) {
				return tt;
			}
		}
		return null;
	}
	
	/**
	 * get target table by table name and table owner
	 * 
	 * @param String owner, String target
	 * @return target table
	 */
	public Table getTargetTableSchema(String owner, String name) {
		if (owner == null) {
			return getTargetTableSchema(name);
		}
		
//		Schema targetSchema = verUtil.getSchemaMapping().get(owner);
		
		for (Table tt : this.targetTables) {
			if (tt.getName().equalsIgnoreCase(name) && tt.getOwner().equalsIgnoreCase(owner)) {
				return tt;
			}
		}
		return null;
	}

	public List<View> getTargetViewSchema() {
		return new ArrayList<View>(targetViews);
	}

	/**
	 * getTargetView
	 * 
	 * @param viewName String
	 * @return View
	 */
	public View getTargetViewSchema(String viewName) {
		for (View view : targetViews) {
			if (view.getName().equals(viewName)) {
				return view;
			}
		}
		return null;
	}
	
	/**
	 * get target view by view name and view owner
	 * 
	 * @param String owner, String target
	 * @return target view
	 */
	public View getTargetViewSchema(String owner, String viewName) {
		if (owner == null) {
			return getTargetViewSchema(viewName);
		}
		
		for (View view : targetViews) {
			if (view.getName().equalsIgnoreCase(viewName) && view.getOwner().equalsIgnoreCase(owner)) {
				return view;
			}
		}
		
		return null;
	}
	
	public boolean nullCheckEquals(String owner, Schema targetSchema) {
		if (owner == null || targetSchema == null) {
			return false;
		}
		
		if (owner.equalsIgnoreCase(targetSchema.getName())) {
			return true;
		}
		
		return false;
	}

	/**
	 * If there are FKs of source DB being exported.
	 * 
	 * @return true if it has FKs to be exported.
	 */
	public boolean hasFKExports() {
		for (SourceEntryTableConfig sourceEntryTableConfig : getExpEntryTableCfg()) {
			if (sourceEntryTableConfig.getFKConfigList().size() > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * If there are indexes of source DB being exported.
	 * 
	 * @return true if it has indexes to be exported.
	 */
	public boolean hasIndexExports() {
		for (SourceEntryTableConfig sourceEntryTableConfig : getExpEntryTableCfg()) {
			if (sourceEntryTableConfig.getIndexConfigList().size() > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieves whether the source database has objects selected to be
	 * exported.
	 * 
	 * @return true:if it has objects to be exported.
	 */
	public boolean hasObjects2Export() {
		if (sourceIsSQL()) {
			return !sqlFiles.isEmpty();
		}
		if (sourceIsCSV()) {
			return !csvFiles.isEmpty();
		}
		for (SourceEntryTableConfig setc : expTables) {
			if (setc.isCreateNewTable() || setc.isMigrateData()) {
				return true;
			}
		}
		for (SourceConfig sc : expViews) {
			if (sc.isCreate()) {
				return true;
			}
		}
		for (SourceSQLTableConfig sstc : expSQLTables) {
			if (sstc.isCreateNewTable() || sstc.isMigrateData()) {
				return true;
			}
		}
		for (SourceConfig sc : expSerials) {
			if (sc.isCreate()) {
				return true;
			}
		}
		for (SourceConfig sc : expSynonyms) {
			if (sc.isCreate()) {
				return true;
			}
		}
		for (SourceConfig sc : expGrants) {
			if (sc.isCreate()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * If has other parameters
	 * 
	 * @return true if it has
	 */
	public boolean hasOtherParam() {
		return !otherParams.isEmpty();
	}
	
	public boolean isCreateConstrainsBeforeData() {
		return createConstrainsBeforeData;
	}

	/**
	 * Delete the template file after loaddb.
	 * 
	 * @return true is delete and false is not
	 */
	public boolean isDeleteTempFile() {
		return deleteTempFile;
	}

	/**
	 * If the table's column is set to be exported
	 * 
	 * @param schema name
	 * @param name source table name
	 * @param cn column name
	 * @return true if it is set to be exported
	 */
	public boolean isExportColumn(String schema, String name, String cn) {
		SourceEntryTableConfig st = this.getExpEntryTableCfg(schema, name);
		return st != null && null != st.getColumnConfig(cn);
	}

	public boolean isExportNoSupportObjects() {
		return exportNoSupportObjects;
	}

	/**
	 * Whether use to count total records before a migration for showing correct
	 * progress.
	 * 
	 * @return true if don't count total count.
	 */
	public boolean isImplicitEstimate() {
		return implicitEstimate;
	}

	public boolean isOneTableOneFile() {
		return oneTableOneFile;
	}

	/**
	 * Retrieves the name is used in target schema
	 * 
	 * @param name to be verified.
	 * @return boolean
	 */
	public boolean isTargetNameInUse(String name) {
		if (StringUtils.isBlank(name)) {
			return false;
		}
		for (SourceEntryTableConfig setc : expTables) {
			if (name.equalsIgnoreCase(setc.getTarget())) {
				return setc.isCreateNewTable() || setc.isMigrateData();
			}
		}
		for (SourceSQLTableConfig sstc : expSQLTables) {
			if (name.equalsIgnoreCase(sstc.getTarget())) {
				return sstc.isCreateNewTable() || sstc.isMigrateData();
			}
		}
		for (SourceConfig sc : expViews) {
			if (name.equalsIgnoreCase(sc.getTarget())) {
				return sc.isCreate();
			}
		}
		//		for (SourceConfig sc : expSerials) {
		//			if (name.equalsIgnoreCase(sc.getTarget())) {
		//				return true;
		//			}
		//		}
		return false;
	}

	/**
	 * Retrieves whether the new name is used by other serial.
	 * 
	 * @param newName serial name
	 * @return true if used
	 */
	public boolean isTargetSerialNameInUse(String newName) {
		for (SourceConfig sc : expSerials) {
			if (sc.getTarget().equalsIgnoreCase(newName)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Retrieves whether the new name is used by other synonym.
	 * 
	 * @param newName synonym name
	 * @return true if used
	 */
	public boolean isTargetSynonymNameInUse(String newName) {
		for (SourceConfig sc : expSynonyms) {
			if (sc.getTarget().equalsIgnoreCase(newName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieves if write the error records to a sql file.
	 * 
	 * @return true if write
	 */
	public boolean isWriteErrorRecords() {
		return isWriteErrorRecords;
	}

	/**
	 * Parsing SourceCSVConfig, get columns and preview information from file
	 * 
	 * @param sc SourceCSVConfig
	 */
	public void parsingCSVFile(SourceCSVConfig sc) {
		BufferedReader reader;
		try {
			//new FileInputStream(sc.getName())
			reader = new BufferedReader(new InputStreamReader(
					CUBRIDIOUtils.getFileInputStream(sc.getName()), getCsvSettings().getCharset()));
			try {
				CSVReader csvReader = new CSVReader(reader, getCsvSettings().getSeparateChar(),
						getCsvSettings().getQuoteChar(), getCsvSettings().getEscapeChar());

				List<String[]> pd = new ArrayList<String[]>();
				int irow = 0;
				String[] line1 = csvReader.readNext();
				while (line1 != null && line1.length > 0 && irow < 10) {
					pd.add(line1);
					line1 = csvReader.readNext();
					irow++;
				}
				sc.setPreviewData(pd);
			} finally {
				reader.close();
			}
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Put some other parameters into configuration
	 * 
	 * @param key String
	 * @param value String
	 */
	public void putOtherParam(String key, String value) {
		otherParams.put(key, value == null ? "" : value);
	}

	/**
	 * removeCSVFile
	 * 
	 * @param file String
	 */
	public void removeCSVFile(String file) {
		SourceCSVConfig sc = getCSVConfigByFile(file);
		if (sc != null) {
			csvFiles.remove(sc);
		}
	}

	/**
	 * Remove all the exporting entry table configurations with schema name.
	 * 
	 * @param schema to be removed.
	 */
	public void removeExpSchema(String schema) {
		if (schema == null) {
			return;
		}
		Iterator<SourceEntryTableConfig> it = expTables.iterator();
		while (it.hasNext()) {
			SourceEntryTableConfig setc = it.next();
			if (setc.getOwner() == null) {
				continue;
			}
			if (schema.equals(setc.getOwner())) {
				it.remove();
			}
		}
		Iterator<SourceViewConfig> itv = expViews.iterator();
		while (itv.hasNext()) {
			SourceViewConfig setc = itv.next();
			if (setc.getOwner() == null) {
				continue;
			}
			if (schema.equals(setc.getOwner())) {
				itv.remove();
			}
		}
		Iterator<SourceSequenceConfig> its = expSerials.iterator();
		while (its.hasNext()) {
			SourceSequenceConfig setc = its.next();
			if (setc.getOwner() == null) {
				continue;
			}
			if (schema.equals(setc.getOwner())) {
				its.remove();
			}
		}
	}

	/**
	 * Rename all the exporting entry table configurations with schema name to a
	 * new schema name.
	 * 
	 * @param es old schema name
	 * @param newSchema new schema name
	 */
	public void renameExpSchema(String es, String newSchema) {
		if (newSchema == null) {
			return;
		}
		Iterator<SourceEntryTableConfig> it = expTables.iterator();
		while (it.hasNext()) {
			SourceEntryTableConfig setc = it.next();
			if (setc.getOwner() == null) {
				continue;
			}
			if (es.equals(setc.getOwner())) {
				setc.setOwner(newSchema);
			}
		}
	}

	/**
	 * Repair the settings about the situation that multi-table were mapping to
	 * a single table. It should be called after target schema was built.
	 * 
	 */
	private void repareN21MigrationSetting() {
		final Iterator<Table> iterator = targetTables.iterator();
		List<SourceTableConfig> stcs = new ArrayList<SourceTableConfig>();
		stcs.addAll(expTables);
		stcs.addAll(expSQLTables);
		while (iterator.hasNext()) {
			Table tt = iterator.next();
			if (getTargetRefedCount(tt.getName()) <= 1) {
				continue;
			}
			for (SourceTableConfig stc : stcs) {
				if (!stc.getTarget().equalsIgnoreCase(tt.getName())) {
					continue;
				}
				Table srcTable = getSrcTableSchema(stc.getOwner(), stc.getName());
				if (srcTable == null) {
					continue;
				}
				for (SourceColumnConfig scc : stc.getColumnConfigList()) {
					Column tcol = tt.getColumnByName(scc.getParent().getTargetOwner(), scc.getParent().getName(), scc.getTarget());
					if (tcol != null) {
						continue;
					}
					final Column scol = srcTable.getColumnByName(scc.getParent().getOwner(), scc.getParent().getName(), scc.getName());
					if (scol == null) {
						continue;
					}
					tcol = getDBTransformHelper().getCUBRIDColumn(scol, this);
					tcol.setName(scc.getTarget());
					tt.addColumn(tcol);
				}
			}
		}
	}

	/**
	 * Re-parse the CSV files with new CSV parsing settings
	 * 
	 * @param ts target Schema
	 */
	public void reparseCSVFiles(Schema ts) {
		List<SourceCSVConfig> sccs = new ArrayList<SourceCSVConfig>();
		for (SourceCSVConfig scc : csvFiles) {
			try {
				parsingCSVFile(scc);
			} catch (Exception ex) {
				sccs.add(scc);
				continue;
			}
			final Table tblInTargetDB = ts.getTableByName(scc.getTarget());
			if (tblInTargetDB != null) {
				scc.changeTarget(tblInTargetDB);
			}
			createTargetCSVTable(scc, tblInTargetDB);
		}
		//Remove the files can't be parsed.
		for (SourceCSVConfig scc : sccs) {
			csvFiles.remove(scc);
		}
	}

	/**
	 * Replace the old configuration with new configuration
	 * 
	 * @param oldsstc SourceSQLTableConfig
	 * @param newName the new SQL's name
	 * @param newSQL the new SQL
	 */
	public void replaceSQL(SourceSQLTableConfig oldsstc, String newName, String newSQL) {
		SourceSQLTableConfig sstc = getExpSQLCfgByName(oldsstc.getName());
		if (sstc == null) {
			return;
		}
		if (sstc.getName().equals(newName) && sstc.getSql().equals(newSQL)) {
			return;
		}
		Table oldtbl = getSrcSQLSchema(sstc.getName());
		if (!sstc.getName().equals(newName)) {
			if (oldtbl != null) {
				oldtbl.setName(newName);
			}
			sstc.setName(newName);
		}
		String cleanSQL = newSQL;
		if (cleanSQL.endsWith(";")) {
			cleanSQL = newSQL.substring(0, newSQL.length() - 1);
		}
		if (!sstc.getSql().equals(cleanSQL)) {
			Table newSQLSchema = getSourceDBType().getMetaDataBuilder().buildSQLTableSchema(
					getSourceConParams(), cleanSQL);

			srcSQLSchemas.remove(oldtbl);
			newSQLSchema.setName(sstc.getName());
			srcSQLSchemas.add(newSQLSchema);
			sstc.setSql(cleanSQL);

			sstc.clearColumnList();
			for (Column col : newSQLSchema.getColumns()) {
				sstc.addColumnConfig(col.getName(), col.getName(), true);
			}
			//If the target table referenced by other source table, don't change it
			if (getTargetRefedCount(sstc.getTarget()) == 1) {
				Table tt = getDBTransformHelper().createCUBRIDTable(sstc, newSQLSchema, this);
				targetTables.remove(getTargetTableSchema(sstc.getTarget()));
				targetTables.add(tt);
			} else {
				Table tt = getTargetTableSchema(sstc.getTarget());
				buildTableColumnCfg(sstc, newSQLSchema, tt, false);
			}
		}
	}

	/**
	 * If source is CUBRID or MySQL, the owner will be set to NULL.
	 * 
	 * It is called before changing catalog of the configuration.
	 * 
	 */
	public void resetSchemaInfo() {
		if (srcCatalog == null || srcCatalog.getDatabaseType().isSupportMultiSchema()) {
			return;
		}
		//Set schema to null and remove duplicated objects
		for (SourceEntryTableConfig setc : expTables) {
			setc.setOwner(null);
		}
		for (SourceViewConfig svc : expViews) {
			svc.setOwner(null);
		}
		for (SourceSequenceConfig ssc : expSerials) {
			ssc.setOwner(null);
		}
	}

	/**
	 * remove SQL configuration
	 * 
	 * @param sstc SourceSQLTableConfig
	 */
	public void rmSQLConfig(SourceSQLTableConfig sstc) {
		expSQLTables.remove(sstc);
		srcSQLSchemas.remove(getSrcSQLSchema(sstc.getName()));
		if (getTargetRefedCount(sstc.getTarget()) == 0) {
			targetTables.remove(getTargetTableSchema(sstc.getTarget()));
		}
	}

	/**
	 * Set all objects to create or not to create
	 * 
	 * @param value status
	 */
	public void setAll(boolean value) {
		for (SourceEntryTableConfig setc : expTables) {
			setc.setCreateNewTable(value);
			setc.setMigrateData(value);
			setc.setCreatePartition(value);
			setc.setCreatePK(value);
			setc.setReplace(value);

			for (SourceColumnConfig scc : setc.getColumnConfigList()) {
				scc.setCreate(value);
				scc.setReplace(value);
			}
			for (SourceFKConfig sfkc : setc.getFKConfigList()) {
				sfkc.setCreate(value);
				sfkc.setReplace(value);
			}
			for (SourceIndexConfig sic : setc.getIndexConfigList()) {
				sic.setCreate(value);
				sic.setReplace(value);
			}
		}

		for (SourceConfig sc : expViews) {
			sc.setCreate(value);
			sc.setReplace(value);
		}

		for (SourceConfig sc : expSerials) {
			sc.setCreate(value);
			sc.setReplace(value);
		}
		
		for (SourceConfig sc : expSynonyms) {
			sc.setCreate(value);
			sc.setReplace(value);
		}
		
		if (!targetIsOnline() || targetDBAGroup) {
			for (SourceConfig sc : expGrants) {
				sc.setCreate(value);
			}
		}

		//Don't clear sqls
		//		if (!value) {
		//			expSQLTables.clear();
		//			srcSQLSchemas.clear();
		//		}
	}

	/**
	 * setAll
	 *
	 * @param ownerName
	 * @param value
	 */
	public void setAll(String ownerName, boolean value) {
		for (SourceEntryTableConfig setc : expTables) {
			if (setc.getOwner().equalsIgnoreCase(ownerName)) {
				setc.setCreateNewTable(value);
				setc.setMigrateData(value);
				setc.setCreatePartition(value);
				setc.setCreatePK(value);
				setc.setReplace(value);

				for (SourceColumnConfig scc : setc.getColumnConfigList()) {
					scc.setCreate(value);
					scc.setReplace(value);
				}
				for (SourceFKConfig sfkc : setc.getFKConfigList()) {
					sfkc.setCreate(value);
					sfkc.setReplace(value);
				}
				for (SourceIndexConfig sic : setc.getIndexConfigList()) {
					sic.setCreate(value);
					sic.setReplace(value);
				}
			}
		}

		for (SourceViewConfig svc : expViews) {
			if (svc.getOwner().equalsIgnoreCase(ownerName)) {
				svc.setCreate(value);
				svc.setReplace(value);
			}
		}

		for (SourceSequenceConfig ssc : expSerials) {
			if (ssc.getOwner().equalsIgnoreCase(ownerName)) {
				ssc.setCreate(value);
				ssc.setReplace(value);
			}
		}
		
		for (SourceSynonymConfig ssyc : expSynonyms) {
			if (ssyc.getOwner().equalsIgnoreCase(ownerName)) {
				ssyc.setCreate(value);
				ssyc.setReplace(value);
			}
		}
		
		if (!targetIsOnline() || targetDBAGroup) {
			for (SourceGrantConfig sgc : expGrants) {
				if (sgc.getOwner().equalsIgnoreCase(ownerName)) {
					sgc.setCreate(value);
				}
			}
		}
	}
	
	/**
	 * @param commitCount the commitCount to set
	 */
	public void setCommitCount(int commitCount) {
		this.commitCount = commitCount;
	}

	public void setCreateConstrainsBeforeData(boolean createConstrainsBeforeData) {
		this.createConstrainsBeforeData = createConstrainsBeforeData;
	}

	/**
	 * Specify whether delete template data file afeter loaddb.
	 * 
	 * @param value true or false
	 */
	public void setDeleteTempFile(boolean value) {
		deleteTempFile = value;
	}

	/**
	 * set Destination Type
	 * 
	 * @param destType integer
	 */
	public void setDestType(int destType) {
		this.destType = destType;
		setMaxCountPerFile(maxCountPerFile);
	}

	/**
	 * set Destination Type
	 * 
	 * @param destName name of the destination type
	 */
	public void setDestTypeName(String destName) {
		if (destName.equalsIgnoreCase("cubrid")) {
			setDestType(MigrationConfiguration.DEST_ONLINE);
		} else if (destName.equalsIgnoreCase("csv")) {
			setDestType(MigrationConfiguration.DEST_CSV);
		} else if (destName.equalsIgnoreCase("sql")) {
			setDestType(MigrationConfiguration.DEST_SQL);
		} else if (destName.equalsIgnoreCase("unload")) {
			setDestType(MigrationConfiguration.DEST_DB_UNLOAD);
		} else if (destName.equalsIgnoreCase("xls")) {
			setDestType(MigrationConfiguration.DEST_XLS);
		} else {
			throw new RuntimeException("Invalid target type in the db.conf of " + destName);
		}
	}

	/**
	 * 
	 * Set exporting to files configuration
	 * 
	 * @param prefix output file's prefix
	 * @param odir output directory
	 * @param charset output file's charset
	 */
	public void setExp2FileOuput(String prefix, String odir, String charset) {
		setFileRepositroyPath(odir);
		setTargetFilePrefix(prefix);
		for (Schema schema : srcCatalog.getSchemas()) {
			addTargetSchemaFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_schema"));
			addTargetTableFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_table"));
			addTargetViewFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_view"));
			addTargetViewQuerySpecFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_view_query_spec"));
			addTargetPkFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_pk"));
			addTargetFkFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_fk"));
			addTargetIndexFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_index"));
			addTargetSerialFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_serial"));
			addTargetDataFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_data" + getDataFileExt()));
			addTargetUpdateStatisticFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_updatestatistic"));
			addTargetSchemaFileListName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_info"));
			addTargetSynonymFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_synonym"));
			addTargetGrantFileName(schema.getName(), PathUtils.mergePath(PathUtils.mergePath(odir, prefix), schema.getName() + "_grant"));
		}
		setTargetCharSet(charset);
	}

	public void setExportNoSupportObjects(boolean value) {
		exportNoSupportObjects = value;
	}

	/**
	 * By default , the import thread count is larger then exporting thread
	 * count
	 * 
	 * 
	 * @param exportThreadCount the exportThreadCount to set
	 */
	public void setExportThreadCount(int exportThreadCount) {
		this.exportThreadCount = exportThreadCount;
	}

	public void setFileRepositroyPath(String fileRepositroyPath) {
		this.fileRepositroyPath = fileRepositroyPath;
	}

	//	/**
	//	 * Change migration configuration's source database schema, and the
	//	 * configuration and target schemas will auto build according to the source
	//	 * database schema. Note that this method may cost much time.
	//	 * 
	//	 * @param sourceDBSchema the sourceDBSchema to set
	//	 * @param reset reset the configuration or not
	//	 */
	//	public void setSourceDBSchema(Schema sourceDBSchema, boolean reset) {
	//		if (sourceDBSchema == null) {
	//			throw new IllegalArgumentException("Schema can't not be null.");
	//		}
	//		this.sourceDBSchema = sourceDBSchema;
	//		if (reset) {
	//			clearAll();
	//		}
	//		this.buildConfigAndTargetSchema(reset);
	//	}

	/**
	 * Set a true if didn't use to count total records before a migration for
	 * showing correct progress.
	 * 
	 * @param useImplicitEstimate boolean
	 */
	public void setImplicitEstimate(boolean useImplicitEstimate) {
		this.implicitEstimate = useImplicitEstimate;
	}

	/**
	 * Set record count per file
	 * 
	 * @param maxCountPerFile integer
	 */
	public void setMaxCountPerFile(int maxCountPerFile) {
		if (destType == DEST_XLS) {
			this.maxCountPerFile = maxCountPerFile > XLS_MAX_COUNT ? XLS_MAX_COUNT
					: maxCountPerFile;
		} else {
			this.maxCountPerFile = maxCountPerFile;
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setOfflineSrcCatalog(Catalog offlineSrcCatalog) {
		this.offlineSrcCatalog = offlineSrcCatalog;
	}

	public void setOneTableOneFile(boolean oneTableOneFile) {
		this.oneTableOneFile = oneTableOneFile;
	}

	public void setPageFetchCount(int pageFetchCount) {
		this.pageFetchCount = pageFetchCount;
	}

	public void setReportLevel(int reportLevel) {
		this.reportLevel = reportLevel;
	}

	/**
	 * @param sourceConParams the sourceConParams to set
	 */
	public void setSourceConParams(ConnParameters sourceConParams) {
		this.sourceConParams = sourceConParams;
		if (sourceConParams != null) {
			setSourceType(sourceConParams.getDatabaseType().getName());
		}
	}

	/**
	 * @param sourceFileEncoding the sourceFileEncoding to set
	 */
	public void setSourceFileEncoding(String sourceFileEncoding) {
		this.sourceFileEncoding = sourceFileEncoding;
	}

	/**
	 * @param sourceFileName the sourceFileName to set
	 */
	public void setSourceFileName(String sourceFileName) {
		this.sourceFileName = sourceFileName;
	}

	/**
	 * @param sourceFileTimeZone the sourceFileTimeZone to set
	 */
	public void setSourceFileTimeZone(String sourceFileTimeZone) {
		this.sourceFileTimeZone = sourceFileTimeZone;
	}

	/**
	 * @param sourceFileVersion the sourceFileVersion to set
	 */
	public void setSourceFileVersion(String sourceFileVersion) {
		this.sourceFileVersion = sourceFileVersion;
	}

	public void setSourceType(int srcType) {
		sourceType = srcType;
	}

	/**
	 * @param dbType the getSourceDBType() to set:xml,sql,mysql,cubrid,oracle
	 */
	public void setSourceType(String dbType) {
		if (SQL.equalsIgnoreCase(dbType)) {
			this.sourceType = MigrationConfiguration.SOURCE_TYPE_SQL;
			return;
		} else if (XML.equalsIgnoreCase(dbType)) {
			this.sourceType = MigrationConfiguration.SOURCE_TYPE_XML_1;
			return;
		} else if (CSV.equalsIgnoreCase(dbType)) {
			this.sourceType = MigrationConfiguration.SOURCE_TYPE_CSV;
			return;
		}
		DatabaseType dt = DatabaseType.getDatabaseTypeIDByDBName(dbType);
		this.sourceType = dt.getID();
	}

	/**
	 * Set SQL files
	 * 
	 * @param sqlFiles to be executed.
	 */
	public void setSqlFiles(List<String> sqlFiles) {
		this.sqlFiles.clear();
		if (CollectionUtils.isEmpty(sqlFiles)) {
			return;
		}
		this.sqlFiles.addAll(sqlFiles);
	}

	/**
	 * Set source catalog
	 * 
	 * @param srcCatalog Catalog
	 * @param reset boolean
	 */
	public void setSrcCatalog(Catalog srcCatalog, boolean reset) {
		if (srcCatalog == null) {
			throw new IllegalArgumentException("Catalog can't not be null.");
		}
		this.srcCatalog = srcCatalog;
		if (reset) {
			clearAll();
		}
		this.buildConfigAndTargetSchema(reset);

	}

	/**
	 * Retrieves the target charset; Empty string will not be returned.
	 * 
	 * @param charset String
	 */
	public void setTargetCharSet(String charset) {
		if (targetIsCSV()) {
			this.csvSettings.setCharset(charset);
		} else if (targetIsFile()) {
			targetCharSet = charset;
		} else if (targetIsOnline()) {
			this.targetConParams.setCharset(charset);
		}
	}

	/**
	 * @param targetConParams the targetConParams to set
	 */
	public void setTargetConParams(ConnParameters targetConParams) {
		this.targetConParams = targetConParams;
	}

	public void setTargetDataFileName(Map<String, String> targetDataFileName) {
		this.targetDataFileName.putAll(targetDataFileName);
	}
	
	public void addTargetDataFileName(String schemaName, String filePath) {
		this.targetDataFileName.put(schemaName, filePath);
	}

	/**
	 * @param targetDBVersion the targetDBVersion to set
	 */
	public void setTargetDBVersion(String targetDBVersion) {
		this.targetDBVersion = targetDBVersion;
	}

	public void setTargetFilePrefix(String targetFilePrefix) {
		this.targetFilePrefix = targetFilePrefix;
	}

	public void setTargetFileTimeZone(String targetFileTimeZone) {
		this.targetFileTimeZone = targetFileTimeZone;
	}
	
	public void setTargetSchemaFileName(Map<String, String> targetSchemaFileName) {
		this.targetSchemaFileName.putAll(targetSchemaFileName);
	}
	
	public void addTargetSchemaFileName(String schemaName, String filePath) {
		this.targetSchemaFileName.put(schemaName, filePath);
	}
	
	public void setTargetTableFileName(Map<String, String> targetTableFileName) {
		this.targetTableFileName.putAll(targetTableFileName);
	}
	
	public void addTargetTableFileName(String schemaName, String filePath) {
		this.targetTableFileName.put(schemaName, filePath);
	}
	
	public void setTargetViewFileName(Map<String, String> targetViewFileName) {
		this.targetViewFileName.putAll(targetViewFileName);
	}
	
	public void addTargetViewFileName(String schemaName, String filePath) {
		this.targetViewFileName.put(schemaName, filePath);
	}
	
	public void setTargetViewQuerySpecFileName(Map<String, String> targetViewQuerySpecFileName) {
		this.targetViewQuerySpecFileName.putAll(targetViewQuerySpecFileName);
	}
	
	public void addTargetViewQuerySpecFileName(String schemaName, String filePath) {
		this.targetViewQuerySpecFileName.put(schemaName, filePath);
	}
	
	public void setTargetPkFileName(Map<String, String> targetPkFileName) {
		this.targetPkFileName.putAll(targetPkFileName);
	}
	
	public void addTargetPkFileName(String schemaName, String filePath) {
		this.targetPkFileName.put(schemaName, filePath);
	}
	
	public void setTargetFkFileName(Map<String, String> targetFkFileName) {
		this.targetFkFileName.putAll(targetFkFileName);
	}
	
	public void addTargetFkFileName(String schemaName, String filePath) {
		this.targetFkFileName.put(schemaName, filePath);
	}

	public void setTargetIndexFileName(Map<String, String> targetIndexFileName) {
		this.targetIndexFileName.putAll(targetIndexFileName);
	}
	
	public void addTargetIndexFileName(String schemaName, String filePath) {
		this.targetIndexFileName.put(schemaName, filePath);
	}
	
	public void setTargetSerialFileName(Map<String, String> targetSerialFileName) {
		this.targetSerialFileName.putAll(targetSerialFileName);
	}
	
	public void addTargetSerialFileName(String schemaName, String filePath) {
		this.targetSerialFileName.put(schemaName, filePath);
	}
	
	public void setTargetUpdateStatisticFileName(Map<String, String> targetUpdateStatisticFileName) {
		this.targetUpdateStatisticFileName.putAll(targetUpdateStatisticFileName);
	}
	
	public void addTargetUpdateStatisticFileName(String schemaName, String filePath) {
		this.targetUpdateStatisticFileName.put(schemaName, filePath);
	}
	
	public void setTargetSchemaFileListName(Map<String, String> targetSchemaFileListName) {
		this.targetSchemaFileListName.putAll(targetSchemaFileListName);
	}
	
	public void addTargetSchemaFileListName(String schemaName, String filePath) {
		this.targetSchemaFileListName.put(schemaName, filePath);
	}
	
	public void setTargetSynonymFileName(Map<String, String> targetSynonymFileName) {
		this.targetSynonymFileName.putAll(targetSynonymFileName);
	}
	
	public void addTargetSynonymFileName(String schemaName, String filePath) {
		this.targetSynonymFileName.put(schemaName, filePath);
	}
	
	public void setTargetGrantFileName(Map<String, String> targetGrantFileName) {
		this.targetGrantFileName.putAll(targetGrantFileName);
	}
	
	public void addTargetGrantFileName(String schemaName, String filePath) {
		this.targetGrantFileName.put(schemaName, filePath);
	}

	/**
	 * Target LOB Root Path will be written into dump files
	 * 
	 * @param text String
	 */
	public void setTargetLOBRootPath(String text) {
		this.targetLOBRootPath = text == null ? "" : text;
		if (StringUtils.isBlank(this.targetLOBRootPath)) {
			return;
		}
		if (this.targetLOBRootPath.endsWith("\\") || this.targetLOBRootPath.endsWith("/")) {
			return;
		}
		this.targetLOBRootPath = this.targetLOBRootPath + "/";
	}
	
	public void setAddUserSchema(boolean addSchema) {
		this.addUserSchema = addSchema;
	}
	
	public void setSplitSchema(boolean isSplit) {
		this.splitSchema = isSplit;
	}
	
	public void setTargetDBAGroup(boolean isDBAGroup) {
		this.targetDBAGroup = isDBAGroup;
	}
	
	public void setCreateUserSQL(boolean createUserSQL) {
		this.createUserSQL = createUserSQL;
	}

	/**
	 * set if write the error records to a sql file.
	 * 
	 * @param isWriteErrorRecords true if write
	 */
	public void setWriteErrorRecords(boolean isWriteErrorRecords) {
		this.isWriteErrorRecords = isWriteErrorRecords;
	}

	/**
	 * Source is csv files
	 * 
	 * @return boolean
	 */
	public boolean sourceIsCSV() {
		return sourceType == MigrationConfiguration.SOURCE_TYPE_CSV;
	}

	/**
	 * If Source is an online database.
	 * 
	 * @return true if the source is an online database
	 */
	public boolean sourceIsOnline() {
		return (sourceType == SOURCE_TYPE_CUBRID) || (sourceType == SOURCE_TYPE_MYSQL)
				|| (sourceType == SOURCE_TYPE_ORACLE) || (sourceType == SOURCE_TYPE_MSSQL)
				|| (sourceType == SOURCE_TYPE_MARIADB)|| (sourceType == SOURCE_TYPE_INFORMIX);
	}

	/**
	 * Source is SQL files
	 * 
	 * @return boolean
	 */
	public boolean sourceIsSQL() {
		return sourceType == MigrationConfiguration.SOURCE_TYPE_SQL;
	}

	/**
	 * Source is XML dump file
	 * 
	 * @return boolean
	 */
	public boolean sourceIsXMLDump() {
		return sourceType == MigrationConfiguration.SOURCE_TYPE_XML_1;
	}

	/**
	 * Is export source to a csv.
	 * 
	 * @return true if export to a dictionary
	 */
	public boolean targetIsCSV() {
		return destType == DEST_CSV;
	}

	/**
	 * Is export source to a db dump.
	 * 
	 * @return true if export to a dictionary
	 */
	public boolean targetIsDBDump() {
		return destType == DEST_DB_UNLOAD;
	}

	/**
	 * Is export source to a dictionary.
	 * 
	 * @return true if export to a dictionary
	 */
	public boolean targetIsFile() {
		return destType == DEST_CSV || destType == DEST_SQL || destType == DEST_XLS
				|| destType == DEST_DB_UNLOAD;
	}

	/**
	 * 
	 * @return the targetDBIsOnline
	 */
	public boolean targetIsOnline() {
		return destType == DEST_ONLINE;
	}

	/**
	 * Is export source to a sql.
	 * 
	 * @return true if export to a dictionary
	 */
	public boolean targetIsSQL() {
		return destType == DEST_SQL;
	}

	/**
	 * Is export source to a xls.
	 * 
	 * @return true if export to a dictionary
	 */
	public boolean targetIsXLS() {
		return destType == DEST_XLS;
	}

	/**
	 * Try to build SQL schema to validate SQL.
	 * 
	 * @param sql String
	 */
	public void validateExpSQLConfig(String sql) {
		//TODO: to check page query's validation
		final Table sqlSchema = getSourceDBType().getMetaDataBuilder().buildSQLTableSchema(
				sourceConParams, sql);
		if (sqlSchema == null || sqlSchema.getColumns().isEmpty()) {
			throw new IllegalArgumentException("Invalid SQL.");
		}
	}

	/**
	 * @return Retrieves true If source is a JDBC connection and can't be
	 *         connected
	 */
	public boolean isSourceOfflineMode() {
		if (!sourceIsOnline()) {
			return false;
		}
		try {
			sourceConParams.createConnection().close();
			return false;
		} catch (Exception ex) {
			return true;
		}
	}

	/**
	 * @return Retrieves true If target is a JDBC connection and can't be
	 *         connected
	 */
	public boolean isTargetOfflineMode() {
		if (!targetIsOnline()) {
			return false;
		}
		try {
			targetConParams.createConnection().close();
			return false;
		} catch (Exception ex) {
			return true;
		}
	}

	public boolean isUpdateStatistics() {
		return updateStatistics;
	}

	public void setUpdateStatistics(boolean updateStatistics) {
		this.updateStatistics = updateStatistics;
	}

	public void setImportThreadCount(int importThreadCount) {
		this.importThreadCount = importThreadCount;
	}

	/**
	 * @return Retrieves the default extend file name of the target schema file.
	 */
	public String getDefaultTargetSchemaFileExtName() {
		if (targetIsDBDump()) {
			return "";
		}
		return ".sql";
	}
	
	public void clearScriptMapping() {
		this.scriptSchemaMapping.clear();
	}

	public Map<String, Schema> getScriptSchemaMapping() {
		return scriptSchemaMapping;
	}

	public void setScriptSchemaMapping(Map<String, Schema> scriptSchemaMapping) {
		this.scriptSchemaMapping = scriptSchemaMapping;
	}
	
	public void addScriptSchemaMapping(String source, Schema schema) {
		scriptSchemaMapping.put(source, schema);
	}
	
	public void setOldScript(boolean isOldSchema) {
		this.isOldScript = isOldSchema;
	}
	
	public boolean isOldScript() {
		return this.isOldScript;
	}
	
	public void setTarSchemaSize(int tarSchemaSize) {
		this.tarSchemaSize = tarSchemaSize;
	}
	
	public int getTarSchemaSize() {
		return this.tarSchemaSize;
	}

	public boolean isTarSchemaDuplicate() {
		return isTarSchemaDuplicate;
	}

	public void setTarSchemaDuplicate(boolean isTarSchemaDuplicate) {
		this.isTarSchemaDuplicate = isTarSchemaDuplicate;
	}
	
	/**
	 * get Schema file full path
	 * 
	 * @param targetSchemaName
	 * @return schema file full path
	 */
	public String getSchemaFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_schema").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Table file full path
	 * 
	 * @param targetSchemaName
	 * @return table file full path
	 */
	public String getTableFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_table").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get View file full path
	 * 
	 * @param targetSchemaName
	 * @return view file full path
	 */
	public String getViewFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_view").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get View Query Specification file full path
	 * 
	 * @param targetSchemaName
	 * @return view query specification file full path
	 */
	public String getViewQuerySpecFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_view_query_spec").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Data file full path
	 * 
	 * @param targetSchemaName
	 * @return data file full path
	 */
	public String getDataFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_data").append(this.getDataFileExt());
	
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Index file full path
	 * 
	 * @param targetSchemaName
	 * @return index file full path
	 */
	public String getIndexFullName(String targetSchemaName) {		
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_index").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Pk file full path
	 * 
	 * @param targetSchemaName
	 * @return pk file full path
	 */
	public String getPkFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_pk").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Fk file full path
	 * 
	 * @param targetSchemaName
	 * @return fk file full path
	 */
	public String getFkFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_fk").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Sequence file full path
	 * 
	 * @param targetSchemaName
	 * @return sequence file full path
	 */
	public String getSequenceFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_serial").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get UpdateStatistic file full path
	 * 
	 * @param targetSchemaName
	 * @return updateStatistic file full path
	 */
	public String getUpdateStatisticFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_updatestatistic").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Info file full path
	 * 
	 * @param targetSchemaName
	 * @return info file full path
	 */
	public String getSchemaFileListFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_info").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get synonym file full path
	 * 
	 * @param targetSchemaName
	 * @return synonym file full path
	 */
	public String getSynonymFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_synonym").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get grant file full path
	 * 
	 * @param targetSchemaName
	 * @return grant file full path
	 */
	public String getGrantFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(this.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_grant").append(
				this.getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(this.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
}
