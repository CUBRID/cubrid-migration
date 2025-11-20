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
package com.cubrid.cubridmigration.ui.wizard.page.view;

import com.cubrid.common.ui.swt.table.celleditor.CheckboxCellEditorFactory;
import com.cubrid.common.ui.swt.table.listener.CheckBoxColumnSelectionListener;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.message.Messages;

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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SchemaTableView {

    private static final String MSG_GRANT_SCHEMA = Messages.msgGrantSchema;
    private static final String MSG_MAIN_SCHEMA = Messages.msgMainSchema;

    private final TableViewer srcTableViewer;
    private final String[] propertyList = {
        "",
        Messages.sourceSchema,
        Messages.msgNote,
        Messages.msgSrcType,
        Messages.targetSchema,
        Messages.msgTarType
    };
    private String[] tarSchemaNameArray;
    private final MigrationConfiguration config;
    private Catalog srcCatalog;
    private Catalog tarCatalog;
    private SchemaLabelProvider labelProvider;

    public static class SrcTable {
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
            this.note = note ? MSG_GRANT_SCHEMA : MSG_MAIN_SCHEMA;
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

    private final class SchemaContentProvider implements IStructuredContentProvider {
        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof List) {
                @SuppressWarnings("unchecked")
                final List<SrcTable> schemaObj = (ArrayList<SrcTable>) inputElement;
                return schemaObj.toArray();
            }
            return new Object[0];
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // No action needed
        }

        @Override
        public void dispose() {
            // No action needed
        }
    }

    private final class SchemaLabelProvider implements ITableLabelProvider {
        private boolean firstVisible = true;

        public void setFirstVisible(boolean firstVisible) {
            this.firstVisible = firstVisible;
        }

        @Override
        public String getColumnText(Object element, int columnIndex) {
            final SrcTable obj = (SrcTable) element;
            switch (columnIndex) {
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
                case 0:
                default:
                    return null;
            }
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex == 0) {
                final SrcTable srcTable = (SrcTable) element;
                if (firstVisible) {
                    final boolean isSelected =
                            MSG_MAIN_SCHEMA.equals(srcTable.getNote()) || srcTable.isSelected();
                    srcTable.setSelected(isSelected);
                    return isSelected ? CompositeUtils.CHECK_IMAGE : CompositeUtils.UNCHECK_IMAGE;
                }
                return srcTable.isSelected()
                        ? CompositeUtils.CHECK_IMAGE
                        : CompositeUtils.UNCHECK_IMAGE;
            }
            return null;
        }

        @Override
        public void removeListener(ILabelProviderListener listener) {
            // No action needed
        }

        @Override
        public boolean isLabelProperty(Object element, String property) {
            return false;
        }

        @Override
        public void dispose() {
            // No action needed
        }

        @Override
        public void addListener(ILabelProviderListener listener) {
            // No action needed
        }
    }

    private final class SchemaCellModifier implements ICellModifier {
        @Override
        public boolean canModify(Object element, String property) {
            return propertyList[4].equals(property) || propertyList[0].equals(property);
        }

        @Override
        public Object getValue(Object element, String property) {
            final SrcTable srcTable = (SrcTable) element;
            if (propertyList[4].equals(property)) {
                if (config.targetIsOnline()) {
                    for (int i = 0; i < tarSchemaNameArray.length; i++) {
                        if (tarSchemaNameArray[i].equalsIgnoreCase(srcTable.getTarSchema())) {
                            return i;
                        }
                    }
                    return 0; // Default to first item if not found
                }
                return srcTable.getTarSchema().toUpperCase(Locale.US);
            } else if (propertyList[0].equals(property)) {
                return srcTable.isSelected();
            }
            return null;
        }

        @Override
        public void modify(Object element, String property, Object value) {
            final TableItem tabItem = (TableItem) element;
            final SrcTable srcTable = (SrcTable) tabItem.getData();

            if (propertyList[4].equals(property)) {
                if (config.targetIsOnline()) {
                    srcTable.setTarSchema(tarSchemaNameArray[(Integer) value]);
                } else {
                    srcTable.setTarSchema(((String) value).toUpperCase(Locale.US));
                }
                srcTableViewer.refresh();
            } else if (propertyList[0].equals(property)) {
                final boolean newSelectedState = !srcTable.isSelected();
                tabItem.setImage(CompositeUtils.getCheckImage(newSelectedState));
                srcTable.setSelected(newSelectedState);
            }
        }
    }

    public SchemaTableView(Composite parent, MigrationConfiguration config) {
        this.config = config;
        final Group srcTableViewerGroup = new Group(parent, SWT.NONE);
        srcTableViewerGroup.setLayout(new FillLayout());
        srcTableViewer = new TableViewer(srcTableViewerGroup, SWT.FULL_SELECTION);

        srcTableViewer.setContentProvider(new SchemaContentProvider());
        labelProvider = new SchemaLabelProvider();
        srcTableViewer.setLabelProvider(labelProvider);
        srcTableViewer.setColumnProperties(propertyList);

        final TableLayout tableLayout = new TableLayout();
        tableLayout.addColumnData(new ColumnWeightData(5, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(13, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));
        tableLayout.addColumnData(new ColumnWeightData(20, true));

        srcTableViewer.getTable().setLayout(tableLayout);
        srcTableViewer.getTable().setLinesVisible(true);
        srcTableViewer.getTable().setHeaderVisible(true);

        createTableColumn(propertyList[0], SWT.LEFT, new CheckBoxColumnSelectionListener());
        srcTableViewer.getTable().getColumn(0).setImage(CompositeUtils.UNCHECK_IMAGE);
        createTableColumn(propertyList[1], SWT.LEFT, null);
        createTableColumn(propertyList[2], SWT.LEFT, null);
        createTableColumn(propertyList[3], SWT.LEFT, null);
        createTableColumn(propertyList[4], SWT.LEFT, null);
        createTableColumn(propertyList[5], SWT.LEFT, null);

        updateCellEditors();
    }

    private void createTableColumn(String text, int style, SelectionListener listener) {
        final TableColumn column = new TableColumn(srcTableViewer.getTable(), style);
        column.setText(text);
        if (listener != null) {
            column.addSelectionListener(listener);
        }
    }

    public void updateCellEditors() {
        if (config.targetIsOnline()) {
            tarSchemaNameArray = getDropdownSchemaNames();
            final CellEditor[] editors =
                    new CellEditor[] {
                        new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
                        null,
                        null,
                        null,
                        new ComboBoxCellEditor(
                                srcTableViewer.getTable(), tarSchemaNameArray, SWT.READ_ONLY),
                        null
                    };
            srcTableViewer.setCellEditors(editors);
        } else {
            final CellEditor[] editors =
                    new CellEditor[] {
                        new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
                        null,
                        null,
                        null,
                        config.isAddUserSchema()
                                ? new TextCellEditor(srcTableViewer.getTable())
                                : null,
                        null
                    };
            srcTableViewer.setCellEditors(editors);
        }
        srcTableViewer.setCellModifier(new SchemaCellModifier());
    }

    private String[] getDropdownSchemaNames() {
        final List<String> dropDownSchemaList = new ArrayList<>();
        final Optional<Catalog> targetCatalogOptional = Optional.ofNullable(this.tarCatalog);
        targetCatalogOptional.ifPresent(
                targetCatalog ->
                        targetCatalog
                                .getSchemas()
                                .forEach(schema -> dropDownSchemaList.add(schema.getName())));

        final Optional<Catalog> srcCatalogOptional = Optional.ofNullable(this.srcCatalog);
        srcCatalogOptional.ifPresent(
                srcCatalog ->
                        srcCatalog
                                .getSchemas()
                                .forEach(
                                        schema -> {
                                            final String schemaName =
                                                    schema.getName().toUpperCase(Locale.US);
                                            if (!dropDownSchemaList.contains(schemaName)) {
                                                dropDownSchemaList.add(schemaName);
                                            }
                                        }));

        return config.isTargetDBAGroup()
                ? dropDownSchemaList.toArray(String[]::new)
                : new String[] {config.getTargetConParams().getConUser().toUpperCase(Locale.US)};
    }

    public void setSrcCatalog(Catalog srcCatalog) {
        this.srcCatalog = srcCatalog;
    }

    public void setTarCatalog(Catalog tarCatalog) {
        this.tarCatalog = tarCatalog;
    }

    public void setInput(List<SrcTable> input) {
        srcTableViewer.setInput(input);
        labelProvider.setFirstVisible(false);
    }

    public TableViewer getViewer() {
        return srcTableViewer;
    }

    public List<SrcTable> getSrcTableList() {
        final TableItem[] items = srcTableViewer.getTable().getItems();
        final List<SrcTable> tables = new ArrayList<>(items.length);
        for (final TableItem item : items) {
            final SrcTable srcTable = (SrcTable) item.getData();
            srcTable.setSelected(item.getImage(0) == CompositeUtils.CHECK_IMAGE);
            tables.add(srcTable);
        }
        return tables;
    }
}
