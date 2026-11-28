/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.core.engine.importer.impl;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.dbobject.DBObject;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.engine.MigrationContext;
import com.cubrid.cubridmigration.core.engine.MigrationDirAndFilesManager;
import com.cubrid.cubridmigration.core.engine.MigrationStatusManager;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.event.ImportRecordsEvent;
import com.cubrid.cubridmigration.core.engine.exception.BreakMigrationException;
import com.cubrid.cubridmigration.core.engine.exception.NormalMigrationException;
import com.cubrid.cubridmigration.core.engine.task.FileMergeRunnable;
import com.cubrid.cubridmigration.core.engine.task.RunnableResultHandler;
import com.cubrid.cubridmigration.cubrid.Data2StrTranslator;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * LoadDBImporter : Use LoadDB and CSQL commands to import database objects.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-8-3 created by Kevin Cao
 */
public class LoadFileImporter extends OfflineImporter {
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

        /** The table this file belongs to, so the final merge can be decided from either event. */
        final SourceTableConfig stc;

        public CurrentDataFileInfo(
                String fileFullName,
                String header,
                String prefix,
                String owner,
                String name,
                String ext,
                String pathID,
                SourceTableConfig stc) {
            this.stc = stc;
            this.fileHeader =
                    header
                            + File.separator
                            + owner
                            + File.separator
                            + "objects"
                            + File.separator
                            + prefix
                            + "_"
                            + pathID
                            + "_"
                            + name;
            this.fileExt = ext;
            this.fileTableFullName = this.fileHeader + ext;
            this.fileFullName = fileFullName;
        }

        /** Create next file. */
        public void nextFile() {
            final StringBuffer sb = new StringBuffer(fileHeader);
            currentFileNO++;
            sb.append("_").append(currentFileNO);
            fileTableFullName = sb.append(fileExt).toString();
            // If has old file ,remove it firstly
            PathUtils.deleteFile(new File(fileTableFullName));
        }
    }

    protected static final Logger LOGGER = LogUtil.getLogger(LoadFileImporter.class);

    private final Map<String, CurrentDataFileInfo> tableFiles =
            new HashMap<String, CurrentDataFileInfo>();
    private final Map<String, String> schemaFiles = new HashMap<String, String>();
    private final Map<String, String> tableSchemaFiles = new HashMap<String, String>();
    private final Map<String, String> viewFiles = new HashMap<String, String>();
    private final Map<String, String> viewQuerySpecFiles = new HashMap<String, String>();
    private final Map<String, String> pkFiles = new HashMap<String, String>();
    private final Map<String, String> fkFiles = new HashMap<String, String>();
    private final Map<String, String> indexFiles = new HashMap<String, String>();
    private final Map<String, String> uniqueIndexFiles = new HashMap<String, String>();
    private final Map<String, String> sequenceFiles = new HashMap<String, String>();
    private final Map<String, String> synonymFiles = new HashMap<String, String>();
    private final Map<String, Map<String, String>> grantFiles =
            new HashMap<String, Map<String, String>>();
    private final Map<String, String> plcsqlProcedureFiles = new HashMap<>();
    private final Map<String, String> plcsqlFunctionFiles = new HashMap<>();

    /** Maps a source's status key (see {@link #sourceKey}) to its key in {@link #tableFiles}. */
    private final Map<String, String> tableKeysBySource = new HashMap<String, String>();

    private final FinalMergeGate finalMergeGate = new FinalMergeGate();

    private final Object lockObj = new Object();

    public LoadFileImporter(MigrationContext mrManager) {
        super(mrManager);
        unloadFileUtil =
                new Data2StrTranslator(
                        mrManager.getDirAndFilesMgr().getMergeFilesDir(),
                        config,
                        mrManager.getDirAndFilesMgr(),
                        config.getDestType());
        final MigrationStatusManager statusMgr = mrManager.getStatusMgr();
        if (statusMgr != null) {
            // The final merge used to be decided only when a batch finished importing, so a table
            // whose last export range finished after its last batch (e.g. the range returned no
            // rows) was never merged. Re-check at that moment too.
            statusMgr.addTableExportFinishedListener(this::onTableExportFinished);
        }
    }

    /**
     * Same key {@link MigrationStatusManager} uses for a source, so a notification can be matched
     * to the table this importer wrote files for.
     */
    private static String sourceKey(String owner, String name) {
        return (owner == null ? "" : owner) + "." + name;
    }

    private String resolveSchemaName(String owner) {
        return config.getSrcCatalog().getDatabaseType().isSupportMultiSchema()
                ? owner
                : config.getSrcConnOwner();
    }

    /**
     * Called when every export range of a source has finished. Does nothing for a source this
     * importer has not written any data file for (no rows, or not a table it handles).
     */
    private void onTableExportFinished(String owner, String source) {
        final String tableKey;
        synchronized (lockObj) {
            tableKey = tableKeysBySource.get(sourceKey(owner, source));
        }
        if (tableKey != null) {
            tryFinalMerge(tableKey);
        }
    }

    /**
     * Runs the table's final merge (or, for XLSX, the final flush) if every export range has
     * finished and every exported record has been imported - and only once per table.
     *
     * <p>Called from both events that can complete that condition: a batch finishing its import,
     * and the last export range finishing.
     *
     * @param tableKey the table's key in {@link #tableFiles}
     */
    private void tryFinalMerge(String tableKey) {
        final CurrentDataFileInfo es;
        final String fileTableFullName;
        final String fileFullName;
        synchronized (lockObj) {
            es = tableFiles.get(tableKey);
            if (es == null) {
                return;
            }
            fileTableFullName = es.fileTableFullName;
            fileFullName = es.fileFullName;
        }
        final SourceTableConfig stc = es.stc;
        final MigrationStatusManager sm = mrManager.getStatusMgr();
        final String owner = stc.getOwner();
        final String name = stc.getName();
        if (config.targetIsCSV()
                || config.targetIsXLS()
                || config.targetIsXLSX()
                || config.isOneTableOneFile()) {
            if (config.targetIsXLSX()
                    && finalMergeGate.tryAcquire(
                            tableKey,
                            sm.getExpFlag(owner, name),
                            sm.getExpCount(owner, name),
                            sm.getImpCount(owner, name))) {
                FileMergeRunnable.flushAndCloseXLSX(fileTableFullName);
            }
            return;
        }
        final Table st;
        if (stc instanceof SourceSQLTableConfig) {
            st = config.getSrcSQLSchema(name);
        } else {
            st = config.getSrcTableSchema(owner, name);
        }
        if (null == st) {
            return;
        }
        // If it is the last merging, merge data files to one data file
        if (finalMergeGate.tryAcquire(
                tableKey,
                sm.getExpFlag(owner, name),
                sm.getExpCount(owner, name),
                sm.getImpCount(owner, name))) {
            executeTask(fileTableFullName, fileFullName, null, true, false);
        }
    }

    /**
     * Execute merge file tasks
     *
     * @param sourceFile is file to be read.
     * @param targetFile specify schema or data
     * @param listener call back method
     * @param deleteFile delete file after task finished
     * @param isSchemaFile true if file is schema file
     */
    private void executeTask(
            String sourceFile,
            String targetFile,
            RunnableResultHandler listener,
            boolean deleteFile,
            boolean isSchemaFile) {
        cmTaskService.execute(
                new FileMergeRunnable(
                        sourceFile,
                        targetFile,
                        config.getTargetCharSet(),
                        listener,
                        deleteFile,
                        isSchemaFile || (!config.targetIsXLS() && !config.targetIsXLSX())));
    }

    /** Ensure header exists at the top of a target data file. */
    private void ensureHeaderPresent(String fileFullName, SourceTableConfig stc) {
        if (config.targetIsCSV()
                || config.targetIsXLS()
                || config.targetIsXLSX()
                || config.targetIsSQL()) {
            return;
        }
        File target = new File(fileFullName);
        try {
            if (!target.exists()) {
                PathUtils.createFile(target);
            } else if (target.length() > 0L) {
                return;
            }
            String header = getDataFileHeader(stc);
            CUBRIDIOUtils.writeLines(
                    target, new String[] {header}, config.getTargetCharSet(), false);
        } catch (IOException ex) {
            throw new BreakMigrationException(ex);
        }
    }

    /**
     * Send schema file and data file to server for loadDB command.
     *
     * @param fileName the file to be sent.
     * @param stc source table configuration.
     * @param impCount the count of records in file.
     * @param expCount exported record count
     */
    protected void handleDataFile(
            String fileName, final SourceTableConfig stc, final int impCount, final int expCount) {
        synchronized (lockObj) {
            MigrationDirAndFilesManager mdfm = mrManager.getDirAndFilesMgr();
            String schemaName;
            String mergeDir;

            schemaName = resolveSchemaName(stc.getOwner());
            mergeDir = mdfm.getMergeFilesDir();
            final String tableKey = schemaName + stc.getName();
            tableKeysBySource.put(sourceKey(stc.getOwner(), stc.getName()), tableKey);

            tableFiles.computeIfAbsent(
                    tableKey,
                    key ->
                            new CurrentDataFileInfo(
                                    config.getTargetDataFileName(schemaName),
                                    mergeDir,
                                    config.getTargetFilePrefix(),
                                    schemaName,
                                    stc.getName(),
                                    config.getDataFileExt(),
                                    stc.getPathID(schemaName),
                                    stc));

            CurrentDataFileInfo es = tableFiles.get(tableKey);

            // If the target file is full.
            if (mdfm.isDataFileFull(es.fileTableFullName, impCount)) {
                String prevFile = es.fileTableFullName;
                // Full name will be changed.
                es.nextFile();
                if (config.targetIsXLSX()) {
                    FileMergeRunnable.flushAndCloseXLSX(prevFile);
                }
            }
            final String fileTableFullName = es.fileTableFullName;
            ensureHeaderPresent(fileTableFullName, stc);
            mdfm.addDataFile(fileTableFullName, impCount);
            executeTask(
                    fileName,
                    fileTableFullName,
                    new RunnableResultHandler() {

                        public void success() {
                            eventHandler.handleEvent(new ImportRecordsEvent(stc, impCount));
                            final MigrationStatusManager sm = mrManager.getStatusMgr();
                            sm.addImpCount(stc.getOwner(), stc.getName(), expCount);
                            // If this was the last batch, merge the data files into one.
                            tryFinalMerge(tableKey);
                        }

                        public void failed(String error) {
                            mrManager
                                    .getStatusMgr()
                                    .addImpCount(stc.getOwner(), stc.getName(), expCount);
                            eventHandler.handleEvent(
                                    new ImportRecordsEvent(
                                            stc,
                                            impCount,
                                            new NormalMigrationException(error),
                                            null));
                            // A failed last batch still completes the counts; the batches that did
                            // succeed must not be left unmerged because of it.
                            tryFinalMerge(tableKey);
                        }
                    },
                    config.isDeleteTempFile(),
                    false);
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

    /**
     * Send View Query Specification file to server loadDB command.
     *
     * @param owner
     * @return view Query Specification file pull path
     */
    protected String handleViewQuerySpecFile(String owner) {
        if (!viewQuerySpecFiles.containsKey(owner)) {
            viewQuerySpecFiles.put(owner, config.getTargetViewQuerySpecFileName(owner));
        }
        return viewQuerySpecFiles.get(owner);
    }

    /**
     * Send PK file to server loadDB command.
     *
     * @param owner
     * @return PK file pull path
     */
    protected String handlePkFile(String owner) {
        if (!pkFiles.containsKey(owner)) {
            pkFiles.put(owner, config.getTargetPkFileName(owner));
        }
        return pkFiles.get(owner);
    }

    /**
     * Send FK file to server loadDB command.
     *
     * @param owner
     * @return FK file pull path
     */
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
     * Send Unique Index file to server loadDB command.
     *
     * @param owner
     * @return unique index file full path
     */
    protected String handleUniqueIndexFile(String owner) {
        if (!uniqueIndexFiles.containsKey(owner)) {
            uniqueIndexFiles.put(owner, config.getTargetUniqueIndexFileName(owner));
        }
        return uniqueIndexFiles.get(owner);
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
    protected String handleGrantFile(String owner, String sourceObjectOwner) {
        if (!grantFiles.containsKey(owner)) {
            grantFiles.put(owner, config.getTargetGrantFileName(owner));
        }
        return grantFiles.get(owner).get(sourceObjectOwner);
    }

    /**
     * Send plcsql Procedure file to server loadDB command.
     *
     * @param owner
     * @return procedure file full path
     */
    protected String handlePlcsqlProcedureFile(String owner, String objectName) {
        return plcsqlProcedureFiles.computeIfAbsent(
                owner + objectName,
                key -> config.getTargetPlcsqlProcedureFileName(owner).get(objectName));
    }

    /**
     * Send plcsql Function file to server loadDB command.
     *
     * @param owner
     * @return function file full path
     */
    protected String handlePlcsqlFunctionFile(String owner, String objectName) {
        return plcsqlFunctionFiles.computeIfAbsent(
                owner + objectName,
                key -> config.getTargetPlcsqlFunctionFileName(owner).get(objectName));
    }

    /**
     * Send schema file and data file to server for loadDB command.
     *
     * @param fileName the file to be sent.
     * @param tableName tableName
     */
    protected void sendLOBFile(String fileName, String tableName) {
        /// home/cmt/CUBRID/database/mt/log/blob1/blob.xxxxxxx
        String targetFile = getLOBDir(tableName) + new File(fileName).getName();
        // Copy to target if it is local
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
    protected void sendSchemaFile(
            String fileName,
            RunnableResultHandler listener,
            String objectType,
            String owner,
            String sourceObjectOwner,
            String objectName) {
        executeTask(
                fileName,
                getFilePath(objectType, owner, sourceObjectOwner, objectName),
                listener,
                config.isDeleteTempFile(),
                true);
    }

    private String getFilePath(
            String objectType, String owner, String sourceObjectOwner, String objectName) {
        if (config.isSplitSchema()) {
            if (objectType.equals(DBObject.OBJ_TYPE_TABLE)) {
                return handleTableSchemaFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_VIEW)) {
                return handleViewFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_VIEW_QUERY_SPEC)) {
                return handleViewQuerySpecFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_PK)) {
                return handlePkFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_FK)) {
                return handleFkFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_INDEX)) {
                return handleIndexFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_UNIQUE_INDEX)) {
                return handleUniqueIndexFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_SEQUENCE)) {
                return handleSequenceFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_SYNONYM)) {
                return handleSynonymFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_GRANT)) {
                return handleGrantFile(owner, sourceObjectOwner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_PLCSQL_PROCEDURE)) {
                return handlePlcsqlProcedureFile(owner, objectName);
            } else if (objectType.equals(DBObject.OBJ_TYPE_PLCSQL_FUNCTION)) {
                return handlePlcsqlFunctionFile(owner, objectName);
            }
        } else {
            if (objectType.equals(DBObject.OBJ_TYPE_TABLE)
                    || objectType.equals(DBObject.OBJ_TYPE_VIEW)
                    || objectType.equals(DBObject.OBJ_TYPE_VIEW_QUERY_SPEC)
                    || objectType.equals(DBObject.OBJ_TYPE_SEQUENCE)
                    || objectType.equals(DBObject.OBJ_TYPE_SYNONYM)
                    || objectType.equals(DBObject.OBJ_TYPE_GRANT)
                    || objectType.equals(DBObject.OBJ_TYPE_PLCSQL_PROCEDURE)
                    || objectType.equals(DBObject.OBJ_TYPE_PLCSQL_FUNCTION)) {
                return handleSchemaFile(owner);
            } else if (objectType.equals(DBObject.OBJ_TYPE_INDEX)
                    || objectType.equals(DBObject.OBJ_TYPE_PK)
                    || objectType.equals(DBObject.OBJ_TYPE_FK)) {
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
