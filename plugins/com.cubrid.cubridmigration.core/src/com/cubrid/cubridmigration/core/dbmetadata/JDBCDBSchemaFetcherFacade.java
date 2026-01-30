/*
 * Copyright (C) 2008 Search Solution Corporation.
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
package com.cubrid.cubridmigration.core.dbmetadata;

import com.cubrid.cubridmigration.core.common.Closer;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaEntry;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBCDBSchemaFetcherFacade
 *
 * @author caoyilin
 * @version 8.4.3 - 2013-2-19
 */
public class JDBCDBSchemaFetcherFacade implements IDBSchemaInfoFetcher {

    private Runnable cancelRunable = null;

    /**
     * get Catalog
     *
     * @param ds JDBC Connection parameters
     * @param filter IBuildSchemaFilter
     * @return Catalog
     */
    public Catalog fetchSchema(IDBSource ds, IBuildSchemaFilter filter) {
        if (cancelRunable != null) {
            throw new RuntimeException("One fetching work is running.");
        }
        try {
            ConnParameters cp = (ConnParameters) ds;
            final Connection conn = cp.createConnection();
            // Create cancel process
            cancelRunable =
                    new Runnable() {

                        public void run() {
                            try {
                                conn.close();
                            } catch (Exception ex) {
                                // Do nothing
                            }
                        }
                    };
            try {
                DatabaseType dt = cp.getDatabaseType();
                AbstractJDBCSchemaFetcher builder = dt.getMetaDataBuilder();
                Catalog catalog = builder.buildCatalog(conn, cp, filter);

                if (catalog.getCharset() == null) {
                    catalog.setCharset(cp.getCharset());
                } else {
                    cp.setCharset(catalog.getCharset());
                }
                if (cp.getTimeZone() == null || cp.getTimeZone().isEmpty()) {
                    cp.setTimeZone(catalog.getTimezone());
                }
                return catalog;
            } finally {
                cancelRunable = null;
                Closer.close(conn);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Delegates to AbstractJDBCSchemaFetcher.buildSchemaCatalog (Lazy Step 1). */
    @Override
    public SchemaCatalog fetchSchemaNames(IDBSource ds) {
        if (cancelRunable != null) {
            throw new IllegalStateException("One fetching work is runnig");
        }
        try {
            ConnParameters cp = (ConnParameters) ds;
            final Connection conn = cp.createConnection();
            // Create cancel process
            cancelRunable =
                    new Runnable() {
                        public void run() {
                            try {
                                conn.close();
                            } catch (Exception ex) {
                                // Do nothing
                            }
                        }
                    };
            try {
                DatabaseType dt = cp.getDatabaseType();
                AbstractJDBCSchemaFetcher builder = dt.getMetaDataBuilder();
                return builder.buildSchemaCatalog(conn, cp);
            } finally {
                cancelRunable = null;
                Closer.close(conn);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Delegates to AbstractJDBCSchemaFetcher.buildSchemaObjects (Lazy Step 2). */
    @Override
    public Catalog fetchSchemaObjects(IDBSource ds, SchemaCatalog sc, List<String> schemas) {
        if (cancelRunable != null) {
            throw new RuntimeException("One fetching work is running.");
        }
        try {
            ConnParameters cp = (ConnParameters) ds;
            final Connection conn = cp.createConnection();
            cancelRunable =
                    new Runnable() {
                        public void run() {
                            try {
                                conn.close();
                            } catch (Exception ex) {
                                // Do nothing
                            }
                        }
                    };
            try {
                DatabaseType dt = cp.getDatabaseType();
                AbstractJDBCSchemaFetcher builder = dt.getMetaDataBuilder();
                Catalog catalog = builder.buildSchemaObjects(conn, sc, schemas);
                if (catalog.getCharset() == null) {
                    catalog.setCharset(cp.getCharset());
                } else {
                    cp.setCharset(catalog.getCharset());
                }
                if (cp.getTimeZone() == null || cp.getTimeZone().isEmpty()) {
                    cp.setTimeZone(catalog.getTimezone());
                }
                return catalog;
            } finally {
                cancelRunable = null;
                Closer.close(conn);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Fetches schema objects only for the given schema names, without querying the full schema
     * list.
     *
     * <p>This method builds a minimal SchemaCatalog using JDBC metadata (e.g., version/type info)
     * and then loads schema objects for the provided schemas using a single JDBC connection.
     */
    public Catalog fetchSchemaObjectsForSchemas(
            ConnParameters cp, List<String> schemas, IBuildSchemaFilter filter) {
        if (cancelRunable != null) {
            throw new RuntimeException("One fetching work is running.");
        }
        try {
            final Connection conn = cp.createConnection();
            cancelRunable =
                    new Runnable() {
                        public void run() {
                            try {
                                conn.close();
                            } catch (Exception ex) {
                                // Do nothing
                            }
                        }
                    };
            try {
                DatabaseType dt = cp.getDatabaseType();
                AbstractJDBCSchemaFetcher builder = dt.getMetaDataBuilder();
                SchemaCatalog sc = builder.buildSchemaCatalogForSchemas(conn, cp, schemas);
                List<String> schemaNames = new ArrayList<String>();
                for (SchemaEntry se : sc.getSchemas()) {
                    schemaNames.add(se.name());
                }
                Catalog catalog = builder.buildSchemaObjects(conn, sc, schemaNames, filter);
                if (catalog.getCharset() == null) {
                    catalog.setCharset(cp.getCharset());
                } else {
                    cp.setCharset(catalog.getCharset());
                }
                if (cp.getTimeZone() == null || cp.getTimeZone().isEmpty()) {
                    cp.setTimeZone(catalog.getTimezone());
                }
                return catalog;
            } finally {
                cancelRunable = null;
                Closer.close(conn);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Cancel fetching schema */
    public void cancel() {
        if (cancelRunable != null) {
            cancelRunable.run();
            cancelRunable = null;
        }
    }
}
