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

import com.cubrid.cubridmigration.ui.message.Messages;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.util.List;

/** Confirmation dialog that presents columns with no selected index. */
public class UnselectedIndexesConfirmDialog extends TitleAreaDialog {

    private static final int[] COLUMN_WIDTHS = new int[] {260, 420};
    private final List<UnselectedIndex> indexes;

    public UnselectedIndexesConfirmDialog(Shell parentShell, List<UnselectedIndex> indexes) {
        super(parentShell);
        this.indexes = indexes;
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.titleUnselectedIndexesConfirm);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        setTitle(Messages.titleUnselectedIndexesConfirm);
        setMessage(Messages.msgUnselectedIndexesConfirm);

        Composite container = new Composite(area, SWT.NONE);
        container.setLayout(new GridLayout(1, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Table table =
                new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.widthHint = 700;
        tableData.heightHint = 260;
        table.setLayoutData(tableData);

        String[] headers = {Messages.lblSourceTableName, Messages.colColumnName};
        for (int i = 0; i < headers.length; i++) {
            TableColumn column = new TableColumn(table, SWT.LEFT);
            column.setText(headers[i]);
            column.setWidth(COLUMN_WIDTHS[i]);
        }

        for (UnselectedIndex index : indexes) {
            TableItem item = new TableItem(table, SWT.NONE);
            item.setText(new String[] {index.tableName, index.columns});
        }

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, Messages.btnOK, true);
        createButton(parent, IDialogConstants.CANCEL_ID, Messages.btnBack, false);
    }

    @Override
    protected int getShellStyle() {
        return super.getShellStyle() | SWT.RESIZE | SWT.MAX;
    }

    public static final class UnselectedIndex {
        private final String tableName;
        private final String columns;

        public UnselectedIndex(String tableName, String columns) {
            this.tableName = tableName;
            this.columns = columns;
        }
    }
}
