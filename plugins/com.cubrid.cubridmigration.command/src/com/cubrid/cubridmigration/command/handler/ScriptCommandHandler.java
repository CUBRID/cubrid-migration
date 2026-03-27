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
package com.cubrid.cubridmigration.command.handler;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.command.ConsoleCommandHandler;
import com.cubrid.cubridmigration.command.ConsoleUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.connection.JDBCDriverManager;
import com.cubrid.cubridmigration.core.connection.JDBCUtil;
import com.cubrid.cubridmigration.core.dbmetadata.DBSchemaInfoFetcherFactory;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSchemaInfoFetcher;
import com.cubrid.cubridmigration.core.dbmetadata.IDBSource;
import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.template.reader.MigrationTemplateReader;
import com.cubrid.cubridmigration.core.engine.template.writer.MigrationTemplateWriter;
import com.cubrid.cubridmigration.cubrid.CUBRIDTimeUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * LogCommandHandler Description
 *
 * @author Kevin Cao
 * @version 1.0 - 2014-1-2 created by Kevin Cao
 */
public class ScriptCommandHandler implements ConsoleCommandHandler {

    private static final Logger LOG = LogUtil.getLogger(ScriptCommandHandler.class);
    private static final List<String> COMMANDS = new ArrayList<String>();

    static {
        String[] cmds = new String[] {"-s", "-t", "-schema", "-o"};
        for (String cmd : cmds) {
            COMMANDS.add(cmd);
        }
    }

    protected PrintStream outPrinter = System.out;
    private Properties dbProperties = new Properties();

    /**
     * If the target table is already in the target database, the create new table option will be
     * disabled.
     *
     * @param config to be configured
     */
    private void configObjectMapping(MigrationConfiguration config) {
        if (!config.targetIsOnline()) {
            return;
        }
        IDBSource ds = config.getTargetConParams();
        if (ds == null) {
            return;
        }
        IDBSchemaInfoFetcher bcf = DBSchemaInfoFetcherFactory.createFetcher(ds);
        Catalog cl = bcf.fetchSchema(ds, null);
        if (cl == null || cl.getSchemas().isEmpty()) {
            return;
        }
        applyTargetTableMapping(config, cl);
    }

    static void applyTargetTableMapping(MigrationConfiguration config, Catalog catalog) {
        if (config == null || catalog == null || catalog.getSchemas().isEmpty()) {
            return;
        }

        List<SourceEntryTableConfig> tables = config.getExpEntryTableCfg();
        for (SourceEntryTableConfig setc : tables) {
            Table tt = null;
            if (StringUtils.isNotBlank(setc.getTargetOwner())) {
                Schema tarSchema = findSchemaByName(catalog, setc.getTargetOwner());
                if (tarSchema != null) {
                    tt = findTableByNameIgnoreCase(tarSchema, setc.getTarget());
                }
            } else {
                tt = findUniqueTableAcrossSchemas(catalog, setc.getTarget());
            }

            if (tt == null) {
                continue;
            }
            setc.setCreateNewTable(false);
            setc.setReplace(false);
            setc.setCreatePK(false);
        }
    }

    private static Schema findSchemaByName(Catalog catalog, String schemaName) {
        if (catalog == null || StringUtils.isBlank(schemaName)) {
            return null;
        }
        for (Schema schema : catalog.getSchemas()) {
            if (schemaName.equalsIgnoreCase(schema.getName())) {
                return schema;
            }
        }
        return null;
    }

    private static Table findUniqueTableAcrossSchemas(Catalog catalog, String tableName) {
        if (catalog == null || StringUtils.isBlank(tableName)) {
            return null;
        }

        Table matched = null;
        for (Schema schema : catalog.getSchemas()) {
            Table table = findTableByNameIgnoreCase(schema, tableName);
            if (table == null) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = table;
        }
        return matched;
    }

    private static Table findTableByNameIgnoreCase(Schema schema, String tableName) {
        if (schema == null || StringUtils.isBlank(tableName)) {
            return null;
        }
        for (Table table : schema.getTables()) {
            if (tableName.equalsIgnoreCase(table.getName())) {
                return table;
            }
        }
        return null;
    }

    /**
     * Read JDBC connection information from db.conf.
     *
     * @param cpname connection name
     * @return ConnParameters
     */
    private ConnParameters getConParamFromDBProperties(String cpname) {
        try {
            String hostIp = dbProperties.getProperty(cpname + ".host");
            int port = Integer.parseInt(dbProperties.getProperty(cpname + ".port"));
            String dbName = dbProperties.getProperty(cpname + ".dbname");
            String dtStr = dbProperties.getProperty(cpname + ".type");
            DatabaseType dt = DatabaseType.getDatabaseTypeIDByDBName(dtStr);
            String charSet = dbProperties.getProperty(cpname + ".charset");
            String username = dbProperties.getProperty(cpname + ".user");
            String password = dbProperties.getProperty(cpname + ".password");
            String driverPath = dbProperties.getProperty(cpname + ".driver");
            String timeZone = dbProperties.getProperty(cpname + ".timezone");
            ConnParameters cp =
                    ConnParameters.getConParam(
                            cpname,
                            hostIp,
                            port,
                            dbName,
                            dt,
                            charSet,
                            username,
                            password,
                            driverPath,
                            null);
            cp.setTimeZone(timeZone);
            return cp;
        } catch (Exception ex) {
            LOG.error("Failed to build connection parameters for [{}].", cpname, ex);
            return null;
        }
    }

    /**
     * Create a configuration template
     *
     * @param tmpArgs the input parameters
     * @return MigrationConfiguration
     */
    private MigrationConfiguration getInitConfig(List<String> tmpArgs) {
        MigrationConfiguration config = null;
        String tpFile = getParameter(tmpArgs, "-template");
        if (StringUtils.isNotBlank(tpFile)) {
            try {
                config = MigrationTemplateReader.parse(tpFile);
            } catch (Exception ex) {
                config = null;
            }
        }
        return config;
    }

    /**
     * Get parameter from input parameter list
     *
     * @param params input parameter list
     * @param paraName to be get
     * @return parameter value
     */
    private String getParameter(List<String> params, String paraName) {
        int index = params.indexOf(paraName);
        if (index >= 0 && (index + 1) < params.size()) {
            return params.get(index + 1);
        }
        return null;
    }

    /**
     * Print migration history's log
     *
     * @param args The parameters input by user
     */
    public void handleCommand(List<String> args) {
        try {
            List<String> tmpArgs = initParameters(args);
            if (tmpArgs.isEmpty()) {
                printHelp();
                return;
            }
            String outputDirPath = getParameter(tmpArgs, "-o");
            if (StringUtils.isBlank(outputDirPath)) {
                outPrinter.println("Output directory should be specified with '-o'.");
                return;
            }
            File outputDir = new File(outputDirPath);
            if (outputDir.exists() && !outputDir.isDirectory()) {
                outPrinter.println("'-o' must be a directory path: " + outputDirPath);
                return;
            }
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                outPrinter.println(
                        "Failed to create output directory: " + outputDir.getAbsolutePath());
                return;
            }
            if (!outputDir.canWrite()) {
                outPrinter.println(
                        "Output directory is not writable: " + outputDir.getAbsolutePath());
                return;
            }
            loadDBProperties();
            try {
                JDBCUtil.initialJdbcByPath(PathUtils.getJDBCLibDir());
            } catch (Exception ex) {
                LOG.warn(
                        "Skip default JDBC init ({}): {}",
                        PathUtils.getJDBCLibDir(),
                        ex.getMessage());
                LOG.debug("Default JDBC init failed", ex);
            }
            boolean isNeedReset = false;
            MigrationConfiguration config = getInitConfig(tmpArgs);
            if (config == null) {
                isNeedReset = true;
                config = new MigrationConfiguration();
                config.setWizardStartDateTime(
                        CUBRIDTimeUtil.wizardStarDateTimeFormat(
                                new Date(System.currentTimeMillis())));
            }
            if (!setSource(config, tmpArgs)) {
                return;
            }
            if (!setTarget(config, tmpArgs)) {
                return;
            }
            setOtherOptions(config, tmpArgs);
            config.setName(
                    config.getSourceDBType().getName(),
                    config.getSourceConParams().getDbName(),
                    config.getWizardStartDateTime());
            Catalog srcCat = config.buildSourceSchema();
            if (srcCat == null) {
                outPrinter.println("Build source schema error.");
                return;
            }
            List<Schema> schemaMappingList = new ArrayList<Schema>();
            for (Schema schema : srcCat.getSchemas()) {
                String schemaName = schema.getName();
                if (StringUtils.isEmpty(schema.getTargetSchemaName())) {
                    schema.setTargetSchemaName(schemaName);
                }
                Schema mappingSchema = new Schema();
                mappingSchema.setName(schemaName);
                mappingSchema.setTargetSchemaName(schemaName);
                schemaMappingList.add(mappingSchema);
            }
            config.removeTargetSchemaList();
            config.setTargetSchemaList(schemaMappingList);
            config.setSrcCatalog(srcCat, isNeedReset);
            if (isNeedReset) {
                config.setAll(true);
            }
            configObjectMapping(config);
            String outputFileName = config.getName() + ".xml";
            File outputFile = new File(outputDir, outputFileName);
            MigrationTemplateWriter.save(
                    config,
                    outputFile.getCanonicalPath(),
                    "yes".equalsIgnoreCase(getParameter(tmpArgs, "-schema")));
            outPrinter.println(outputFile.getCanonicalPath() + " was created successfully.");
        } catch (Exception ex) {
            outPrinter.println("Unexpected error. Please check the log for more information.");
            LOG.error("Unexpected error while processing CLI arguments: {}.", args, ex);
        }
    }

    /**
     * Initialize the input parameters
     *
     * @param args origin input parameter
     * @return standard parameter list
     */
    private List<String> initParameters(List<String> args) {
        List<String> tmpArgs = new ArrayList<String>();
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String paraName = iterator.next();
            tmpArgs.add(paraName);
            // If current value is a parameter name
            if (COMMANDS.indexOf(paraName) >= 0) {
                // If args has next value as parameter value
                if (iterator.hasNext()) {
                    String paramValue = iterator.next();
                    // If next value is a parameter name
                    if (COMMANDS.indexOf(paramValue) >= 0) {
                        tmpArgs.add("");
                    }
                    tmpArgs.add(paramValue);
                } else {
                    tmpArgs.add("");
                }
            }
        }
        return tmpArgs;
    }

    /** Load db.conf configuration at the start up. */
    private void loadDBProperties() {
        // dbProperties
        File dbProFile = new File(PathUtils.getInstallPath() + "db.conf");
        if (!dbProFile.exists() || dbProFile.isDirectory()) {
            return;
        }
        try (FileInputStream fis = new FileInputStream(dbProFile)) {
            dbProperties.load(fis);
        } catch (Exception ex) {
            outPrinter.println("Load db.conf error.");
            LOG.error("Failed to load db.conf", ex);
        }
    }

    /** printHelp */
    protected void printHelp() {
        ConsoleUtils.printHelp("/com/cubrid/cubridmigration/command/help_script.txt");
    }

    /**
     * Set other configuration of script
     *
     * @param config to be set
     * @param tmpArgs input parameters
     */
    private void setOtherOptions(MigrationConfiguration config, List<String> tmpArgs) {
        String haValue = getParameter(tmpArgs, "-ha");
        config.setCreateConstrainsBeforeData("yes".equalsIgnoreCase(haValue));
        String errValue = getParameter(tmpArgs, "-err");
        config.setWriteErrorRecords("yes".equalsIgnoreCase(errValue));
        String tcValue = getParameter(tmpArgs, "-tc");
        if (StringUtils.isNumeric(tcValue)) {
            config.setExportThreadCount(Integer.parseInt(tcValue));
        }
        String pfcValue = getParameter(tmpArgs, "-pfc");
        if (StringUtils.isNumeric(pfcValue)) {
            config.setPageFetchCount(Integer.parseInt(pfcValue));
        }
        String ccValue = getParameter(tmpArgs, "-cc");
        if (StringUtils.isNumeric(ccValue)) {
            config.setCommitCount(Integer.parseInt(ccValue));
        }
    }

    /**
     * Set source configuration to migration script.
     *
     * @param config to be set
     * @param args parameters
     * @return true if set successfully
     */
    private boolean setSource(MigrationConfiguration config, List<String> args) {
        String svalue = getParameter(args, "-s");
        if (StringUtils.isBlank(svalue)) {
            outPrinter.println("Please specify source with '-s'.");
            return false;
        }
        String type = dbProperties.getProperty(svalue + ".type");
        if (StringUtils.isBlank(type)) {
            return false;
        }
        try {
            // Set source type
            config.setSourceType(type);
        } catch (Exception ex) {
            outPrinter.println("Invalid type in the db.conf of " + svalue);
            return false;
        }
        if (config.sourceIsOnline()) {
            ConnParameters scp = getConParamFromDBProperties(svalue);
            if (scp == null) {
                outPrinter.println("Read JDBC configuration error:" + svalue);
                return false;
            }
            if (!checkJDBCDriver(scp.getDatabaseType(), scp.getDriverFileName())) {
                outPrinter.println("Invalid driver : " + scp.getDriverFileName());
                return false;
            }
            try {
                Connection con = scp.createConnection();
                con.close();
            } catch (Exception e) {
                outPrinter.println("Can't connect database:" + svalue);
                LOG.error("Failed to connect to source database [{}].", svalue, e);
                return false;
            }
            config.setSourceConParams(scp);
        } else if (config.sourceIsXMLDump()) {
            String charSet = dbProperties.getProperty(svalue + ".charset");
            charSet = StringUtils.isBlank(charSet) ? "utf-8" : charSet;
            String xmlfile = dbProperties.getProperty(svalue + ".file");
            File xF = new File(xmlfile);
            if (!xF.exists() || xF.isDirectory()) {
                outPrinter.println("Invalid MySQL XML dump file:" + xmlfile);
                return false;
            }
            config.setSourceFileName(xmlfile);
            config.setSourceFileEncoding(charSet);
            config.setSourceFileTimeZone("Default");
        } else if (config.sourceIsCSV()) {
            // TODO:list all csv files
            outPrinter.println("CSV source is not supported.");
            return false;
        } else if (config.sourceIsSQL()) {
            // TODO:list all csv files
            outPrinter.println("SQL source is not supported.");
            return false;
        }
        return true;
    }

    /**
     * Set target configuration of migration script.
     *
     * @param config to be set
     * @param tmpArgs parameters
     * @return true if set successfully.
     */
    private boolean setTarget(MigrationConfiguration config, List<String> tmpArgs) {
        String tvalue = getParameter(tmpArgs, "-t");
        if (StringUtils.isBlank(tvalue)) {
            outPrinter.println("Please specify target with '-t'.");
            return false;
        }
        String tType = dbProperties.getProperty(tvalue + ".type");
        if (StringUtils.isBlank(tType)) {
            return false;
        }
        try {
            config.setDestTypeName(tType);
        } catch (Exception ex) {
            outPrinter.println("Invalid target type in the db.conf of " + tvalue);
            return false;
        }
        if (config.targetIsOnline()) {
            ConnParameters tcp = getConParamFromDBProperties(tvalue);
            if (tcp == null) {
                outPrinter.println("Read JDBC configuration error:" + tvalue);
                return false;
            }
            if (!checkJDBCDriver(tcp.getDatabaseType(), tcp.getDriverFileName())) {
                outPrinter.println("Invalid driver : " + tcp.getDriverFileName());
                return false;
            }
            try (Connection con = tcp.createConnection()) {
                DatabaseMetaData metaData = con.getMetaData();
                int version =
                        metaData.getDatabaseMajorVersion() * 10
                                + metaData.getDatabaseMinorVersion();
                config.setTargetDBVersion(String.valueOf(version));
            } catch (Exception e) {
                outPrinter.println("Can't connect database:" + tvalue);
                LOG.error("Failed to connect to target database [{}].", tvalue, e);
                return false;
            }
            config.setTargetConParams(tcp);
            applyTargetOutputOptions(config, dbProperties, tvalue);
        } else if (config.targetIsFile()) {
            String prefix = dbProperties.getProperty(tvalue + ".file_prefix");
            config.setTargetFilePrefix(
                    prefix == null
                            ? (config.getSourceConParams() == null
                                    ? config.getSrcConnOwner()
                                    : config.getSourceConParams().getDbName())
                            : prefix);
            config.setFileRepositroyPath(dbProperties.getProperty(tvalue + ".output"));
            config.setTargetCharSet(dbProperties.getProperty(tvalue + ".charset"));
            config.setTargetFileTimeZone("Default");
            applyTargetOutputOptions(config, dbProperties, tvalue);
            applyFileTargetOptions(config, dbProperties, tvalue);
        }
        return true;
    }

    static void applyTargetOutputOptions(
            MigrationConfiguration config, Properties properties, String targetName) {
        if (config == null || properties == null || StringUtils.isBlank(targetName)) {
            return;
        }

        String addSchema = properties.getProperty(targetName + ".add_schema");
        config.setAddUserSchema(isDefaultYes(addSchema));
    }

    static void applyFileTargetOptions(
            MigrationConfiguration config, Properties properties, String targetName) {
        if (config == null || properties == null || StringUtils.isBlank(targetName)) {
            return;
        }

        String splitSchema = properties.getProperty(targetName + ".split_schema");
        config.setSplitSchema(isDefaultYes(splitSchema));

        String oneTableOneFile = properties.getProperty(targetName + ".one_table_one_file");
        config.setOneTableOneFile(isDefaultNo(oneTableOneFile));
    }

    /**
     * Check and add JDBC driver if necessary.
     *
     * @param dt DatabaseType
     * @param driverPath driverPath
     * @return true if driver exists or added successfully
     */
    private boolean checkJDBCDriver(DatabaseType dt, String driverPath) {
        boolean isDriverAlreadyRegistered =
                JDBCDriverManager.getInstance().addDriver(driverPath, false);
        boolean successfullyAddedNewDriver = (dt.getJDBCData(driverPath) != null);
        return successfullyAddedNewDriver || isDriverAlreadyRegistered;
    }

    /**
     * Return true if the value is null or not "no".
     *
     * @param value the text to check
     * @return true if default is "yes"
     */
    private static boolean isDefaultYes(String value) {
        if (value == null) {
            return true;
        }
        return !value.equalsIgnoreCase("no");
    }

    /**
     * Return true if the value is "yes". Return false is null or anything else.
     *
     * @param value the text to check
     * @return true if default is "no"
     */
    private static boolean isDefaultNo(String value) {
        if (value == null) {
            return false;
        }
        return value.equalsIgnoreCase("yes");
    }
}
