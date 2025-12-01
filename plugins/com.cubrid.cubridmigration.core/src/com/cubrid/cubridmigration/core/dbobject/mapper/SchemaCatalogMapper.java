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
package com.cubrid.cubridmigration.core.dbobject.mapper;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaEntry;

import java.util.ArrayList;
import java.util.List;

/** Helper to convert between Catalog and names-only SchemaCatalog. */
public final class SchemaCatalogMapper {

    private SchemaCatalogMapper() {}

    /** Creates a names-only SchemaCatalog view from a full Catalog. */
    public static SchemaCatalog toSchemaCatalog(Catalog catalog) {
        if (catalog == null) {
            return null;
        }
        SchemaCatalog sc =
                new SchemaCatalog(
                        catalog.getName(),
                        catalog.getDatabaseType(),
                        catalog.getConnectionParameters(),
                        catalog.getVersion(),
                        catalog.getSupportedDataType());

        for (Schema schema : catalog.getSchemas()) {
            SchemaEntry se = new SchemaEntry(schema.getName(), schema.isGrantorSchema());
            sc.getSchemas().add(se);
        }
        return sc;
    }

    /** Creates an empty Catalog shell for the given schemas (no objects loaded). */
    public static Catalog createEmptyCatalogFromSchemaCatalog(
            SchemaCatalog sc, List<String> selectedSchemas) {
        if (sc == null) {
            return null;
        }

        if (selectedSchemas == null || selectedSchemas.isEmpty()) {
            selectedSchemas = extractAllSchemaNames(sc);
        }

        Catalog catalog = new Catalog();
        catalog.setName(sc.getName());
        catalog.setDatabaseType(sc.getDatabaseType());
        catalog.setConnectionParameters(sc.getConnectionParameters());
        catalog.setVersion(sc.getVersion());
        catalog.setSupportedDataType(sc.getSupportedDataType());

        for (SchemaEntry se : sc.getSchemas()) {
            if (!selectedSchemas.contains(se.name())) {
                continue;
            }
            Schema schema = new Schema();
            schema.setName(se.name());
            schema.setGrantorSchema(se.grantorSchema());
            catalog.addSchema(schema);
        }
        return catalog;
    }

    private static List<String> extractAllSchemaNames(SchemaCatalog sc) {
        List<String> names = new ArrayList<String>();
        if (sc == null) {
            return names;
        }
        for (SchemaEntry se : sc.getSchemas()) {
            names.add(se.name());
        }
        return names;
    }
}
