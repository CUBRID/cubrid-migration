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
package com.cubrid.cubridmigration.ui.wizard.page;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.MigrationWizard;
import com.cubrid.cubridmigration.ui.wizard.page.view.SchemaTableView;
import com.cubrid.cubridmigration.ui.wizard.page.view.SchemaTableView.SrcTable;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SchemaMappingPage extends MigrationWizardPage {
    private static final Logger LOG = LogUtil.getLogger(SchemaMappingPage.class);

    private MigrationWizard wizard;
    private MigrationConfiguration config;
    private SchemaTableView schemaTableView;
    private final List<SrcTable> srcTableList = new ArrayList<>();
    private Catalog srcCatalog;

    public SchemaMappingPage(String pageName) {
        super(pageName);
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        schemaTableView = new SchemaTableView(container, getMigrationWizard().getMigrationConfig());

        setControl(container);
    }

    private void setOfflineSchemaMappingPage() {
        setOfflineData();
        schemaTableView.updateCellEditors();
    }

    private void setOnlineSchemaMappingPage() {
        setOnlineData();
        schemaTableView.updateCellEditors();
    }

    private void setOfflineData() {
        final Catalog catalog = wizard.getOriginalSourceCatalog().createCatalog();

        for (Schema schema : catalog.getSchemas()) {
            SrcTable srcTable = createSrcTable(catalog, schema);

            if (config.targetIsSQL()) {
                srcTable.setTarDBType(Messages.msgCubridSQL);
            } else if (config.targetIsCSV()) {
                srcTable.setTarDBType(Messages.msgCubridCSV);
            } else if (config.targetIsXLS()) {
                srcTable.setTarDBType(Messages.msgCubridXLS);
            } else {
                srcTable.setTarDBType(Messages.msgCubridDump);
            }
            setOfflineTargetSchema(srcTable, schema);
        }
    }

    private void setOfflineTargetSchema(SrcTable srcTable, Schema schema) {
        final Map<String, Schema> scriptSchemaMap = config.getScriptSchemaMapping();
        final List<Schema> targetSchemaList = config.getTargetSchemaList();

        String tarSchemaName = null;
        if (!scriptSchemaMap.isEmpty()) {
            LOG.info("offline script schema");
            Schema scriptSchema = scriptSchemaMap.get(srcTable.getSrcSchema());
            if (scriptSchema != null) {
                tarSchemaName = scriptSchema.getTargetSchemaName();
                srcTable.setSelected(scriptSchema.isMigration());
            }
        } else if (config.isAddUserSchema() && !targetSchemaList.isEmpty()) {
            Optional<String> result =
                    targetSchemaList.stream()
                            .filter(ts -> ts.getName().equals(schema.getName()))
                            .map(Schema::getTargetSchemaName)
                            .findFirst();
            if (result.isPresent()) {
                tarSchemaName = result.get();
            }
        }

        srcTable.setTarSchema(
                StringUtils.isEmpty(tarSchemaName) ? srcTable.getSrcSchema() : tarSchemaName);
    }

    private SrcTable createSrcTable(Catalog catalog, Schema schema) {
        SrcTable srcTable = new SrcTable();
        srcTable.setSrcDBType(catalog.getDatabaseType().getName());
        srcTable.setSrcSchema(schema.getName());
        srcTable.setNote(schema.isGrantorSchema());

        if (!schema.isGrantorSchema()) {
            srcTableList.add(0, srcTable);
        } else {
            srcTableList.add(srcTable);
        }
        return srcTable;
    }

    private void setOnlineData() {
        final Catalog catalog = wizard.getOriginalSourceCatalog().createCatalog();
        final Catalog tarCatalog = wizard.getTargetCatalog();

        for (Schema schema : catalog.getSchemas()) {
            SrcTable srcTable = createSrcTable(catalog, schema);
            srcTable.setTarDBType(tarCatalog.getDatabaseType().getName());
            setOnlineTargetSchema(srcTable, tarCatalog);
        }
    }

    private void setOnlineTargetSchema(SrcTable srcTable, Catalog tarCatalog) {
        final Map<String, Schema> scriptSchemaMap = config.getScriptSchemaMapping();
        if (!scriptSchemaMap.isEmpty()) {
            LOG.info("script schema");

            Schema scriptSchema = scriptSchemaMap.get(srcTable.getSrcSchema());
            String tarSchemaName = null;
            if (scriptSchema != null) {
                tarSchemaName = scriptSchema.getTargetSchemaName().toUpperCase();
                srcTable.setTarSchema(tarSchemaName);
                srcTable.setSelected(scriptSchema.isMigration());
            }

            if (StringUtils.isEmpty(tarSchemaName)) {
                srcTable.setTarSchema(srcTable.getSrcSchema());
            }
            LOG.info("srcTable target schema : " + srcTable.getTarSchema());
            return;
        }

        int version =
                tarCatalog.getVersion().getDbMajorVersion() * 10
                        + tarCatalog.getVersion().getDbMinorVersion();

        if (tarCatalog.isDBAGroup() && version >= 112) {
            srcTable.setTarSchema(srcTable.getSrcSchema());
        } else {
            srcTable.setTarSchema(tarCatalog.getSchemas().get(0).getName());
        }
    }

    @Override
    protected void afterShowCurrentPage(PageChangedEvent event) {
        wizard = getMigrationWizard();
        config = wizard.getMigrationConfig();

        if (!srcTableList.isEmpty()) {
            srcTableList.clear();
        }

        setTitle(wizard.getStepNoMsg(this) + Messages.schemaMappingPageTitle);
        if ((config.targetIsOnline() && !wizard.getTargetCatalog().isDBAGroup())
                || (!config.targetIsOnline()) && !config.isAddUserSchema()) {
            setDescription(Messages.schemaMappingPageDescriptionUncorrectable);
        } else {
            setDescription(Messages.schemaMappingPageDescription);
        }

        schemaTableView.setSrcCatalog(wizard.getOriginalSourceCatalog());
        schemaTableView.setTarCatalog(wizard.getTargetCatalog());
        schemaTableView.updateCellEditors();

        if (!config.targetIsOnline()) {
            setOfflineSchemaMappingPage();
        } else {
            setOnlineSchemaMappingPage();
        }

        schemaTableView.setInput(srcTableList);
    }

    @Override
    protected void handlePageLeaving(PageChangingEvent event) {
        if (!isPageComplete()) {
            return;
        }
        if (isGotoNextPage(event)) {
            srcCatalog = wizard.getOriginalSourceCatalog().createCatalog();
            List<SrcTable> currentSrcTables = schemaTableView.getSrcTableList();

            for (SrcTable srcTable : currentSrcTables) {
                if (!srcTable.isSelected()) {
                    Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
                    srcCatalog.removeOneSchema(srcSchema);
                }
            }
            wizard.setSourceCatalog(srcCatalog);

            if (config.targetIsOnline()) {
                event.doit = saveOnlineData(currentSrcTables);
            } else {
                event.doit =
                        saveOfflineData(
                                config.isAddUserSchema(), config.isSplitSchema(), currentSrcTables);
            }
        }
    }

    private boolean saveOnlineData(final List<SrcTable> currentSrcTables) {
        final Catalog tarCatalog = wizard.getTargetCatalog();
        if (currentSrcTables.stream().noneMatch(SrcTable::isSelected)) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }

        List<String> checkNewSchemaDuplicate = new ArrayList<>();
        for (SrcTable srcTable : currentSrcTables) {
            if (!srcTable.isSelected()) {
                continue;
            }

            if (!(tarCatalog.isDbHasUserSchema())) {
                srcTable.setTarSchema(null);
                continue;
            }

            if (StringUtils.isEmpty(srcTable.getTarSchema())) {
                MessageDialog.openError(
                        getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
                return false;
            }

            Schema targetSchema = tarCatalog.getSchemaByName(srcTable.getTarSchema());
            final Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
            if (targetSchema != null) {
                srcSchema.setTargetSchemaName(targetSchema.getName());
            } else {
                Schema newSchema = new Schema();
                newSchema.setName(srcTable.getTarSchema());
                newSchema.setNewTargetSchema(true);
                srcSchema.setTargetSchemaName(newSchema.getName());
                if (checkNewSchemaDuplicate.contains(newSchema.getName())) {
                    config.setTarSchemaDuplicate(true);
                    continue;
                }
                checkNewSchemaDuplicate.add(newSchema.getName());
                config.setNewTargetSchema(newSchema.getName());
            }
        }
        wizard.setSourceDBNode(srcCatalog);
        return true;
    }

    private static class OfflineFilePathContext {
        final Map<String, String> schemaFullName = new HashMap<>();
        final Map<String, String> tableFullName = new HashMap<>();
        final Map<String, String> viewFullName = new HashMap<>();
        final Map<String, String> viewQuerySpecFullName = new HashMap<>();
        final Map<String, String> pkFullName = new HashMap<>();
        final Map<String, String> fkFullName = new HashMap<>();
        final Map<String, String> dataFullName = new HashMap<>();
        final Map<String, String> indexFullName = new HashMap<>();
        final Map<String, String> uniqueIndexFullName = new HashMap<>();
        final Map<String, String> serialFullName = new HashMap<>();
        final Map<String, String> updateStatisticFullName = new HashMap<>();
        final Map<String, String> schemaFileListFullName = new HashMap<>();
        final Map<String, String> synonymFileListFullName = new HashMap<>();
        final Map<String, Map<String, String>> grantFileListFullName = new HashMap<>();
        final Map<String, List<String>> tableDataFileListFullName = new HashMap<>();
        final Map<String, String> plcsqlProcedureHeaderFullName = new HashMap<>();
        final Map<String, String> plcsqlFunctionHeaderFullName = new HashMap<>();
        final Map<String, String> plcsqlProcedureFullName = new HashMap<>();
        final Map<String, String> plcsqlFunctionFullName = new HashMap<>();
        final Map<String, Map<String, String>> plcsqlProcedureFileListFullName = new HashMap<>();
        final Map<String, Map<String, String>> plcsqlFunctionFileListFullName = new HashMap<>();
    }

    private boolean saveOfflineData(
            boolean addUserSchema, boolean splitSchema, List<SrcTable> currentSrcTables) {
        if (currentSrcTables.stream().noneMatch(SrcTable::isSelected)) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }

        List<Schema> targetSchemaList = new ArrayList<>();
        OfflineFilePathContext pathContext = new OfflineFilePathContext();

        for (SrcTable srcTable : currentSrcTables) {
            if (!srcTable.isSelected()) {
                continue;
            }

            String targetSchemaName = srcTable.getTarSchema();
            if (addUserSchema && StringUtils.trimToEmpty(targetSchemaName).isEmpty()) {
                MessageDialog.openError(
                        getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
                return false;
            }

            Schema schema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
            schema.setTargetSchemaName(srcTable.getTarSchema());
            targetSchemaList.add(schema);

            populateFilePathsForSchema(schema, srcTable.getSrcSchema(), splitSchema, pathContext);
        }

        updateConfigurationWithPaths(targetSchemaList, pathContext);
        wizard.setSourceDBNode(srcCatalog);

        return true;
    }

    private void populateFilePathsForSchema(
            Schema schema,
            String srcSchemaName,
            boolean splitSchema,
            OfflineFilePathContext pathContext) {
        String schemaName =
                srcCatalog.getDatabaseType().isSupportMultiSchema()
                        ? srcSchemaName
                        : config.getSrcConnOwner();

        if (splitSchema) {
            populateSplitSchemaPaths(schema, schemaName, pathContext);
        } else {
            pathContext.schemaFullName.put(
                    schemaName, config.buildLocalFileFullPath(schemaName, "schema", null));
        }

        populateDataAndIndexPaths(schema, schemaName, pathContext);
    }

    private void populateSplitSchemaPaths(
            Schema schema, String schemaName, OfflineFilePathContext pathContext) {
        pathContext.tableFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "class", null));
        pathContext.viewFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "vclass", null));
        pathContext.viewQuerySpecFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "vclass_query_spec", null));
        pathContext.pkFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "pk", null));
        pathContext.fkFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "fk", null));
        pathContext.uniqueIndexFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "uk", null));
        pathContext.serialFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "serial", null));
        pathContext.schemaFileListFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "info", null));
        pathContext.synonymFileListFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "synonym", null));

        for (Grant grant : schema.getGrantList()) {
            pathContext.grantFileListFullName.putIfAbsent(schemaName, new HashMap<>());
            Map<String, String> grantMap = pathContext.grantFileListFullName.get(schemaName);
            grantMap.putIfAbsent(
                    grant.getSourceObjectOwner(),
                    config.buildLocalFileFullPath(
                            schemaName, "grant", grant.getSourceObjectOwner()));
        }

        populatePlcsqlPaths(schema, schemaName, pathContext);
    }

    private void populatePlcsqlPaths(
            Schema schema, String schemaName, OfflineFilePathContext pathContext) {
        pathContext.plcsqlProcedureHeaderFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "procedure_header", null));
        pathContext.plcsqlFunctionHeaderFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "function_header", null));

        pathContext.plcsqlProcedureFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "procedure", null));
        pathContext.plcsqlFunctionFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "function", null));

        Map<String, String> procedureFiles = new HashMap<>();
        schema.getPlcsqlProcedures()
                .forEach(
                        proc ->
                                procedureFiles.put(
                                        proc.getName(),
                                        config.buildPlcsqlProcedureFileFullPath(
                                                schemaName, proc.getName(), "procedure")));
        pathContext.plcsqlProcedureFileListFullName.put(schemaName, procedureFiles);

        Map<String, String> functionFiles = new HashMap<>();
        schema.getPlcsqlFunctions()
                .forEach(
                        func ->
                                functionFiles.put(
                                        func.getName(),
                                        config.buildPlcsqlProcedureFileFullPath(
                                                schemaName, func.getName(), "function")));
        pathContext.plcsqlFunctionFileListFullName.put(schemaName, functionFiles);
    }

    private void populateDataAndIndexPaths(
            Schema schema, String schemaName, OfflineFilePathContext pathContext) {
        if (config.isOneTableOneFile()) {
            List<String> tableList = new ArrayList<>();
            schema.getTables()
                    .forEach(
                            table ->
                                    tableList.add(
                                            config.buildDataFileFullPath(
                                                    schemaName, table.getName())));
            pathContext.tableDataFileListFullName.put(schemaName, tableList);
        }
        pathContext.dataFullName.put(
                schemaName, config.buildDataFileFullPath(schemaName, "objects"));
        pathContext.indexFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "indexes", null));
        pathContext.updateStatisticFullName.put(
                schemaName, config.buildLocalFileFullPath(schemaName, "updatestatistic", null));
    }

    private void updateConfigurationWithPaths(
            List<Schema> targetSchemaList, OfflineFilePathContext pathContext) {
        if (config.getTargetSchemaList().size() > 0) {
            config.removeTargetSchemaList();
        }
        config.setTargetSchemaList(targetSchemaList);
        config.setTargetSchemaFileName(pathContext.schemaFullName);
        config.setTargetTableFileName(pathContext.tableFullName);
        config.setTargetViewFileName(pathContext.viewFullName);
        config.setTargetViewQuerySpecFileName(pathContext.viewQuerySpecFullName);
        config.setTargetDataFileName(pathContext.dataFullName);
        config.setTargetIndexFileName(pathContext.indexFullName);
        config.setTargetPkFileName(pathContext.pkFullName);
        config.setTargetFkFileName(pathContext.fkFullName);
        config.setTargetUniqueIndexFileName(pathContext.uniqueIndexFullName);
        config.setTargetSerialFileName(pathContext.serialFullName);
        config.setTargetUpdateStatisticFileName(pathContext.updateStatisticFullName);
        config.setTargetSchemaFileListName(pathContext.schemaFileListFullName);
        config.setTargetSynonymFileName(pathContext.synonymFileListFullName);
        config.setTargetGrantFileName(pathContext.grantFileListFullName);
        config.setTargetTableDataFileName(pathContext.tableDataFileListFullName);
        config.setTargetAllPlcsqlProcedureHeaderFileName(pathContext.plcsqlProcedureHeaderFullName);
        config.setTargetAllPlcsqlFunctionHeaderFileName(pathContext.plcsqlFunctionHeaderFullName);
        config.setTargetAllPlcsqlProcedureFileName(pathContext.plcsqlProcedureFullName);
        config.setTargetAllPlcsqlFunctionFileName(pathContext.plcsqlFunctionFullName);
        config.setTargetPlcsqlProcedureFileName(pathContext.plcsqlProcedureFileListFullName);
        config.setTargetPlcsqlFunctionFileName(pathContext.plcsqlFunctionFileListFullName);
    }
}
