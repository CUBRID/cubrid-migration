package com.cubrid.cubridmigration.core.engine.task.exp;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.core.engine.event.MigrationNoSupportEvent;
import com.cubrid.cubridmigration.core.engine.task.ExportTask;

public class SynonymNoSupportExportTask extends
		ExportTask {
	protected MigrationConfiguration config;
	protected SourceSynonymConfig sn;
	
	public SynonymNoSupportExportTask(MigrationConfiguration config, SourceSynonymConfig sn) {
		this.config = config;
		this.sn = sn;
	}
	
	/**
	 * Execute export operation
	 */
	@Override
	protected void executeExportTask() {
		eventHandler.handleEvent(new MigrationNoSupportEvent(
				exporter.exportSynonym(sn.getOwner() + "." + sn.getName())));
	}
}
