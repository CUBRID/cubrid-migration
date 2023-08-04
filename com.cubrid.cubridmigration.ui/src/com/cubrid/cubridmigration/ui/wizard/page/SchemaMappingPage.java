/*
 * Copyright (C) 2009 Search Solution Corporation. All rights reserved by Search Solution. 
 * Copyright (c) 2016 CUBRID Corporation.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnWeightData;
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
import org.eclipse.ui.PlatformUI;

import com.cubrid.common.ui.swt.table.celleditor.CheckboxCellEditorFactory;
import com.cubrid.common.ui.swt.table.celleditor.EditableComboBoxCellEditor;
import com.cubrid.common.ui.swt.table.listener.CheckBoxColumnSelectionListener;
import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.ui.common.CompositeUtils;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.wizard.MigrationWizard;

public class SchemaMappingPage extends MigrationWizardPage {
	
	private Logger logger = LogUtil.getLogger(SchemaMappingPage.class);
	
	private MigrationWizard wizard = null;
	private MigrationConfiguration config = null;
	
	private String[] propertyList = {"", Messages.sourceSchema, Messages.msgNote, Messages.msgSrcType, Messages.targetSchema, Messages.msgTarType};
	private String[] tarSchemaNameArray =  null;
	
	Catalog srcCatalog;
	Catalog tarCatalog;
		
	TableViewer srcTableViewer = null;
	TableViewer tarTableViewer = null;
	
	ArrayList<String> tarSchemaNameList = new ArrayList<String>();	
	ArrayList<SrcTable> srcTableList = new ArrayList<SrcTable>();
	
	List<Schema> srcSchemaList = null;
	List<Schema> tarSchemaList = null;
	
	EditableComboBoxCellEditor comboEditor = null;
	
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
	Map<String, String> grantFileListFullName;
	
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
		
		public void setNote(String note){
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
		
		srcTableViewer.setContentProvider(new IStructuredContentProvider() {
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
		
		srcTableViewer.setLabelProvider(new ITableLabelProvider() {
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
					return obj.getTarSchema();
				case 5:
					return obj.getTarDBType();
				default:
					return null;
						
				}
			}
			
			@Override
			public Image getColumnImage(Object element, int columnIndex) {
				SrcTable srcTable  = (SrcTable) element;
				
				if (columnIndex == 0) {
					if (firstVisible) {
						if (srcTable.getNote().equals(Messages.msgMainSchema) || srcTable.isSelected()) {
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
			public boolean isLabelProperty(Object element, String property) {return false;}
			
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
		
		final SelectionListener[] selectionListeners = new SelectionListener[] {
				new CheckBoxColumnSelectionListener(),
				null,
				null,
				null,
				null,
				null};
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
			tarSchemaNameArray = new String[] {targetCatalog.getConnectionParameters().getConUser()};
		}
	}
	
	private void setOnlineEditor() {
		comboEditor = new EditableComboBoxCellEditor(srcTableViewer.getTable(), tarSchemaNameArray);
		
		CellEditor[] editors = new CellEditor[] {
				new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
				null,
				null,
				null,
				tarCatalog.isDBAGroup() ? comboEditor : null,
				null
		};
		
		srcTableViewer.setCellEditors(editors);
		srcTableViewer.setCellModifier(new ICellModifier() {
			
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
				if (index != -1) {
					return tarSchemaNameArray[index];
				} else {
					String testValue = item.getText();
					
					MessageDialog.openError(getShell(), Messages.msgError, "This schema does not exist");
					
					return testValue;
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
	
	private void setOfflineEditor(boolean isAddUserSchema) {
		textEditor = new TextCellEditor(srcTableViewer.getTable());
		
		CellEditor[] editors = new CellEditor[] {
				new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
				null,
				null,
				null,
				isAddUserSchema ? textEditor : null,
				null
		};
		
		srcTableViewer.setCellEditors(editors);
		srcTableViewer.setCellModifier(new ICellModifier() {

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
					return ((SrcTable) element).getTarSchema();
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
					srcTable.setTarSchema((String) value);
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
		
		for (Schema schema : srcSchemaList) {
			SrcTable srcTable = new SrcTable();
			srcTable.setSrcDBType(srcCatalog.getDatabaseType().getName());
			srcTable.setSrcSchema(schema.getName());
			srcTable.setNote(schema.isGrantorSchema());
			
			srcTable.setTarDBType(Messages.msgCubridDump);
			if (!schema.isGrantorSchema()) {
				srcTableList.add(0, srcTable);
			} else {
				srcTableList.add(srcTable);
			}
			
			if (scriptSchemaMap.size() != 0) {
				logger.info("offline script schema");
				Schema scriptSchema = scriptSchemaMap.get(srcTable.getSrcSchema()); 
				if (scriptSchema != null) {
					srcTable.setTarSchema(scriptSchemaMap.get(srcTable.getSrcSchema()).getTargetSchemaName());
					srcTable.setSelected(scriptSchemaMap.get(srcTable.getSrcSchema()).isMigration());
				}
				
				if (srcTable.getTarSchema() == null || srcTable.getTarSchema().isEmpty()) {
					srcTable.setTarSchema(srcTable.getSrcSchema());
				}
				
			} else {
				if (config.isAddUserSchema()) {
					srcTable.setTarSchema(Messages.msgTypeSchema);
				} else {
					srcTable.setTarSchema(srcTable.getSrcSchema());
				}
			}
		}
	}
	
	private void setOnlineSchemaMappingPage() {
		setOnlineData();
		getSchemaValues();
		
		if (tarCatalog.isDbHasUserSchema()) {
			setOnlineEditor();
		}
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
					srcTable.setSelected(scriptSchemaMap.get(srcTable.getSrcSchema()).isMigration());
				}
				
				if (tarSchemaName == null || tarSchemaName.isEmpty()) {
					srcTable.setTarSchema(tarCatalog.getName());
				}
				
				logger.info("srcTable target schema : " + srcTable.getTarSchema());
			} else {
				int version = tarCatalog.getVersion().getDbMajorVersion() * 10 + tarCatalog.getVersion().getDbMinorVersion();
				
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
		// TODO need reset when select different target connection
		wizard = getMigrationWizard();
		config = wizard.getMigrationConfig();

		if (firstVisible) {
			setTitle(Messages.schemaMappingPageTitle);
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
			
			firstVisible = false;
		}
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
			MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
			return false;
		}
		
		List<String> checkNewSchemaDuplicate = new ArrayList<String>();
		for (SrcTable srcTable : srcTableList) {
			if (!(tarCatalog.isDbHasUserSchema())) {
				srcTable.setTarSchema(null);
				continue;
			}
			
			if (srcTable.getTarSchema().isEmpty() || isDefaultMessage(srcTable.getTarSchema())) {
				MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
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
			MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaCheckbox);
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
		grantFileListFullName = new HashMap<String, String>();
		
		for (SrcTable srcTable : srcTableList) {
			if (addUserSchema && srcTable.isSelected() && (srcTable.getTarSchema().isEmpty() || srcTable.getTarSchema() == null 
					|| srcTable.getTarSchema().equals(Messages.msgTypeSchema))) {
				MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
				
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
			
			if (splitSchema) {
				tableFullName.put(srcTable.getTarSchema(), config.getTableFullName(srcTable.getTarSchema()));
				viewFullName.put(srcTable.getTarSchema(), config.getViewFullName(srcTable.getTarSchema()));
				viewQuerySpecFullName.put(srcTable.getTarSchema(), config.getViewQuerySpecFullName(srcTable.getTarSchema()));
				pkFullName.put(srcTable.getTarSchema(), config.getPkFullName(srcTable.getTarSchema()));
				fkFullName.put(srcTable.getTarSchema(), config.getFkFullName(srcTable.getTarSchema()));
				serialFullName.put(srcTable.getTarSchema(), config.getSequenceFullName(srcTable.getTarSchema()));
				schemaFileListFullName.put(srcTable.getTarSchema(), config.getSchemaFileListFullName(srcTable.getTarSchema()));
				synonymFileListFullName.put(srcTable.getTarSchema(), config.getSynonymFullName(srcTable.getTarSchema()));
				grantFileListFullName.put(srcTable.getTarSchema(), config.getGrantFullName(srcTable.getTarSchema()));
			} else {
				schemaFullName.put(srcTable.getTarSchema(), config.getSchemaFullName(srcTable.getTarSchema()));
			}
			dataFullName.put(srcTable.getTarSchema(), config.getDataFullName(srcTable.getTarSchema()));
			indexFullName.put(srcTable.getTarSchema(), config.getIndexFullName(srcTable.getTarSchema()));
			updateStatisticFullName.put(srcTable.getTarSchema(), config.getUpdateStatisticFullName(srcTable.getTarSchema()));
		}
		
		if (!checkFileRepositroy()) {
			return false;
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
	private boolean checkFileRepositroy() {
		String lineSeparator = System.getProperty("line.separator");
		StringBuffer buffer = new StringBuffer();
		try {
			for (SrcTable srcTable : srcTableList) {
				if (!srcTable.isSelected) {
					continue;
				}
				
				if (config.isSplitSchema()) {
					File tableFile = new File(tableFullName.get(srcTable.getTarSchema()));
					File viewFile = new File(viewFullName.get(srcTable.getTarSchema()));
					File viewQuerySpecFile = new File(viewQuerySpecFullName.get(srcTable.getTarSchema()));
					File pkFile = new File(pkFullName.get(srcTable.getTarSchema()));
					File fkFile = new File(fkFullName.get(srcTable.getTarSchema()));
					File serialFile = new File(serialFullName.get(srcTable.getTarSchema()));
					File infoFile = new File(schemaFileListFullName.get(srcTable.getTarSchema()));
					File synonymFile = new File(synonymFileListFullName.get(srcTable.getTarSchema()));
					File grantFile = new File(grantFileListFullName.get(srcTable.getTarSchema()));
					
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
					if (grantFile.exists()) {
						buffer.append(grantFile.getCanonicalPath()).append(lineSeparator);
					}
				} else {
					File schemaFile = new File(schemaFullName.get(srcTable.getTarSchema()));
					if (schemaFile.exists()) {
						buffer.append(schemaFile.getCanonicalPath()).append(lineSeparator);
					}
				}
				
				File indexFile = new File(indexFullName.get(srcTable.getTarSchema()));
				File dataFile = new File(dataFullName.get(srcTable.getTarSchema()));
				File updateStatisticFile = new File(updateStatisticFullName.get(srcTable.getTarSchema()));
				
				if (dataFile.exists()) {
					buffer.append(dataFile.getCanonicalPath()).append(lineSeparator);
				}
				if (indexFile.exists()) {
					buffer.append(indexFile.getCanonicalPath()).append(lineSeparator);
				}
				if (updateStatisticFile.exists()) {
					buffer.append(updateStatisticFile.getCanonicalPath()).append(lineSeparator);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (buffer.length() > 0) {
			return MessageDialog.openConfirm(
					PlatformUI.getWorkbench().getDisplay().getActiveShell(),
					Messages.msgConfirmation,
					Messages.fileWarningMessage + "\r\n" + buffer.toString() + "\r\n"
							+ Messages.confirmMessage);
		}
		return true;
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
	
	private boolean isDefaultMessage(String enterSchema) {
		if (enterSchema.equals(Messages.msgDefaultSchema) ||
				enterSchema.equals(Messages.msgTypeSchema)) {
			return true;
		}
		
		return false;
	}
}
