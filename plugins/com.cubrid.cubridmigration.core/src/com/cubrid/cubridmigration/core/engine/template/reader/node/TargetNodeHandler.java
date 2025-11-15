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

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A SAX {@link DefaultHandler} implementation for parsing the target configuration section of a
 * migration template.
 *
 * <p>This handler reads target-related information from XML and maps it to {@link
 * MigrationConfiguration}.
 */
public class TargetNodeHandler extends DefaultHandler {

    private static final Logger log = LogUtil.getLogger(TargetNodeHandler.class);

    private final MigrationConfiguration config;
    private final CUBRIDDataTypeHelper dtHelper = CUBRIDDataTypeHelper.getInstance(null);
    private final Map<String, Consumer<Attributes>> startTagHandlers = new HashMap<>();
    private final Map<String, Runnable> endTagHandlers = new HashMap<>();

    private Table targetTable;
    private View targetView;
    private StringBuffer sqlStatement;
    private StringBuffer schemaCache;

    public TargetNodeHandler(MigrationConfiguration config) {
        this.config = config;
        initializeStartTagHandlers();
        initializeEndTagHandlers();
    }

    private void initializeStartTagHandlers() {
        startTagHandlers.put(TAG_SCHEMA, attr -> schemaCache = new StringBuffer());
        startTagHandlers.put(TAG_TABLE, this::parseTargetTable);
        startTagHandlers.put(TAG_COLUMN, this::parseTargetColumn);
        startTagHandlers.put(TAG_PK, this::parseTargetPK);
        startTagHandlers.put(TAG_FK, this::parseTargetFK);
        startTagHandlers.put(TAG_INDEX, this::parseTargetIndex);
        startTagHandlers.put(TAG_PARTITIONS, this::parseTargetPartition);
        startTagHandlers.put(TAG_RANGE, this::parseTargetRangePartition);
        startTagHandlers.put(TAG_LIST, this::parseTargetRangePartition);
        startTagHandlers.put(TAG_HASH, this::parseTargetHashPartition);
        startTagHandlers.put(TAG_VIEW, this::parseTargetView);
        startTagHandlers.put(TAG_VIEW_COLUMN, this::parseTargetViewColumn);
        startTagHandlers.put(TAG_SEQUENCE, this::parseTargetSequence);
        startTagHandlers.put(TAG_SYNONYM, this::parseTargetSynonym);
        startTagHandlers.put(TAG_GRANT, this::parseTargetGrant);
        startTagHandlers.put(TAG_PLCSQL_PROCEDURE, this::parseTargetPlcsqlProcedure);
        startTagHandlers.put(TAG_PLCSQL_FUNCTION, this::parseTargetPlcsqlFunction);
        startTagHandlers.put(TAG_VIEW_QUERY_SQL, attr -> sqlStatement = new StringBuffer());
        startTagHandlers.put(TAG_CREATE_VIEW_SQL, attr -> sqlStatement = new StringBuffer());
        startTagHandlers.put(TAG_PARTITION_DDL, attr -> sqlStatement = new StringBuffer());
    }

    private void initializeEndTagHandlers() {
        endTagHandlers.put(TAG_TABLE, this::handleEndTable);
        endTagHandlers.put(TAG_VIEW, this::handleEndView);
        endTagHandlers.put(TAG_VIEW_QUERY_SQL, this::handleEndViewQuerySql);
        endTagHandlers.put(TAG_CREATE_VIEW_SQL, this::handleEndCreateViewSql);
        endTagHandlers.put(TAG_PARTITION_DDL, this::handleEndPartitionDdl);
    }

    public void processAttributes(Attributes attributes) {
        config.setTargetDBVersion(attributes.getValue(ATTR_VERSION));
        String type = attributes.getValue(ATTR_TYPE);
        config.setDestType(MigrationConfiguration.DEST_ONLINE);
        if (VALUE_ONLINE.equalsIgnoreCase(type)) {
            config.setDestType(MigrationConfiguration.DEST_ONLINE);
        } else if (VALUE_DIR.equalsIgnoreCase(type)) {
            config.setDestType(MigrationConfiguration.DEST_DB_UNLOAD);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        Consumer<Attributes> handler = startTagHandlers.get(qName);
        if (handler != null) {
            handler.accept(attributes);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        Runnable handler = endTagHandlers.get(qName);
        if (handler != null) {
            handler.run();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
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

    private void parseTargetTable(Attributes attributes) {
        targetTable = new Table();
        targetTable.setName(attributes.getValue(ATTR_NAME));
        targetTable.setReuseOID(getBoolean(attributes.getValue(ATTR_REUSE_OID), false));
        targetTable.setOwner(attributes.getValue(ATTR_SCHEMA));
        targetTable.setSourceOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        if (targetTable.getOwner() != null && targetTable.getOwner().isEmpty()) {
            targetTable.setOwner(null);
        }
        targetTable.setComment(attributes.getValue(ATTR_COMMENT));
        config.addTargetTableSchema(targetTable);
    }

    private void parseTargetColumn(Attributes attributes) {
        Column column = new Column();
        column.setName(attributes.getValue(ATTR_NAME));
        column.setNullable(getBoolean(attributes.getValue(ATTR_NULL), true));
        column.setAutoIncrement(getBoolean(attributes.getValue(ATTR_AUTO_INCREMENT), false));
        column.setUnique(getBoolean(attributes.getValue(ATTR_UNIQUE), false));
        column.setShared(getBoolean(attributes.getValue(ATTR_SHARED), false));
        if (column.isShared()) {
            column.setSharedValue(attributes.getValue(ATTR_SHARED_VALUE));
        }
        if (column.isAutoIncrement()) {
            column.setAutoIncIncrVal(Integer.parseInt(attributes.getValue(ATTR_INCREMENT)));
            column.setAutoIncSeedVal(Integer.parseInt(attributes.getValue(ATTR_START)));
        }
        column.setDefaultValue(attributes.getValue(ATTR_DEFAULT));
        column.setDefaultIsExpression(
                getBoolean(attributes.getValue(ATTR_DEFAULT_EXPRESSION), false));
        final String type = attributes.getValue(ATTR_TYPE);
        if (StringUtils.isEmpty(type)) {
            log.warn("Column type missing: {}.{}", targetTable.getName(), column.getName());
        }
        column.setComment(attributes.getValue(ATTR_COMMENT));
        dtHelper.setColumnDataType(type, column);
        targetTable.addColumn(column);
    }

    private void parseTargetPK(Attributes attributes) {
        PK pk = new PK(targetTable);
        targetTable.setPk(pk);
        pk.setPkColumns(getStringList(attributes.getValue(ATTR_FIELDS)));
    }

    private void parseTargetFK(Attributes attributes) {
        FK fk = new FK(targetTable);
        targetTable.addFK(fk);
        fk.setName(attributes.getValue(ATTR_NAME));
        fk.setDeleteRule(getFKOptIndex(attributes.getValue(ATTR_ON_DELETE)));
        fk.setUpdateRule(getFKOptIndex(attributes.getValue(ATTR_ON_UPDATE)));
        fk.setReferencedTableName(attributes.getValue(ATTR_REF_TABLE));
        final List<String> cols = getStringList(attributes.getValue(ATTR_FIELDS));
        final List<String> refCols = getStringList(attributes.getValue(ATTR_REF_FIELDS));
        if (CollectionUtils.isNotEmpty(cols)
                && CollectionUtils.isNotEmpty(refCols)
                && cols.size() == refCols.size()) {
            for (int i = 0; i < cols.size(); i++) {
                fk.addRefColumnName(cols.get(i), refCols.get(i));
            }
        } else {
            targetTable.removeFK(fk.getName());
        }
    }

    private void parseTargetIndex(Attributes attributes) {
        Index index = new Index(targetTable);
        index.setName(attributes.getValue(ATTR_NAME));

        List<String> rules = getStringList(attributes.getValue(ATTR_ORDER_RULE));
        List<String> columns = getStringList(attributes.getValue(ATTR_FIELDS));
        if (CollectionUtils.isEmpty(rules)
                || CollectionUtils.isEmpty(columns)
                || rules.size() != columns.size()) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            index.addColumn(columns.get(i), rules.get(i).startsWith("A"));
        }
        index.setUnique(getBoolean(attributes.getValue(ATTR_UNIQUE), false));
        index.setReverse(getBoolean(attributes.getValue(ATTR_REVERSE), false));

        targetTable.addIndex(index);
    }

    private void parseTargetPartition(Attributes attributes) {
        PartitionInfo partition = new PartitionInfo();
        targetTable.setPartitionInfo(partition);
        partition.setPartitionExp(attributes.getValue(ATTR_EXPRESSION));
        partition.setPartitionMethod(attributes.getValue(ATTR_TYPE));
    }

    private void parseTargetRangePartition(Attributes attributes) {
        PartitionTable pt = new PartitionTable();
        pt.setPartitionName(attributes.getValue(ATTR_NAME));
        pt.setPartitionDesc(attributes.getValue(ATTR_VALUE));
        targetTable.getPartitionInfo().addPartition(pt);
    }

    private void parseTargetHashPartition(Attributes attributes) {
        PartitionTable pt = new PartitionTable();
        pt.setPartitionName(attributes.getValue(ATTR_NAME));
        targetTable.getPartitionInfo().addPartition(pt);
    }

    private void parseTargetView(Attributes attributes) {
        targetView = new View();
        targetView.setOwner(attributes.getValue(ATTR_SCHEMA));
        targetView.setTargetOwner(attributes.getValue(ATTR_SCHEMA));
        targetView.setName(attributes.getValue(ATTR_NAME));
        targetView.setSourceOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        targetView.setComment(attributes.getValue(ATTR_COMMENT));
        config.addTargetViewSchema(targetView);
    }

    private void parseTargetViewColumn(Attributes attributes) {
        Column column = new Column();
        targetView.addColumn(column);
        column.setTableOrView(targetView);
        column.setName(attributes.getValue(ATTR_NAME));
        column.setComment(attributes.getValue(ATTR_COMMENT));
        dtHelper.setColumnDataType(attributes.getValue(ATTR_TYPE), column);
    }

    private void parseTargetSequence(Attributes attributes) {
        Sequence seq = new Sequence();
        seq.setName(attributes.getValue(ATTR_NAME));
        seq.setIncrementBy(new BigInteger(attributes.getValue(ATTR_INCREMENT)));
        seq.setCurrentValue(new BigInteger(attributes.getValue(ATTR_START)));
        seq.setCycleFlag(getBoolean(attributes.getValue(ATTR_CYCLE), false));
        seq.setNoCache(!getBoolean(attributes.getValue(ATTR_CACHE), true));
        seq.setOwner(attributes.getValue(ATTR_SCHEMA));
        seq.setTargetOwner(attributes.getValue(ATTR_SCHEMA));
        if (!seq.isNoCache()) {
            final String cs = attributes.getValue(ATTR_CACHE_SIZE);
            seq.setCacheSize(cs == null ? 2 : Integer.parseInt(cs));
        }
        seq.setNoMaxValue(getBoolean(attributes.getValue(ATTR_NO_MAX), true));
        if (!seq.isNoMaxValue()) {
            seq.setMaxValue(new BigInteger(attributes.getValue(ATTR_MAX)));
        }
        seq.setNoMinValue(getBoolean(attributes.getValue(ATTR_NO_MIN), true));
        if (!seq.isNoMinValue()) {
            seq.setMinValue(new BigInteger(attributes.getValue(ATTR_MIN)));
        }
        seq.setSourceOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        config.addTargetSerialSchema(seq);
    }

    private void parseTargetSynonym(Attributes attributes) {
        Synonym syn = new Synonym();
        syn.setName(attributes.getValue(ATTR_NAME));
        syn.setOwner(attributes.getValue(ATTR_SCHEMA));
        syn.setObjectName(attributes.getValue(ATTR_OBJECT_NAME));
        syn.setObjectOwner(attributes.getValue(ATTR_OBJECT_SCHEMA));
        syn.setSourceOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        syn.setPublic(false);
        config.addTargetSynonymSchema(syn);
    }

    private void parseTargetGrant(Attributes attributes) {
        Grant grn = new Grant();
        grn.setOwner(attributes.getValue(ATTR_SCHEMA));
        grn.setName(attributes.getValue(ATTR_NAME));
        grn.setGrantorName(attributes.getValue(ATTR_GRANTOR));
        grn.setGranteeName(attributes.getValue(ATTR_GRANTEE));
        grn.setClassOwner(attributes.getValue(ATTR_OBJECT_SCHEMA));
        grn.setClassName(attributes.getValue(ATTR_OBJECT_NAME));
        grn.setAuthType(attributes.getValue(ATTR_PRIVILEGE));
        grn.setGrantable(getBoolean(attributes.getValue(ATTR_WITH_GRANT_OPTION), false));
        grn.setSourceOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        grn.setSourceObjectOwner(attributes.getValue(ATTR_SOURCE_OBJECT_SCHEMA));
        config.addTargetGrantSchema(grn);
    }

    private void parseTargetPlcsqlProcedure(Attributes attributes) {
        PlcsqlProcedure proc = new PlcsqlProcedure();
        proc.setTargetOwner(attributes.getValue(ATTR_SCHEMA));
        proc.setTargetName(attributes.getValue(ATTR_NAME));
        proc.setOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        proc.setName(attributes.getValue(ATTR_SOURCE_NAME));
        proc.setAuthid(attributes.getValue(ATTR_AUTH_ID));
        proc.setAuthidChanged(getBoolean(attributes.getValue(ATTR_AUTH_ID_CHANGED), false));
        proc.setSourceDDL(attributes.getValue(ATTR_SOURCE_DDL));
        proc.setHeaderDDL(attributes.getValue(ATTR_HEADER_DDL));
        proc.setBodyDDL(attributes.getValue(ATTR_BODY_DDL));
        proc.setProcedureDDL(attributes.getValue(ATTR_PROCEDURE_DDL));
        config.addTargetPlcsqlProcedureSchema(proc);
    }

    private void parseTargetPlcsqlFunction(Attributes attributes) {
        PlcsqlFunction func = new PlcsqlFunction();
        func.setTargetOwner(attributes.getValue(ATTR_SCHEMA));
        func.setTargetName(attributes.getValue(ATTR_NAME));
        func.setOwner(attributes.getValue(ATTR_SOURCE_SCHEMA));
        func.setName(attributes.getValue(ATTR_SOURCE_NAME));
        func.setAuthid(attributes.getValue(ATTR_AUTH_ID));
        func.setAuthidChanged(getBoolean(attributes.getValue(ATTR_AUTH_ID_CHANGED), false));
        func.setSourceDDL(attributes.getValue(ATTR_SOURCE_DDL));
        func.setHeaderDDL(attributes.getValue(ATTR_HEADER_DDL));
        func.setBodyDDL(attributes.getValue(ATTR_BODY_DDL));
        func.setFunctionDDL(attributes.getValue(ATTR_FUNCTION_DDL));
        config.addTargetPlcsqlFunctionSchema(func);
    }

    // endElement

    private void handleEndTable() {
        targetTable = null;
    }

    private void handleEndView() {
        targetView = null;
    }

    private void handleEndViewQuerySql() {
        targetView.setQuerySpec(sqlStatement.toString().trim());
        sqlStatement = null;
    }

    private void handleEndCreateViewSql() {
        targetView.setDDL(sqlStatement.toString().trim());
        sqlStatement = null;
    }

    private void handleEndPartitionDdl() {
        targetTable.getPartitionInfo().setDDL(sqlStatement.toString());
        sqlStatement = null;
    }
}
