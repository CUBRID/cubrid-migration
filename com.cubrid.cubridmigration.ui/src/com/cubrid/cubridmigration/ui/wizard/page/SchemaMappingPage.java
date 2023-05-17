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
import com.cubrid.cubridmigration.core.common.PathUtils;
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
	Map<String, String> pkFullName;
	Map<String, String> fkFullName;
	Map<String, String> dataFullName;
	Map<String, String> indexFullName;
	Map<String, String> serialFullName;
	Map<String, String> updateStatisticFullName;
	Map<String, String> SchemaFileListFullName;
	
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
					if (srcTable.getNote().equals(Messages.msgMainSchema)) {
						return CompositeUtils.CHECK_IMAGE;
					} else {
						return CompositeUtils.UNCHECK_IMAGE;
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
		Catalog sourceCatalog = wizard.getSourceCatalog();
		
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
				comboEditor,
				null
		};
		
		if (!tarCatalog.isDBAGroup()) {
			return;
		}
		
		srcTableViewer.setCellEditors(editors);
		srcTableViewer.setCellModifier(new ICellModifier() {
			
			@Override
			public void modify(Object element, String property, Object value) {
				TableItem tabItem = (TableItem) element;
				SrcTable srcTable = (SrcTable) tabItem.getData();
				
				if (property.equals(propertyList[4])) {
					srcTable.setTarSchema(returnValue((Integer) value, tabItem));
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
		});
	}
	
	private void setOfflineEditor() {
		textEditor = new TextCellEditor(srcTableViewer.getTable());
		
		CellEditor[] editors = new CellEditor[] {
				new CheckboxCellEditorFactory().getCellEditor(srcTableViewer.getTable()),
				null,
				null,
				null,
				textEditor,
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
					srcTableViewer.refresh();
				} else if (property.equals(propertyList[0])) {
					tabItem.setImage(CompositeUtils.getCheckImage(!srcTable.isSelected));
					srcTable.setSelected(!srcTable.isSelected);
				}
			}
		});
	}
	
	private void setOfflineSchemaMappingPage() {
		setOfflineData();
		
		config.isAddUserSchema();
		
		if (config.isAddUserSchema()) {
			setOfflineEditor();
		}
	}
	
	private void setOfflineData() {
		srcCatalog = wizard.getSourceCatalog();
		srcSchemaList = srcCatalog.getSchemas();
		Map<String, String> scriptSchemaMap = config.getScriptSchemaMapping();
		
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
				srcTable.setTarSchema(scriptSchemaMap.get(srcTable.getSrcSchema()));
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
		
//		int tarSchemaSize = getMigrationWizard().getTarCatalogSchemaCount();
		
		tarCatalog.isDbHasUserSchema();
		
		if (tarCatalog.isDbHasUserSchema()) {
			setOnlineEditor();
		}
	}
	
	private void setOnlineData() {
		srcCatalog = wizard.getSourceCatalog();
		tarCatalog = wizard.getTargetCatalog();
		
		//TODO: extract schema names and DB type
		srcSchemaList = srcCatalog.getSchemas();
		tarSchemaList = tarCatalog.getSchemas();
		
		Map<String, String> scriptSchemaMap = config.getScriptSchemaMapping();
		
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
			
			if (scriptSchemaMap.size() != 0 && srcCatalog.getDatabaseType().getID() == 1) {
				logger.info("script schema");
				
				srcTable.setTarSchema(scriptSchemaMap.get(srcTable.getSrcSchema()).toUpperCase());
				
				String tarSchemaName = scriptSchemaMap.get(srcTable.getSrcSchema()).toUpperCase();
				
				if (tarSchemaName.isEmpty() || tarSchemaName == null) {
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
			setDescription(Messages.schemaMappingPageDescription);
			
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
			if (config.targetIsOnline()) {
				event.doit = saveOnlineData();
			} else {
				event.doit = saveOfflineData(config.isAddUserSchema(), config.isSplitSchema());
			}
		}
	}
	
	private boolean saveOnlineData() {
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
			
			String targetSchemaName = srcTable.getTarSchema();
			
			logger.info("src schema : " + srcTable.getSrcSchema());
			logger.info("tar schema : " + srcTable.getTarSchema());
			
			Schema targetSchema = tarCatalog.getSchemaByName(targetSchemaName);
			
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
				logger.info("-------------------------------------------");
			}
		}
		
		return true;
	}
	
	private boolean saveOfflineData(boolean addUserSchema, boolean splitSchema) {
		List<Schema> targetSchemaList = new ArrayList<Schema>();
		schemaFullName = new HashMap<String, String>();
		tableFullName = new HashMap<String, String>();
		viewFullName = new HashMap<String, String>();
		pkFullName = new HashMap<String, String>();
		fkFullName = new HashMap<String, String>();
		dataFullName = new HashMap<String, String>();
		indexFullName = new HashMap<String, String>();
		serialFullName = new HashMap<String, String>();
		updateStatisticFullName = new HashMap<String, String>();
		SchemaFileListFullName = new HashMap<String, String>();
		
		for (SrcTable srcTable : srcTableList) {
			if (addUserSchema && (srcTable.getTarSchema().isEmpty() || srcTable.getTarSchema() == null 
					|| srcTable.getTarSchema().equals(Messages.msgTypeSchema))) {
				MessageDialog.openError(getShell(), Messages.msgError, Messages.msgErrEmptySchemaName);
				
				return false;
			}

			Schema schema = srcCatalog.getSchemaByName(srcTable.getSrcSchema());
			schema.setTargetSchemaName(srcTable.getTarSchema());
			targetSchemaList.add(schema);
			
			if (splitSchema) {
				tableFullName.put(srcTable.getTarSchema(), getTableFullName(srcTable.getTarSchema()));
				viewFullName.put(srcTable.getTarSchema(), getViewFullName(srcTable.getTarSchema()));
				pkFullName.put(srcTable.getTarSchema(), getPkFullName(srcTable.getTarSchema()));
				fkFullName.put(srcTable.getTarSchema(), getFkFullName(srcTable.getTarSchema()));
				serialFullName.put(srcTable.getTarSchema(), getSequenceFullName(srcTable.getTarSchema()));
				SchemaFileListFullName.put(srcTable.getTarSchema(), getSchemaFileListFullName(srcTable.getTarSchema()));
			} else {
				schemaFullName.put(srcTable.getTarSchema(), getSchemaFullName(srcTable.getTarSchema()));
			}
			dataFullName.put(srcTable.getTarSchema(), getDataFullName(srcTable.getTarSchema()));
			indexFullName.put(srcTable.getTarSchema(), getIndexFullName(srcTable.getTarSchema()));
			updateStatisticFullName.put(srcTable.getTarSchema(), getUpdateStatisticFullName(srcTable.getTarSchema()));
		}
		
		if (!checkFileRepositroy()) {
			return false;
		}
		
		config.setTargetSchemaList(targetSchemaList);
		config.setTargetSchemaFileName(schemaFullName);
		config.setTargetTableFileName(tableFullName);
		config.setTargetViewFileName(viewFullName);
		config.setTargetDataFileName(dataFullName);
		config.setTargetIndexFileName(indexFullName);
		config.setTargetPkFileName(pkFullName);
		config.setTargetFkFileName(fkFullName);
		config.setTargetSerialFileName(serialFullName);
		config.setTargetUpdateStatisticFileName(updateStatisticFullName);
		config.setTargetSchemaFileListName(SchemaFileListFullName);
		return true;
	}
	
	/**
	 * get Schema file full path
	 * 
	 * @param targetSchemaName
	 * @return schema file full path
	 */
	private String getSchemaFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_schema").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Table file full path
	 * 
	 * @param targetSchemaName
	 * @return table file full path
	 */
	private String getTableFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_table").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get View file full path
	 * 
	 * @param targetSchemaName
	 * @return view file full path
	 */
	private String getViewFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_view").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Data file full path
	 * 
	 * @param targetSchemaName
	 * @return data file full path
	 */
	private String getDataFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_data").append(config.getDataFileExt());
	
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Index file full path
	 * 
	 * @param targetSchemaName
	 * @return index file full path
	 */
	private String getIndexFullName(String targetSchemaName) {		
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_index").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Pk file full path
	 * 
	 * @param targetSchemaName
	 * @return pk file full path
	 */
	private String getPkFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_pk").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Fk file full path
	 * 
	 * @param targetSchemaName
	 * @return fk file full path
	 */
	private String getFkFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_fk").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Sequence file full path
	 * 
	 * @param targetSchemaName
	 * @return sequence file full path
	 */
	private String getSequenceFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_serial").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get UpdateStatistic file full path
	 * 
	 * @param targetSchemaName
	 * @return updateStatistic file full path
	 */
	private String getUpdateStatisticFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_updatestatistic").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
	}
	
	/**
	 * get Info file full path
	 * 
	 * @param targetSchemaName
	 * @return info file full path
	 */
	private String getSchemaFileListFullName(String targetSchemaName) {
		StringBuffer fileName = new StringBuffer();
		fileName.append(File.separator).append(config.getTargetFilePrefix()).append("_").append(targetSchemaName).append("_info").append(
				getMigrationWizard().getMigrationConfig().getDefaultTargetSchemaFileExtName());
		
		return PathUtils.mergePath(PathUtils.mergePath(config.getFileRepositroyPath(), targetSchemaName), fileName.toString());
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
		StringBuffer buffer = new StringBuffer();
		try {
			for (SrcTable srcTable : srcTableList) {
				if (config.isSplitSchema()) {
					File tableFile = new File(tableFullName.get(srcTable.getTarSchema()));
					File viewFile = new File(viewFullName.get(srcTable.getTarSchema()));
					File pkFile = new File(pkFullName.get(srcTable.getTarSchema()));
					File fkFile = new File(fkFullName.get(srcTable.getTarSchema()));
					File serialFile = new File(serialFullName.get(srcTable.getTarSchema()));
					
					if (tableFile.exists()) {
						buffer.append(tableFile.getCanonicalPath()).append(System.lineSeparator());
					}
					if (viewFile.exists()) {
						buffer.append(viewFile.getCanonicalPath()).append(System.lineSeparator());
					}
					if (pkFile.exists()) {
						buffer.append(pkFile.getCanonicalPath()).append(System.lineSeparator());
					}
					if (fkFile.exists()) {
						buffer.append(fkFile.getCanonicalPath()).append(System.lineSeparator());
					}
					if (serialFile.exists()) {
						buffer.append(serialFile.getCanonicalPath()).append(System.lineSeparator());
					}
					
				} else {
					File schemaFile = new File(schemaFullName.get(srcTable.getTarSchema()));
					if (schemaFile.exists()) {
						buffer.append(schemaFile.getCanonicalPath()).append(System.lineSeparator());
					}
				}
				
				File indexFile = new File(indexFullName.get(srcTable.getTarSchema()));
				File dataFile = new File(dataFullName.get(srcTable.getTarSchema()));
				File updateStatisticFile = new File(updateStatisticFullName.get(srcTable.getTarSchema()));
				
				if (dataFile.exists()) {
					buffer.append(dataFile.getCanonicalPath()).append(System.lineSeparator());
				}
				if (indexFile.exists()) {
					buffer.append(indexFile.getCanonicalPath()).append(System.lineSeparator());
				}
				if (updateStatisticFile.exists()) {
					buffer.append(updateStatisticFile.getCanonicalPath()).append(System.lineSeparator());
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
	
	private boolean isDefaultMessage(String enterSchema) {
		if (enterSchema.equals(Messages.msgDefaultSchema) ||
				enterSchema.equals(Messages.msgTypeSchema)) {
			return true;
		}
		
		return false;
	}
}
