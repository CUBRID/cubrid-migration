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

import com.cubrid.common.ui.swt.table.celleditor.CheckboxCellEditorFactory;
import com.cubrid.common.ui.swt.table.celleditor.EditableComboBoxCellEditor;
import com.cubrid.common.ui.swt.table.listener.CheckBoxColumnSelectionListener;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.common.dialog.DetailMessageDialog;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.MigrationWizard;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

public class SchemaMappingPage extends MigrationWizardPage {

    private Logger logger = LogUtil.getLogger(SchemaMappingPage.class);

    private MigrationWizard wizard = null;
    private MigrationConfiguration config = null;

    private String[] propertyList = {
        "",
        Messages.sourceSchema,
        Messages.msgNote,
        Messages.msgSrcType,
        Messages.targetSchema,
        Messages.msgTarType
    };
    private String[] tarSchemaNameArray = null;

    Catalog srcCatalog;
    Catalog tarCatalog;

    TableViewer srcTableViewer = null;
    TableViewer tarTableViewer = null;

    ArrayList<String> tarSchemaNameList = new ArrayList<String>();
    ArrayList<SrcTable> srcTableList = new ArrayList<SrcTable>();

    List<Schema> srcSchemaList = null;
    List<Schema> tarSchemaList = null;

    ComboBoxCellEditor comboEditor = null;

    TextCellEditor textEditor = null;

    private boolean firstVisible = true;

    Map<String, String> schemaFullName;
    Map<String, String> tableFullName;
    Map<String, String> viewFullName;
    Map<String, String> viewQuerySpecFullName;
    Map<String, String> pkFullName;
    Map<String, String> fkFullName;
    Map<String, String> dataFullName;
    Map<String, String> indexFullName;
    Map<String, String> serialFullName;
    Map<String, String> updateStatisticFullName;
    Map<String, String> schemaFileListFullName;
    Map<String, String> synonymFileListFullName;
    Map<String, Map<String, String>> grantFileListFullName;
    Map<String, List<String>> tableDataFileListFullName;
    Map<String, String> plcsqlProcedureHeaderFullName;
    Map<String, String> plcsqlFunctionHeaderFullName;
    Map<String, String> plcsqlProcedureFullName;
    Map<String, String> plcsqlFunctionFullName;
    Map<String, Map<String, String>> plcsqlProcedureFileListFullName;
    Map<String, Map<String, String>> plcsqlFunctionFileListFullName;

    protected class SrcTable {
        private boolean isSelected;

        private String note;

        private String srcSchema;
        private String srcDBType;

        private String tarSchema;
        private String tarDBType;

        public boolean isSelected() {
            return isSelected;
        }

        public void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public void setNote(boolean note) {
            if (note == true) {
                setNote(Messages.msgGrantSchema);
            } else {
                setNote(Messages.msgMainSchema);
            }
        }

        public String getSrcSchema() {
            return srcSchema;
        }

        public void setSrcSchema(String srcSchema) {
            this.srcSchema = srcSchema;
        }

        public String getSrcDBType() {
            return srcDBType;
        }

        public void setSrcDBType(String srcDBtype) {
            this.srcDBType = srcDBtype;
        }

        public String getTarSchema() {
            return tarSchema;
        }

        public void setTarSchema(String tarSchema) {
            this.tarSchema = tarSchema;
        }

        public String getTarDBType() {
            return tarDBType;
        }

        public void setTarDBType(String tarDBType) {
            this.tarDBType = tarDBType;
        }
    }

    public SchemaMappingPage(String pageName) {
        super(pageName);
    }

    @Override
    public void createControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createSrcTable(container);

        setControl(container);
    }

    private void createSrcTable(Composite container) {
        Group srcTableViewerGroup = new Group(container, SWT.NONE);
        srcTableViewerGroup.setLayout(new FillLayout());
        srcTableViewer = new TableViewer(srcTableViewerGroup, SWT.FULL_SELECTION);

        srcTableViewer.setContentProvider(
                new IStructuredContentProvider() {
                    @Override
                    public Object[] getElements(Object inputElement) {
                        if (inputElement instanceof List) {

                            @SuppressWarnings("unchecked")
                            List<SrcTable> schemaObj = (ArrayList<SrcTable>) inputElement;

                            return schemaObj.toArray();
                        } else {
                            return new Object[0];
                        }
                    }

                    @Override
                    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

                    @Override
                    public void dispose() {}
                });

        srcTableViewer.setLabelProvider(
                new ITableLabelProvider() {
                    @Override
                    public String getColumnText(Object element, int columnIndex) {
                        SrcTable obj = (SrcTable) element;

                        switch (columnIndex) {
                            case 0:
                                return null;
                            case 1:
                                return obj.getSrcSchema();
                            case 2:
                                return obj.getNote();
                            case 3:
                                return obj.getSrcDBType();
                            case 4:
                                return obj.getTarSchema().toUpperCase(Locale.US);
                            case 5:
                                return obj.getTarDBType();
                            default:
                                return null;
                        }
                    }

                    @Override
                    public Image getColumnImage(Object element, int columnIndex) {
                        SrcTable srcTable = (SrcTable) element;

                        if (columnIndex == 0) {
                            if (firstVisible) {
                                if (srcTable.getNote().equals(Messages.msgMainSchema)
                                        || srcTable.isSelected()) {
                                    srcTable.setSelected(true);
                                    return CompositeUtils.CHECK_IMAGE;
                                } else {
                                    srcTable.setSelected(false);
                                    return CompositeUtils.UNCHECK_IMAGE;
                                }
                            } else {
                                if (srcTable.isSelected()) {
                                    return CompositeUtils.CHECK_IMAGE;
                                } else {
                                    return CompositeUtils.UNCHECK_IMAGE;
                                }
                            }
                        }
                        return null;
                    }

                    @Override
                    public void removeListener(ILabelProviderListener listener) {}

                    @Override
                    public boolean isLabelProperty(Object element, String property) {
                        return false;
                    }

                    @Override
                    public void dispose() {}

                    @Override
                    public void addListener(ILabelProviderListener listener) {}
                });

        srcTableViewer.setColumnProperties(propertyList);

        TableLayout tableLayout = new TableLayout();

        tableLayout.addColumnData(new ColumnWeightData(5, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(13, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));

        srcTableViewer.getTable().setLayout(tableLayout);
        srcTableViewer.getTable().setLinesVisible(true);
        srcTableViewer.getTable().setHeaderVisible(true);

        TableColumn col1 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);
        TableColumn col2 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);
        TableColumn col3 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);
        TableColumn col4 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);
        TableColumn col5 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);
        TableColumn col6 = new TableColumn(srcTableViewer.getTable(), SWT.LEFT);

        final SelectionListener[] selectionListeners =
                new SelectionListener[] {
                    new CheckBoxColumnSelectionListener(), null, null, null, null, null
                };
        CompositeUtils.setTableColumnSelectionListener(srcTableViewer, selectionListeners);

        col1.setImage(CompositeUtils.getCheckImage(false));
        col2.setText(propertyList[1]);
        col3.setText(propertyList[2]);
        col4.setText(propertyList[3]);
        col5.setText(propertyList[4]);
        col6.setText(propertyList[5]);
    }

    private void getSchemaValues() {
        Catalog targetCatalog = wizard.getTargetCatalog();
        Catalog sourceCatalog = wizard.getOriginalSourceCatalog();

        List<Schema> targetSchemaList = targetCatalog.getSchemas();
        List<Schema> sourceSchemaList = sourceCatalog.getSchemas();

        tarSchemaNameList = new ArrayList<String>();
        ArrayList<String> dropDownSchemaList = new ArrayList<String>();

        for (Schema schema : targetSchemaList) {
            tarSchemaNameList.add(schema.getName());
            dropDownSchemaList.add(schema.getName());
        }

        for (Schema schema : sourceSchemaList) {
            if (tarSchemaNameList.contains(schema.getName().toUpperCase())) {
                continue;
            }

            dropDownSchemaList.add(schema.getName());
        }

        if (targetCatalog.isDBAGroup()) {
            tarSchemaNameArray = dropDownSchemaList.toArray(new String[] {});

        } else {
            tarSchemaNameArray =
                    new String[] {targetCatalog.getConnectionParameters().getConUser()};
        }
    }

    private void setOnlineEditor() {
        comboEditor =
                new EditableComboBoxCellEditor(
                        srcTableViewer.getTable(), tarSchemaNameArray, SWT.READ_ONLY);

        CellEditor[] editors =
                new CellEditor[] {
                    new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
                    null,
                    null,
                    null,
                    comboEditor,
                    null
                };

        srcTableViewer.setCellEditors(editors);
        srcTableViewer.setCellModifier(
                new ICellModifier() {

                    @Override
                    public void modify(Object element, String property, Object value) {
                        TableItem tabItem = (TableItem) element;
                        SrcTable srcTable = (SrcTable) tabItem.getData();

                        if (property.equals(propertyList[4])) {
                            srcTable.setTarSchema(returnValue((Integer) value, tabItem));
                            addSelectCheckboxValue();
                            srcTableViewer.refresh();
                        } else if (property.equals(propertyList[0])) {
                            tabItem.setImage(CompositeUtils.getCheckImage(!srcTable.isSelected));
                            srcTable.setSelected(!srcTable.isSelected);
                        }
                    }

                    @Override
                    public Object getValue(Object element, String property) {
                        if (property.equals(propertyList[4])) {
                            return returnIndex(element);
                        } else if (property.equals(propertyList[0])) {
                            return true;
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public boolean canModify(Object element, String property) {
                        if (property.equals(propertyList[4]) || property.equals(propertyList[0])) {
                            return true;
                        } else {
                            return false;
                        }
                    }

                    public int returnIndex(Object element) {
                        if (element instanceof SrcTable) {
                            SrcTable srcTable = (SrcTable) element;

                            for (int i = 0; i < tarSchemaNameArray.length; i++) {
                                if (tarSchemaNameArray[i].equals(srcTable.getTarSchema())) {
                                    return i;
                                }
                            }
                        }

                        return 0;
                    }

                    public String returnValue(int index, TableItem item) {
                        return tarSchemaNameArray[index];
                    }

                    private void addSelectCheckboxValue() {
                        TableItem[] tableItems = srcTableViewer.getTable().getItems();
                        for (int i = 0; i < tableItems.length; i++) {
                            if (tableItems[i].getImage().equals(CompositeUtils.CHECK_IMAGE)) {
                                srcTableList.get(i).setSelected(true);
                            } else {
                                srcTableList.get(i).setSelected(false);
                            }
                        }
                    }
                });
    }

    private void setOfflineEditor(boolean isAddUserSchema) {
        textEditor = new TextCellEditor(srcTableViewer.getTable());

        CellEditor[] editors =
                new CellEditor[] {
                    new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
                    null,
                    null,
                    null,
                    isAddUserSchema ? textEditor : null,
                    null
                };

        srcTableViewer.setCellEditors(editors);
        srcTableViewer.setCellModifier(
                new ICellModifier() {

                    @Override
                    public boolean canModify(Object element, String property) {
                        if (property.equals(propertyList[4]) || property.equals(propertyList[0])) {
                            return true;
                        } else {
                            return false;
                        }
                    }

                    @Override
                    public Object getValue(Object element, String property) {
                        if (property.equals(propertyList[4])) {
                            return ((SrcTable) element).getTarSchema().toUpperCase(Locale.US);
                        } else if (property.equals(propertyList[0])) {
                            return true;
                        } else {
                            return null;
                        }
                    }

                    @Override
                    public void modify(Object element, String property, Object value) {
                        TableItem tabItem = (TableItem) element;
                        SrcTable srcTable = (SrcTable) tabItem.getData();

                        if (property.equals(propertyList[4])) {
                            srcTable.setTarSchema(((String) value).toUpperCase(Locale.US));
                            addSelectCheckboxValue();
                            srcTableViewer.refresh();
                        } else if (property.equals(propertyList[0])) {
                            tabItem.setImage(CompositeUtils.getCheckImage(!srcTable.isSelected));
                            srcTable.setSelected(!srcTable.isSelected);
                        }
                    }

                    private void addSelectCheckboxValue() {
                        TableItem[] tableItems = srcTableViewer.getTable().getItems();
                        for (int i = 0; i < tableItems.length; i++) {
                            if (tableItems[i].getImage().equals(CompositeUtils.CHECK_IMAGE)) {
                                srcTableList.get(i).setSelected(true);
                            } else {
                                srcTableList.get(i).setSelected(false);
                            }
                        }
                    }
                });
    }

    private void setOfflineSchemaMappingPage() {
        setOfflineData();
        setOfflineEditor(config.isAddUserSchema());
    }

    private void setOfflineData() {
        srcCatalog = wizard.getOriginalSourceCatalog().createCatalog();
        srcSchemaList = srcCatalog.getSchemas();
        Map<String, Schema> scriptSchemaMap = config.getScriptSchemaMapping();
        List<Schema> targetSchemaList = config.getTargetSchemaList();

        for (Schema schema : srcSchemaList) {
            SrcTable srcTable = new SrcTable();
            srcTable.setSrcDBType(srcCatalog.getDatabaseType().getName());
            srcTable.setSrcSchema(schema.getName());
            srcTable.setNote(schema.isGrantorSchema());

            if (config.targetIsSQL()) {
                srcTable.setTarDBType(Messages.msgCubridSQL);
            } else if (config.targetIsCSV()) {
                srcTable.setTarDBType(Messages.msgCubridCSV);
            } else if (config.targetIsXLS()) {
                srcTable.setTarDBType(Messages.msgCubridXLS);
            } else {
                srcTable.setTarDBType(Messages.msgCubridDump);
            }

            if (!schema.isGrantorSchema()) {
                srcTableList.add(0, srcTable);
            } else {
                srcTableList.add(srcTable);
            }

            if (scriptSchemaMap.size() != 0) {
                logger.info("offline script schema");
                Schema scriptSchema = scriptSchemaMap.get(srcTable.getSrcSchema());
                if (scriptSchema != null) {
                    srcTable.setTarSchema(
                            scriptSchemaMap.get(srcTable.getSrcSchema()).getTargetSchemaName());
                    srcTable.setSelected(
                            scriptSchemaMap.get(srcTable.getSrcSchema()).isMigration());
                }

                if (srcTable.getTarSchema() == null || srcTable.getTarSchema().isEmpty()) {
                    srcTable.setTarSchema(srcTable.getSrcSchema());
                }

            } else if (config.isAddUserSchema() && !targetSchemaList.isEmpty()) {
                for (Schema targetSchema : targetSchemaList) {
                    if (targetSchema.getName().equals(schema.getName())) {
                        srcTable.setTarSchema(targetSchema.getTargetSchemaName());
                        break;
                    }
                }
                if (srcTable.getTarSchema() == null || srcTable.getTarSchema().isEmpty()) {
                    srcTable.setTarSchema(srcTable.getSrcSchema());
                }
            } else {
                srcTable.setTarSchema(srcTable.getSrcSchema());
            }
        }
    }

    private void setOnlineSchemaMappingPage() {
        setOnlineData();
        getSchemaValues();
        setOnlineEditor();
    }

    private void setOnlineData() {
        srcCatalog = wizard.getOriginalSourceCatalog().createCatalog();
        tarCatalog = wizard.getTargetCatalog();

        srcSchemaList = srcCatalog.getSchemas();
        tarSchemaList = tarCatalog.getSchemas();

        Map<String, Schema> scriptSchemaMap = config.getScriptSchemaMapping();

        for (Schema schema : srcSchemaList) {
            SrcTable srcTable = new SrcTable();
            srcTable.setSrcDBType(srcCatalog.getDatabaseType().getName());
            srcTable.setSrcSchema(schema.getName());
            srcTable.setNote(schema.isGrantorSchema());

            if (!schema.isGrantorSchema()) {
                srcTableList.add(0, srcTable);
            } else {
                srcTableList.add(srcTable);
            }

            srcTable.setTarDBType(tarCatalog.getDatabaseType().getName());

            if (scriptSchemaMap.size() != 0) {
                logger.info("script schema");

                Schema scriptSchema = scriptSchemaMap.get(srcTable.getSrcSchema());
                String tarSchemaName = null;
                if (scriptSchema != null) {
                    srcTable.setTarSchema(scriptSchema.getTargetSchemaName().toUpperCase());
                    tarSchemaName = scriptSchema.getTargetSchemaName().toUpperCase();
                    srcTable.setSelected(
                            scriptSchemaMap.get(srcTable.getSrcSchema()).isMigration());
                }

                if (tarSchemaName == null || tarSchemaName.isEmpty()) {
                    srcTable.setTarSchema(tarCatalog.getName());
                }

                logger.info("srcTable target schema : " + srcTable.getTarSchema());
            } else {
                int version =
                        tarCatalog.getVersion().getDbMajorVersion() * 10
                                + tarCatalog.getVersion().getDbMinorVersion();

                if (tarCatalog.isDBAGroup() && version >= 112) {
                    srcTable.setTarSchema(srcTable.getSrcSchema());
                } else {
                    srcTable.setTarSchema(tarCatalog.getSchemas().get(0).getName());
                }
            }
        }
    }

    @Override
    protected void afterShowCurrentPage(PageChangedEvent event) {
        wizard = getMigrationWizard();
        config = wizard.getMigrationConfig();

        if (srcTableList != null) {
            srcTableList.clear();
        }

        setTitle(wizard.getStepNoMsg(this) + Messages.schemaMappingPageTitle);
        if ((config.targetIsOnline() && !wizard.getTargetCatalog().isDBAGroup())
                || (!config.targetIsOnline()) && !config.isAddUserSchema()) {
            setDescription(Messages.schemaMappingPageDescriptionUncorrectable);
        } else {
            setDescription(Messages.schemaMappingPageDescription);
        }

        if (!config.targetIsOnline()) {
            setOfflineSchemaMappingPage();
        } else {
            setOnlineSchemaMappingPage();
        }

        srcTableViewer.setInput(srcTableList);
        firstVisible = firstVisible ? false : true;
    }

    @Override
    protected void handlePageLeaving(PageChangingEvent event) {
        if (!isPageComplete()) {
            return;
        }
        if (isGotoNextPage(event)) {
            Catalog originalSrcCatlog = wizard.getOriginalSourceCatalog();
            if (originalSrcCatlog.getSchemas().size() != srcCatalog.getSchemas().size()) {
                srcCatalog.getSchemas().clear();
                srcCatalog.setSchemas(originalSrcCatlog.getSchemas());
            }
            if (config.targetIsOnline()) {
                event.doit = saveOnlineData();
            } else {
                event.doit = saveOfflineData(config.isAddUserSchema(), config.isSplitSchema());
            }
        }
    }

    private boolean saveOnlineData() {
        if (!isSelectCheckbox()) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }

        List<String> checkNewSchemaDuplicate = new ArrayList<String>();
        for (SrcTable srcTable : srcTableList) {
            if (!(tarCatalog.isDbHasUserSchema())) {
                srcTable.setTarSchema(null);
                if (!srcTable.isSelected) {
                    srcCatalog.removeOneSchema(srcCatalog.getSchemaByName(srcTable.getSrcSchema()));
                }
                continue;
            }

            if (srcTable.getTarSchema().isEmpty()) {
                MessageDialog.openError(
                        getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
                return false;
            }

            if (!srcTable.isSelected) {
                Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
                srcCatalog.removeOneSchema(srcSchema);
                continue;
            }

            logger.info("src schema : " + srcTable.getSrcSchema());
            logger.info("tar schema : " + srcTable.getTarSchema());

            Schema targetSchema = tarCatalog.getSchemaByName(srcTable.getTarSchema());

            if (targetSchema != null) {
                Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
                srcSchema.setTargetSchemaName(targetSchema.getName());

            } else {
                logger.info("need to create a new schema for target db");
                Schema newSchema = new Schema();
                newSchema.setName(srcTable.getTarSchema());
                newSchema.setNewTargetSchema(true);

                Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
                srcSchema.setTargetSchemaName(newSchema.getName());

                if (checkNewSchemaDuplicate.contains(newSchema.getName())) {
                    config.setTarSchemaDuplicate(true);
                    continue;
                }

                checkNewSchemaDuplicate.add(newSchema.getName());
                config.setNewTargetSchema(newSchema.getName());
            }
        }
        wizard.setSourceCatalog(srcCatalog);
        getMigrationWizard().setSourceDBNode(srcCatalog);

        return true;
    }

    private boolean saveOfflineData(boolean addUserSchema, boolean splitSchema) {
        if (!isSelectCheckbox()) {
            MessageDialog.openError(
                    getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
            return false;
        }

        List<Schema> targetSchemaList = new ArrayList<Schema>();
        schemaFullName = new HashMap<String, String>();
        tableFullName = new HashMap<String, String>();
        viewFullName = new HashMap<String, String>();
        viewQuerySpecFullName = new HashMap<String, String>();
        pkFullName = new HashMap<String, String>();
        fkFullName = new HashMap<String, String>();
        dataFullName = new HashMap<String, String>();
        indexFullName = new HashMap<String, String>();
        serialFullName = new HashMap<String, String>();
        updateStatisticFullName = new HashMap<String, String>();
        schemaFileListFullName = new HashMap<String, String>();
        synonymFileListFullName = new HashMap<String, String>();
        grantFileListFullName = new HashMap<String, Map<String, String>>();
        tableDataFileListFullName = new HashMap<String, List<String>>();
        plcsqlProcedureFullName = new HashMap<>();
        plcsqlFunctionFullName = new HashMap<>();
        plcsqlProcedureHeaderFullName = new HashMap<>();
        plcsqlFunctionHeaderFullName = new HashMap<>();
        plcsqlProcedureFileListFullName = new HashMap<>();
        plcsqlFunctionFileListFullName = new HashMap<>();

        for (SrcTable srcTable : srcTableList) {
            String targetSchemaName = srcTable.getTarSchema();
            if (addUserSchema
                    && srcTable.isSelected()
                    && (targetSchemaName == null
                            || targetSchemaName.isEmpty()
                            || StringUtils.trimToEmpty(targetSchemaName).equals(""))) {
                MessageDialog.openError(
                        getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
                return false;
            }

            if (!srcTable.isSelected) {
                Schema srcSchema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
                srcCatalog.removeOneSchema(srcSchema);
                continue;
            }

            Schema schema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
            schema.setTargetSchemaName(srcTable.getTarSchema());
            targetSchemaList.add(schema);

            String schemaName =
                    srcCatalog.getDatabaseType().isSupportMultiSchema()
                            ? srcTable.getSrcSchema()
                            : config.getSrcConnOwner();

            if (splitSchema) {
                tableFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "class", null));
                viewFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "vclass", null));
                viewQuerySpecFullName.put(
                        schemaName,
                        config.buildLocalFileFullPath(schemaName, "vclass_query_spec", null));
                pkFullName.put(schemaName, config.buildLocalFileFullPath(schemaName, "pk", null));
                fkFullName.put(schemaName, config.buildLocalFileFullPath(schemaName, "fk", null));
                serialFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "serial", null));
                schemaFileListFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "info", null));
                synonymFileListFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "synonym", null));

                List<Grant> grantList = schema.getGrantList();
                for (Grant grant : grantList) {
                    if (!grantFileListFullName.containsKey(schemaName)) {
                        grantFileListFullName.put(schemaName, new HashMap<String, String>());
                    }
                    Map<String, String> grantMap = grantFileListFullName.get(schemaName);
                    if (!grantMap.containsKey(grant.getSourceObjectOwner())) {
                        grantMap.put(
                                grant.getSourceObjectOwner(),
                                config.buildLocalFileFullPath(
                                        schemaName, "grant", grant.getSourceObjectOwner()));
                    }
                }

                plcsqlProcedureHeaderFullName.put(
                        schemaName,
                        config.buildLocalFileFullPath(schemaName, "procedure_header", null));
                plcsqlFunctionHeaderFullName.put(
                        schemaName,
                        config.buildLocalFileFullPath(schemaName, "function_header", null));

                plcsqlProcedureFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "procedure", null));
                plcsqlFunctionFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "function", null));

                Map<String, String> procedureFiles = new HashMap<>();
                schema.getPlcsqlProcedures()
                        .forEach(
                                proc ->
                                        procedureFiles.put(
                                                proc.getName(),
                                                config.buildPlcsqlProcedureFileFullPath(
                                                        schemaName, proc.getName(), "procedure")));
                plcsqlProcedureFileListFullName.put(schemaName, procedureFiles);

                Map<String, String> functionFiles = new HashMap<>();
                schema.getPlcsqlFunctions()
                        .forEach(
                                func ->
                                        functionFiles.put(
                                                func.getName(),
                                                config.buildPlcsqlProcedureFileFullPath(
                                                        schemaName, func.getName(), "function")));
                plcsqlFunctionFileListFullName.put(schemaName, functionFiles);

            } else {
                schemaFullName.put(
                        schemaName, config.buildLocalFileFullPath(schemaName, "schema", null));
            }
            if (config.isOneTableOneFile()) {
                List<String> tableList = new ArrayList<>();
                schema.getTables()
                        .forEach(
                                table ->
                                        tableList.add(
                                                config.buildDataFileFullPath(
                                                        schemaName, table.getName())));
                tableDataFileListFullName.put(schemaName, tableList);
            }
            dataFullName.put(schemaName, config.buildDataFileFullPath(schemaName, "objects"));
            indexFullName.put(
                    schemaName, config.buildLocalFileFullPath(schemaName, "indexes", null));
            updateStatisticFullName.put(
                    schemaName, config.buildLocalFileFullPath(schemaName, "updatestatistic", null));
        }

        if (!checkFileRepository()) {
            return false;
        }

        if (config.getTargetSchemaList().size() > 0) {
            config.removeTargetSchemaList();
        }
        config.setTargetSchemaList(targetSchemaList);
        config.setTargetSchemaFileName(schemaFullName);
        config.setTargetTableFileName(tableFullName);
        config.setTargetViewFileName(viewFullName);
        config.setTargetViewQuerySpecFileName(viewQuerySpecFullName);
        config.setTargetDataFileName(dataFullName);
        config.setTargetIndexFileName(indexFullName);
        config.setTargetPkFileName(pkFullName);
        config.setTargetFkFileName(fkFullName);
        config.setTargetSerialFileName(serialFullName);
        config.setTargetUpdateStatisticFileName(updateStatisticFullName);
        config.setTargetSchemaFileListName(schemaFileListFullName);
        config.setTargetSynonymFileName(synonymFileListFullName);
        config.setTargetGrantFileName(grantFileListFullName);
        config.setTargetTableDataFileName(tableDataFileListFullName);
        config.setTargetAllPlcsqlProcedureHeaderFileName(plcsqlProcedureHeaderFullName);
        config.setTargetAllPlcsqlFunctionHeaderFileName(plcsqlFunctionHeaderFullName);
        config.setTargetAllPlcsqlProcedureFileName(plcsqlProcedureFullName);
        config.setTargetAllPlcsqlFunctionFileName(plcsqlFunctionFullName);
        config.setTargetPlcsqlProcedureFileName(plcsqlProcedureFileListFullName);
        config.setTargetPlcsqlFunctionFileName(plcsqlFunctionFileListFullName);

        wizard.setSourceCatalog(srcCatalog);
        getMigrationWizard().setSourceDBNode(srcCatalog);

        return true;
    }

    /**
     * Check if overwriting to a file
     *
     * @param schemaFullName
     * @param dataFullName
     * @param indexFullName
     * @return boolean
     */
    private boolean checkFileRepository() {
        StringBuffer buffer = new StringBuffer();

        if (srcCatalog.getDatabaseType().isSupportMultiSchema()) {
            for (SrcTable srcTable : srcTableList) {
                if (!srcTable.isSelected) {
                    continue;
                }
                duplicateFilePath(buffer, srcTable.getSrcSchema());
            }
        } else {
            duplicateFilePath(buffer, config.getSrcConnOwner());
        }

        if (buffer.length() > 0) {
            return DetailMessageDialog.openConfirm(
                    getShell(),
                    Messages.msgConfirmation,
                    Messages.msgDuplicateFileDetail,
                    buffer.toString());
        }
        return true;
    }

    private void duplicateFilePath(StringBuffer buffer, String schemaName) {
        String lineSeparator = System.getProperty("line.separator");
        try {
            if (config.isSplitSchema()) {
                File tableFile = new File(tableFullName.get(schemaName));
                File viewFile = new File(viewFullName.get(schemaName));
                File viewQuerySpecFile = new File(viewQuerySpecFullName.get(schemaName));
                File pkFile = new File(pkFullName.get(schemaName));
                File fkFile = new File(fkFullName.get(schemaName));
                File serialFile = new File(serialFullName.get(schemaName));
                File infoFile = new File(schemaFileListFullName.get(schemaName));
                File synonymFile = new File(synonymFileListFullName.get(schemaName));
                Map<String, String> grantFilePaths = grantFileListFullName.get(schemaName);
                Iterator<String> keys = null;
                if (grantFilePaths != null) {
                    keys = grantFilePaths.keySet().iterator();
                }

                if (tableFile.exists()) {
                    buffer.append(tableFile.getCanonicalPath()).append(lineSeparator);
                }
                if (viewFile.exists()) {
                    buffer.append(viewFile.getCanonicalPath()).append(lineSeparator);
                }
                if (viewQuerySpecFile.exists()) {
                    buffer.append(viewQuerySpecFile.getCanonicalPath()).append(lineSeparator);
                }
                if (pkFile.exists()) {
                    buffer.append(pkFile.getCanonicalPath()).append(lineSeparator);
                }
                if (fkFile.exists()) {
                    buffer.append(fkFile.getCanonicalPath()).append(lineSeparator);
                }
                if (serialFile.exists()) {
                    buffer.append(serialFile.getCanonicalPath()).append(lineSeparator);
                }
                if (infoFile.exists()) {
                    buffer.append(infoFile.getCanonicalPath()).append(lineSeparator);
                }
                if (synonymFile.exists()) {
                    buffer.append(synonymFile.getCanonicalPath()).append(lineSeparator);
                }
                if (keys != null) {
                    while (keys.hasNext()) {
                        File grantFile = new File(grantFilePaths.get(keys.next()));
                        if (grantFile.exists()) {
                            buffer.append(grantFile.getCanonicalPath()).append(lineSeparator);
                        }
                    }
                }
            } else {
                File schemaFile = new File(schemaFullName.get(schemaName));
                if (schemaFile.exists()) {
                    buffer.append(schemaFile.getCanonicalPath()).append(lineSeparator);
                }
            }

            if (config.isOneTableOneFile()) {
                for (String tableDataFilePath : tableDataFileListFullName.get(schemaName)) {
                    File tableDataFile = new File(tableDataFilePath);
                    if (tableDataFile.exists()) {
                        buffer.append(tableDataFile.getCanonicalPath()).append(lineSeparator);
                    }
                }
            } else {
                File dataFile = new File(dataFullName.get(schemaName));
                if (dataFile.exists()) {
                    buffer.append(dataFile.getCanonicalPath()).append(lineSeparator);
                }
            }

            File indexFile = new File(indexFullName.get(schemaName));
            File updateStatisticFile = new File(updateStatisticFullName.get(schemaName));

            if (indexFile.exists()) {
                buffer.append(indexFile.getCanonicalPath()).append(lineSeparator);
            }
            if (updateStatisticFile.exists()) {
                buffer.append(updateStatisticFile.getCanonicalPath()).append(lineSeparator);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isSelectCheckbox() {
        TableItem[] tableItems = srcTableViewer.getTable().getItems();
        for (int i = 0; i < tableItems.length; i++) {
            if (tableItems[i].getImage().equals(CompositeUtils.CHECK_IMAGE)) {
                srcTableList.get(i).setSelected(true);
            } else {
                srcTableList.get(i).setSelected(false);
            }
        }

        for (SrcTable srcTable : srcTableList) {
            if (srcTable.isSelected) {
                return true;
            }
        }
        return false;
    }
}
