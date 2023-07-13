/*
 * Copyright 2016 CUBRID Corporation. All rights reserved by Search Solution. 
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
		btnReplace.setVisible(false);
		
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
	 * @param obj should be a GrantNode
	 */
	public void showData(Object obj) {
		if (config.targetIsOnline() && !config.isTargetDBAGroup()) {
			btnCreate.setEnabled(false);
		}
		
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
		
		Grant tgrant = config.getTargetGrantSchema(grantConfig.getTarget());
		if (tgrant == null) {
			grpTarget.setEditable(false);
			return;
		}
		grpTarget.setEditable(false);
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
		//Grant cannot be modified regardless of choice
		//grpTarget.setEditable(btnCreate.getSelection());
	}
	
	private class GrantInfoComposite {
		private Group grp;		
		private Text txtAuthType;
		private Text txtGrantor;
		private Text txtClassOwner;
		private Text txtClassName;
		private Text txtGrantee;
		private Text isGrantable;
		
		private Grant grant;
		
		GrantInfoComposite(Composite parent, String name) {
			grp = new Group(parent, SWT.NONE);
			grp.setLayout(new GridLayout(2, false));
			GridData gd = new GridData(SWT.LEFT, SWT.FILL, false, true);
			gd.widthHint = PART_WIDTH;
			grp.setLayoutData(gd);
			grp.setText(name);
			
			Label lblAuthType = new Label(grp, SWT.NONE);
			lblAuthType.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblAuthType.setText(Messages.lblGrantAuthType);
			
			txtAuthType = new Text(grp, SWT.BORDER);
			txtAuthType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtAuthType.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtAuthType.setText("");
			
			Label lblGrantor = new Label(grp, SWT.NONE);
			lblGrantor.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblGrantor.setText(Messages.lblGrantGrantor);
			
			txtGrantor = new Text(grp, SWT.BORDER);
			txtGrantor.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtGrantor.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtGrantor.setText("");
			
			Label lblClassOwner = new Label(grp, SWT.NONE);
			lblClassOwner.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblClassOwner.setText(Messages.lblGrantClassOwner);
			
			txtClassOwner = new Text(grp, SWT.BORDER);
			txtClassOwner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtClassOwner.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtClassOwner.setText("");
			
			Label lblClassName = new Label(grp, SWT.NONE);
			lblClassName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblClassName.setText(Messages.lblGrantClassName);
			
			txtClassName = new Text(grp, SWT.BORDER);
			txtClassName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtClassName.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtClassName.setText("");
			
			Label lblGrantee = new Label(grp, SWT.NONE);
			lblGrantee.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblGrantee.setText(Messages.lblGrantGrantee);
			
			txtGrantee = new Text(grp, SWT.BORDER);
			txtGrantee.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtGrantee.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtGrantee.setText("");
			
			Label lblGrantable = new Label(grp, SWT.NONE);
			lblGrantable.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblGrantable.setText(Messages.lblGrantGrantable);
			
			isGrantable = new Text(grp, SWT.BORDER);
			isGrantable.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			isGrantable.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			isGrantable.setText("");
		}
		
		/**
		 * Set grant to UI
		 * 
		 * @param grant
		 */
		void setGrant(Grant grant) {
			this.grant = grant;
			txtAuthType.setText(grant.getAuthType().toUpperCase());
			txtGrantor.setText(grant.getGrantorName().toUpperCase());
			txtClassOwner.setText(grant.getClassOwner().toUpperCase());
			txtClassName.setText(grant.getClassName().toUpperCase());
			txtGrantee.setText(grant.getGranteeName().toUpperCase());
			isGrantable.setText(grant.isGrantable() ? "YES" : "NO");
		}
		
		/**
		 * Set the edit-able status of class
		 * 
		 * @param editable
		 */
		void setEditable(boolean editable) {
			txtAuthType.setEditable(editable);
			txtGrantor.setEditable(editable);
			txtClassOwner.setEditable(editable);
			txtClassName.setEditable(editable);
			txtGrantee.setEditable(editable);
			isGrantable.setEditable(editable);
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
			final String newAuthType = txtAuthType.getText().trim();
			final String newGrantor = txtGrantor.getText().trim();
			final String newClassOwner = txtClassOwner.getText().trim();
			final String newClassName = txtClassName.getText().trim();
			final String newGrantee = txtGrantee.getText().trim();
			final boolean newGrantable = isGrantable.getText().trim().equals("YES") ? true : false;
			
			//Save target grant
			grant.setAuthType(newAuthType);
			grant.setGrantorName(newGrantor);
			grant.setClassOwner(newClassOwner);
			grant.setClassName(newClassName);
			grant.setGranteeName(newGrantee);
			grant.setGrantable(newGrantable);
			return new VerifyResultMessages();
		}
	}
}
