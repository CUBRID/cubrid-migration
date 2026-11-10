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
package com.cubrid.cubridmigration.ui.wizard.dialog;

import com.cubrid.common.ui.StructuredContentProviderAdaptor;
import com.cubrid.common.ui.swt.table.CellEditorFactory;
import com.cubrid.common.ui.swt.table.ObjectArrayRowCellModifier;
import com.cubrid.common.ui.swt.table.TableViewerBuilder;
import com.cubrid.common.ui.swt.table.celleditor.CheckboxCellEditorFactory;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceIndexConfig;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.message.Messages;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TableSelectorDialog Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2012-7-23 created by Kevin Cao
 */
public class TableIndexSelectorDialog extends Dialog {

    private static final int IDX_CREATE = 3;
    private static final int IDX_OBJ = IDX_CREATE + 1;
    private static final int DESELECT_ALL_ID = IDialogConstants.CLIENT_ID + 1;
    private static final int BACK_TO_DEFAULT_ID = IDialogConstants.CLIENT_ID + 2;

    private TableViewer tvTableIndexes;
    private final MigrationConfiguration cfg;
    private final Map<SourceIndexConfig, Boolean> initialCreateStates = new LinkedHashMap<>();
    private final Map<SourceIndexConfig, Boolean> initialReplaceStates = new LinkedHashMap<>();

    /**
     * @author Kevin Cao
     */
    private final class IndexSelectionTableContentProvider
            extends StructuredContentProviderAdaptor {

        /**
         * @param inputElement input of the viewer
         * @return data list in the viewer
         */
        @SuppressWarnings("unchecked")
        public Object[] getElements(Object inputElement) {
            List<Object> data = new ArrayList<Object>();
            Set<SourceEntryTableConfig> tables = new LinkedHashSet<SourceEntryTableConfig>();
            for (SourceIndexConfig sc : (List<SourceIndexConfig>) inputElement) {
                tables.add(sc.getParent());
            }
            Set<SourceEntryTableConfig> displayedTables = new HashSet<SourceEntryTableConfig>();
            for (SourceEntryTableConfig tableConfig : tables) {
                List<SourceIndexConfig> tableIndexes = tableConfig.getIndexConfigList();
                if (tableIndexes.size() < 2) {
                    continue;
                }
                Map<String, List<SourceIndexConfig>> groups =
                        new LinkedHashMap<String, List<SourceIndexConfig>>();
                for (SourceIndexConfig index : tableIndexes) {
                    groups.computeIfAbsent(getColumnGroupKey(index), key -> new ArrayList<>())
                            .add(index);
                }
                String schema = tableConfig.getOwner() == null ? "" : tableConfig.getOwner();
                String tableName =
                        schema.isEmpty()
                                ? tableConfig.getName()
                                : schema + "." + tableConfig.getName();
                for (List<SourceIndexConfig> group : groups.values()) {
                    if (group.size() < 2) {
                        continue;
                    }
                    SourceIndexConfig selectedIndex = null;
                    for (SourceIndexConfig index : group) {
                        if (index.isCreate() && selectedIndex == null) {
                            selectedIndex = index;
                        }
                    }
                    for (int i = 0; i < group.size(); i++) {
                        SourceIndexConfig index = group.get(i);
                        List<String> columns =
                                cfg.getSourceIndexColumns(tableConfig, index.getName());
                        data.add(
                                new Object[] {
                                    displayedTables.add(tableConfig) ? tableName : "",
                                    i == 0 ? String.join(", ", columns) : "",
                                    index.getName(),
                                    index == selectedIndex,
                                    index
                                });
                    }
                }
            }
            return super.getElements(data);
        }
    }

    /**
     * @author Kevin Cao
     */
    private final class IndexSelectionCellModifier extends ObjectArrayRowCellModifier {

        /**
         * Modify cell element and table item
         *
         * @param ti TableItem
         * @param element Object[]
         * @param columnIdx int
         * @param value Object
         */
        protected void modify(TableItem ti, Object[] element, int columnIdx, Object value) {
            Object[] obj = (Object[]) ti.getData();
            if (value instanceof Boolean) {
                boolean bv = (Boolean) value;
                if (columnIdx == IDX_CREATE) {
                    SourceIndexConfig currentIndex = (SourceIndexConfig) obj[IDX_OBJ];
                    String currentGroup = getColumnGroupKey(currentIndex);
                    for (TableItem item : tvTableIndexes.getTable().getItems()) {
                        Object[] other = (Object[]) item.getData();
                        if (other == null) {
                            continue;
                        }
                        SourceIndexConfig otherIndex = (SourceIndexConfig) other[IDX_OBJ];
                        if (isSameTable(otherIndex.getParent(), currentIndex.getParent())
                                && currentGroup.equals(getColumnGroupKey(otherIndex))) {
                            boolean isCurrent = bv && (otherIndex == currentIndex);
                            other[IDX_CREATE] = isCurrent;
                            otherIndex.setCreate(isCurrent);
                            otherIndex.setReplace(isCurrent);
                            item.setImage(IDX_CREATE, CompositeUtils.getCheckImage(isCurrent));
                        }
                    }
                }
            }
            super.modify(ti, element, columnIdx, value);
        }

        @Override
        public void updateColumnImage(Object value, TableItem ti, final int idx) {
            // Keep the column header as text only (no checkbox icon)
        }
    }

    private static boolean isSameTable(SourceEntryTableConfig t1, SourceEntryTableConfig t2) {
        if (t1 == t2) {
            return true;
        }
        if (t1 == null || t2 == null) {
            return false;
        }
        return StringUtils.equalsIgnoreCase(t1.getName(), t2.getName())
                && StringUtils.equalsIgnoreCase(t1.getOwner(), t2.getOwner());
    }

    private String getColumnGroupKey(SourceIndexConfig index) {
        if (index == null) {
            return "";
        }
        SourceEntryTableConfig parent = index.getParent();
        List<String> columns =
                parent == null
                        ? Collections.emptyList()
                        : cfg.getSourceIndexColumns(parent, index.getName());
        if (columns.isEmpty()) {
            return "index:" + index.getName().toLowerCase(java.util.Locale.ROOT);
        }
        return "columns:" + MigrationConfiguration.normalizeIndexColumnList(columns);
    }

    public TableIndexSelectorDialog(Shell parentShell, MigrationConfiguration cfg) {
        super(parentShell);
        this.cfg = cfg;
        for (SourceEntryTableConfig tableConfig : cfg.getExpEntryTableCfg()) {
            for (SourceIndexConfig index : tableConfig.getIndexConfigList()) {
                initialCreateStates.put(index, index.isCreate());
                initialReplaceStates.put(index, index.isReplace());
            }
        }
    }

    /**
     * Create dialog area
     *
     * @param parent Composite
     * @return control
     */
    protected Control createDialogArea(Composite parent) {
        final Composite composite = (Composite) super.createDialogArea(parent);

        Label description = new Label(composite, SWT.WRAP);
        description.setText(Messages.lblSelectIndexesDescription);
        GridData descData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        descData.widthHint = 600;
        description.setLayoutData(descData);

        TableViewerBuilder tvBuilder = new TableViewerBuilder();
        tvBuilder.setColumnNames(
                new String[] {
                    Messages.lblSourceTableName,
                    Messages.colColumnName,
                    Messages.lblIndexName,
                    Messages.lblCreate
                });
        tvBuilder.setTableCursorSupported(true);
        tvBuilder.setColumnWidths(new int[] {140, 150, 240, 70});
        tvBuilder.setCellEditorClasses(
                new CellEditorFactory[] {null, null, null, new CheckboxCellEditorFactory()});
        tvBuilder.setCellModifier(new IndexSelectionCellModifier());
        tvBuilder.setContentProvider(new IndexSelectionTableContentProvider());
        tvTableIndexes =
                tvBuilder.buildTableViewer(
                        composite, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        ((GridData) tvTableIndexes.getTable().getLayoutData()).heightHint = 300;
        tvTableIndexes.getTable().getColumn(IDX_CREATE).setImage(null);

        cfg.retainOneIndexPerDuplicateColumnList();
        fillTable();
        return composite;
    }

    /** Fill data to table viewer */
    private void fillTable() {
        List<SourceIndexConfig> list = new ArrayList<SourceIndexConfig>();
        for (SourceEntryTableConfig setc : cfg.getExpEntryTableCfg()) {
            if (!setc.isCreateNewTable()) {
                continue;
            }
            if (setc.getIndexConfigList().size() < 2) {
                continue;
            }
            for (SourceIndexConfig sic : setc.getIndexConfigList()) {
                if (sic.getParent() == null) {
                    sic.setParent(setc);
                }
            }
            list.addAll(setc.getIndexConfigList());
        }
        tvTableIndexes.setInput(list);
        tvTableIndexes.getTable().getColumn(IDX_CREATE).setImage(null);
    }

    /** constrainShellSize */
    protected void constrainShellSize() {
        super.constrainShellSize();
        getShell().setText(Messages.lblSelectIndexes);
    }

    /**
     * createButtonsForButtonBar
     *
     * @param parent Composite
     */
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, DESELECT_ALL_ID, Messages.btnDeselectAll, false);
        createButton(parent, BACK_TO_DEFAULT_ID, Messages.btnBackToDefault, false);
        createButton(parent, IDialogConstants.OK_ID, Messages.btnOK, true);
        createButton(parent, IDialogConstants.CANCEL_ID, Messages.btnCancel, false);
        getButton(IDialogConstants.OK_ID).setEnabled(true);
    }

    /**
     * @return shell style
     */
    protected int getShellStyle() {
        return super.getShellStyle() | SWT.RESIZE | SWT.MAX;
    }

    /**
     * Don't support help
     *
     * @return false
     */
    public boolean isHelpAvailable() {
        return false;
    }

    /**
     * Button pressed
     *
     * @param buttonId OK or not
     */
    protected void buttonPressed(int buttonId) {
        if (buttonId == BACK_TO_DEFAULT_ID) {
            Set<String> defaultGroups = new HashSet<>();
            for (Map.Entry<SourceIndexConfig, Boolean> state : initialCreateStates.entrySet()) {
                SourceIndexConfig index = state.getKey();
                index.setCreate(state.getValue());
                index.setReplace(initialReplaceStates.get(index));
            }
            for (TableItem ti : tvTableIndexes.getTable().getItems()) {
                Object[] row = (Object[]) ti.getData();
                if (row == null) {
                    continue;
                }
                SourceIndexConfig index = (SourceIndexConfig) row[IDX_OBJ];
                SourceEntryTableConfig table = index.getParent();
                String tableKey =
                        (table.getOwner() == null
                                        ? ""
                                        : table.getOwner().toLowerCase(java.util.Locale.ROOT))
                                + "."
                                + table.getName().toLowerCase(java.util.Locale.ROOT);
                String groupKey = tableKey + "|" + getColumnGroupKey(index);
                boolean create = defaultGroups.add(groupKey);
                row[IDX_CREATE] = create;
                index.setCreate(create);
                index.setReplace(create);
                ti.setImage(IDX_CREATE, CompositeUtils.getCheckImage(create));
            }
            cfg.syncColumnUniqueFlags();
            return;
        }
        if (buttonId == DESELECT_ALL_ID) {
            if (!MessageDialog.openConfirm(
                    getShell(), Messages.msgConfirmation, Messages.msgConfirmDeselectAllIndexes)) {
                return;
            }
            for (TableItem ti : tvTableIndexes.getTable().getItems()) {
                Object[] obj = (Object[]) ti.getData();
                if (obj == null) {
                    continue;
                }
                SourceIndexConfig index = (SourceIndexConfig) obj[IDX_OBJ];
                obj[IDX_CREATE] = false;
                index.setCreate(false);
                index.setReplace(false);
                ti.setImage(IDX_CREATE, CompositeUtils.getCheckImage(false));
            }
            cfg.syncColumnUniqueFlags();
            return;
        }
        if (buttonId == IDialogConstants.CANCEL_ID) {
            restoreInitialIndexSelection();
            super.buttonPressed(buttonId);
            return;
        }
        if (buttonId == Dialog.OK) {
            List<UnselectedIndexesConfirmDialog.UnselectedIndex> unselectedIndexes =
                    new ArrayList<UnselectedIndexesConfirmDialog.UnselectedIndex>();
            Set<String> unselectedColumnGroups = new HashSet<String>();
            for (TableItem ti : tvTableIndexes.getTable().getItems()) {
                Object[] obj = (Object[]) ti.getData();
                if (obj == null) {
                    continue;
                }
                SourceIndexConfig sic = (SourceIndexConfig) obj[IDX_OBJ];
                boolean create = (Boolean) obj[IDX_CREATE];
                if (!create) {
                    SourceEntryTableConfig table = sic.getParent();
                    String columnGroupKey = getColumnGroupKey(sic);
                    boolean groupHasSelectedIndex = false;
                    for (TableItem otherItem : tvTableIndexes.getTable().getItems()) {
                        Object[] other = (Object[]) otherItem.getData();
                        if (other == null) {
                            continue;
                        }
                        SourceIndexConfig otherIndex = (SourceIndexConfig) other[IDX_OBJ];
                        if (isSameTable(table, otherIndex.getParent())
                                && columnGroupKey.equals(getColumnGroupKey(otherIndex))
                                && (Boolean) other[IDX_CREATE]) {
                            groupHasSelectedIndex = true;
                            break;
                        }
                    }
                    if (groupHasSelectedIndex) {
                        sic.setCreate(create);
                        sic.setReplace(create);
                        continue;
                    }
                    String schema = table.getOwner() == null ? "" : table.getOwner();
                    String tableName =
                            schema.isEmpty() ? table.getName() : schema + "." + table.getName();
                    String groupIdentity = tableName + "|" + columnGroupKey;
                    if (unselectedColumnGroups.add(groupIdentity)) {
                        List<String> columns = cfg.getSourceIndexColumns(table, sic.getName());
                        unselectedIndexes.add(
                                new UnselectedIndexesConfirmDialog.UnselectedIndex(
                                        tableName, String.join(", ", columns)));
                    }
                }
                sic.setCreate(create);
                sic.setReplace(create);
            }
            cfg.syncColumnUniqueFlags();
            if (!unselectedIndexes.isEmpty()) {
                UnselectedIndexesConfirmDialog confirmDialog =
                        new UnselectedIndexesConfirmDialog(getShell(), unselectedIndexes);
                int confirmResult = confirmDialog.open();
                if (confirmResult != Dialog.OK) {
                    return;
                }
            }
        }
        super.buttonPressed(buttonId);
    }

    /** Restore in-memory index settings when the dialog is cancelled. */
    private void restoreInitialIndexSelection() {
        for (Map.Entry<SourceIndexConfig, Boolean> state : initialCreateStates.entrySet()) {
            SourceIndexConfig index = state.getKey();
            index.setCreate(state.getValue());
            index.setReplace(initialReplaceStates.get(index));
        }
        cfg.syncColumnUniqueFlags();
    }

    /** Also restore settings when the user closes the dialog with the window close control. */
    @Override
    protected void handleShellCloseEvent() {
        restoreInitialIndexSelection();
        super.handleShellCloseEvent();
    }
}
