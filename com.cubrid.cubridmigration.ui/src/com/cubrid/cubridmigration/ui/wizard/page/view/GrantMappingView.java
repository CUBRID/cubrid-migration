/*
 * Copyright (C) 2009 Search Solution Corporation. All rights reserved by Search Solution. 
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

import java.util.Locale;

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

import com.cubrid.cubridmigration.core.dbobject.Grant;
import com.cubrid.cubridmigration.core.engine.config.SourceGrantConfig;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.common.navigator.node.GrantNode;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.utils.MigrationCfgUtils;
import com.cubrid.cubridmigration.ui.wizard.utils.VerifyResultMessages;

/**
 * GrantMappingView response to show entry grant configuration
 * 
 * @author Dongmin Kim
 *
 */
public class GrantMappingView extends 
		AbstractMappingView {
	
	private Composite container;
	private GrantInfoComposite grpSource;
	private GrantInfoComposite grpTarget;
	
	private Button btnCreate;
	private Button btnReplace;
	
	private SourceGrantConfig grantConfig;
	
	public GrantMappingView(Composite parent) {
		super(parent);
	}

	/**
	 * Hide
	 * 
	 */
	public void hide() {
		CompositeUtils.hideOrShowComposite(container, true);
	}

	/**
	 * Show
	 */
	public void show() {
		CompositeUtils.hideOrShowComposite(container, false);
	}
	
	/**
	 * @param parent of the composites
	 */
	protected void createControl(Composite parent) {
		container = new Composite(parent, SWT.NONE);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd.exclude = true;
		container.setLayoutData(gd);
		container.setVisible(false);
		container.setLayout(new GridLayout(2, true));

		btnCreate = new Button(container, SWT.CHECK);
		btnCreate.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		btnCreate.setText(Messages.lblCreate);
		btnCreate.addSelectionListener(new SelectionAdapter() {

			public void widgetSelected(SelectionEvent ev) {
				setButtonsStatus();
			}

		});

		btnReplace = new Button(container, SWT.CHECK);
		btnReplace.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		btnReplace.setText(Messages.lblReplace);

		createSourcePart(container);
		createTargetPart(container);
		
	}
	
	/**
	 * 
	 * @param parent of source object
	 * 
	 */
	protected void createSourcePart(Composite parent) {
		grpSource = new GrantInfoComposite(parent, Messages.lblSource);
		grpSource.setEditable(false);
	}
	
	/**
	 * @param parent of target object
	 * 
	 */
	protected void createTargetPart(Composite parent) {
		grpTarget = new GrantInfoComposite(parent, Messages.lblTarget);
	}
	
	/**
	 * @param obj should be a SynonymNode
	 */
	public void showData(Object obj) {
		super.showData(obj);
		if (!(obj instanceof GrantNode)) {
			return;
		}
		Grant grant = ((GrantNode) obj).getGrant();
		if (grant == null) {
			grpTarget.setEditable(false);
			return;
		}
		grantConfig = config.getExpGrantCfg(grant.getOwner(), grant.getName());
		if (grantConfig == null) {
			grpTarget.setEditable(false);
			return;
		}
		grpSource.setGrant(grant);
		btnCreate.setSelection(grantConfig.isCreate());
		
		Grant tgrant = config.getTargetGrantSchema(grantConfig.getTargetOwner(), grantConfig.getTarget());
		if (tgrant == null) {
			grpTarget.setEditable(false);
			return;
		}
		grpTarget.setEditable(grantConfig.isCreate());
		grpTarget.setGrant(tgrant);

		setButtonsStatus();
	}
	
	/**
	 * Verify input and save UI to object
	 * 
	 * @return VerifyResultMessages
	 */
	public VerifyResultMessages save() {
		if (grpSource.grant == null || grpTarget.grant == null) {
			return super.save();
		}
		grantConfig.setCreate(btnCreate.getSelection());
		grantConfig.setReplace(btnReplace.getSelection());
		if (grantConfig.isCreate()) {
			final VerifyResultMessages result = grpTarget.save();
			if (!result.hasError()) {
				grantConfig.setTarget(grpTarget.grant.getName());
			}
			return result;
		}
		return super.save();
	}
	
	/**
	 * Set the buttons status
	 * 
	 */
	private void setButtonsStatus() {
		btnReplace.setSelection(btnCreate.getSelection());
		btnReplace.setEnabled(btnCreate.getSelection());
		grpTarget.setEditable(btnCreate.getSelection());
	}
	
	private class GrantInfoComposite {
		private Group grp;
		private Text txtName;
		private Text txtOwner;
//		private Text txtAuthType;
//		private Text txtGrantor;
//		private Text txtGrantee;
//		private Text txtClassOwner;
//		private Text txtClassName;
//		private Text isGrantable;
		
		private Grant grant;
		
		GrantInfoComposite(Composite parent, String name) {
			grp = new Group(parent, SWT.NONE);
			grp.setLayout(new GridLayout(2, false));
			GridData gd = new GridData(SWT.LEFT, SWT.FILL, false, true);
			gd.widthHint = PART_WIDTH;
			grp.setLayoutData(gd);
			grp.setText(name);
			
			Label lblTableName = new Label(grp, SWT.NONE);
			lblTableName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblTableName.setText(Messages.lblSynonymName);
			
			txtName = new Text(grp, SWT.BORDER);
			txtName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtName.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtName.setText("");
			
			Label lblOwnerName = new Label(grp, SWT.NONE);
			lblOwnerName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblOwnerName.setText(Messages.lblSynonymOwnerName);
			
			txtOwner = new Text(grp, SWT.BORDER);
			txtOwner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtOwner.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtOwner.setText("");
		}
		
		/**
		 * Set grant to UI
		 * 
		 * @param grant
		 */
		void setGrant(Grant grant) {
			this.grant = grant;
			txtName.setText(grant.getName());
			txtOwner.setText(grant.getOwner() == null ? "" : grant.getOwner());
		}
		
		/**
		 * Set the edit-able status of class
		 * 
		 * @param editable
		 */
		void setEditable(boolean editable) {
			txtName.setEditable(editable);
		}
		
		/**
		 * Save UI to grant including validation
		 * 
		 * @return VerifyResultMessages
		 */
		VerifyResultMessages save() {
			if (grant == null) {
				return new VerifyResultMessages();
			}
			final String newName = txtName.getText().trim().toLowerCase(Locale.US);
			if (!MigrationCfgUtils.verifyTargetDBObjName(newName)) {
				return new VerifyResultMessages(Messages.msgErrInvalidSynonymName, null, null);
			}
			final String newOwnerName = txtOwner.getText().trim();
			if (!MigrationCfgUtils.verifyTargetDBObjName(newOwnerName)) {
				return new VerifyResultMessages(Messages.msgErrInvalidSynonymName, null, null);
			}
			
			//Save target grant
			grant.setName(newName);
			grant.setOwner(newOwnerName);
			return new VerifyResultMessages();
		}
	}
}
