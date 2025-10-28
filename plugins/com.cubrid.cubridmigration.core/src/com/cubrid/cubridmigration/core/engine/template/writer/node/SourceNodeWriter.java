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
package com.cubrid.cubridmigration.core.engine.template.writer.node;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.*;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.common.TextFileUtils;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
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
import com.cubrid.cubridmigration.cubrid.CUBRIDDatabase;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;

/** Writes the <source> section of the migration XML file. */
public class SourceNodeWriter {

    private static final Logger log = LogUtil.getLogger(SourceNodeWriter.class);

    public void write(XMLStreamWriter writer, MigrationConfiguration config, boolean saveSchema)
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

    private void writeOnlineSource(
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

    private void writeSQLSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TAG_SQL);
        writer.writeAttribute(ATTR_CHARSET, config.getSourceFileEncoding());
        List<String> files = config.getSqlFiles();
        for (String file : files) {
            writer.writeEmptyElement(TAG_SQL_FILE);
            writer.writeAttribute(ATTR_LOCATION, file);
        }
        writer.writeEndElement(); // </sql>
    }

    private void writeXMLDumpSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeEmptyElement(TAG_FILE);
        writer.writeAttribute(ATTR_LOCATION, config.getSourceFileName());
        writer.writeAttribute(ATTR_CHARSET, config.getSourceFileEncoding());
        writer.writeAttribute(ATTR_TIMEZONE, config.getSourceFileTimeZone());
        writer.writeAttribute(ATTR_VERSION, config.getSourceFileVersion());
    }

    private void writeCSVSource(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TAG_CSVS);

        writeCsvsAttributes(writer, config);

        List<SourceCSVConfig> csvFiles = config.getCSVConfigs();
        for (SourceCSVConfig scc : csvFiles) {
            writeSingleCsvElement(writer, scc);
        }
        writer.writeEndElement(); // </csvs>
    }

    private void writeCsvsAttributes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeAttribute(
                ATTR_CSV_SEPARATE,
                config.getCsvSettings().getSeparateChar() == MigrationConfiguration.CSV_NO_CHAR
                        ? ""
                        : String.valueOf(config.getCsvSettings().getSeparateChar()));
        writer.writeAttribute(
                ATTR_CSV_QUOTE,
                config.getCsvSettings().getQuoteChar() == MigrationConfiguration.CSV_NO_CHAR
                        ? ""
                        : String.valueOf(config.getCsvSettings().getQuoteChar()));
        writer.writeAttribute(
                ATTR_CSV_ESCAPE,
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
            writer.writeAttribute(ATTR_CSV_NULL_VALUE, sb.toString());
        }
        writer.writeAttribute(ATTR_CHARSET, String.valueOf(config.getCsvSettings().getCharset()));
    }

    private void writeSingleCsvElement(XMLStreamWriter writer, SourceCSVConfig scc)
            throws XMLStreamException {
        writer.writeStartElement(TAG_CSV);
        writer.writeAttribute(ATTR_NAME, scc.getName());
        writer.writeAttribute(ATTR_TARGET, scc.getTarget());
        writer.writeAttribute(ATTR_CREATE, getBooleanString(scc.isCreate()));
        writer.writeAttribute(ATTR_REPLACE, getBooleanString(scc.isReplace()));
        writer.writeAttribute(ATTR_IMPORT_FIRST_ROW, getBooleanString(scc.isImportFirstRow()));
        writer.writeStartElement(TAG_CSV_COLUMNS);
        for (SourceCSVColumnConfig sccc : scc.getColumnConfigs()) {
            writer.writeEmptyElement(TAG_CSV_COLUMN);
            writer.writeAttribute(ATTR_NAME, sccc.getName());
            writer.writeAttribute(ATTR_TARGET, sccc.getTarget());
            writer.writeAttribute(ATTR_CREATE, getBooleanString(sccc.isCreate()));
        }
        writer.writeEndElement(); // </csv_columns>
        writer.writeEndElement(); // </csv>
    }

    private void writeSourceJDBCNode(XMLStreamWriter writer, MigrationConfiguration config)
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

    private void writeSourceSchemaNode(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException, IOException {
        Catalog srcCatalog = config.getSrcCatalog();
        if (srcCatalog != null) {
            writer.writeStartElement(TAG_SCHEMA);
            try {
                File tempFile = new File(PathUtils.getBaseTempDir() + UUID.randomUUID());
                srcCatalog.saveXML(tempFile);
                String schemaXML =
                        TextFileUtils.readText(
                                tempFile.getCanonicalPath(), UTF_8.name(), Integer.MAX_VALUE);
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
            writer.writeStartElement(TAG_SQL_SCHEMA);
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
                                tempFile.getCanonicalPath(), UTF_8.name(), Integer.MAX_VALUE);
                PathUtils.deleteFile(tempFile);
                writer.writeCData(schemaXML);
            } catch (Exception e) {
                log.error("Failed to write SQL source shcmea", e);
                throw new IOException("Failed to write SQL source schema.", e);
            }
            writer.writeEndElement(); // </sql_schema>
        }
    }

    private void writeSourceSchemaMapping(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TAG_SCHEMAS);
        Catalog srcCatalog = config.getSrcCatalog();
        if (srcCatalog != null) {
            for (Schema schema : srcCatalog.getSchemas()) {
                writer.writeEmptyElement(TAG_SCHEMA_INFO);
                writer.writeAttribute(ATTR_SCHEMA_NAME, schema.getName());
                writer.writeAttribute(ATTR_TARGET_SCHEMA, schema.getTargetSchemaName());
            }
        } else {
            for (Schema schema : config.getScriptSchemaMapping().values()) {
                writer.writeEmptyElement(TAG_SCHEMA_INFO);
                writer.writeAttribute(ATTR_SCHEMA_NAME, schema.getName());
                writer.writeAttribute(ATTR_TARGET_SCHEMA, schema.getTargetSchemaName());
            }
        }
        writer.writeEndElement(); // </schemas>
    }

    private void writeSourceTables(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceEntryTableConfig> exportEntryTables = config.getExpEntryTableCfg();
        if (exportEntryTables.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_TABLES);
        for (SourceEntryTableConfig setc : exportEntryTables) {
            writeSingleTableElement(writer, setc);
        }
        writer.writeEndElement(); // </tables>
    }

    private void writeSingleTableElement(XMLStreamWriter writer, SourceEntryTableConfig setc)
            throws XMLStreamException {
        writer.writeStartElement(TAG_TABLE);
        writeTableAttributes(writer, setc);
        writeTableColumns(writer, setc.getColumnConfigList());
        writeTableConstraints(writer, setc.getIndexConfigList(), setc.getFKConfigList());
        writer.writeEndElement(); // </table>
    }

    private void writeTableAttributes(XMLStreamWriter writer, SourceEntryTableConfig setc)
            throws XMLStreamException {
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
    }

    private void writeTableColumns(
            XMLStreamWriter writer, List<SourceColumnConfig> columnConfigList)
            throws XMLStreamException {
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
    }

    private void writeTableConstraints(
            XMLStreamWriter writer,
            List<SourceIndexConfig> indexConfigList,
            List<SourceFKConfig> fkConfigList)
            throws XMLStreamException {
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
    }

    private void writeSourceSQLTables(XMLStreamWriter writer, MigrationConfiguration config)
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

    private void writeSourceSequences(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceSequenceConfig> exportSerials = config.getExpSerialCfg();
        if (exportSerials.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_SEQUENCES);

        for (SourceSequenceConfig sc : exportSerials) {
            writer.writeEmptyElement(TAG_SEQUENCE);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(
                    ATTR_AUTO_SYNCHRONIZE_START_VALUE,
                    getBooleanString(sc.isAutoSynchronizeStartValue()));
        }
        writer.writeEndElement(); // </sequences>
    }

    private void writeSourceSynonyms(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceSynonymConfig> exportSynonyms = config.getExpSynonymCfg();
        if (exportSynonyms.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_SYNONYMS);
        for (SourceSynonymConfig sc : exportSynonyms) {
            writer.writeEmptyElement(TAG_SYNONYM);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT, sc.getObjectName());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT_OWNER, sc.getObjectOwner());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT_TARGET, sc.getObjectTargetName());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT_TARGET_OWNER, sc.getObjectTargetOwner());
        }
        writer.writeEndElement(); // </synonyms>
    }

    private void writeSourceViews(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceViewConfig> exportViews = config.getExpViewCfg();
        if (exportViews.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_VIEWS);
        for (SourceViewConfig sc : exportViews) {
            writer.writeEmptyElement(TAG_VIEW);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_TARGET, sc.getTarget());
            writer.writeAttribute(ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(ATTR_COMMENT, sc.getComment());
        }
        writer.writeEndElement(); // </views>
    }

    private void writeSourceGrants(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourceGrantConfig> exportGrants = config.getExpGrantCfg();
        if (exportGrants.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_GRANTS);
        for (SourceGrantConfig sc : exportGrants) {
            writer.writeEmptyElement(TAG_GRANT);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_GRANTOR, sc.getGrantorName());
            writer.writeAttribute(ATTR_GRANTEE, sc.getGranteeName());
            writer.writeAttribute(ATTR_OBJECT_NAME, sc.getClassName());
            writer.writeAttribute(ATTR_OBJECT_OWNER, sc.getClassOwner());
            writer.writeAttribute(ATTR_AUTH_TYPE, sc.getAuthType());
            writer.writeAttribute(ATTR_GRANTABLE, getBooleanString(sc.isGrantable()));
            writer.writeAttribute(ATTR_TARGET_OWNER, sc.getTargetOwner());
            writer.writeAttribute(ATTR_SOURCE_GRANTOR_NAME, sc.getSourceGrantorName());
            writer.writeAttribute(ATTR_SOURCE_OBJECT_OWNER, sc.getSourceObjectOwner());
        }
        writer.writeEndElement(); // </grants>
    }

    private void writeSourceTriggers(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportTriggers = config.getExpTriggerCfg();
        if (exportTriggers.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_TRIGGERS);
        for (String sc : exportTriggers) {
            writer.writeEmptyElement(TAG_TRIGGER);
            writer.writeAttribute(ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </triggers>
    }

    private void writeSourceFunctions(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportFunctions = config.getExpFunctionCfg();
        if (exportFunctions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_FUNCTIONS);
        for (String sc : exportFunctions) {
            writer.writeEmptyElement(TAG_FUNCTION);
            writer.writeAttribute(ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </functions>
    }

    private void writeSourceProcedures(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<String> exportProcedures = config.getExpProcedureCfg();
        if (exportProcedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_PROCEDURES);
        for (String sc : exportProcedures) {
            writer.writeEmptyElement(TAG_PROCEDURE);
            writer.writeAttribute(ATTR_NAME, sc);
        }
        writer.writeEndElement(); // </procedures>
    }

    private void writeSourcePlcsqlFunctions(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourcePlcsqlFunctionConfig> functions = config.getExpPlcsqlFunctionCfg();
        if (functions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_PLCSQL_FUNCTIONS);
        for (SourcePlcsqlFunctionConfig sfc : functions) {
            writer.writeEmptyElement(TAG_PLCSQL_FUNCTION);
            writer.writeAttribute(ATTR_NAME, sfc.getName());
            writer.writeAttribute(ATTR_OWNER, sfc.getOwner());
            writer.writeAttribute(ATTR_TARGET, sfc.getTarget());
            writer.writeAttribute(ATTR_TARGET_OWNER, sfc.getTargetOwner());
            writer.writeAttribute(ATTR_AUTH_ID, sfc.getAuthid());
            writer.writeAttribute(ATTR_AUTH_ID_CHANGED, getBooleanString(sfc.isAuthidChanged()));
            writer.writeAttribute(ATTR_SOURCE_DDL, sfc.getSourceDDL());
        }
        writer.writeEndElement(); // </plcsql_functions>
    }

    private void writeSourcePlcsqlProcedures(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<SourcePlcsqlProcedureConfig> procedures = config.getExpPlcsqlProcedureCfg();
        if (procedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_PLCSQL_PROCEDURES);
        for (SourcePlcsqlProcedureConfig spc : procedures) {
            writer.writeEmptyElement(TAG_PLCSQL_PROCEDURE);
            writer.writeAttribute(ATTR_NAME, spc.getName());
            writer.writeAttribute(ATTR_OWNER, spc.getOwner());
            writer.writeAttribute(ATTR_TARGET, spc.getTarget());
            writer.writeAttribute(ATTR_TARGET_OWNER, spc.getTargetOwner());
            writer.writeAttribute(ATTR_AUTH_ID, spc.getAuthid());
            writer.writeAttribute(ATTR_AUTH_ID_CHANGED, getBooleanString(spc.isAuthidChagned()));
            writer.writeAttribute(ATTR_SOURCE_DDL, spc.getSourceDDL());
        }
        writer.writeEndElement(); // </plcsql_procedures>
    }
}
