package com.cubrid.cubridmigration.core.engine.task.exp;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceGrantConfig;
import com.cubrid.cubridmigration.core.engine.event.MigrationNoSupportEvent;
import com.cubrid.cubridmigration.core.engine.task.ExportTask;

public class GrantNoSupportExportTask extends 
		ExportTask{
	protected MigrationConfiguration config;
	protected SourceGrantConfig gr;
	
	public GrantNoSupportExportTask(MigrationConfiguration config, SourceGrantConfig gr) {
		this.config = config;
		this.gr = gr;
	}
	
	/**
	 * Execute export operation
	 */
	@Override
	protected void executeExportTask() {
		eventHandler.handleEvent(new MigrationNoSupportEvent(
				exporter.exportGrant(gr.getOwner() + "." + gr.getName())));
	}
}
