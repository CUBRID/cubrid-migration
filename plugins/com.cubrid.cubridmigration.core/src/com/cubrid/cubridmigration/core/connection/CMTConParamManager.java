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
package com.cubrid.cubridmigration.core.connection;

import com.cubrid.common.configuration.jdbc.IJDBCConnectionChangedObserver;
import com.cubrid.common.configuration.jdbc.IJDBCInfoChangedSubject;
import com.cubrid.common.configuration.jdbc.JDBCChangingManager;
import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.CipherUtils;
import com.cubrid.cubridmigration.core.common.xml.IXMLMemento;
import com.cubrid.cubridmigration.core.common.xml.XMLMemento;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbtype.DBConstant;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.SchemaSelection;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

/**
 * JDBCConnectionManager is response for managing local JDBC connection information. The connection
 * name is case sensitive. So the "test" and "Test" are different connections. All input and output
 * connection object are copies.
 *
 * @author Kevin Cao
 * @version 1.0 - 2013-4-23 created by Kevin Cao
 */
public final class CMTConParamManager implements IJDBCInfoChangedSubject {

    private static final Logger LOG = LogUtil.getLogger(CMTConParamManager.class);

    private static final CMTConParamManager MANAGER;

    static {
        MANAGER = new CMTConParamManager();
        // Do nothing here
        JDBCChangingManager jcm = JDBCChangingManager.getInstance();
        jcm.registerSubject(MANAGER);
        jcm.registerObservor(new JDBCInfoChangedObserver());
    }

    private List<ConnParameters> connections = new ArrayList<ConnParameters>();
    private Map<ConnParameters, Catalog> catalogs = new HashMap<ConnParameters, Catalog>();
    private final Map<ConnParameters, SchemaCatalog> sourceSchemaCatalogCache = new HashMap<>();
    private final SourceSelectedCatalogCache sourceSelectedCatalogCache =
            new SourceSelectedCatalogCache();

    private File defaultFile = null;

    private List<IJDBCConnectionChangedObserver> observers =
            new ArrayList<IJDBCConnectionChangedObserver>();

    private CMTConParamManager() {
        // Do nothing here.
    }

    public static CMTConParamManager getInstance() {
        return MANAGER;
    }

    /**
     * Load from a XML file
     *
     * @param file File
     */
    public void loadFromFile(File file) {
        try {
            FileInputStream reader = new FileInputStream(file);
            try {
                IXMLMemento memento = XMLMemento.loadMemento(reader);
                if (memento == null) {
                    return;
                }
                IXMLMemento[] children = memento.getChildren("database");

                for (int i = 0; i < children.length; i++) {
                    final IXMLMemento child = children[i];
                    boolean isXmlDatabase = child.getBoolean("isXMLDatabase");

                    if (isXmlDatabase) {
                        continue;
                    }
                    String dbName = child.getString("dbName");
                    Integer databaseTypeID = child.getInteger("databaseTypeID");
                    String charSet = child.getString("charSet");
                    String username = child.getString("user");
                    String password;
                    if (child.getBoolean("encrypted")) {
                        password = CipherUtils.decrypt(child.getString("password"));
                    } else {
                        password = child.getString("password");
                    }
                    String hostIP = child.getString("hostIP");
                    int port = Integer.parseInt(child.getString("port"));
                    String driverPath = child.getString("driverPath");
                    // String driverVersion = children[i].getString("driverVersion");
                    DatabaseType dt = DatabaseType.getDatabaseTypeByID(databaseTypeID);

                    String conName = child.getString("name");
                    String schema = child.getString("schema");
                    ConnParameters cp =
                            ConnParameters.getConParam(
                                    conName,
                                    hostIP,
                                    port,
                                    dbName,
                                    dt,
                                    charSet,
                                    username,
                                    password,
                                    driverPath,
                                    schema);
                    cp.setUserJDBCURL(child.getString("user_jdbc_url"));
                    cp.setConServer(child.getString("con_server"));
                    addConnection(cp, true);
                }
            } finally {
                reader.close();
            }
        } catch (FileNotFoundException ex) {
            LOG.error("", ex);
        } catch (IOException ex) {
            LOG.error("", ex);
        }
    }

    /** Save to file */
    public void save2File() {
        if (defaultFile == null) {
            return;
        }
        try {
            XMLMemento memento = XMLMemento.createWriteRoot("databases");

            for (ConnParameters cp : connections) {
                IXMLMemento child = memento.createChild("database");
                child.putBoolean("isXMLDatabase", false);

                child.putString("name", cp.getConName());
                child.putString("dbName", cp.getDbName());
                child.putInteger("databaseTypeID", cp.getDatabaseType().getID());
                child.putString("charSet", cp.getCharset());
                child.putString("driverClass", cp.getDriverClass());
                child.putString("user", cp.getConUser());
                child.putString("password", CipherUtils.encrypt(cp.getConPassword()));
                child.putBoolean("encrypted", true);
                child.putString("hostIP", cp.getHost());
                child.putString("port", cp.getPort() + "");
                child.putString("driverPath", cp.getDriverFileName());
                child.putString("user_jdbc_url", cp.getUserJDBCURL());
                if (cp.getDatabaseType().getID() == DBConstant.DBTYPE_INFORMIX) {
                    child.putString("con_server", cp.getConServer());
                }
                // child.putString("schema", cp.getSchema());
            }
            FileOutputStream writer = new FileOutputStream(defaultFile);
            try {
                memento.save(writer);
            } finally {
                writer.close();
            }
        } catch (ParserConfigurationException ex) {
            LOG.error("", ex);
        } catch (IOException ex) {
            LOG.error("", ex);
        }
    }

    /**
     * Add a new connection
     *
     * @param cp ConnParameters
     * @param silence if true the event will not be triggered.
     */
    public void addConnection(ConnParameters cp, boolean silence) {
        if (cp == null || isNameUsed(cp.getConName()) || isConnectionExists(cp)) {
            return;
        }
        connections.add(cp.clone());
        save2File();
        if (silence) {
            return;
        }
        for (IJDBCConnectionChangedObserver ob : observers) {
            try {
                ob.afterAdd(this, cp);
            } catch (Exception ex) {
                LOG.error("", ex);
            }
        }
    }

    /**
     * Update a saved connection and keep related caches in sync.
     *
     * @param oldName old connection name
     * @param newcp new ConnParameters
     * @param silence if true, the event will not be triggered.
     */
    public void updateConnection(String oldName, ConnParameters newcp, boolean silence) {
        ConnParameters oldStored = getInternalConParameter(oldName);
        if (oldStored == null || newcp == null) {
            return;
        }
        boolean identityChanged = !oldStored.isSameDB(newcp);
        if (identityChanged && isConnectionExists(newcp)) {
            return;
        }
        ConnParameters newStored = newcp.clone();

        updateConnectionList(oldStored, newStored);
        updateCaches(oldStored, newStored, identityChanged);
        save2File();

        if (!silence) {
            fireAfterModifyEvent(oldStored, newStored);
        }
    }

    private void updateConnectionList(ConnParameters oldStored, ConnParameters newStored) {
        int index = connections.indexOf(oldStored);
        if (index != -1) {
            connections.set(index, newStored);
        } else {
            connections.add(newStored);
        }
    }

    private void updateCaches(
            ConnParameters oldStored, ConnParameters newStored, boolean identityChanged) {
        Catalog fullCatalog = catalogs.remove(oldStored);
        if (!identityChanged && fullCatalog != null) {
            catalogs.put(newStored, fullCatalog);
        }
        if (identityChanged) {
            sourceSelectedCatalogCache.clear(oldStored);
        } else {
            sourceSelectedCatalogCache.rekey(oldStored, newStored);
        }
        SchemaCatalog sc = sourceSchemaCatalogCache.remove(oldStored);
        if (!identityChanged && sc != null) {
            sourceSchemaCatalogCache.put(newStored, sc);
        }
    }

    private void fireAfterModifyEvent(ConnParameters oldStored, ConnParameters newStored) {
        ConnParameters oldSnapshot = oldStored.clone();
        ConnParameters newSnapshot = newStored.clone();
        for (IJDBCConnectionChangedObserver ob : observers) {
            try {
                ob.afterModify(this, oldSnapshot, newSnapshot);
            } catch (Exception ex) {
                LOG.error("", ex);
            }
        }
    }

    /**
     * Update a catalog cache by connection name
     *
     * @param conName String
     * @param catalog Catalog
     */
    public void updateCatalog(String conName, Catalog catalog) {
        ConnParameters cp = getInternalConParameter(conName);
        if (cp == null) {
            return;
        }
        catalogs.put(cp, catalog);
    }

    /** Update SchemaCatalog cache for the given source connection. */
    public void updateSourceSchemaCatalog(ConnParameters cp, SchemaCatalog sc) {
        sourceSchemaCatalogCache.put(cp, sc);
    }

    /** Cache a detailed source Catalog for the given connection and schema selection. */
    public void updateSelectedSourceCatalog(
            ConnParameters cp, Collection<String> selectedSchemas, Catalog catalog) {
        if (cp == null || selectedSchemas == null || selectedSchemas.isEmpty() || catalog == null)
            return;
        SchemaSelection sel = SchemaSelection.of(selectedSchemas);
        sourceSelectedCatalogCache.put(cp, sel, catalog);
    }

    /**
     * Retrieves a cached catalog by connection name
     *
     * @param conName String
     * @return Catalog
     */
    public Catalog getCatalog(String conName) {
        ConnParameters cp = getInternalConParameter(conName);
        if (cp == null) {
            return null;
        }
        return catalogs.get(cp);
    }

    /** Get cached SchemaCatalog for the given connection, or null if none. */
    public SchemaCatalog getSourceSchemaCatalog(ConnParameters cp) {
        return sourceSchemaCatalogCache.get(cp);
    }

    /** Get cached detailed Catalog for the given connection and schema selection. */
    public Catalog getSelectedSourceCatalog(ConnParameters cp, Collection<String> selectedSchemas) {
        if (cp == null) return null;
        SchemaSelection sel = SchemaSelection.of(selectedSchemas);
        return sourceSelectedCatalogCache.get(cp, sel);
    }

    /**
     * Get internal ConParameter
     *
     * @param conName String
     * @return ConnParameters
     */
    private ConnParameters getInternalConParameter(String conName) {
        for (ConnParameters cp : connections) {
            if (cp.getConName().equals(conName)) {
                return cp;
            }
        }
        return null;
    }

    /**
     * Retrieves a deep copy of connections
     *
     * @return List<ConnParameters>
     */
    public List<ConnParameters> getConnections() {
        final ArrayList<ConnParameters> result = new ArrayList<ConnParameters>();
        for (ConnParameters cp : connections) {
            result.add(cp.clone());
        }
        return result;
    }

    /**
     * Retrieves a copy of the connection parameter searched by name
     *
     * @param conName String
     * @return a copy of the connection parameter
     */
    public ConnParameters getConnection(String conName) {
        for (ConnParameters cp : connections) {
            if (cp.getConName().equals(conName)) {
                return cp.clone();
            }
        }
        return null;
    }

    /**
     * Remove a connection and clear all related caches.
     *
     * @param conName String
     * @param silence if true,no event will be triggered.
     */
    public void removeConnection(String conName, boolean silence) {
        ConnParameters target = null;
        for (ConnParameters cp : connections) {
            if (cp.getConName().equals(conName)) {
                target = cp;
                break;
            }
        }

        if (target == null) {
            return;
        }

        ConnParameters deletedSnapshot = target.clone();
        connections.remove(target);
        catalogs.remove(target);
        sourceSchemaCatalogCache.remove(target);
        sourceSelectedCatalogCache.clear(target);
        save2File();

        if (silence) {
            return;
        }

        for (IJDBCConnectionChangedObserver ob : observers) {
            try {
                ob.afterDelete(this, deletedSnapshot);
            } catch (Exception ex) {
                LOG.error("", ex);
            }
        }
    }

    /** Clear detailed source Catalog cache for the given connection. */
    public void clearSelectedSourceCatalog(ConnParameters cp) {
        sourceSelectedCatalogCache.clear(cp);
    }

    /** Clear cache for the specified schemas only. */
    public void clearSelectedSourceCatalog(
            ConnParameters cp, java.util.List<String> selectedSchemas) {
        if (cp == null || selectedSchemas == null || selectedSchemas.isEmpty()) return;
        SchemaSelection selection = SchemaSelection.of(selectedSchemas);
        sourceSelectedCatalogCache.remove(cp, selection);
    }

    /**
     * Is the name is in used.
     *
     * @param name String
     * @return true if in used
     */
    public boolean isNameUsed(String name) {
        for (ConnParameters cp : connections) {
            if (cp.getConName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is same connection is exists.
     *
     * @param newcp ConnParameters
     * @return true if in used
     */
    public boolean isConnectionExists(ConnParameters newcp) {
        for (ConnParameters cp : connections) {
            if (cp.isSameDB(newcp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the file where the configuration will be saved.
     *
     * @param defaultFile File
     */
    public void setDefaultFile(File defaultFile) {
        this.defaultFile = defaultFile;
    }

    /**
     * Add Observer
     *
     * @param obv IJDBCConnectionChangedObserver
     */
    public void addObservor(IJDBCConnectionChangedObserver obv) {
        if (!observers.contains(obv)) {
            observers.add(obv);
        }
    }
}
