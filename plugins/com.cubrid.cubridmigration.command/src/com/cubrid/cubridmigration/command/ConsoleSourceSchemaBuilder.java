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
package com.cubrid.cubridmigration.command;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbmetadata.BuildSchemaFilterFactory;
import com.cubrid.cubridmigration.core.dbmetadata.DBSchemaInfoFetcherFactory;
import com.cubrid.cubridmigration.core.dbmetadata.IBuildSchemaFilter;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSchemaInfoFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.JDBCDBSchemaFetcherFacade;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.mysql.MysqlXmlDumpSource;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ConsoleSourceSchemaBuilder {

    private ConsoleSourceSchemaBuilder() {}

    public static Catalog buildSelectedOnly(MigrationConfiguration config, PrintStream outPrinter) {
        if (config == null) {
            return null;
        }
        Set<String> selectedSchemas = requireSelectedSchemas(config, outPrinter);
        if (selectedSchemas == null) {
            return null;
        }
        IBuildSchemaFilter filter = BuildSchemaFilterFactory.from(config);

        if (config.sourceIsOnline()) {
            return buildOnlineSchema(config, outPrinter, selectedSchemas, filter);
        }

        if (config.sourceIsXMLDump()) {
            return buildXmlDumpSchema(config, outPrinter, selectedSchemas, filter);
        }

        return null;
    }

    private static Set<String> requireSelectedSchemas(
            MigrationConfiguration config, PrintStream outPrinter) {
        Set<String> selectedSchemas = config.getSelectedSrcSchemas();
        if (selectedSchemas == null || selectedSchemas.isEmpty()) {
            if (outPrinter != null) {
                outPrinter.println("Invalid migration script: <schemas> is required for console.");
            }
            return null;
        }
        return selectedSchemas;
    }

    private static Catalog buildOnlineSchema(
            MigrationConfiguration config,
            PrintStream outPrinter,
            Set<String> selectedSchemas,
            IBuildSchemaFilter filter) {
        ConnParameters cp = config.getSourceConParams();
        if (cp == null) {
            if (outPrinter != null) {
                outPrinter.println("Invalid source database connection.");
            }
            return null;
        }
        JDBCDBSchemaFetcherFacade facade = new JDBCDBSchemaFetcherFacade();
        List<String> schemaList = new ArrayList<String>(selectedSchemas);
        return facade.fetchSchemaObjectsForSchemas(cp, schemaList, filter);
    }

    private static Catalog buildXmlDumpSchema(
            MigrationConfiguration config,
            PrintStream outPrinter,
            Set<String> selectedSchemas,
            IBuildSchemaFilter filter) {
        if (outPrinter != null) {
            outPrinter.println("Warning: XML dump loads full schema metadata before filtering.");
        }
        MysqlXmlDumpSource ds =
                new MysqlXmlDumpSource(config.getSourceFileName(), config.getSourceFileEncoding());
        IDBSchemaInfoFetcher fetcher = DBSchemaInfoFetcherFactory.createFetcher(ds);
        Catalog catalog = fetcher.fetchSchema(ds, filter);
        if (catalog == null) {
            return null;
        }
        filterSchemas(catalog, selectedSchemas);
        return catalog;
    }

    private static void filterSchemas(Catalog catalog, Set<String> selectedSchemas) {
        if (catalog == null || selectedSchemas == null || selectedSchemas.isEmpty()) {
            return;
        }
        List<Schema> removeSchemas = new ArrayList<Schema>();
        for (Schema schema : catalog.getSchemas()) {
            if (!containsIgnoreCase(selectedSchemas, schema.getName())) {
                removeSchemas.add(schema);
            }
        }
        if (!removeSchemas.isEmpty()) {
            catalog.removeSchema(removeSchemas);
        }
    }

    private static boolean containsIgnoreCase(Set<String> selectedSchemas, String schemaName) {
        if (schemaName == null) {
            return false;
        }
        for (String s : selectedSchemas) {
            if (schemaName.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }
}
