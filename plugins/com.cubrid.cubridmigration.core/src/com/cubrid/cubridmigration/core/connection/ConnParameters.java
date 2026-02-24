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

import com.cubrid.common.configuration.jdbc.IJDBCConnecInfo;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSource;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Objects;

/**
 * ConnectionParameters:DB Connection parameters
 *
 * @author JessieHuang
 * @version 1.0 - 2009-9-18
 */
public final class ConnParameters implements Serializable, IDBSource, IJDBCConnecInfo {

    private static final long serialVersionUID = 2531031197450692000L;

    private String charSet;
    private int dbType;
    private String dbName;
    // private String schema;
    private String driverFileName;
    private String host;
    private String conName;
    private String conPassword;
    private int port;
    private String timeZone;
    private String conUser;
    private String userJDBCURL;

    private ConnParameters() {}

    private ConnParameters(ConnParameters src) {
        Objects.requireNonNull(src, "src");
        this.dbType = src.dbType;
        this.host = src.host;
        this.port = src.port;
        this.dbName = src.dbName;
        this.charSet = src.charSet;
        this.timeZone = src.timeZone;
        this.conUser = src.conUser;
        this.conPassword = src.conPassword;
        this.driverFileName = src.driverFileName;
        this.userJDBCURL = src.userJDBCURL;
        this.conName = src.conName;
    }

    public String getCharset() {
        return charSet;
    }

    public DatabaseType getDatabaseType() {
        return DatabaseType.getDatabaseTypeByID(dbType);
    }

    public String getDbName() {
        return dbName;
    }

    /**
     * get a Connection
     *
     * @return conn Connection
     */
    public String getDefaultURL() {
        return DatabaseType.getDatabaseTypeByID(dbType).getConHelper().makeUrl(this);
    }

    /**
     * Retrieves the driver of jdbc
     *
     * @return Driver
     */
    public Driver getDriver() {
        JDBCData jd = getDatabaseType().getJDBCData(driverFileName);
        return jd == null ? null : jd.getDriver();
    }

    /**
     * Retrieves the JDBC driver's class
     *
     * @return class name
     */
    public String getDriverClass() {
        JDBCData jd = getDatabaseType().getJDBCData(driverFileName);
        return jd == null ? "" : jd.getDriverClassName();
    }

    public String getDriverFileName() {
        return driverFileName;
    }

    public String getHost() {
        return host;
    }

    public String getConName() {
        return conName;
    }

    public String getConPassword() {
        return conPassword;
    }

    public int getPort() {
        return port;
    }

    public String getTimeZone() {
        return timeZone;
    }

    /**
     * return JDBC url
     *
     * @return String
     */
    public String getUrl() {
        return DatabaseType.getDatabaseTypeByID(dbType).getConHelper().makeUrl(this);
    }

    public String getConUser() {
        return conUser;
    }

    public String getUserJDBCURL() {
        return userJDBCURL;
    }

    public void setCharset(String charSet) {
        this.charSet = charSet;
    }

    /**
     * set database type
     *
     * @param databaseType int
     */
    public void setDatabaseType(DatabaseType databaseType) {
        this.dbType = databaseType.getID();
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    /**
     * set driver path
     *
     * @param driverPath String
     */
    public void setDriverFileName(String driverPath) {
        this.driverFileName = driverPath;
    }

    public void setHost(String hostIp) {
        this.host = hostIp;
    }

    public void setName(String name) {
        this.conName = name;
    }

    public void setConPassword(String password) {
        this.conPassword = password;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public void setConUser(String user) {
        this.conUser = user;
    }

    public void setUserJDBCURL(String userJDBCURL) {
        this.userJDBCURL = userJDBCURL;
    }

    public int getDbType() {
        return dbType;
    }

    /* (non-Javadoc)
     * @see com.cubrid.common.configuration.jdbc.IJDBCConnecInfo#getJDBCAttrs()
     */
    public String getJDBCAttrs() {
        return null;
    }

    /* (non-Javadoc)
     * @see com.cubrid.common.configuration.jdbc.IJDBCConnecInfo#isAutoCommit()
     */
    public boolean isAutoCommit() {
        return false;
    }

    /**
     * Set other connection parameters
     *
     * @param key of the parameter
     * @param value of the parameter
     */
    public void setParameter(String key, Object value) {
        // No nothing
    }

    /**
     * Get other connection parameters
     *
     * @param key of the parameter
     * @return value of the parameter
     */
    public Object getParameter(String key) {
        return null;
    }

    /* (non-Javadoc)
     * @see com.cubrid.common.configuration.jdbc.IJDBCConnecInfo#getVersion()
     */
    public String getVersion() {
        return null;
    }

    /**
     * Schema TODO: will be used in the future.
     *
     * @return schemas
     */
    public String getSchema() {
        return "";
    }

    /**
     * get a Connection: you can call DBTool.getConnection
     *
     * @param name String
     * @param hostIp String
     * @param port integer
     * @param dbName String
     * @param dt Integer
     * @param charSet String
     * @param username String
     * @param password String
     * @param driverPath String
     * @param schemaName String
     * @return ConnParameters
     */
    public static ConnParameters getConParam(
            String name,
            String hostIp,
            int port,
            String dbName,
            DatabaseType dt,
            String charSet,
            String username,
            String password,
            String driverPath,
            String schemaName) {
        ConnParameters cp = new ConnParameters();
        cp.setDatabaseType(dt);

        // Fix DB name: Oracle is special. For backward compatibility
        if (dt.getID() == DatabaseType.ORACLE.getID()) {
            String db = dbName;
            String header = "";
            if (dbName.indexOf('/') == 0) {
                db = db.substring(1);
                header = "/";
            }
            String[] names = db.split("/");
            cp.setDbName(header + names[0]);
        } else {
            cp.setDbName(dbName);
        }

        cp.setHost(hostIp);
        cp.setPort(port);
        cp.setCharset(charSet);

        cp.setConUser(username);
        cp.setConPassword(password);
        cp.setDriverFileName(driverPath);
        if (StringUtils.isBlank(name)) {
            cp.setName(getDefaultName(cp));
        } else {
            cp.setName(name);
        }
        return cp;
    }

    /**
     * getConParamByInfo
     *
     * @param newCon IJDBCConnecInfo
     * @return ConnParameters
     */
    public static ConnParameters getConParamByInfo(IJDBCConnecInfo newCon) {
        ConnParameters cp =
                ConnParameters.getConParam(
                        newCon.getConName(),
                        newCon.getHost(),
                        newCon.getPort(),
                        newCon.getDbName(),
                        DatabaseType.getDatabaseTypeByID(newCon.getDbType()),
                        newCon.getCharset(),
                        newCon.getConUser(),
                        newCon.getConPassword(),
                        newCon.getDriverFileName(),
                        "");
        return cp;
    }

    /**
     * Retrieves the default name of the connection parameters.
     *
     * @param cp ConnParameters
     * @return default name
     */
    public static String getDefaultName(ConnParameters cp) {
        return new StringBuffer(cp.getHost())
                .append(":")
                .append(cp.getPort())
                .append("/")
                .append(cp.getDbName())
                .append("/")
                .append(cp.getConUser())
                .toString();
    }

    /**
     * Only check ip,port,db name and user
     *
     * @param value the ConnParameters to be compared.
     * @return true if ip,port,db name and user are same.
     */
    public boolean isSameDB(Object value) {
        if (!(value instanceof ConnParameters)) {
            return false;
        }
        ConnParameters cp = (ConnParameters) value;

        if (dbType != cp.dbType || port != cp.port) {
            return false;
        }

        String h1 = StringUtils.defaultString(host);
        String h2 = StringUtils.defaultString(cp.host);
        if (!h1.equals(h2)) {
            return false;
        }

        String u1 = StringUtils.defaultString(conUser);
        String u2 = StringUtils.defaultString(cp.conUser);
        if (!u1.equalsIgnoreCase(u2)) {
            return false;
        }

        String d1 = StringUtils.defaultString(dbName);
        String d2 = StringUtils.defaultString(cp.dbName);

        boolean isCubrid = (dbType == DatabaseType.CUBRID.getID());
        return isCubrid ? d1.equalsIgnoreCase(d2) : d1.equals(d2);
    }

    /**
     * Return a clone of this instance.
     *
     * <p>Semantic: deep copy. When adding mutable fields in the future, update the copy constructor
     * accordingly.
     *
     * @return ConnParameters
     */
    @Override
    public ConnParameters clone() {
        return new ConnParameters(this);
    }

    /**
     * Copy input to object.
     *
     * @param cp ConnParameters
     */
    public void copy(ConnParameters cp) {
        this.dbType = cp.dbType;
        this.host = cp.host;
        this.port = cp.port;
        this.dbName = cp.dbName;
        this.charSet = cp.charSet;
        this.timeZone = cp.timeZone;
        this.conUser = cp.conUser;
        this.conPassword = cp.conPassword;
        this.driverFileName = cp.driverFileName;
        this.userJDBCURL = cp.userJDBCURL;
        this.conName = cp.conName;
    }

    /**
     * get a Connection
     *
     * @return conn Connection
     * @throws SQLException e
     */
    public Connection createConnection() throws SQLException {
        return DatabaseType.getDatabaseTypeByID(dbType).getConHelper().createConnection(this);
    }

    /**
     * Retrieves the default schema name.
     *
     * @param dt DatabaseType
     * @param dbName String
     * @param userName String
     * @return the default schema name.
     */
    public static String getDefaultSchema(DatabaseType dt, String dbName, String userName) {
        if (dt.getID() == DatabaseType.MSSQL.getID()) {
            return "dbo";
        } else if (dt.getID() == DatabaseType.ORACLE.getID()
                || dt.getID() == DatabaseType.TIBERO.getID()) {
            return StringUtils.upperCase(userName);
        }
        return dbName;
    }

    /** Logical equality based on database identity (same as {@link #isSameDB(Object)}). */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ConnParameters)) return false;
        return isSameDB(obj);
    }

    /** Hash code consistent with isSameDB/equals (dbType, host, port, dbName, conUser). */
    @Override
    public int hashCode() {
        String h = this.host == null ? "" : this.host;
        String d = this.dbName == null ? "" : this.dbName;
        if (this.dbType == DatabaseType.CUBRID.getID()) {
            d = d.toLowerCase();
        }
        String u = this.conUser == null ? "" : this.conUser;
        u = u.toLowerCase();
        return Objects.hash(dbType, h, port, d, u);
    }
}
