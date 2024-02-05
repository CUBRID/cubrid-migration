package com.cubrid.cubridmigration.core.engine.task.imp;

import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.task.ImportTask;

public class SchemaImportTask extends ImportTask {

    private Schema dummySchema;

    public SchemaImportTask(Schema dummySchema) {
        this.dummySchema = dummySchema;
    }

    @Override
    protected void executeImport() {
        importer.createSchema(dummySchema);
    }
}
