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

import com.cubrid.cubridmigration.core.dbobject.Function;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlFunctionConfig;
import com.cubrid.cubridmigration.cubrid.CUBRIDSQLHelper;
import com.cubrid.cubridmigration.oracle.parser.PlConvOracleToCubrid;
import com.cubrid.cubridmigration.oracle.parser.ProcedureDDL;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.common.navigator.node.FunctionNode;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.utils.VerifyResultMessages;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/**
 * Edit target function DDL
 *
 * @author Dongmin Kim
 */
public class FunctionMappingView extends AbstractMappingView {

    private Composite container;
    private Text txtTargetSQL;
    private Button btnCreate;
    private Button btnReplace;
    private SourcePlcsqlFunctionConfig funcConfig;

    public FunctionMappingView(Composite parent) {
        super(parent);
    }

    /** Hide the view. */
    public void hide() {
        CompositeUtils.hideOrShowComposite(container, true);
    }

    /** Bring the view onto the top. */
    public void show() {
        CompositeUtils.hideOrShowComposite(container, false);
    }

    /**
     * Create controls
     *
     * @param parent of the controls
     */
    protected void createControl(Composite parent) {
        container = new Composite(parent, SWT.NONE);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.exclude = true;
        container.setLayoutData(gd);
        container.setVisible(false);
        container.setLayout(new GridLayout(2, false));

        btnCreate = new Button(container, SWT.CHECK);
        btnCreate.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        btnCreate.setText(Messages.lblCreate);
        btnCreate.addSelectionListener(
                new SelectionAdapter() {

                    public void widgetSelected(SelectionEvent ev) {
                        btnReplace.setEnabled(btnCreate.getSelection());
                        btnReplace.setSelection(btnCreate.getSelection());
                        txtTargetSQL.setEditable(btnCreate.getSelection());
                    }
                });

        btnReplace = new Button(container, SWT.CHECK);
        btnReplace.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        btnReplace.setText(Messages.lblReplace);

        createTargetPart(container);
    }

    /**
     * Create target part
     *
     * @param parent of the target part
     */
    private void createTargetPart(Composite parent) {
        Group grpTarget = new Group(parent, SWT.NONE);
        grpTarget.setLayout(new GridLayout(2, false));
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        grpTarget.setLayoutData(gd);
        grpTarget.setText(Messages.lblTarget);

        Label lblSQL = new Label(grpTarget, SWT.NONE);
        lblSQL.setText("Function");

        txtTargetSQL = new Text(grpTarget, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        txtTargetSQL.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        txtTargetSQL.setText("");
    }

    /**
     * Show source view DDL and target view DDL
     *
     * @param obj should be a ViewNode
     */
    public void showData(Object obj) {
        super.showData(obj);
        if (!(obj instanceof FunctionNode)) {
            return;
        }
        btnCreate.setEnabled(false);
        btnReplace.setEnabled(false);
        txtTargetSQL.setEditable(false);

        FunctionNode fNode = (FunctionNode) obj;
        Function func = fNode.getFunction();
        funcConfig = config.getExpPlcsqlFunctionCfg(func.getOwner(), func.getName());
        if (funcConfig == null) {
            txtTargetSQL.setEditable(false);
            return;
        }

        PlcsqlFunction targetFunc =
                config.getTargetPlcsqlFunctionSchema(func.getOwner(), func.getName());

        // Perform parsing when displaying on the screen. Skip parsing during migration.
        final CUBRIDSQLHelper ddlUtils = CUBRIDSQLHelper.getInstance(null);
        if (targetFunc.getHeaderDDL() == null && targetFunc.getBodyDDL() == null) {
            ProcedureDDL functionDDL =
                    PlConvOracleToCubrid.getProcedureDDL(targetFunc.getSourceDDL(), true);
            targetFunc.setHeaderDDL(functionDDL.getHeader());
            targetFunc.setBodyDDL(functionDDL.getBody());
        }
        String funcDDL = ddlUtils.getPlcsqlFunctionDDL(targetFunc, config.isAddUserSchema());
        txtTargetSQL.setText(funcDDL);
        // Set controls status
        btnCreate.setEnabled(true);
        btnCreate.setSelection(funcConfig.isCreate());
        btnReplace.setEnabled(btnCreate.getSelection());
        btnReplace.setSelection(funcConfig.isReplace() && btnCreate.getSelection());
        txtTargetSQL.setEditable(btnCreate.getSelection());
    }

    /**
     * Save UI to view object.
     *
     * @return VerifyResultMessages
     */
    public VerifyResultMessages save() {
        if (funcConfig == null) {
            return super.save();
        }
        // Save create option
        funcConfig.setCreate(btnCreate.getSelection());
        funcConfig.setReplace(btnReplace.getSelection());
        if (!btnCreate.getSelection()) {
            return super.save();
        }
        // Validate and Save more information
        PlcsqlFunction targetFunc =
                config.getTargetPlcsqlFunctionSchema(funcConfig.getOwner(), funcConfig.getName());
        if (targetFunc == null) {
            return super.save();
        }

        if (StringUtils.isBlank(txtTargetSQL.getText())) {
            return new VerifyResultMessages(Messages.msgErrEmptyViewDDL, null, null);
        }

        String targetSQL = txtTargetSQL.getText().trim();

        // Get Function name
        String regex =
                "(?i)CREATE(?:\\s+OR\\s+REPLACE)?\\s+FUNCTION\\s+(?:(\\[[a-zA-Z0-9_#]+\\]|[a-zA-Z0-9_#]+)\\.)?(\\[[a-zA-Z0-9_#]+\\]|[a-zA-Z0-9_#]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(targetSQL);

        if (matcher.find()) {
            String extractedOwner = matcher.group(1);
            if (extractedOwner != null) {
                extractedOwner = removeBrackets(extractedOwner);
                if (!targetFunc.getOwner().equalsIgnoreCase(extractedOwner)) {
                    targetFunc.setTargetOwner(extractedOwner);
                }
            }

            String extractedName = removeBrackets(matcher.group(2));
            if (!targetFunc.getName().equalsIgnoreCase(extractedName)) {
                targetFunc.setTargetName(extractedName.toLowerCase());
            }
        }

        ProcedureDDL functionDDL = PlConvOracleToCubrid.getProcedureDDL(targetSQL, false);
        targetFunc.setHeaderDDL(functionDDL.getHeader());
        targetFunc.setBodyDDL(functionDDL.getBody());
        return super.save();
    }

    private String removeBrackets(String procName) {
        Pattern pattern = Pattern.compile("^\\[(.+)]$");
        Matcher matcher = pattern.matcher(procName);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return procName;
    }
}
