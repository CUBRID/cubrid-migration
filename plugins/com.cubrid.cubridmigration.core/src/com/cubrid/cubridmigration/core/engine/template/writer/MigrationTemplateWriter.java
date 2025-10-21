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
package com.cubrid.cubridmigration.core.engine.template.writer;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.*;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.TextFileUtils;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceFKConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceGrantConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceIndexConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlFunctionConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlProcedureConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceViewConfig;
import com.cubrid.cubridmigration.core.engine.template.TemplateTags;
import com.cubrid.cubridmigration.cubrid.CUBRIDDatabase;
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;

public final class MigrationTemplateWriter {
    private static final Logger log = LogUtil.getLogger(MigrationTemplateWriter.class);

    private MigrationTemplateWriter() {}

    public static void save(MigrationConfiguration config, String fileName, boolean saveSchema) {
        XMLStreamWriter writer = null;
        try {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            writer =
                    new IndentingXMLStreamWriter(
                            factory.createXMLStreamWriter(new FileOutputStream(fileName), UTF_8));

            writer.writeStartDocument(UTF_8, "1.0");
            writer.writeStartElement(TAG_MIGRATION);
            writeMigrationAttributes(writer, config);
            writeSourceNode(writer, config, saveSchema);
            writeTargetNode(writer, config, saveSchema);
            writeParametersNode(writer, config);

            writer.writeEndElement(); // </migration>
            writer.writeEndDocument();
        } catch (Exception e) {
            log.error("Failed to save migration script to file: " + fileName, e);
            throw new RuntimeException("Failed to save migration script.", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (XMLStreamException e) {
                    log.error("Error closing XMLStreamWriter", e);
                }
            }
        }
    }

    private static void writeMigrationAttributes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        writer.writeAttribute(ATTR_NAME, config.getName());
        writer.writeAttribute(ATTR_VERSION, "11.1.0");
        writer.writeAttribute(ATTR_WIZARD_START_DATE_TIME, config.getWizardStartDateTime());
    }

    private static void writeParametersNode(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeEmptyElement(TAG_PARAMS);
        writer.writeAttribute(ATTR_EXPORT_THREAD, String.valueOf(config.getExportThreadCount()));
        writer.writeAttribute(ATTR_IMPORT_THREAD, String.valueOf(config.getImportThreadCount()));
        writer.writeAttribute(ATTR_COMMIT_COUNT, String.valueOf(config.getCommitCount()));
        writer.writeAttribute(ATTR_PAGE_FETCH_COUNT, String.valueOf(config.getPageFetchCount()));
        writer.writeAttribute(
                ATTR_IMPLICIT_ESTIMATE_PROGRESS, getBooleanString(config.isImplicitEstimate()));
        writer.writeAttribute(
                ATTR_UPDATE_STATISTICS, getBooleanString(config.isUpdateStatistics()));

        if (config.hasOtherParam()) {
            String s1 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIME);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_TIME, s1 == null ? "" : s1);
            String s2 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_DATE);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_DATE, s2 == null ? "" : s2);
            String s3 = config.getOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
            writer.writeAttribute(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP, s3 == null ? "" : s3);
            String s4 = config.getOtherParam(MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
            writer.writeAttribute(MySQL2CUBRIDMigParas.REPLAXE_CHAR0, s4 == null ? "" : s4);
        }
    }

    private static void writeSourceNode(
            XMLStreamWriter writer, MigrationConfiguration config, boolean saveSchema)
            throws XMLStreamException, IOException {
        writer.writeStartElement(TAG_SOURCE);
        writer.writeAttribute(ATTR_DB_TYPE, config.getSourceTypeName());
        writer.writeAttribute(ATTR_ONLINE, getBooleanString(config.sourceIsOnline()));
        if (config.sourceIsOnline() && config.getSourceDBType().equals(DatabaseType.CUBRID)) {
            writer.writeAttribute(ATTR_VERSION, String.valueOf(CUBRIDDatabase.dbVersion));
        }

        if (config.sourceIsOnline()) {
            writeOnlineSource(writer, config, saveSchema);
        } else if (config.sourceIsSQL()) {
            writeSQLSource(writer, config);
        } else if (config.sourceIsXMLDump()) {
            writeXMLDumpSource(writer, config);
        } else if (config.sourceIsCSV()) {
            writeCSVSource(writer, config);
        }

        writer.writeEndElement(); // </source>
    }

    private static void writeOnlineSource(
            XMLStreamWriter writer, MigrationConfiguration config, boolean saveSchema)
            throws XMLStreamException, IOException {
        writeSourceJDBCNode(writer, config);

        if (saveSchema) {
            writeSourceSchemaNode(writer, config);
        }

        writeSourceSchemaMapping(writer, config);
        writeSourceTables(writer, config);
        writeSourceSQLTables(writer, config);
        writeSourceSequences(writer, config);
        writeSourceSynonyms(writer, config);
        writeSourceViews(writer, config);
        writeSourceGrants(writer, config);
        writeSourceTriggers(writer, config);
        writeSourceFunctions(writer, config);
        writeSourceProcedures(writer, config);
        writeSourcePlcsqlFunctions(writer, config);
        writeSourcePlcsqlProcedures(writer, config);
    }

    private static void writeSQLSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TemplateTags.TAG_SQL);
        writer.writeAttribute(TemplateTags.ATTR_CHARSET, config.getSourceFileEncoding());
        List<String> files = config.getSqlFiles();
        for (String file : files) {
            writer.writeEmptyElement(TemplateTags.TAG_SQL_FILE);
            writer.writeAttribute(TemplateTags.ATTR_LOCATION, file);
        }
        writer.writeEndElement(); // </sql>
    }

    private static void writeXMLDumpSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeEmptyElement(TemplateTags.TAG_FILE);
        writer.writeAttribute(TemplateTags.ATTR_LOCATION, config.getSourceFileName());
        writer.writeAttribute(TemplateTags.ATTR_CHARSET, config.getSourceFileEncoding());
        writer.writeAttribute(TemplateTags.ATTR_TIMEZONE, config.getSourceFileTimeZone());
        writer.writeAttribute(TemplateTags.ATTR_VERSION, config.getSourceFileVersion());
    }

    private static void writeCSVSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TemplateTags.TAG_CSVS);
        writer.writeAttribute(
                TemplateTags.ATTR_CSV_SEPARATE,
                config.getCsvSettings().getSeparateChar() == MigrationConfiguration.CSV_NO_CHAR
                        ? ""
                        : String.valueOf(config.getCsvSettings().getSeparateChar()));
        writer.writeAttribute(
                TemplateTags.ATTR_CSV_QUOTE,
                config.getCsvSettings().getQuoteChar() == MigrationConfiguration.CSV_NO_CHAR
                        ? ""
                        : String.valueOf(config.getCsvSettings().getQuoteChar()));
        writer.writeAttribute(
                TemplateTags.ATTR_CSV_ESCAPE,
                config.getCsvSettings().getEscapeChar() == MigrationConfiguration.CSV_NO_CHAR
                        ? ""
                        : String.valueOf(config.getCsvSettings().getEscapeChar()));
        StringBuilder sb = new StringBuilder();
        for (String ns : config.getCsvSettings().getNullStrings()) {
            if (sb.length() > 0) {
                sb.append(";");
            }
            sb.append(ns);
        }
        if (sb.length() > 0) {
            writer.writeAttribute(TemplateTags.ATTR_CSV_NULL_VALUE, sb.toString());
        }
        writer.writeAttribute(
                TemplateTags.ATTR_CHARSET, String.valueOf(config.getCsvSettings().getCharset()));
        List<SourceCSVConfig> csvFiles = config.getCSVConfigs();
        for (SourceCSVConfig scc : csvFiles) {
            writer.writeStartElement(TemplateTags.TAG_CSV);
            writer.writeAttribute(TemplateTags.ATTR_NAME, scc.getName());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, scc.getTarget());
            writer.writeAttribute(TemplateTags.ATTR_CREATE, getBooleanString(scc.isCreate()));
            writer.writeAttribute(TemplateTags.ATTR_REPLACE, getBooleanString(scc.isReplace()));
            writer.writeAttribute(
                    TemplateTags.ATTR_IMPORT_FIRST_ROW, getBooleanString(scc.isImportFirstRow()));
            writer.writeStartElement(TemplateTags.TAG_CSV_COLUMNS);
            for (SourceCSVColumnConfig sccc : scc.getColumnConfigs()) {
                writer.writeEmptyElement(TemplateTags.TAG_CSV_COLUMN);
                writer.writeAttribute(TemplateTags.ATTR_NAME, sccc.getName());
                writer.writeAttribute(TemplateTags.ATTR_TARGET, sccc.getTarget());
                writer.writeAttribute(TemplateTags.ATTR_CREATE, getBooleanString(sccc.isCreate()));
            }
            writer.writeEndElement(); // </csv_columns>
            writer.writeEndElement(); // </csv>
        }
        writer.writeEndElement(); // </csvs>
    }

    private static void writeSourceJDBCNode(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        ConnParameters scp = config.getSourceConParams();
        if (scp == null) {
            return;
        }
        writer.writeEmptyElement(TAG_JDBC);
        writer.writeAttribute(ATTR_HOST, scp.getHost());
        writer.writeAttribute(ATTR_PORT, String.valueOf(scp.getPort()));
        writer.writeAttribute(ATTR_DRIVER, scp.getDriverFileName());
        writer.writeAttribute(ATTR_NAME, scp.getDbName());
        writer.writeAttribute(ATTR_USER, scp.getConUser());
        writer.writeAttribute(ATTR_PASSWORD, scp.getConPassword());
        writer.writeAttribute(ATTR_CHARSET, scp.getCharset());
        writer.writeAttribute(ATTR_TIMEZONE, scp.getTimeZone());
        writer.writeAttribute(ATTR_USER_JDBC_URL, scp.getUserJDBCURL());
    }

    private static void writeSourceSchemaNode(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException, IOException {
        Catalog srcCatalog = config.getSrcCatalog();
        if (srcCatalog != null) {
            writer.writeStartElement(TemplateTags.TAG_SCHEMA);
            try {
                File tempFile = new File(PathUtils.getBaseTempDir() + UUID.randomUUID());
                srcCatalog.saveXML(tempFile);
                String schemaXML =
                        TextFileUtils.readText(
                                tempFile.getCanonicalPath(), UTF_8, Integer.MAX_VALUE);
                PathUtils.deleteFile(tempFile);
                writer.writeCData(schemaXML);
            } catch (Exception e) {
                log.error("Failed to write source shcmea", e);
                throw new IOException("Failed to write source shcmea", e);
            }
            writer.writeEndElement(); // </schema>
        }
        List<Table> srcSQLTables = config.getSrcSQLSchema2Exp();
        if (CollectionUtils.isNotEmpty(srcSQLTables)) {
            writer.writeStartElement(TemplateTags.TAG_SQL_SCHEMA);
            try {
                File tempFile = new File(PathUtils.getBaseTempDir() + UUID.randomUUID());
                Catalog sqlCatalog = new Catalog();
                sqlCatalog.setName("sql_catalog");
                Schema sqlSchema = new Schema();
                sqlCatalog.addSchema(sqlSchema);
                sqlSchema.setName("sql_schema");
                sqlSchema.setTables(srcSQLTables);
                sqlCatalog.saveXML(tempFile);
                String schemaXML =
                        TextFileUtils.readText(
                                tempFile.getCanonicalPath(), UTF_8, Integer.MAX_VALUE);
                PathUtils.deleteFile(tempFile);
                writer.writeCData(schemaXML);
            } catch (Exception e) {
                log.error("Failed to write SQL source shcmea", e);
                throw new IOException("Failed to write SQL source schema.", e);
            }
            writer.writeEndElement(); // </sql_schema>
        }
    }

    private static void writeSourceSchemaMapping(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        writer.writeStartElement(TAG_SCHEMAS);
        Catalog srcCatalog = config.getSrcCatalog();
        if (srcCatalog != null) {
            for (Schema schema : srcCatalog.getSchemas()) {
                writer.writeEmptyElement(TAG_SCHEMA_INFO);
                writer.writeAttribute(ATTR_SCHEMA_NAME, schema.getName());
                writer.writeAttribute(ATTR_TARGET_SCHEMA, schema.getTargetSchemaName());
            }
        } else {
            config.getScriptSchemaMapping()
                    .forEach(
                            (schemaName, schema) -> {
                                try {
                                    writer.writeEmptyElement(TAG_SCHEMA_INFO);
                                    writer.writeAttribute(ATTR_SCHEMA_NAME, schema.getName());
                                    writer.writeAttribute(
                                            ATTR_TARGET_SCHEMA, schema.getTargetSchemaName());
                                } catch (XMLStreamException e) {
                                    throw new RuntimeException(e);
                                }
                            });
        }
        writer.writeEndElement(); // </schemas>
    }

    private static void writeSourceTables(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceEntryTableConfig> exportEntryTables = config.getExpEntryTableCfg();
        if (exportEntryTables.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_TABLES);
        for (SourceEntryTableConfig setc : exportEntryTables) {
            writer.writeStartElement(TAG_TABLE);
            writer.writeAttribute(ATTR_NAME, setc.getName());
            writer.writeAttribute(ATTR_OWNER, setc.getOwner());
            writer.writeAttribute(ATTR_TARGET, setc.getTarget());
            writer.writeAttribute(ATTR_TARGET_SCHEMA, setc.getTargetOwner());
            writer.writeAttribute(ATTR_CHANGE_NAME, getBooleanString(setc.isChangeTableName()));
            writer.writeAttribute(ATTR_CREATE, getBooleanString(setc.isCreateNewTable()));
            writer.writeAttribute(ATTR_MIGRATE_DATA, getBooleanString(setc.isMigrateData()));
            writer.writeAttribute(ATTR_REPLACE, getBooleanString(setc.isReplace()));
            writer.writeAttribute(ATTR_PK, getBooleanString(setc.isCreatePK()));
            writer.writeAttribute(ATTR_PARTITION, getBooleanString(setc.isCreatePartition()));
            writer.writeAttribute(ATTR_CONDITION, setc.getCondition());
            writer.writeAttribute(ATTR_BEFORE_SQL, setc.getSqlBefore());
            writer.writeAttribute(ATTR_AFTER_SQL, setc.getSqlAfter());
            if (setc.isEnableExpOpt()) {
                writer.writeAttribute(ATTR_EXP_OPT_COL, getBooleanString(setc.isEnableExpOpt()));
                writer.writeAttribute(
                        ATTR_START_TAR_MAX, getBooleanString(setc.isStartFromTargetMax()));
            }
            writer.writeAttribute(ATTR_COMMENT, setc.getComment());

            List<SourceColumnConfig> columnConfigList = setc.getColumnConfigList();
            writer.writeStartElement(TAG_COLUMNS);
            for (SourceColumnConfig scc : columnConfigList) {
                writer.writeEmptyElement(TAG_COLUMN);
                writer.writeAttribute(ATTR_NAME, scc.getName());
                writer.writeAttribute(ATTR_TARGET, scc.getTarget());
                writer.writeAttribute(ATTR_TRIM, getBooleanString(scc.isNeedTrim()));
                writer.writeAttribute(ATTR_REPLACE_EXPRESSION, scc.getReplaceExp());
                writer.writeAttribute(ATTR_USER_DATA_HANDLER, scc.getUserDataHandler());
                writer.writeAttribute(ATTR_COMMENT, scc.getComment());
            }
            writer.writeEndElement(); // </columns>

            List<SourceIndexConfig> indexConfigList = setc.getIndexConfigList();
            List<SourceFKConfig> fkConfigList = setc.getFKConfigList();
            if (!indexConfigList.isEmpty() || !fkConfigList.isEmpty()) {
                writer.writeStartElement(TAG_CONSTRAINTS);
                for (SourceFKConfig fkc : fkConfigList) {
                    writer.writeEmptyElement(TAG_FK);
                    writer.writeAttribute(ATTR_NAME, fkc.getName());
                    writer.writeAttribute(ATTR_TARGET, fkc.getTarget());
                }
                for (SourceIndexConfig sic : indexConfigList) {
                    writer.writeEmptyElement(TAG_INDEX);
                    writer.writeAttribute(ATTR_NAME, sic.getName());
                    writer.writeAttribute(ATTR_TARGET, sic.getTarget());
                }
                writer.writeEndElement(); // </constraints>
            }
            writer.writeEndElement(); // </table>
        }
        writer.writeEndElement(); // </tables>
    }

    private static void writeSourceSQLTables(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceSQLTableConfig> exportSQLTables = config.getExpSQLCfg();
        if (exportSQLTables.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_SQLTABLES);
        for (SourceSQLTableConfig sstc : exportSQLTables) {
            writer.writeStartElement(TAG_SQLTABLE);
            writer.writeAttribute(ATTR_TARGET_SCHEMA, sstc.getTargetOwner());
            writer.writeAttribute(ATTR_NAME, sstc.getName());
            writer.writeAttribute(ATTR_CREATE, getBooleanString(sstc.isCreateNewTable()));
            writer.writeAttribute(ATTR_REPLACE, getBooleanString(sstc.isReplace()));
            writer.writeAttribute(ATTR_MIGRATE_DATA, getBooleanString(sstc.isMigrateData()));
            writer.writeAttribute(ATTR_TARGET, sstc.getTarget());

            writer.writeStartElement(TAG_STATEMENT);
            writer.writeCharacters(sstc.getSql());
            writer.writeEndElement();

            writer.writeStartElement(TAG_COLUMNS);
            for (SourceColumnConfig scc : sstc.getColumnConfigList()) {
                writer.writeEmptyElement(TAG_COLUMN);
                writer.writeAttribute(ATTR_NAME, scc.getName());
                writer.writeAttribute(ATTR_TARGET, scc.getTarget());
                writer.writeAttribute(ATTR_TRIM, getBooleanString(scc.isNeedTrim()));
                writer.writeAttribute(ATTR_REPLACE_EXPRESSION, scc.getReplaceExp());
                writer.writeAttribute(ATTR_USER_DATA_HANDLER, scc.getUserDataHandler());
            }
            writer.writeEndElement(); // </columns>
            writer.writeEndElement(); // </sqltable>
        }
        writer.writeEndElement(); // </sqltables>
    }

    private static void writeSourceSequences(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceSequenceConfig> exportSerials = config.getExpSerialCfg();
        if (exportSerials.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_SEQUENCES);

        for (SourceSequenceConfig sc : exportSerials) {
            writer.writeEmptyElement(TemplateTags.TAG_SEQUENCE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(
                    TemplateTags.ATTR_AUTO_SYNCHRONIZE_START_VALUE,
                    getBooleanString(sc.isAutoSynchronizeStartValue()));
        }
        writer.writeEndElement(); // </sequences>
    }

    private static void writeSourceSynonyms(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceSynonymConfig> exportSynonyms = config.getExpSynonymCfg();
        if (exportSynonyms.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_SYNONYMS);
        for (SourceSynonymConfig sc : exportSynonyms) {
            writer.writeEmptyElement(TemplateTags.TAG_SYNONYM);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_SYNONYM_OBJECT, sc.getObjectName());
            writer.writeAttribute(TemplateTags.ATTR_SYNONYM_OBJECT_OWNER, sc.getObjectOwner());
            writer.writeAttribute(
                    TemplateTags.ATTR_SYNONYM_OBJECT_TARGET, sc.getObjectTargetName());
            writer.writeAttribute(
                    TemplateTags.ATTR_SYNONYM_OBJECT_TARGET_OWNER, sc.getObjectTargetOwner());
        }
        writer.writeEndElement(); // </synonyms>
    }

    private static void writeSourceViews(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceViewConfig> exportViews = config.getExpViewCfg();
        if (exportViews.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_VIEWS);
        for (SourceViewConfig sc : exportViews) {
            writer.writeEmptyElement(TemplateTags.TAG_VIEW);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_COMMENT, sc.getComment());
        }
        writer.writeEndElement(); // </views>
    }

    private static void writeSourceGrants(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceGrantConfig> exportGrants = config.getExpGrantCfg();
        if (exportGrants.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_GRANTS);
        for (SourceGrantConfig sc : exportGrants) {
            writer.writeEmptyElement(TemplateTags.TAG_GRANT);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_GRANTOR, sc.getGrantorName());
            writer.writeAttribute(TemplateTags.ATTR_GRANTEE, sc.getGranteeName());
            writer.writeAttribute(TemplateTags.ATTR_OBJECT_NAME, sc.getClassName());
            writer.writeAttribute(TemplateTags.ATTR_OBJECT_OWNER, sc.getClassOwner());
            writer.writeAttribute(TemplateTags.ATTR_AUTH_TYPE, sc.getAuthType());
            writer.writeAttribute(TemplateTags.ATTR_GRANTABLE, getBooleanString(sc.isGrantable()));
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_GRANTOR_NAME, sc.getSourceGrantorName());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_OBJECT_OWNER, sc.getSourceObjectOwner());
        }
        writer.writeEndElement(); // </grants>
    }

    private static void writeSourceTriggers(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportTriggers = config.getExpTriggerCfg();
        if (exportTriggers.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_TRIGGERS);
        for (String sc : exportTriggers) {
            writer.writeEmptyElement(TemplateTags.TAG_TRIGGER);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </triggers>
    }

    private static void writeSourceFunctions(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportFunctions = config.getExpFunctionCfg();
        if (exportFunctions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_FUNCTIONS);
        for (String sc : exportFunctions) {
            writer.writeEmptyElement(TemplateTags.TAG_FUNCTION);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </functions>
    }

    private static void writeSourceProcedures(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportProcedures = config.getExpProcedureCfg();
        if (exportProcedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_PROCEDURES);
        for (String sc : exportProcedures) {
            writer.writeEmptyElement(TemplateTags.TAG_PROCEDURE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </procedures>
    }

    private static void writeSourcePlcsqlFunctions(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<SourcePlcsqlFunctionConfig> functions = config.getExpPlcsqlFunctionCfg();
        if (functions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_PLCSQL_FUNCTIONS);
        for (SourcePlcsqlFunctionConfig sfc : functions) {
            writer.writeEmptyElement(TemplateTags.TAG_PLCSQL_FUNCTION);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sfc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sfc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, sfc.getTarget());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, sfc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_AUTH_ID, sfc.getAuthid());
            writer.writeAttribute(
                    TemplateTags.ATTR_AUTH_ID_CHANGED, getBooleanString(sfc.isAuthidChanged()));
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_DDL, sfc.getSourceDDL());
        }
        writer.writeEndElement(); // </plcsql_functions>
    }

    private static void writeSourcePlcsqlProcedures(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<SourcePlcsqlProcedureConfig> procedures = config.getExpPlcsqlProcedureCfg();
        if (procedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_PLCSQL_PROCEDURES);
        for (SourcePlcsqlProcedureConfig spc : procedures) {
            writer.writeEmptyElement(TemplateTags.TAG_PLCSQL_PROCEDURE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, spc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, spc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET, spc.getTarget());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, spc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_AUTH_ID, spc.getAuthid());
            writer.writeAttribute(
                    TemplateTags.ATTR_AUTH_ID_CHANGED, getBooleanString(spc.isAuthidChagned()));
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_DDL, spc.getSourceDDL());
        }
        writer.writeEndElement(); // </plcsql_procedures>
    }

    // target

    private static void writeTargetNode(
            XMLStreamWriter writer, MigrationConfiguration config, boolean saveSchema)
            throws XMLStreamException {
        writer.writeStartElement(TemplateTags.TAG_TARGET);
        writer.writeAttribute(TemplateTags.ATTR_VERSION, config.getTargetDBVersion());
        if (config.targetIsOnline()) {
            writer.writeAttribute(TemplateTags.ATTR_TYPE, TemplateTags.VALUE_ONLINE);
        } else if (config.targetIsFile()) {
            writer.writeAttribute(TemplateTags.ATTR_TYPE, TemplateTags.VALUE_DIR);
        } else {
            writer.writeAttribute(TemplateTags.ATTR_TYPE, TemplateTags.VALUE_OFFLINE);
        }
        writer.writeAttribute(TemplateTags.ATTR_DB_TYPE, "cubrid");

        writeTargetConInfoNode(writer, config);

        if (saveSchema) {
            writer.writeEndElement();
            return;
        }

        writeTargetSchemaNodes(writer, config);
        writeTargetTableNodes(writer, config);
        writeTargetSequenceNodes(writer, config);
        writeTargetViewNodes(writer, config);
        writeTargetSynonymNodes(writer, config);
        writeTargetPlcsqlProcedureNodes(writer, config);
        writeTargetPlcsqlFunctionNodes(writer, config);
        writer.writeEndElement(); // </target>
    }

    private static void writeTargetConInfoNode(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        if (config.targetIsOnline()) {
            writer.writeEmptyElement(TemplateTags.TAG_JDBC);
            ConnParameters tcp = config.getTargetConParams();
            writer.writeAttribute(TemplateTags.ATTR_HOST, tcp.getHost());
            writer.writeAttribute(TemplateTags.ATTR_PORT, String.valueOf(tcp.getPort()));
            writer.writeAttribute(TemplateTags.ATTR_DRIVER, tcp.getDriverFileName());
            writer.writeAttribute(TemplateTags.ATTR_NAME, tcp.getDbName());
            writer.writeAttribute(TemplateTags.ATTR_USER, tcp.getConUser());
            writer.writeAttribute(TemplateTags.ATTR_PASSWORD, tcp.getConPassword());
            writer.writeAttribute(TemplateTags.ATTR_CHARSET, tcp.getCharset());
            writer.writeAttribute(TemplateTags.ATTR_TIMEZONE, tcp.getTimeZone());
            writer.writeAttribute(TemplateTags.ATTR_USER_JDBC_URL, tcp.getUserJDBCURL());
            writer.writeAttribute(
                    TemplateTags.ATTR_CREATE_CONSTRAINT_NOW,
                    getBooleanString(config.isCreateConstrainsBeforeData()));
            writer.writeAttribute(
                    TemplateTags.ATTR_WRITE_ERROR_RECORDS,
                    getBooleanString(config.isWriteErrorRecords()));
            writer.writeAttribute(
                    TemplateTags.ATTR_ADD_SCHEMA, getBooleanString(config.isAddUserSchema()));
        } else if (config.targetIsFile()) {
            writer.writeEmptyElement(TemplateTags.TAG_FILE_REPOSITORY);
            writer.writeAttribute(TemplateTags.ATTR_DIR, config.getFileRepositroyPath());
            writer.writeAttribute(TemplateTags.ATTR_TIMEZONE, config.getTargetFileTimeZone());
            writer.writeAttribute(TemplateTags.ATTR_CHARSET, config.getTargetCharSet());
            writer.writeAttribute(
                    TemplateTags.ATTR_ADD_SCHEMA, getBooleanString(config.isAddUserSchema()));
            writer.writeAttribute(
                    TemplateTags.ATTR_SPLIT_SCHEMA, getBooleanString(config.isSplitSchema()));
            writer.writeAttribute(
                    TemplateTags.ATTR_CREATE_USER_SQL, getBooleanString(config.isCreateUserSQL()));
            writer.writeAttribute(
                    TemplateTags.ATTR_ONETABLEONEFILE,
                    getBooleanString(config.isOneTableOneFile()));
            writer.writeAttribute(
                    TemplateTags.ATTR_DATA_FILE_FORMAT, String.valueOf(config.getDestType()));
            writer.writeAttribute(
                    TemplateTags.ATTR_OUTPUT_FILE_PREFIX, config.getTargetFilePrefix());
            writer.writeAttribute(
                    TemplateTags.ATTR_FILE_MAX_SIZE, String.valueOf(config.getMaxCountPerFile()));
            if (config.targetIsCSV()) {
                writer.writeAttribute(
                        TemplateTags.ATTR_CSV_SEPARATE,
                        config.getCsvSettings().getSeparateChar()
                                        == MigrationConfiguration.CSV_NO_CHAR
                                ? ""
                                : String.valueOf(config.getCsvSettings().getSeparateChar()));
                writer.writeAttribute(
                        TemplateTags.ATTR_CSV_QUOTE,
                        config.getCsvSettings().getQuoteChar() == MigrationConfiguration.CSV_NO_CHAR
                                ? ""
                                : String.valueOf(config.getCsvSettings().getQuoteChar()));
                writer.writeAttribute(
                        TemplateTags.ATTR_CSV_ESCAPE,
                        config.getCsvSettings().getEscapeChar()
                                        == MigrationConfiguration.CSV_NO_CHAR
                                ? ""
                                : String.valueOf(config.getCsvSettings().getEscapeChar()));
            }
            if (config.targetIsDBDump()) {
                writer.writeAttribute(
                        TemplateTags.ATTR_LOB_ROOT_DIR, config.getTargetLOBRootPath());
            }
        }
    }

    private static void writeTargetSchemaNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<Schema> schemaList = config.getTargetSchemaList();
        if (schemaList.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_SCHEMAS);
        for (Schema schema : schemaList) {
            writer.writeEmptyElement(TemplateTags.TAG_SCHEMA_INFO);
            writer.writeAttribute(TemplateTags.ATTR_SCHEMA_NAME, schema.getName());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_SCHEMA, schema.getTargetSchemaName());
        }
        writer.writeEndElement(); // </schemas>
    }

    private static void writeTargetTableNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<Table> targetTables = config.getTargetTableSchema();
        if (targetTables.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_TABLES);
        for (Table table : targetTables) {
            writer.writeStartElement(TemplateTags.TAG_TABLE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, table.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, table.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_OWNER, table.getSourceOwner());
            writer.writeAttribute(
                    TemplateTags.ATTR_REUSE_OID, getBooleanString(table.isReuseOID()));
            writer.writeAttribute(TemplateTags.ATTR_COMMENT, table.getComment());

            writer.writeStartElement(TemplateTags.TAG_COLUMNS);
            for (Column col : table.getColumns()) {
                writer.writeEmptyElement(TemplateTags.TAG_COLUMN);
                writer.writeAttribute(TemplateTags.ATTR_NAME, col.getName());
                writer.writeAttribute(TemplateTags.ATTR_TYPE, col.getShownDataType());
                writer.writeAttribute(TemplateTags.ATTR_BASE_TYPE, col.getDataType());
                if (col.getSubDataType() != null) {
                    writer.writeAttribute(TemplateTags.ATTR_SUB_TYPE, col.getSubDataType());
                }
                writer.writeAttribute(TemplateTags.ATTR_NULL, getBooleanString(col.isNullable()));
                writer.writeAttribute(TemplateTags.ATTR_UNIQUE, getBooleanString(col.isUnique()));
                writer.writeAttribute(TemplateTags.ATTR_SHARED, getBooleanString(col.isShared()));
                if (col.getDefaultValue() != null) {
                    writer.writeAttribute(TemplateTags.ATTR_DEFAULT, col.getDefaultValue());
                    writer.writeAttribute(
                            TemplateTags.ATTR_DEFAULT_EXPRESSION,
                            getBooleanString(col.isDefaultIsExpression()));
                }
                writer.writeAttribute(
                        TemplateTags.ATTR_AUTO_INCREMENT, getBooleanString(col.isAutoIncrement()));
                if (col.isAutoIncrement()) {
                    writer.writeAttribute(
                            TemplateTags.ATTR_START, String.valueOf(col.getAutoIncSeedVal()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_INCREMENT, String.valueOf(col.getAutoIncIncrVal()));
                }
                if (col.isShared()) {
                    writer.writeAttribute(TemplateTags.ATTR_SHARED_VALUE, col.getSharedValue());
                }
                writer.writeAttribute(TemplateTags.ATTR_COMMENT, col.getComment());
            }
            writer.writeEndElement(); // </columns>

            PK pk = table.getPk();
            List<FK> fks = table.getFks();
            List<Index> indexes = table.getIndexes();
            if (pk != null || !fks.isEmpty() || !indexes.isEmpty()) {
                writer.writeStartElement(TemplateTags.TAG_CONSTRAINTS);
                if (pk != null && CollectionUtils.isNotEmpty(pk.getPkColumns())) {
                    writer.writeEmptyElement(TemplateTags.TAG_PK);
                    writer.writeAttribute(TemplateTags.ATTR_FIELDS, list2String(pk.getPkColumns()));
                }
                for (FK fk : fks) {
                    writer.writeEmptyElement(TemplateTags.TAG_FK);
                    writer.writeAttribute(TemplateTags.ATTR_NAME, fk.getName());
                    writer.writeAttribute(TemplateTags.ATTR_REF_TABLE, fk.getReferencedTableName());
                    writer.writeAttribute(
                            TemplateTags.ATTR_ON_UPDATE, FK_OPERATION.get(fk.getUpdateRule()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_ON_DELETE, FK_OPERATION.get(fk.getDeleteRule()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_FIELDS, list2String(fk.getColumnNames()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_REF_FIELDS, list2String(fk.getCol2RefMapping()));
                }
                for (Index index : indexes) {
                    writer.writeEmptyElement(TemplateTags.TAG_INDEX);
                    writer.writeAttribute(TemplateTags.ATTR_NAME, index.getName());
                    writer.writeAttribute(
                            TemplateTags.ATTR_FIELDS, list2String(index.getColumnNames()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_ORDER_RULE,
                            list2String(index.getColumnOrderRulesString()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_REVERSE, getBooleanString(index.isReverse()));
                    writer.writeAttribute(
                            TemplateTags.ATTR_UNIQUE, getBooleanString(index.isUnique()));
                }
                writer.writeEndElement(); // </constraints>
            }
            PartitionInfo pi = table.getPartitionInfo();
            if (pi != null) {
                writer.writeStartElement(TemplateTags.TAG_PARTITIONS);
                writer.writeAttribute(TemplateTags.ATTR_TYPE, pi.getPartitionMethod());
                writer.writeAttribute(TemplateTags.ATTR_EXPRESSION, pi.getPartitionExp());
                for (PartitionTable pt : pi.getPartitions()) {
                    writer.writeEmptyElement(pi.getPartitionMethod().toLowerCase());
                    writer.writeAttribute(TemplateTags.ATTR_NAME, pt.getPartitionName());
                    if (!TemplateTags.VALUE_HASH.equals(pi.getPartitionMethod())) {
                        writer.writeAttribute(TemplateTags.ATTR_VALUE, pt.getPartitionDesc());
                    }
                }
                writer.writeStartElement(TemplateTags.TAG_PARTITION_DDL);
                writer.writeCData(pi.getDDL());
                writer.writeEndElement(); // </partition_ddl>
                writer.writeEndElement(); // </partitions>
            }
            writer.writeEndElement(); // </table>
        }
        writer.writeEndElement(); // </tables>
    }

    private static void writeTargetSequenceNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<Sequence> targetSerials = config.getTargetSerialSchema();
        if (targetSerials.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_SEQUENCES);
        for (Sequence sc : targetSerials) {
            writer.writeEmptyElement(TemplateTags.TAG_SEQUENCE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_OWNER, sc.getSourceOwner());
            writer.writeAttribute(TemplateTags.ATTR_START, String.valueOf(sc.getCurrentValue()));
            writer.writeAttribute(TemplateTags.ATTR_INCREMENT, String.valueOf(sc.getIncrementBy()));
            writer.writeAttribute(
                    TemplateTags.ATTR_MIN,
                    sc.isNoMinValue() ? "0" : String.valueOf(sc.getMinValue()));
            writer.writeAttribute(
                    TemplateTags.ATTR_MAX,
                    sc.isNoMaxValue() ? "0" : String.valueOf(sc.getMaxValue()));
            writer.writeAttribute(TemplateTags.ATTR_NO_MIN, getBooleanString(sc.isNoMinValue()));
            writer.writeAttribute(TemplateTags.ATTR_NO_MAX, getBooleanString(sc.isNoMaxValue()));
            writer.writeAttribute(TemplateTags.ATTR_CYCLE, getBooleanString(sc.isCycleFlag()));
            writer.writeAttribute(TemplateTags.ATTR_CACHE, getBooleanString(!sc.isNoCache()));
            writer.writeAttribute(
                    TemplateTags.ATTR_CACHE_SIZE,
                    sc.isNoCache() ? "0" : String.valueOf(sc.getCacheSize()));
        }
        writer.writeEndElement(); // </sequences>
    }

    private static void writeTargetViewNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<View> targetViews = config.getTargetViewSchema();
        if (targetViews.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_VIEWS);
        for (View view : targetViews) {
            writer.writeStartElement(TemplateTags.TAG_VIEW);
            writer.writeAttribute(TemplateTags.ATTR_NAME, view.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, view.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, view.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_OWNER, view.getSourceOwner());
            writer.writeAttribute(TemplateTags.ATTR_COMMENT, view.getComment());
            writer.writeStartElement(TemplateTags.TAG_VIEWQUERYSQL);
            writer.writeCData(view.getQuerySpec());
            writer.writeEndElement(); // </viewquerysql>
            writer.writeStartElement(TemplateTags.TAG_VIEWCOLUMNS);
            for (Column col : view.getColumns()) {
                writer.writeEmptyElement(TemplateTags.TAG_VIEWCOLUMN);
                writer.writeAttribute(TemplateTags.ATTR_NAME, col.getName());
                writer.writeAttribute(TemplateTags.ATTR_TYPE, col.getShownDataType());
                writer.writeAttribute(TemplateTags.ATTR_BASE_TYPE, col.getDataType());
                if (col.getSubDataType() != null) {
                    writer.writeAttribute(TemplateTags.ATTR_SUB_TYPE, col.getSubDataType());
                }
                if (col.getDefaultValue() != null) {
                    writer.writeAttribute(TemplateTags.ATTR_DEFAULT, col.getDefaultValue());
                }
                writer.writeAttribute(TemplateTags.ATTR_COMMENT, col.getComment());
            }
            writer.writeEndElement(); // </viewcolumns>
            writer.writeEndElement(); // </view>
        }
        writer.writeEndElement(); // </views>
    }

    private static void writeTargetSynonymNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<Synonym> targetSynonyms = config.getTargetSynonymSchema();
        if (targetSynonyms.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_SYNONYMS);
        for (Synonym sc : targetSynonyms) {
            writer.writeEmptyElement(TemplateTags.TAG_SYNONYM);
            writer.writeAttribute(TemplateTags.ATTR_NAME, sc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_SYNONYM_OBJECT_OWNER, sc.getObjectOwner());
            writer.writeAttribute(TemplateTags.ATTR_SYNONYM_OBJECT, sc.getObjectName());
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_OWNER, sc.getSourceOwner());
        }
        writer.writeEndElement(); // </synonyms>
    }

    private static void writeTargetPlcsqlProcedureNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<PlcsqlProcedure> procedures = config.getTargetPlcsqlProcedureSchema();
        if (procedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_PLCSQL_PROCEDURES);
        for (PlcsqlProcedure proc : procedures) {
            writer.writeEmptyElement(TemplateTags.TAG_PLCSQL_PROCEDURE);
            writer.writeAttribute(TemplateTags.ATTR_NAME, proc.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, proc.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_NAME, proc.getTargetName());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, proc.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_AUTH_ID, proc.getAuthid());
            writer.writeAttribute(
                    TemplateTags.ATTR_AUTH_ID_CHANGED, getBooleanString(proc.isAuthidChanged()));
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_DDL, proc.getSourceDDL());
            writer.writeAttribute(TemplateTags.ATTR_HEADER_DDL, proc.getHeaderDDL());
            writer.writeAttribute(TemplateTags.ATTR_BODY_DDL, proc.getBodyDDL());
            writer.writeAttribute(TemplateTags.ATTR_PROCEDURE_DDL, proc.getDDL());
        }
        writer.writeEndElement(); // </plcsql_procedures>
    }

    private static void writeTargetPlcsqlFunctionNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<PlcsqlFunction> functions = config.getTargetPlcsqlFunctionSchema();
        if (functions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TemplateTags.TAG_PLCSQL_FUNCTIONS);
        for (PlcsqlFunction func : functions) {
            writer.writeEmptyElement(TemplateTags.TAG_PLCSQL_FUNCTION);
            writer.writeAttribute(TemplateTags.ATTR_NAME, func.getName());
            writer.writeAttribute(TemplateTags.ATTR_OWNER, func.getOwner());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_NAME, func.getTargetName());
            writer.writeAttribute(TemplateTags.ATTR_TARGET_OWNER, func.getTargetOwner());
            writer.writeAttribute(TemplateTags.ATTR_AUTH_ID, func.getAuthid());
            writer.writeAttribute(
                    TemplateTags.ATTR_AUTH_ID_CHANGED, getBooleanString(func.isAuthidChanged()));
            writer.writeAttribute(TemplateTags.ATTR_SOURCE_DDL, func.getSourceDDL());
            writer.writeAttribute(TemplateTags.ATTR_HEADER_DDL, func.getHeaderDDL());
            writer.writeAttribute(TemplateTags.ATTR_BODY_DDL, func.getBodyDDL());
            writer.writeAttribute(TemplateTags.ATTR_FUNCTION_DDL, func.getDDL());
        }
        writer.writeEndElement(); // </plcsql_functions>
    }
}
