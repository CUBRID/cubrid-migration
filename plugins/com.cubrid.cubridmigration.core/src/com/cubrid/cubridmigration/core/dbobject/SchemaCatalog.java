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
package com.cubrid.cubridmigration.core.dbobject;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.datatype.DataType;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Names-only schema metadata for lazy loading (no table/column objects). */
public class SchemaCatalog {

    private final String name;
    private final DatabaseType databaseType;
    private final ConnParameters connectionParameters;
    private final Version version;
    private final Map<String, List<DataType>> supportedDataType;
    private final List<SchemaEntry> schemas = new ArrayList<>();

    public SchemaCatalog(
            String name,
            DatabaseType databaseType,
            ConnParameters connectionParameters,
            Version version,
            Map<String, List<DataType>> supportedDataType) {
        this.name = name;
        this.databaseType = databaseType;
        this.connectionParameters = connectionParameters;
        this.version = version;
        this.supportedDataType = supportedDataType;
    }

    public String getName() {
        return name;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public ConnParameters getConnectionParameters() {
        return connectionParameters;
    }

    public List<SchemaEntry> getSchemas() {
        return schemas;
    }

    public Version getVersion() {
        return version;
    }

    public Map<String, List<DataType>> getSupportedDataType() {
        return supportedDataType;
    }

    public SchemaEntry getSchemaByName(String schemaName) {
        for (SchemaEntry s : schemas) {
            if (s.name().equalsIgnoreCase(schemaName)) {
                return s;
            }
        }
        return null;
    }
}
