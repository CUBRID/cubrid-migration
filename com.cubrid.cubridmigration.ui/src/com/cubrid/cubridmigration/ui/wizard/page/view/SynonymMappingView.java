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

import org.apache.commons.lang.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.swt.widgets.Composite;

import com.cubrid.cubridmigration.core.dbobject.Synonym;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.cubrid.CUBRIDDataTypeHelper;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.common.navigator.node.SynonymNode;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.utils.MigrationCfgUtils;
import com.cubrid.cubridmigration.ui.wizard.utils.VerifyResultMessages;

/**
 * SynonymMappingView response to show entry synonym configuration
 * 
 * @author Dongmin Kim
 */
public class SynonymMappingView extends
		AbstractMappingView {

	private Composite container;
	private SynonymInfoComposite grpSource;
	private SynonymInfoComposite grpTarget;
	
	private Button btnCreate;
	private Button btnReplace;
	
	private SourceSynonymConfig synonymConfig;
	
	public SynonymMappingView(Composite parent) {
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
		grpSource = new SynonymInfoComposite(parent, Messages.lblSource);
		grpSource.setEditable(false);
	}
	
	/**
	 * @param parent of target object
	 * 
	 */
	protected void createTargetPart(Composite parent) {
		grpTarget = new SynonymInfoComposite(parent, Messages.lblTarget);
	}
	
	/**
	 * @param obj should be a SynonymNode
	 */
	public void showData(Object obj) {
		super.showData(obj);
		if (!(obj instanceof SynonymNode)) {
			return;
		}
		Synonym synonym = ((SynonymNode) obj).getSynonym();
		if (synonym == null) {
			grpTarget.setEditable(false);
			return;
		}
		synonymConfig = config.getExpSynonymCfg(synonym.getOwner(), synonym.getName());
		if (synonymConfig == null) {
			grpTarget.setEditable(false);
			return;
		}
		grpSource.setSynonym(synonym);
		btnCreate.setSelection(synonymConfig.isCreate());
		
		Synonym tsynonym = config.getTargetSynonymSchema(synonymConfig.getTargetOwner(), synonymConfig.getTarget());
		if (tsynonym == null) {
			grpTarget.setEditable(false);
			return;
		}
		grpTarget.setEditable(synonymConfig.isCreate());
		grpTarget.setSynonym(tsynonym);

		setButtonsStatus();
	}
	
	/**
	 * Verify input and save UI to object
	 * 
	 * @return VerifyResultMessages
	 */
	public VerifyResultMessages save() {
		if (grpSource.synonym == null || grpTarget.synonym == null) {
			return super.save();
		}
		synonymConfig.setCreate(btnCreate.getSelection());
		synonymConfig.setReplace(btnReplace.getSelection());
		if (synonymConfig.isCreate()) {
			final VerifyResultMessages result = grpTarget.save();
			if (!result.hasError()) {
				synonymConfig.setTarget(grpTarget.synonym.getName());
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
	
	private class SynonymInfoComposite {
		
		private Group grp;
		private Text txtName;
		private Text txtOwnerName;
		private Text txtObjectName;
		private Text txtObjectOwnerName;
		
		private Synonym synonym;
		
		SynonymInfoComposite(Composite parent, String name) {
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
			
			txtOwnerName = new Text(grp, SWT.BORDER);
			txtOwnerName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtOwnerName.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtOwnerName.setText("");
			
			Label lblTargetObjectName = new Label(grp, SWT.NONE);
			lblTargetObjectName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblTargetObjectName.setText(Messages.lblSynonymObjectName);
			
			txtObjectName = new Text(grp, SWT.BORDER);
			txtObjectName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtObjectName.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtObjectName.setText("");
			
			Label lblTargetName = new Label(grp, SWT.NONE);
			lblTargetName.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			lblTargetName.setText(Messages.lblSynonymObjectOwnerName);
			
			txtObjectOwnerName = new Text(grp, SWT.BORDER);
			txtObjectOwnerName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			txtObjectOwnerName.setTextLimit(CUBRIDDataTypeHelper.DB_OBJ_NAME_MAX_LENGTH);
			txtObjectOwnerName.setText("");
			
			Label sep = new Label(grp, SWT.NONE);
			sep.setText("");
		}
		
		/**
		 * Set synonym to UI
		 * 
		 * @param synonym
		 */
		void setSynonym(Synonym synonym) {
			this.synonym = synonym;
			txtName.setText(synonym.getName());
			txtOwnerName.setText(synonym.getOwner() == null ? "" : synonym.getOwner());
			txtObjectOwnerName.setText(synonym.getObjectOwner() == null ? "" : synonym.getObjectOwner());
			txtObjectName.setText(synonym.getObjectName());
		}
		
		/**
		 * Set the edit-able status of class
		 * 
		 * @param editable
		 */
		void setEditable(boolean editable) {
			txtName.setEditable(editable);
			txtOwnerName.setEditable(editable);
			txtObjectOwnerName.setEditable(editable);
			txtObjectName.setEditable(editable);
		}
		
		/**
		 * Save UI to synonym including validation
		 * 
		 * @return VerifyResultMessages
		 */
		VerifyResultMessages save() {
			if (synonym == null) {
				return new VerifyResultMessages();
			}
			final String newName = txtName.getText().trim().toLowerCase(Locale.US);
			if (!MigrationCfgUtils.verifyTargetDBObjName(newName)) {
				return new VerifyResultMessages(Messages.msgErrInvalidSynonymName, null, null);
			}
			final String newOwnerName = txtOwnerName.getText().trim();
			if (!MigrationCfgUtils.verifyTargetDBObjName(newOwnerName)) {
				return new VerifyResultMessages(Messages.msgErrInvalidSynonymName, null, null);
			}
			if (StringUtils.isBlank(txtObjectOwnerName.getText())) {
				txtObjectOwnerName.setFocus();
				return new VerifyResultMessages(Messages.msgErrEmptyStartValue, null, null);
			}
			if (StringUtils.isBlank(txtObjectName.getText())) {
				txtObjectName.setFocus();
				return new VerifyResultMessages(Messages.msgErrEmptyStartValue, null, null);
			}
			if (!newName.equalsIgnoreCase(synonym.getName())) {
				if (config.isTargetSynonymNameInUse(newName)) {
					return new VerifyResultMessages(Messages.bind(
							Messages.errDuplicateSynonymName, newName), null, null);
				}
			}
			
			//Save target synonym
			synonym.setName(newName);
			synonym.setOwner(newOwnerName);
			synonym.setObjectName(txtObjectName.getText());
			synonym.setObjectOwner(txtObjectOwnerName.getText());
			return new VerifyResultMessages();
		}
	}
}
