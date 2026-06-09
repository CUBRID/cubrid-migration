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
import com.cubrid.cubridmigration.core.connection.CMTConParamManager;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaEntry;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.ui.database.SchemaFetcherWithProgress;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.MigrationWizard;
import com.cubrid.cubridmigration.ui.wizard.page.view.SchemaTableView;
import com.cubrid.cubridmigration.ui.wizard.page.view.SchemaTableView.SrcTable;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SchemaMappingPage extends MigrationWizardPage {
    private static final Logger LOG = LogUtil.getLogger(SchemaMappingPage.class);

    private MigrationWizard wizard;
    private MigrationConfiguration config;
    private SchemaTableView schemaTableView;
    private final List<SrcTable> srcTableList = new ArrayList<>();
    private Catalog srcCatalog;
    private Button btnUpdateObjects;

    public SchemaMappingPage(String pageName) {
        super(pageName);
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        container.setLayout(layout);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite header = new Composite(container, SWT.NONE);
        GridLayout headerLayout = new GridLayout(1, false);
        headerLayout.marginWidth = 0;
        headerLayout.marginHeight = 0;
        header.setLayout(headerLayout);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        btnUpdateObjects = new Button(header, SWT.PUSH);
        btnUpdateObjects.setText(Messages.objectMappingRefreshLabel);
        btnUpdateObjects.addSelectionListener(
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e) {
                        handleRefreshDatabaseObject();
                    }
                });

        schemaTableView = new SchemaTableView(container, getMigrationWizard().getMigrationConfig());

        schemaTableView
                .getViewer()
                .getTable()
                .getParent()
                .setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        setControl(container);
    }

    @Override
    protected void afterShowCurrentPage(PageChangedEvent event) {
        wizard = getMigrationWizard();
        config = wizard.getMigrationConfig();

        if (!srcTableList.isEmpty()) {
            srcTableList.clear();
        }

        setTitle(wizard.getStepNoMsg(this) + Messages.schemaMappingPageTitle);
        updateDescription();

        schemaTableView.setSrcSchemaCatalog(wizard.getSourceSchemaCatalog());
        schemaTableView.setTarCatalog(wizard.getTargetCatalog());
        schemaTableView.updateCellEditors();

        if (btnUpdateObjects != null && !btnUpdateObjects.isDisposed()) {
            boolean enableUpdateObjects = config.sourceIsOnline();
            btnUpdateObjects.setEnabled(enableUpdateObjects);
        }

        if (!config.targetIsOnline()) {
            setOfflineSchemaMappingPage();
        } else {
            setOnlineSchemaMappingPage();
        }

        schemaTableView.setInput(srcTableList);
    }

    private void updateDescription() {
        if ((config.targetIsOnline() && !wizard.getTargetCatalog().isDBAGroup())
                || (!config.targetIsOnline() && !config.isAddUserSchema())) {
            setDescription(Messages.schemaMappingPageDescriptionUncorrectable);
        } else {
            setDescription(Messages.schemaMappingPageDescription);
        }
    }

    @Override
    protected void handlePageLeaving(PageChangingEvent event) {
        if (!isPageComplete()) return;
        if (!isGotoNextPage(event)) return;

        List<SrcTable> currentSrcTables = schemaTableView.getSrcTableList();
        List<String> selectedSchemas = collectSelectedSchemas(currentSrcTables);
        if (config.targetIsOnline()) {
            event.doit = saveOnlineData(currentSrcTables, selectedSchemas);
        } else {
            event.doit =
                    saveOfflineData(
                            config.isAddUserSchema(),
                            config.isSplitSchema(),
                            currentSrcTables,
                            selectedSchemas);
        }
    }

    private void handleRefreshDatabaseObject() {
        final MigrationWizard wizard = getMigrationWizard();
        final MigrationConfiguration cfg = wizard.getMigrationConfig();

        List<SrcTable> currentSrcTables = schemaTableView.getSrcTableList();
        List<String> selectedSchemas = collectSelectedSchemas(currentSrcTables);
        if (selectedSchemas.isEmpty()) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return;
        }

        boolean confirmed =
                MessageDialog.openConfirm(
                        getShell(),
                        Messages.msgConfirmation,
                        Messages.objectMappingRefreshActionMessage);
        if (!confirmed) {
            return;
        }

        if (!validateSourceConfiguration(cfg, wizard.getSourceSchemaCatalog())) {
            return;
        }

        cfg.setSelectedSrcSchemas(selectedSchemas);
        refreshSourceCatalog(cfg, wizard, selectedSchemas);
    }

    private boolean validateSourceConfiguration(
            MigrationConfiguration cfg, SchemaCatalog sourceSchemaCatalog) {
        if (cfg.getSourceConParams() == null) {
            MessageDialog.openError(getShell(), Messages.msgError, "Source connection is not set.");
            return false;
        }
        if (sourceSchemaCatalog == null) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, "Source schema catalog is not loaded.");
            return false;
        }
        return true;
    }

    private void refreshSourceCatalog(
            MigrationConfiguration cfg, MigrationWizard wizard, List<String> selectedSchemas) {
        try {
            ConnParameters cp = cfg.getSourceConParams();
            SchemaFetcherWithProgress fetcher =
                    SchemaFetcherWithProgress.getInstance(
                            wizard.getSourceSchemaCatalog().getConnectionParameters());
            Catalog detailed =
                    fetcher.fetchDetails(wizard.getSourceSchemaCatalog(), selectedSchemas);

            if (fetcher.isCanceled()) {
                return;
            }
            if (fetcher.getError() != null) {
                throw fetcher.getError();
            }
            if (detailed == null) return;

            srcCatalog = detailed;
            wizard.setSourceCatalog(srcCatalog);
            CMTConParamManager.getInstance()
                    .updateSelectedSourceCatalog(cp, selectedSchemas, srcCatalog);
            wizard.setSourceDBNode(srcCatalog);
        } catch (Exception e) {
            LOG.error("Failed to refresh detailed catalog in SchemaMappingPage", e);
        }
    }

    private List<String> collectSelectedSchemas(List<SrcTable> tables) {
        List<String> selected = new ArrayList<>();
        for (SrcTable srcTable : tables) {
            if (srcTable.isSelected()) {
                selected.add(srcTable.getSrcSchema());
            }
        }
        return selected;
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
        final SchemaCatalog sourceSchemaCatalog = wizard.getSourceSchemaCatalog();

        Set<String> selected = config.getSelectedSrcSchemas();
        for (SchemaEntry schemaEntry : sourceSchemaCatalog.getSchemas()) {
            SrcTable srcTable = createSrcTable(sourceSchemaCatalog, schemaEntry);
            String schemaName = schemaEntry.name();
            boolean isSelected;
            if (!selected.isEmpty()) {
                isSelected = selected.contains(schemaName);
            } else {
                isSelected = !schemaEntry.grantorSchema();
            }
            srcTable.setSelected(isSelected);

            if (config.targetIsSQL()) {
                srcTable.setTarDBType(Messages.msgCubridSQL);
            } else if (config.targetIsCSV()) {
                srcTable.setTarDBType(Messages.msgCubridCSV);
            } else if (config.targetIsXLS()) {
                srcTable.setTarDBType(Messages.msgCubridXLS);
            } else {
                srcTable.setTarDBType(Messages.msgCubridDump);
            }
            setOfflineTargetSchema(srcTable, schemaEntry);
        }
    }

    private void setOfflineTargetSchema(SrcTable srcTable, SchemaEntry schema) {
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
                            .filter(ts -> ts.getName().equals(schema.name()))
                            .map(Schema::getTargetSchemaName)
                            .findFirst();
            if (result.isPresent()) {
                tarSchemaName = result.get();
            }
        }

        srcTable.setTarSchema(
                StringUtils.isEmpty(tarSchemaName) ? srcTable.getSrcSchema() : tarSchemaName);
    }

    private SrcTable createSrcTable(SchemaCatalog schemaCatalog, SchemaEntry schema) {
        SrcTable srcTable = new SrcTable();
        srcTable.setSrcDBType(schemaCatalog.getDatabaseType().getName());
        srcTable.setSrcSchema(schema.name());
        srcTable.setNote(schema.grantorSchema());

        if (!schema.grantorSchema()) {
            srcTableList.add(0, srcTable);
        } else {
            srcTableList.add(srcTable);
        }
        return srcTable;
    }

    private void setOnlineData() {
        final SchemaCatalog sourceSchemaCatalog = wizard.getSourceSchemaCatalog();
        final Catalog tarCatalog = wizard.getTargetCatalog();

        Set<String> selected = config.getSelectedSrcSchemas();
        for (SchemaEntry schemaEntry : sourceSchemaCatalog.getSchemas()) {
            SrcTable srcTable = createSrcTable(sourceSchemaCatalog, schemaEntry);
            String schemaName = schemaEntry.name();
            boolean isSelected;
            if (!selected.isEmpty()) {
                isSelected = selected.contains(schemaName);
            } else {
                isSelected = !schemaEntry.grantorSchema();
            }
            srcTable.setSelected(isSelected);
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

    private boolean saveOnlineData(
            final List<SrcTable> currentSrcTables, final List<String> selectedSchemas) {
        final Catalog tarCatalog = wizard.getTargetCatalog();

        if (!ensureDetailedSrcCatalog(selectedSchemas)) {
            return false;
        }

        config.clearNewTargetShemaList();
        for (Schema srcSchema : srcCatalog.getSchemas()) {
            srcSchema.setTargetSchemaName(null);
        }

        List<String> checkNewSchemaDuplicate = new ArrayList<>();
        config.setTarSchemaDuplicate(false);

        for (SrcTable srcTable : currentSrcTables) {
            if (!srcTable.isSelected()) {
                continue;
            }
            if (!processOnlineTableMapping(srcTable, tarCatalog, checkNewSchemaDuplicate)) {
                return false;
            }
        }

        config.rebuildTargetSchemaListFromSource(srcCatalog);

        wizard.setSourceDBNode(srcCatalog);
        return true;
    }

    private boolean processOnlineTableMapping(
            SrcTable srcTable, Catalog tarCatalog, List<String> checkNewSchemaDuplicate) {
        if (!(tarCatalog.isDbHasUserSchema())) {
            srcTable.setTarSchema(null);
            Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
            if (srcSchema != null) {
                srcSchema.setTargetSchemaName(srcSchema.getName());
            }
            return true;
        }

        if (StringUtils.isEmpty(srcTable.getTarSchema())) {
            MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
            return false;
        }

        Schema targetSchema = tarCatalog.getSchemaByName(srcTable.getTarSchema());
        final Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
        if (srcSchema == null) return true;

        if (targetSchema != null) {
            srcSchema.setTargetSchemaName(targetSchema.getName());
        } else {
            configureNewTargetSchema(srcSchema, srcTable.getTarSchema(), checkNewSchemaDuplicate);
        }
        return true;
    }

    private void configureNewTargetSchema(
            Schema srcSchema, String targetSchemaName, List<String> checkNewSchemaDuplicate) {
        Schema newSchema = new Schema();
        newSchema.setName(targetSchemaName);
        newSchema.setNewTargetSchema(true);
        srcSchema.setTargetSchemaName(newSchema.getName());
        if (checkNewSchemaDuplicate.contains(newSchema.getName())) {
            config.setTarSchemaDuplicate(true);
        } else {
            checkNewSchemaDuplicate.add(newSchema.getName());
            config.setNewTargetSchema(newSchema.getName());
        }
    }

    /**
     * Ensures that srcCatalog has detailed objects for the selected schemas using cache first, then
     * lazy loading if needed.
     */
    private boolean ensureDetailedSrcCatalog(List<String> selectedSchemas) {
        if (selectedSchemas == null || selectedSchemas.isEmpty()) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }
        config.setSelectedSrcSchemas(selectedSchemas);
        if (config.sourceIsOnline()) {
            return ensureDetailedSrcCatalogOnline(selectedSchemas);
        } else {
            return ensureDetailedSrcCatalogOffline(selectedSchemas);
        }
    }

    /**
     * Logic to get the detailed catalog for online sources. Extracted the online-specific logic
     * from the existing ensureDetailedSrcCatalog.
     */
    private boolean ensureDetailedSrcCatalogOnline(List<String> selectedSchemas) {
        SchemaCatalog schemaCatalog = wizard.getSourceSchemaCatalog();
        ConnParameters cp = config.getSourceConParams();

        if (cp == null
                || schemaCatalog == null
                || schemaCatalog.getConnectionParameters() == null) {
            return srcCatalog != null;
        }

        Catalog cached =
                CMTConParamManager.getInstance().getSelectedSourceCatalog(cp, selectedSchemas);
        if (cached != null) {
            srcCatalog = cached;
            wizard.setSourceCatalog(srcCatalog);
            return true;
        }

        return fetchAndCacheDetailedCatalog(schemaCatalog, cp, selectedSchemas);
    }

    private boolean fetchAndCacheDetailedCatalog(
            SchemaCatalog schemaCatalog, ConnParameters cp, List<String> selectedSchemas) {
        try {
            SchemaFetcherWithProgress fetcher =
                    SchemaFetcherWithProgress.getInstance(schemaCatalog.getConnectionParameters());
            Catalog detailed = fetcher.fetchDetails(schemaCatalog, selectedSchemas);

            if (fetcher.isCanceled()) {
                return false;
            }
            if (fetcher.getError() != null) {
                throw fetcher.getError();
            }
            if (detailed == null) {
                return false;
            }

            srcCatalog = detailed;
            wizard.setSourceCatalog(srcCatalog);
            CMTConParamManager.getInstance()
                    .updateSelectedSourceCatalog(cp, selectedSchemas, detailed);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to fetch detailed source catalog in SchemaMappingPage", e);
            return false;
        }
    }

    /**
     * Logic to get the detailed catalog for offline sources. Uses the catalog pre-loaded via
     * config.setSrcCatalog.
     */
    private boolean ensureDetailedSrcCatalogOffline(List<String> selectedSchemas) {
        if (selectedSchemas == null || selectedSchemas.isEmpty()) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }

        Catalog full = config.getOfflineFullSrcCatalog();
        if (full == null) {
            full = config.getSrcCatalog();
            if (full != null) {
                config.setOfflineFullSrcCatalog(full);
            }
        }

        if (full == null) {
            MessageDialog.openError(
                    getShell(),
                    Messages.msgError,
                    "Source catalog is not loaded for offline source.");
            return false;
        }

        Catalog working = full.createCatalog();
        Set<String> selectedSet = new HashSet<>(selectedSchemas);

        List<Schema> toRemove = new ArrayList<>();
        for (Schema s : working.getSchemas()) {
            if (!selectedSet.contains(s.getName())) {
                toRemove.add(s);
            }
        }
        working.removeSchema(toRemove);

        srcCatalog = working;
        wizard.setSourceCatalog(srcCatalog);
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
            boolean addUserSchema,
            boolean splitSchema,
            List<SrcTable> currentSrcTables,
            List<String> selectedSchemas) {
        if (!ensureDetailedSrcCatalog(selectedSchemas)) {
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

            pathContext.schemaFullName.put(
                    MigrationConfiguration.SQLTABLE,
                    config.buildLocalFileFullPath(MigrationConfiguration.SQLTABLE, "schema", null));
        }

        pathContext.dataFullName.put(
                MigrationConfiguration.SQLTABLE,
                config.buildSQLDataFileFullPath(MigrationConfiguration.SQLTABLE, "objects"));

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
        pathContext.tableFullName.put(
                MigrationConfiguration.SQLTABLE,
                config.buildLocalFileFullPath(MigrationConfiguration.SQLTABLE, "class", null));

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
