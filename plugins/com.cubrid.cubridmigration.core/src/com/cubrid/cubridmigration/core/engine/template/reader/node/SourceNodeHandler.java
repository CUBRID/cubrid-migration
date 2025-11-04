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
package com.cubrid.cubridmigration.core.engine.template.reader.node;

import static com.cubrid.cubridmigration.core.engine.template.MigrationTemplateUtils.*;
import static com.cubrid.cubridmigration.core.engine.template.TemplateTags.*;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.CSVSettings;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceCSVConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceColumnConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.cubrid.CUBRIDDatabase;

import org.apache.commons.collections4.CollectionUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A SAX {@link DefaultHandler} implementation for parsing the source configuration section of a
 * migration template.
 *
 * <p>This handler reads source-related information from XML and maps it to {@link
 * MigrationConfiguration}.
 */
public class SourceNodeHandler extends DefaultHandler {

    private final MigrationConfiguration config;
    private final Map<String, Consumer<Attributes>> startTagHandlers = new HashMap<>();
    private final Map<String, Runnable> endTagHandlers = new HashMap<>();

    private SourceTableConfig srcTableCfg;
    private StringBuffer sqlStatement;
    private StringBuffer schemaCache;
    private Catalog srcCatalog; // NOPMD
    private Catalog srcSQLCatalog; // NOPMD

    private SourceCSVConfig srcCSV;

    public SourceNodeHandler(MigrationConfiguration config) {
        this.config = config;
        initializeStartTagHandlers();
        initializeEndTagHandlers();
    }

    private void initializeStartTagHandlers() {
        startTagHandlers.put(TAG_JDBC, this::parseSourceJDBC);
        startTagHandlers.put(TAG_SCHEMA, attr -> schemaCache = new StringBuffer());
        startTagHandlers.put(TAG_SQL_SCHEMA, attr -> schemaCache = new StringBuffer());
        startTagHandlers.put(TAG_SCHEMA_INFO, this::parseSourceSchemaInfo);
        startTagHandlers.put(TAG_FILE, this::parseSourceFile);
        startTagHandlers.put(TAG_TABLE, this::parseSourceTable);
        startTagHandlers.put(TAG_COLUMN, this::parseSourceColumn);
        startTagHandlers.put(TAG_FK, this::parseSourceFK);
        startTagHandlers.put(TAG_INDEX, this::parseSourceIndex);
        startTagHandlers.put(TAG_SQLTABLE, this::parseSourceSQLTable);
        startTagHandlers.put(TAG_STATEMENT, attr -> sqlStatement = new StringBuffer());
        startTagHandlers.put(TAG_VIEW, this::parseSourceView);
        startTagHandlers.put(TAG_SEQUENCE, this::parseSourceSequence);
        startTagHandlers.put(TAG_SYNONYM, this::parseSourceSynonym);
        startTagHandlers.put(TAG_GRANT, this::parseSourceGrant);
        startTagHandlers.put(
                TAG_TRIGGER, attr -> config.addExpTriggerCfg(attr.getValue(ATTR_NAME)));
        startTagHandlers.put(
                TAG_FUNCTION, attr -> config.addExpFunctionCfg(attr.getValue(ATTR_NAME)));
        startTagHandlers.put(
                TAG_PROCEDURE, attr -> config.addExpProcedureCfg(attr.getValue(ATTR_NAME)));
        startTagHandlers.put(TAG_PLCSQL_FUNCTION, this::parseSourcePlcsqlFunction);
        startTagHandlers.put(TAG_PLCSQL_PROCEDURE, this::parseSourcePlcsqlProcedure);
        startTagHandlers.put(
                TAG_SQL, attr -> config.setSourceFileEncoding(attr.getValue(ATTR_CHARSET)));
        startTagHandlers.put(TAG_SQL_FILE, attr -> config.addSQLFile(attr.getValue(ATTR_LOCATION)));
        startTagHandlers.put(TAG_CSVS, this::parseSourceCSVS);
        startTagHandlers.put(TAG_CSV, this::parseSourceCSV);
        startTagHandlers.put(TAG_CSV_COLUMN, this::parseSourceCSVColumn);
    }

    private void initializeEndTagHandlers() {
        endTagHandlers.put(TAG_SCHEMA, this::handleEndSchema);
        endTagHandlers.put(TAG_SQL_SCHEMA, this::handleEndSqlSchema);
        endTagHandlers.put(TAG_TABLE, this::handleEndTable);
        endTagHandlers.put(TAG_SQLTABLE, this::handleEndTable);
        endTagHandlers.put(TAG_STATEMENT, this::handleEndStatement);
        endTagHandlers.put(TAG_CSV, this::handleEndCsv);
        endTagHandlers.put(TAG_SOURCE, this::handleEndSource);
    }

    public void processAttributes(Attributes attributes) {
        config.setSourceType(attributes.getValue(ATTR_DB_TYPE));
        if (config.getSourceDBType().equals(DatabaseType.CUBRID)) {
            ((CUBRIDDatabase) config.getSourceDBType())
                    .setVersion(attributes.getValue(ATTR_VERSION));
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        Consumer<Attributes> handler = startTagHandlers.get(qName);
        if (handler != null) {
            handler.accept(attributes);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        Runnable handler = endTagHandlers.get(qName);
        if (handler != null) {
            handler.run();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (schemaCache != null) {
            schemaCache.append(ch, start, length);
            return;
        }
        if (sqlStatement == null) {
            return;
        }
        sqlStatement.append(ch, start, length);
    }

    // startElement

    private void parseSourceJDBC(Attributes attributes) {
        ConnParameters scp =
                ConnParameters.getConParam(
                        null,
                        attributes.getValue(ATTR_HOST),
                        Integer.parseInt(attributes.getValue(ATTR_PORT)),
                        attributes.getValue(ATTR_NAME),
                        config.getSourceDBType(),
                        attributes.getValue(ATTR_CHARSET),
                        attributes.getValue(ATTR_USER),
                        attributes.getValue(ATTR_PASSWORD),
                        attributes.getValue(ATTR_DRIVER),
                        attributes.getValue(ATTR_SCHEMA));
        scp.setUserJDBCURL(attributes.getValue(ATTR_USER_JDBC_URL));
        scp.setTimeZone(attributes.getValue(ATTR_TIMEZONE));
        config.setSourceConParams(scp);
    }

    private void parseSourceSchemaInfo(Attributes attributes) {
        Schema schema = new Schema();
        schema.setName(attributes.getValue(ATTR_SCHEMA_NAME));
        schema.setTargetSchemaName(attributes.getValue(ATTR_TARGET_SCHEMA));
        schema.setMigration(true);
        config.addScriptSchemaMapping(attributes.getValue(ATTR_SCHEMA_NAME), schema);
    }

    private void parseSourceFile(Attributes attributes) {
        config.setSourceFileName(attributes.getValue(ATTR_LOCATION));
        config.setSourceFileEncoding(attributes.getValue(ATTR_CHARSET));
        config.setSourceFileTimeZone(attributes.getValue(ATTR_TIMEZONE));
        config.setSourceFileVersion(attributes.getValue(ATTR_VERSION));
    }

    private void parseSourceTable(Attributes attributes) {
        srcTableCfg = new SourceEntryTableConfig();
        srcTableCfg.setCreateNewTable(getBoolean(attributes.getValue(ATTR_CREATE), true));
        srcTableCfg.setMigrateData(getBoolean(attributes.getValue(ATTR_MIGRATE_DATA), true));
        srcTableCfg.setName(attributes.getValue(ATTR_NAME));
        srcTableCfg.setReplace(getBoolean(attributes.getValue(ATTR_REPLACE), false));
        srcTableCfg.setTarget(attributes.getValue(ATTR_TARGET));
        srcTableCfg.setSqlBefore(attributes.getValue(ATTR_BEFORE_SQL));
        srcTableCfg.setSqlAfter(attributes.getValue(ATTR_AFTER_SQL));
        srcTableCfg.setOwner(attributes.getValue(ATTR_OWNER));
        srcTableCfg.setTargetOwner(attributes.getValue(ATTR_TARGET_SCHEMA));
        srcTableCfg.setChangeTableName(getBoolean(attributes.getValue(ATTR_CHANGE_NAME), false));

        SourceEntryTableConfig setc = ((SourceEntryTableConfig) srcTableCfg);
        setc.setCreatePartition(getBoolean(attributes.getValue(ATTR_PARTITION), false));
        setc.setCreatePK(getBoolean(attributes.getValue(ATTR_PK), true));
        setc.setCondition(attributes.getValue(ATTR_CONDITION));
        setc.setEnableExpOpt(getBoolean(attributes.getValue(ATTR_EXP_OPT_COL), true));
        setc.setStartFromTargetMax(getBoolean(attributes.getValue(ATTR_START_TAR_MAX), false));
        setc.setComment(attributes.getValue(ATTR_COMMENT));
        config.addExpEntryTableCfg(setc);
    }

    private void parseSourceColumn(Attributes attributes) {
        String colName = attributes.getValue(ATTR_NAME);
        srcTableCfg.addColumnConfig(colName, attributes.getValue(ATTR_TARGET), true);
        SourceColumnConfig scc = srcTableCfg.getColumnConfig(colName);
        scc.setNeedTrim(getBoolean(attributes.getValue(ATTR_TRIM), false));
        scc.setReplaceExpression(attributes.getValue(ATTR_REPLACE_EXPRESSION));
        scc.setUserDataHandler(attributes.getValue(ATTR_USER_DATA_HANDLER));
        scc.setComment(attributes.getValue(ATTR_COMMENT));
    }

    private void parseSourceFK(Attributes attributes) {
        ((SourceEntryTableConfig) srcTableCfg)
                .addFKConfig(
                        attributes.getValue(ATTR_NAME), attributes.getValue(ATTR_TARGET), true);
    }

    private void parseSourceIndex(Attributes attributes) {
        ((SourceEntryTableConfig) srcTableCfg)
                .addIndexConfig(
                        attributes.getValue(ATTR_NAME), attributes.getValue(ATTR_TARGET), true);
    }

    private void parseSourceSQLTable(Attributes attributes) {
        srcTableCfg = new SourceSQLTableConfig();
        srcTableCfg.setName(attributes.getValue(ATTR_NAME));
        srcTableCfg.setCreateNewTable(getBoolean(attributes.getValue(ATTR_CREATE), true));
        srcTableCfg.setMigrateData(getBoolean(attributes.getValue(ATTR_MIGRATE_DATA), true));
        srcTableCfg.setReplace(getBoolean(attributes.getValue(ATTR_REPLACE), false));
        srcTableCfg.setTarget(attributes.getValue(ATTR_TARGET));
        srcTableCfg.setTargetOwner(attributes.getValue(ATTR_TARGET_SCHEMA));
        config.addExpSQLTableCfg((SourceSQLTableConfig) srcTableCfg);
    }

    private void parseSourceView(Attributes attributes) {
        config.addExpViewCfg(
                attributes.getValue(ATTR_OWNER),
                attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_TARGET),
                attributes.getValue(ATTR_TARGET_OWNER),
                attributes.getValue(ATTR_COMMENT));
    }

    private void parseSourceSequence(Attributes attributes) {
        SourceSequenceConfig ssc =
                config.addExpSerialCfg(
                        attributes.getValue(ATTR_OWNER),
                        attributes.getValue(ATTR_NAME),
                        attributes.getValue(ATTR_TARGET));
        ssc.setAutoSynchronizeStartValue(
                getBoolean(attributes.getValue(ATTR_AUTO_SYNCHRONIZE_START_VALUE), true));
    }

    private void parseSourceSynonym(Attributes attributes) {
        config.addExpSynonymCfg(
                attributes.getValue(ATTR_OWNER),
                attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_TARGET_OWNER),
                attributes.getValue(ATTR_TARGET),
                attributes.getValue(ATTR_SYNONYM_OBJECT_OWNER),
                attributes.getValue(ATTR_SYNONYM_OBJECT),
                attributes.getValue(ATTR_SYNONYM_OBJECT_TARGET_OWNER),
                attributes.getValue(ATTR_SYNONYM_OBJECT_TARGET));
    }

    private void parseSourceGrant(Attributes attributes) {
        config.addExpGrantCfg(
                attributes.getValue(ATTR_OWNER),
                attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_GRANTOR),
                attributes.getValue(ATTR_GRANTEE),
                attributes.getValue(ATTR_OBJECT_NAME),
                attributes.getValue(ATTR_OBJECT_OWNER),
                attributes.getValue(ATTR_AUTH_TYPE),
                getBoolean(attributes.getValue(ATTR_GRANTABLE), false),
                attributes.getValue(ATTR_TARGET_OWNER),
                attributes.getValue(ATTR_SOURCE_GRANTOR_NAME),
                attributes.getValue(ATTR_SOURCE_OBJECT_OWNER));
    }

    private void parseSourcePlcsqlFunction(Attributes attributes) {
        config.addExpPlcsqlFunctionCfg(
                attributes.getValue(ATTR_OWNER),
                attributes.getValue(ATTR_TARGET_OWNER),
                attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_TARGET),
                attributes.getValue(ATTR_AUTH_ID),
                getBoolean(attributes.getValue(ATTR_AUTH_ID_CHANGED), false),
                attributes.getValue(ATTR_SOURCE_DDL),
                attributes.getValue(ATTR_HEADER_DDL),
                attributes.getValue(ATTR_BODY_DDL),
                attributes.getValue(ATTR_FUNCTION_DDL));
    }

    private void parseSourcePlcsqlProcedure(Attributes attributes) {
        config.addExpPlcsqlProcedureCfg(
                attributes.getValue(ATTR_OWNER),
                attributes.getValue(ATTR_TARGET_OWNER),
                attributes.getValue(ATTR_NAME),
                attributes.getValue(ATTR_TARGET),
                attributes.getValue(ATTR_AUTH_ID),
                getBoolean(attributes.getValue(ATTR_AUTH_ID_CHANGED), false),
                attributes.getValue(ATTR_SOURCE_DDL),
                attributes.getValue(ATTR_HEADER_DDL),
                attributes.getValue(ATTR_BODY_DDL),
                attributes.getValue(ATTR_PROCEDURE_DDL));
    }

    private void parseSourceCSVS(Attributes attributes) {
        final CSVSettings csvSettings = config.getCsvSettings();
        setCharSetting(attributes, ATTR_CSV_SEPARATE, csvSettings::setSeparateChar);
        setCharSetting(attributes, ATTR_CSV_QUOTE, csvSettings::setQuoteChar);
        setCharSetting(attributes, ATTR_CSV_ESCAPE, csvSettings::setEscapeChar);

        setStringSetting(attributes, ATTR_CSV_NULL_VALUE, csvSettings::setNullStrings);
        setStringSetting(attributes, ATTR_CHARSET, csvSettings::setCharset);
    }

    private void setCharSetting(
            Attributes attributes, String attrName, Consumer<Character> setter) {
        String cs = attributes.getValue(attrName);
        if (cs != null) {
            if (cs.length() > 0) {
                setter.accept(cs.charAt(0));
            } else {
                setter.accept(MigrationConfiguration.CSV_NO_CHAR);
            }
        }
    }

    private void setStringSetting(Attributes attributes, String attrName, Consumer<String> setter) {
        String cs = attributes.getValue(attrName);
        if (cs != null) {
            setter.accept(cs);
        }
    }

    private void parseSourceCSV(Attributes attributes) {
        srcCSV = new SourceCSVConfig();
        srcCSV.setCreate(getBoolean(attributes.getValue(ATTR_CREATE), false));
        srcCSV.setReplace(getBoolean(attributes.getValue(ATTR_REPLACE), false));
        srcCSV.setImportFirstRow(getBoolean(attributes.getValue(ATTR_IMPORT_FIRST_ROW), false));
        srcCSV.setName(attributes.getValue(ATTR_NAME));
        srcCSV.setTarget(attributes.getValue(ATTR_TARGET));
    }

    private void parseSourceCSVColumn(Attributes attributes) {
        SourceCSVColumnConfig sccc = new SourceCSVColumnConfig();
        sccc.setCreate(getBoolean(attributes.getValue(ATTR_CREATE), true));
        sccc.setReplace(false);
        sccc.setName(attributes.getValue(ATTR_NAME));
        sccc.setTarget(attributes.getValue(ATTR_TARGET));
        srcCSV.addColumn(sccc);
    }

    // endElement

    private void handleEndSchema() {
        srcCatalog = Catalog.loadXML(schemaCache.toString());
        schemaCache = null;
    }

    private void handleEndSqlSchema() {
        srcSQLCatalog = Catalog.loadXML(schemaCache.toString());
        schemaCache = null;
    }

    private void handleEndTable() {
        srcTableCfg = null;
    }

    private void handleEndStatement() {
        ((SourceSQLTableConfig) srcTableCfg).setSql(sqlStatement.toString().trim());
        sqlStatement = null;
    }

    private void handleEndCsv() {
        config.addCSVFile(srcCSV);
    }

    private void handleEndSource() {
        processSQLCatalog();
        processSourceCatalog();
    }

    private void processSQLCatalog() {
        if (srcSQLCatalog != null && CollectionUtils.isNotEmpty(srcSQLCatalog.getSchemas())) {
            Schema sqlSchema = srcSQLCatalog.getSchemas().get(0);
            for (Table tt : sqlSchema.getTables()) {
                config.addExpSQLTableSchema(tt);
            }
        }
    }

    private void processSourceCatalog() {
        if (srcCatalog != null) {
            ConnParameters sourceConParams = config.getSourceConParams();
            srcCatalog.setConnectionParameters(
                    sourceConParams == null ? null : sourceConParams.clone());
            config.setSrcCatalog(srcCatalog, false);
            config.setOfflineSrcCatalog(srcCatalog);
        }
    }
}
