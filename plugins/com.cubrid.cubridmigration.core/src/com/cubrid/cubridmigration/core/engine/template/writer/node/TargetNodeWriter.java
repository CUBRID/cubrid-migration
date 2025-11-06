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

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.FK;
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

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Writes the <target> section of the migration XML file. */
public class TargetNodeWriter {

    public void write(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        writer.writeStartElement(TAG_TARGET);
        writer.writeAttribute(ATTR_VERSION, config.getTargetDBVersion());
        if (config.targetIsOnline()) {
            writer.writeAttribute(ATTR_TYPE, VALUE_ONLINE);
        } else if (config.targetIsFile()) {
            writer.writeAttribute(ATTR_TYPE, VALUE_DIR);
        } else {
            writer.writeAttribute(ATTR_TYPE, VALUE_OFFLINE);
        }
        writer.writeAttribute(ATTR_DB_TYPE, "cubrid");

        writeTargetTableNodes(writer, config);
        writeTargetSequenceNodes(writer, config);
        writeTargetViewNodes(writer, config);
        writeTargetSynonymNodes(writer, config);
        writeTargetPlcsqlProcedureNodes(writer, config);
        writeTargetPlcsqlFunctionNodes(writer, config);
        writer.writeEndElement(); // </target>
    }

    private void writeTargetTableNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<Table> targetTables = config.getTargetTableSchema();
        if (targetTables.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_TABLES);
        for (Table table : targetTables) {
            writeSingleTargetTable(writer, table);
        }
        writer.writeEndElement(); // </tables>
    }

    private void writeSingleTargetTable(XMLStreamWriter writer, Table table)
            throws XMLStreamException {
        writer.writeStartElement(TAG_TABLE);
        writeTargetTableAttributes(writer, table);
        writeTargetColumns(writer, table);
        writeTargetConstraints(writer, table);
        writeTargetPartitions(writer, table);
        writer.writeEndElement(); // </table>
    }

    private void writeTargetTableAttributes(XMLStreamWriter writer, Table table)
            throws XMLStreamException {
        writer.writeAttribute(ATTR_NAME, table.getName());
        writer.writeAttribute(ATTR_OWNER, table.getOwner());
        writer.writeAttribute(ATTR_SOURCE_OWNER, table.getSourceOwner());
        writer.writeAttribute(ATTR_REUSE_OID, getBooleanString(table.isReuseOID()));
        writer.writeAttribute(ATTR_COMMENT, table.getComment());
    }

    private void writeTargetColumns(XMLStreamWriter writer, Table table) throws XMLStreamException {
        writer.writeStartElement(TAG_COLUMNS);
        for (Column col : table.getColumns()) {
            writer.writeEmptyElement(TAG_COLUMN);
            writer.writeAttribute(ATTR_NAME, col.getName());
            writer.writeAttribute(ATTR_TYPE, col.getShownDataType());
            writer.writeAttribute(ATTR_BASE_TYPE, col.getDataType());
            if (col.getSubDataType() != null) {
                writer.writeAttribute(ATTR_SUB_TYPE, col.getSubDataType());
            }
            writer.writeAttribute(ATTR_NULL, getBooleanString(col.isNullable()));
            writer.writeAttribute(ATTR_UNIQUE, getBooleanString(col.isUnique()));
            writer.writeAttribute(ATTR_SHARED, getBooleanString(col.isShared()));
            if (col.getDefaultValue() != null) {
                writer.writeAttribute(ATTR_DEFAULT, col.getDefaultValue());
                writer.writeAttribute(
                        ATTR_DEFAULT_EXPRESSION, getBooleanString(col.isDefaultIsExpression()));
            }
            writer.writeAttribute(ATTR_AUTO_INCREMENT, getBooleanString(col.isAutoIncrement()));
            if (col.isAutoIncrement()) {
                writer.writeAttribute(ATTR_START, String.valueOf(col.getAutoIncSeedVal()));
                writer.writeAttribute(ATTR_INCREMENT, String.valueOf(col.getAutoIncIncrVal()));
            }
            if (col.isShared()) {
                writer.writeAttribute(ATTR_SHARED_VALUE, col.getSharedValue());
            }
            writer.writeAttribute(ATTR_COMMENT, col.getComment());
        }
        writer.writeEndElement(); // </columns>
    }

    private void writeTargetConstraints(XMLStreamWriter writer, Table table)
            throws XMLStreamException {
        PK pk = table.getPk();
        List<FK> fks = table.getFks();
        List<Index> indexes = table.getIndexes();
        if (pk != null || !fks.isEmpty() || !indexes.isEmpty()) {
            writer.writeStartElement(TAG_CONSTRAINTS);
            if (pk != null && CollectionUtils.isNotEmpty(pk.getPkColumns())) {
                writer.writeEmptyElement(TAG_PK);
                writer.writeAttribute(ATTR_FIELDS, list2String(pk.getPkColumns()));
            }
            for (FK fk : fks) {
                writer.writeEmptyElement(TAG_FK);
                writer.writeAttribute(ATTR_NAME, fk.getName());
                writer.writeAttribute(ATTR_REF_TABLE, fk.getReferencedTableName());
                writer.writeAttribute(ATTR_ON_UPDATE, FK_OPERATION.get(fk.getUpdateRule()));
                writer.writeAttribute(ATTR_ON_DELETE, FK_OPERATION.get(fk.getDeleteRule()));
                writer.writeAttribute(ATTR_FIELDS, list2String(fk.getColumnNames()));
                writer.writeAttribute(ATTR_REF_FIELDS, list2String(fk.getCol2RefMapping()));
            }
            for (Index index : indexes) {
                writer.writeEmptyElement(TAG_INDEX);
                writer.writeAttribute(ATTR_NAME, index.getName());
                writer.writeAttribute(ATTR_FIELDS, list2String(index.getColumnNames()));
                writer.writeAttribute(
                        ATTR_ORDER_RULE, list2String(index.getColumnOrderRulesString()));
                writer.writeAttribute(ATTR_REVERSE, getBooleanString(index.isReverse()));
                writer.writeAttribute(ATTR_UNIQUE, getBooleanString(index.isUnique()));
            }
            writer.writeEndElement(); // </constraints>
        }
    }

    private void writeTargetPartitions(XMLStreamWriter writer, Table table)
            throws XMLStreamException {
        PartitionInfo pi = table.getPartitionInfo();
        if (pi != null) {
            writer.writeStartElement(TAG_PARTITIONS);
            writer.writeAttribute(ATTR_TYPE, pi.getPartitionMethod());
            writer.writeAttribute(ATTR_EXPRESSION, pi.getPartitionExp());
            for (PartitionTable pt : pi.getPartitions()) {
                writer.writeEmptyElement(pi.getPartitionMethod().toLowerCase());
                writer.writeAttribute(ATTR_NAME, pt.getPartitionName());
                if (!VALUE_HASH.equals(pi.getPartitionMethod())) {
                    writer.writeAttribute(ATTR_VALUE, pt.getPartitionDesc());
                }
            }
            writer.writeStartElement(TAG_PARTITION_DDL);
            writer.writeCData(pi.getDDL());
            writer.writeEndElement(); // </partition_ddl>
            writer.writeEndElement(); // </partitions>
        }
    }

    private void writeTargetSequenceNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<Sequence> targetSerials = config.getTargetSerialSchema();
        if (targetSerials.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_SEQUENCES);
        for (Sequence sc : targetSerials) {
            writer.writeEmptyElement(TAG_SEQUENCE);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_SOURCE_OWNER, sc.getSourceOwner());
            writer.writeAttribute(ATTR_START, String.valueOf(sc.getCurrentValue()));
            writer.writeAttribute(ATTR_INCREMENT, String.valueOf(sc.getIncrementBy()));
            writer.writeAttribute(
                    ATTR_MIN, sc.isNoMinValue() ? "0" : String.valueOf(sc.getMinValue()));
            writer.writeAttribute(
                    ATTR_MAX, sc.isNoMaxValue() ? "0" : String.valueOf(sc.getMaxValue()));
            writer.writeAttribute(ATTR_NO_MIN, getBooleanString(sc.isNoMinValue()));
            writer.writeAttribute(ATTR_NO_MAX, getBooleanString(sc.isNoMaxValue()));
            writer.writeAttribute(ATTR_CYCLE, getBooleanString(sc.isCycleFlag()));
            writer.writeAttribute(ATTR_CACHE, getBooleanString(!sc.isNoCache()));
            writer.writeAttribute(
                    ATTR_CACHE_SIZE, sc.isNoCache() ? "0" : String.valueOf(sc.getCacheSize()));
        }
        writer.writeEndElement(); // </sequences>
    }

    private void writeTargetViewNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<View> targetViews = config.getTargetViewSchema();
        if (targetViews.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_VIEWS);
        for (View view : targetViews) {
            writer.writeStartElement(TAG_VIEW);
            writer.writeAttribute(ATTR_NAME, view.getName());
            writer.writeAttribute(ATTR_OWNER, view.getOwner());
            writer.writeAttribute(ATTR_TARGET_OWNER, view.getOwner());
            writer.writeAttribute(ATTR_SOURCE_OWNER, view.getSourceOwner());
            writer.writeAttribute(ATTR_COMMENT, view.getComment());
            writer.writeStartElement(TAG_VIEWQUERYSQL);
            writer.writeCData(view.getQuerySpec());
            writer.writeEndElement(); // </viewquerysql>
            writer.writeStartElement(TAG_VIEWCOLUMNS);
            for (Column col : view.getColumns()) {
                writer.writeEmptyElement(TAG_VIEWCOLUMN);
                writer.writeAttribute(ATTR_NAME, col.getName());
                writer.writeAttribute(ATTR_TYPE, col.getShownDataType());
                writer.writeAttribute(ATTR_BASE_TYPE, col.getDataType());
                if (col.getSubDataType() != null) {
                    writer.writeAttribute(ATTR_SUB_TYPE, col.getSubDataType());
                }
                if (col.getDefaultValue() != null) {
                    writer.writeAttribute(ATTR_DEFAULT, col.getDefaultValue());
                }
                writer.writeAttribute(ATTR_COMMENT, col.getComment());
            }
            writer.writeEndElement(); // </viewcolumns>
            writer.writeEndElement(); // </view>
        }
        writer.writeEndElement(); // </views>
    }

    private void writeTargetSynonymNodes(XMLStreamWriter writer, MigrationConfiguration config)
            throws XMLStreamException {
        List<Synonym> targetSynonyms = config.getTargetSynonymSchema();
        if (targetSynonyms.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_SYNONYMS);
        for (Synonym sc : targetSynonyms) {
            writer.writeEmptyElement(TAG_SYNONYM);
            writer.writeAttribute(ATTR_NAME, sc.getName());
            writer.writeAttribute(ATTR_OWNER, sc.getOwner());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT_OWNER, sc.getObjectOwner());
            writer.writeAttribute(ATTR_SYNONYM_OBJECT, sc.getObjectName());
            writer.writeAttribute(ATTR_SOURCE_OWNER, sc.getSourceOwner());
        }
        writer.writeEndElement(); // </synonyms>
    }

    private void writeTargetPlcsqlProcedureNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<PlcsqlProcedure> procedures = config.getTargetPlcsqlProcedureSchema();
        if (procedures.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_PLCSQL_PROCEDURES);
        for (PlcsqlProcedure proc : procedures) {
            writer.writeEmptyElement(TAG_PLCSQL_PROCEDURE);
            writer.writeAttribute(ATTR_NAME, proc.getName());
            writer.writeAttribute(ATTR_OWNER, proc.getOwner());
            writer.writeAttribute(ATTR_TARGET_NAME, proc.getTargetName());
            writer.writeAttribute(ATTR_TARGET_OWNER, proc.getTargetOwner());
            writer.writeAttribute(ATTR_AUTH_ID, proc.getAuthid());
            writer.writeAttribute(ATTR_AUTH_ID_CHANGED, getBooleanString(proc.isAuthidChanged()));
            writer.writeAttribute(ATTR_SOURCE_DDL, proc.getSourceDDL());
            writer.writeAttribute(ATTR_HEADER_DDL, proc.getHeaderDDL());
            writer.writeAttribute(ATTR_BODY_DDL, proc.getBodyDDL());
            writer.writeAttribute(ATTR_PROCEDURE_DDL, proc.getDDL());
        }
        writer.writeEndElement(); // </plcsql_procedures>
    }

    private void writeTargetPlcsqlFunctionNodes(
            XMLStreamWriter writer, MigrationConfiguration config) throws XMLStreamException {
        List<PlcsqlFunction> functions = config.getTargetPlcsqlFunctionSchema();
        if (functions.isEmpty()) {
            return;
        }
        writer.writeStartElement(TAG_PLCSQL_FUNCTIONS);
        for (PlcsqlFunction func : functions) {
            writer.writeEmptyElement(TAG_PLCSQL_FUNCTION);
            writer.writeAttribute(ATTR_NAME, func.getName());
            writer.writeAttribute(ATTR_OWNER, func.getOwner());
            writer.writeAttribute(ATTR_TARGET_NAME, func.getTargetName());
            writer.writeAttribute(ATTR_TARGET_OWNER, func.getTargetOwner());
            writer.writeAttribute(ATTR_AUTH_ID, func.getAuthid());
            writer.writeAttribute(ATTR_AUTH_ID_CHANGED, getBooleanString(func.isAuthidChanged()));
            writer.writeAttribute(ATTR_SOURCE_DDL, func.getSourceDDL());
            writer.writeAttribute(ATTR_HEADER_DDL, func.getHeaderDDL());
            writer.writeAttribute(ATTR_BODY_DDL, func.getBodyDDL());
            writer.writeAttribute(ATTR_FUNCTION_DDL, func.getDDL());
        }
        writer.writeEndElement(); // </plcsql_functions>
    }
}
