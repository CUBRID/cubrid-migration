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
package com.cubrid.cubridmigration.ui.wizard.page;

import static com.cubrid.cubridmigration.core.dbtype.DatabaseType.getDatabaseTypeByID;

import com.cubrid.cubridmigration.core.common.log.LogUtil;
import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceFKConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceGrantConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceIndexConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlFunctionConfig;
import com.cubrid.cubridmigration.core.engine.config.SourcePlcsqlProcedureConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSQLTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSequenceConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceSynonymConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceViewConfig;
import com.cubrid.cubridmigration.mysql.trans.MySQL2CUBRIDMigParas;
import com.cubrid.cubridmigration.ui.SWTResourceConstents;
import com.cubrid.cubridmigration.ui.common.UIConstant;
import com.cubrid.cubridmigration.ui.message.Messages;
import com.cubrid.cubridmigration.ui.preference.MigrationConfigPage;
import com.cubrid.cubridmigration.ui.wizard.MigrationWizard;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolItem;

/**
 * new wizard step 5. Confirm Migration Settings
 *
 * @author fulei,Kevin Cao
 */
public class ConfirmationPage extends BaseConfirmationPage {
    private static final Logger LOG = LogUtil.getLogger(ConfirmationPage.class);

    private static final int USERSCHEMA_VERSION = 112;

    /**
     * Get the migration configuration summary
     *
     * @param migration MigrationConfiguration
     * @param styleRanges List<StyleRange> displayed in StyleText
     * @return String summary text
     */
    public static String getConfigSummary(
            MigrationConfiguration migration, List<StyleRange> styleRanges) {
        String lineSeparator = System.getProperty("line.separator");
        String tabSeparator = "\t";
        StringBuffer text = new StringBuffer();
        // source db
        text.append(Messages.confirmSettingsSourceDatabase)
                .append(lineSeparator)
                .append(tabSeparator)
                .append(Messages.confirmSettingsType);
        if (migration.sourceIsOnline()) {
            ConnParameters srcConnParameters = migration.getSourceConParams();
            text.append("Online").append(lineSeparator).append(tabSeparator);
            text.append(Messages.confirmHostIP)
                    .append("  ")
                    .append(srcConnParameters.getHost())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmDatabaseType)
                    .append("  ")
                    .append(getDatabaseTypeByID(srcConnParameters.getDbType()).getName())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmDatabaseName)
                    .append("  ")
                    .append(srcConnParameters.getDbName())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmPort)
                    .append("  ")
                    .append(srcConnParameters.getPort())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmUserName)
                    .append("  ")
                    .append(srcConnParameters.getConUser())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmCharset)
                    .append("  ")
                    .append(srcConnParameters.getCharset())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmTimezone)
                    .append("  ");
            // cubrid source doesn't read time zone
            if (srcConnParameters.getTimeZone() == null) {
                text.append(Messages.msgDefault).append(lineSeparator);
            } else {
                int length =
                        srcConnParameters.getTimeZone().length() > 9
                                ? 9
                                : srcConnParameters.getTimeZone().length();
                text.append(srcConnParameters.getTimeZone().substring(0, length))
                        .append(lineSeparator);
            }

        } else {
            text.append("MYSQL XML Dump file").append(lineSeparator).append(tabSeparator);
            text.append(Messages.confirmFile)
                    .append(migration.getSourceFileName())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmCharset)
                    .append(migration.getSourceFileEncoding())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmTimezone);
            int length =
                    migration.getSourceFileTimeZone().length() > 9
                            ? 9
                            : migration.getSourceFileTimeZone().length();
            text.append(migration.getSourceFileTimeZone().substring(0, length))
                    .append(lineSeparator);
        }
        // target db
        text.append(Messages.confirmSettingsTargetDatabase)
                .append(lineSeparator)
                .append(tabSeparator)
                .append(Messages.confirmSettingsType);
        if (migration.targetIsOnline()) {
            ConnParameters tcp = migration.getTargetConParams();
            text.append("Online").append(lineSeparator).append(tabSeparator);
            text.append(Messages.confirmHostIP)
                    .append("  ")
                    .append(tcp.getHost())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmDatabaseType)
                    .append("  ")
                    .append(getDatabaseTypeByID(tcp.getDbType()).getName())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmDatabaseName)
                    .append("  ")
                    .append(tcp.getDbName())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmPort)
                    .append("  ")
                    .append(tcp.getPort())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmCharset)
                    .append("  ")
                    .append(tcp.getCharset())
                    .append(lineSeparator)
                    .append(tabSeparator)
                    .append(Messages.confirmTimezone)
                    .append("  ");
            String timeZone = tcp.getTimeZone();
            timeZone = timeZone == null ? UIConstant.DEFAULT_TIME_ZONE : timeZone;
            int length = timeZone.length() > 9 ? 9 : timeZone.length();
            text.append(timeZone.substring(0, length)).append(lineSeparator);
        } else if (migration.targetIsFile()) {
            text.append(Messages.confirmFileRepository).append(" (");
            if (styleRanges != null) {
                styleRanges.add(
                        new StyleRange(
                                text.length(),
                                migration.getTargetDataFileFormatLabel().length(),
                                SWTResourceConstents.COLOR_BLUE,
                                null));
            }
            text.append(migration.getTargetDataFileFormatLabel());
            text.append(")").append(lineSeparator).append(tabSeparator);

            text.append(Messages.confirmDatabaseType)
                    .append("  ")
                    .append(
                            migration.isAddUserSchema()
                                    ? Messages.confirmCubrid112Higher
                                    : Messages.confirmCubrid110Lower)
                    .append(lineSeparator)
                    .append(tabSeparator);

            text.append(Messages.confirmPath).append("  ");
            if (styleRanges != null) {
                styleRanges.add(
                        new StyleRange(
                                text.length(),
                                migration.getFileRepositroyPath().length(),
                                SWTResourceConstents.COLOR_BLUE,
                                null));
            }
            text.append(migration.getFileRepositroyPath());
            text.append(lineSeparator).append(tabSeparator);

            int oldLength;
            boolean isSupportMultiSchema =
                    migration.getSrcCatalog().getDatabaseType().isSupportMultiSchema();
            String conUser = migration.getSrcConnOwner();

            if (migration.isSplitSchema()) {
                // table
                text.append(Messages.confrimTable).append(lineSeparator);
                oldLength = text.length();

                Set<String> tableOwners = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreateNewTable()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (tableOwners.contains(owner)) {
                        continue;
                    }

                    tableOwners.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetTableFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // view
                text.append(tabSeparator);
                text.append(Messages.confrimView).append(lineSeparator);
                oldLength = text.length();

                Set<String> viewOwners = new HashSet<>();
                for (SourceViewConfig expView : migration.getExpViewCfg()) {
                    if (!expView.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expView.getOwner() : conUser;
                    if (viewOwners.contains(owner)) {
                        continue;
                    }

                    viewOwners.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetViewFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // pk
                text.append(tabSeparator);
                text.append(Messages.confrimPk).append(lineSeparator);
                oldLength = text.length();

                Set<String> expPKs = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreatePK()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (expPKs.contains(owner)) {
                        continue;
                    }

                    expPKs.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetPkFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // fk
                text.append(tabSeparator);
                text.append(Messages.confrimFk).append(lineSeparator);
                oldLength = text.length();

                Set<String> expFKs = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreateNewTable() || expTable.getFKConfigList().size() == 0) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (expFKs.contains(owner)) {
                        continue;
                    }

                    expFKs.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetFkFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // serial
                text.append(tabSeparator);
                text.append(Messages.confrimSerial).append(lineSeparator);
                oldLength = text.length();

                Set<String> expSerials = new HashSet<>();
                for (SourceSequenceConfig expSerial : migration.getExpSerialCfg()) {
                    if (!expSerial.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expSerial.getOwner() : conUser;
                    if (expSerials.contains(owner)) {
                        continue;
                    }

                    expSerials.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetSerialFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // synonym
                text.append(tabSeparator);
                text.append(Messages.confrimSynonym).append(lineSeparator);
                oldLength = text.length();

                Set<String> expSynonyms = new HashSet<>();
                for (SourceSynonymConfig expSynonym : migration.getExpSynonymCfg()) {
                    if (!expSynonym.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expSynonym.getOwner() : conUser;
                    if (expSynonyms.contains(owner)) {
                        continue;
                    }

                    expSynonyms.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetSynonymFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // grant
                text.append(tabSeparator);
                text.append(Messages.confrimGrant).append(lineSeparator);
                oldLength = text.length();
                Set<String> grantFileKeys = new HashSet<String>();

                for (SourceGrantConfig expGrant : migration.getExpGrantCfg()) {
                    if (!expGrant.isCreate()) {
                        continue;
                    }

                    String grantFileKey =
                            isSupportMultiSchema
                                    ? expGrant.getOwner() + expGrant.getSourceObjectOwner()
                                    : conUser + expGrant.getSourceObjectOwner();
                    if (grantFileKeys.contains(grantFileKey)) {
                        continue;
                    }

                    grantFileKeys.add(grantFileKey);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(
                            migration
                                    .getTargetGrantFileName(
                                            isSupportMultiSchema ? expGrant.getOwner() : conUser)
                                    .get(expGrant.getSourceObjectOwner()));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // procedure
                text.append(tabSeparator).append(Messages.confrimProcedure).append(lineSeparator);
                oldLength = text.length();
                Set<String> expProcedures = new HashSet<>();
                for (SourcePlcsqlProcedureConfig expProcedure :
                        migration.getExpPlcsqlProcedureCfg()) {
                    if (!expProcedure.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expProcedure.getOwner() : conUser;
                    if (expProcedures.contains(owner)) {
                        continue;
                    }

                    expProcedures.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    String procedureFile =
                            migration.getTargetPlcsqlProcedureFileName(owner).values().stream()
                                    .findAny()
                                    .orElse("");
                    int lastSlashIndex = procedureFile.lastIndexOf(File.separator);
                    if (lastSlashIndex != -1) {
                        procedureFile = procedureFile.substring(0, lastSlashIndex + 1);
                    }
                    text.append(procedureFile);
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // function
                text.append(tabSeparator).append(Messages.confrimFunction).append(lineSeparator);
                oldLength = text.length();
                Set<String> expFunctions = new HashSet<>();
                for (SourcePlcsqlFunctionConfig expFunction : migration.getExpPlcsqlFunctionCfg()) {
                    if (!expFunction.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expFunction.getOwner() : conUser;
                    if (expFunctions.contains(owner)) {
                        continue;
                    }

                    expFunctions.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    String functionFile =
                            migration.getTargetPlcsqlFunctionFileName(owner).values().stream()
                                    .findAny()
                                    .orElse("");
                    int lastSlashIndex = functionFile.lastIndexOf(File.separator);
                    if (lastSlashIndex != -1) {
                        functionFile = functionFile.substring(0, lastSlashIndex + 1);
                    }
                    text.append(functionFile);
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // index
                text.append(tabSeparator).append(Messages.confrimIndex).append(lineSeparator);
                oldLength = text.length();
                Set<String> expIndexes = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreateNewTable() || expTable.getIndexConfigList().size() == 0) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (expIndexes.contains(owner)) {
                        continue;
                    }

                    expIndexes.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetIndexFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }
            } else {
                // schema
                text.append(Messages.confrimSchema).append(lineSeparator);
                oldLength = text.length();
                List<String> schemas = new ArrayList<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreateNewTable()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (schemas.contains(owner)) {
                        break;
                    }
                    schemas.add(owner);
                }

                for (SourceViewConfig expView : migration.getExpViewCfg()) {
                    if (!expView.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expView.getOwner() : conUser;
                    if (schemas.contains(owner)) {
                        break;
                    }
                    schemas.add(owner);
                }

                for (SourceSequenceConfig expSerial : migration.getExpSerialCfg()) {
                    if (!expSerial.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expSerial.getOwner() : conUser;
                    if (schemas.contains(owner)) {
                        break;
                    }
                    schemas.add(owner);
                }

                for (SourceSynonymConfig expSynonym : migration.getExpSynonymCfg()) {
                    if (!expSynonym.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expSynonym.getOwner() : conUser;
                    if (schemas.contains(owner)) {
                        break;
                    }
                    schemas.add(owner);
                }

                for (SourceGrantConfig expGrant : migration.getExpGrantCfg()) {
                    if (!expGrant.isCreate()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expGrant.getOwner() : conUser;
                    if (schemas.contains(owner)) {
                        break;
                    }
                    schemas.add(owner);
                }

                for (String schema : schemas) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetSchemaFileName(schema));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }

                // index
                text.append(tabSeparator).append(Messages.confrimIndex).append(lineSeparator);
                oldLength = text.length();
                List<String> expIndexes = new ArrayList<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isCreateNewTable()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (expIndexes.contains(owner)) {
                        continue;
                    }

                    if (expTable.isCreatePK()
                            || expTable.getIndexConfigList().size() > 0
                            || expTable.getFKConfigList().size() > 0) {
                        expIndexes.add(owner);
                    }
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
                if (text.length() == oldLength) {
                    text.append(tabSeparator).append(tabSeparator);
                    text.append("-");
                    text.append(lineSeparator);
                }
            }

            // data
            text.append(tabSeparator).append(Messages.confrimData).append(lineSeparator);
            oldLength = text.length();
            if (migration.isOneTableOneFile()) {
                text.append(tabSeparator).append(tabSeparator);
                text.append(Messages.btnOneTableOneFile).append(lineSeparator);
            } else {
                Set<String> expTableData = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isMigrateData()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (expTableData.contains(owner)) {
                        continue;
                    }

                    expTableData.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    if (migration.getDestType() == MigrationConfiguration.DEST_DB_UNLOAD
                            || migration.getDestType() == MigrationConfiguration.DEST_SQL) {
                        text.append(migration.getTargetDataFileName(owner));
                    } else {
                        text.append(migration.getFileRepositroyPath());
                        text.append(owner).append(File.separator);
                        text.append(migration.getTargetFilePrefix())
                                .append(owner)
                                .append(Messages.lblConfirmDataFormat)
                                .append(migration.getDataFileExt());
                    }
                    text.append(lineSeparator);
                }
            }
            if (styleRanges != null) {
                styleRanges.add(
                        new StyleRange(
                                oldLength,
                                text.length() - oldLength,
                                SWTResourceConstents.COLOR_BLUE,
                                null));
            }
            if (text.length() == oldLength) {
                text.append(tabSeparator).append(tabSeparator);
                text.append("-");
                text.append(lineSeparator);
            }

            // updateStatistic
            text.append(tabSeparator).append(Messages.confrimUpdateStatistic).append(lineSeparator);
            oldLength = text.length();
            if (migration.isUpdateStatistics()) {
                Set<String> updateStatistics = new HashSet<>();
                for (SourceEntryTableConfig expTable : migration.getExpEntryTableCfg()) {
                    if (!expTable.isMigrateData()) {
                        continue;
                    }

                    String owner = isSupportMultiSchema ? expTable.getOwner() : conUser;
                    if (updateStatistics.contains(owner)) {
                        continue;
                    }

                    updateStatistics.add(owner);
                    text.append(tabSeparator).append(tabSeparator);
                    text.append(migration.getTargetUpdateStatisticFileName(owner));
                    text.append(lineSeparator);
                }
                if (styleRanges != null) {
                    styleRanges.add(
                            new StyleRange(
                                    oldLength,
                                    text.length() - oldLength,
                                    SWTResourceConstents.COLOR_BLUE,
                                    null));
                }
            }
            if (text.length() == oldLength) {
                text.append(tabSeparator).append(tabSeparator);
                text.append("-");
                text.append(lineSeparator);
            }

            int length =
                    migration.getTargetFileTimeZone().length() > 9
                            ? 9
                            : migration.getTargetFileTimeZone().length();
            text.append(Messages.confirmTimezone)
                    .append(" ")
                    .append(migration.getTargetFileTimeZone().substring(0, length))
                    .append(lineSeparator);
        }

        // table
        List<SourceEntryTableConfig> sourceTableConfigList = migration.getExpEntryTableCfg();
        if (!sourceTableConfigList.isEmpty()) {
            text.append(Messages.confrimExportTables).append(lineSeparator);
            for (SourceTableConfig sourceTableConfig : sourceTableConfigList) {
                if (!sourceTableConfig.isCreateNewTable() && !sourceTableConfig.isMigrateData()) {
                    continue;
                }
                text.append(tabSeparator)
                        .append(sourceTableConfig.getName())
                        .append(tabSeparator)
                        .append(" -> ")
                        .append(tabSeparator)
                        .append(sourceTableConfig.getTarget())
                        .append(lineSeparator);
            }
        }
        // view
        List<SourceViewConfig> sourceConfigViewList = migration.getExpViewCfg();
        if (!sourceConfigViewList.isEmpty()) {
            text.append(Messages.confrimExportViews).append(lineSeparator);
            for (SourceConfig sourceConfig : sourceConfigViewList) {
                if (!sourceConfig.isCreate()) {
                    continue;
                }
                text.append(tabSeparator)
                        .append(sourceConfig.getName())
                        .append(tabSeparator)
                        .append(" -> ")
                        .append(tabSeparator)
                        .append(sourceConfig.getTarget())
                        .append(lineSeparator);
            }
        }
        // sql
        List<SourceSQLTableConfig> sourceSQLTableConfigList = migration.getExpSQLCfg();
        if (!sourceSQLTableConfigList.isEmpty()) {
            text.append(Messages.confrimSQLTables).append(lineSeparator);
            for (SourceSQLTableConfig sourceSQLTableConfig : sourceSQLTableConfigList) {
                if (!sourceSQLTableConfig.isCreateNewTable()
                        && !sourceSQLTableConfig.isMigrateData()) {
                    continue;
                }
                text.append(tabSeparator)
                        .append(sourceSQLTableConfig.getSql())
                        .append(tabSeparator)
                        .append(" -> ")
                        .append(tabSeparator)
                        .append(sourceSQLTableConfig.getTarget())
                        .append(lineSeparator);
            }
        }
        // FK
        if (migration.hasFKExports()) {
            text.append(Messages.confrimExportFKs).append(lineSeparator);
            for (SourceEntryTableConfig sourceEntryTableConfig : migration.getExpEntryTableCfg()) {
                if (!sourceEntryTableConfig.isCreateNewTable()) {
                    continue;
                }
                List<SourceFKConfig> sourceFKConfigList = sourceEntryTableConfig.getFKConfigList();
                if (!sourceTableConfigList.isEmpty()) {
                    for (SourceFKConfig sourceFKConfig : sourceFKConfigList) {
                        if (!sourceFKConfig.isCreate()) {
                            continue;
                        }
                        text.append(tabSeparator)
                                .append(sourceFKConfig.getName())
                                .append(tabSeparator)
                                .append(" -> ")
                                .append(tabSeparator)
                                .append(sourceFKConfig.getTarget())
                                .append(lineSeparator);
                    }
                }
            }
        }
        // index
        if (migration.hasIndexExports()) {
            StringBuilder indexText = new StringBuilder();

            for (SourceEntryTableConfig sourceEntryTableConfig : migration.getExpEntryTableCfg()) {
                if (!sourceEntryTableConfig.isCreateNewTable()) {
                    continue;
                }
                List<SourceIndexConfig> sourceIndexConfigList =
                        sourceEntryTableConfig.getIndexConfigList();
                if (!sourceTableConfigList.isEmpty()) {
                    for (SourceIndexConfig sourceIndexConfig : sourceIndexConfigList) {
                        if (!sourceIndexConfig.isCreate()) {
                            continue;
                        }
                        indexText
                                .append(tabSeparator)
                                .append(sourceIndexConfig.getName())
                                .append(tabSeparator)
                                .append(" -> ")
                                .append(tabSeparator)
                                .append(sourceIndexConfig.getTarget())
                                .append(lineSeparator);
                    }
                }
            }

            if (indexText.length() > 0) {
                text.append(Messages.confrimExportIndexs).append(lineSeparator);
                text.append(indexText);
            }
        }
        // sequence
        List<SourceSequenceConfig> sourceConfigSequencesList = migration.getExpSerialCfg();
        if (!sourceConfigSequencesList.isEmpty()) {
            text.append(Messages.confrimExportSerial).append(lineSeparator);
            for (SourceConfig sourceConfig : sourceConfigSequencesList) {
                if (!sourceConfig.isCreate()) {
                    continue;
                }
                text.append(tabSeparator)
                        .append(sourceConfig.getName())
                        .append(tabSeparator)
                        .append(" -> ")
                        .append(tabSeparator)
                        .append(sourceConfig.getTarget())
                        .append(lineSeparator);
            }
        }
        // synonym
        if (!migration.targetIsOnline()
                || Integer.parseInt(migration.getTargetDBVersion()) >= USERSCHEMA_VERSION) {
            List<SourceSynonymConfig> sourceConfigSynonymList = migration.getExpSynonymCfg();
            if (!sourceConfigSynonymList.isEmpty()) {
                text.append(Messages.confrimExportSynonym).append(lineSeparator);
                for (SourceConfig sourceConfig : sourceConfigSynonymList) {
                    if (!sourceConfig.isCreate()) {
                        continue;
                    }
                    text.append(tabSeparator)
                            .append(sourceConfig.getName())
                            .append(tabSeparator)
                            .append(" -> ")
                            .append(tabSeparator)
                            .append(sourceConfig.getTarget())
                            .append(lineSeparator);
                }
            }
        }
        // grant
        if (!migration.targetIsOnline()
                || Integer.parseInt(migration.getTargetDBVersion()) >= USERSCHEMA_VERSION) {
            List<SourceGrantConfig> sourceConfigGrantList = migration.getExpGrantCfg();
            if (!sourceConfigGrantList.isEmpty()) {
                text.append(Messages.confrimExportGrants).append(lineSeparator);
                for (SourceConfig sourceConfig : sourceConfigGrantList) {
                    if (!sourceConfig.isCreate()) {
                        continue;
                    }
                    text.append(tabSeparator)
                            .append(sourceConfig.getName())
                            .append(tabSeparator)
                            .append(" -> ")
                            .append(tabSeparator)
                            .append(sourceConfig.getTarget())
                            .append(lineSeparator);
                }
            }
        }
        return text.toString();
    }

    private boolean isScriptSaved;
    private Button btnSaveSchema;

    private final List<StyleRange> styleRanges = new ArrayList<StyleRange>();

    public ConfirmationPage(String pageName) {
        super(pageName);
    }

    /** @param parent Composite */
    public void createControl(Composite parent) {
        super.createControl(parent);
        new ToolItem(tbTools, SWT.SEPARATOR);
        btnSaveSchema = new Button(comRoot, SWT.CHECK);
        btnSaveSchema.setText("Save Source Catalog");
        btnSaveSchema.setToolTipText("Save Source Catalog to Script");
        btnSaveSchema.addSelectionListener(
                new SelectionAdapter() {

                    public void widgetSelected(SelectionEvent ev) {
                        MigrationWizard wzd = getMigrationWizard();
                        wzd.setSaveSchema(btnSaveSchema.getSelection());
                    }
                });
    }

    /**
     * When migration wizard displayed current page.
     *
     * @param event PageChangedEvent
     */
    protected void afterShowCurrentPage(PageChangedEvent event) {
        try {
            MigrationWizard wzd = getMigrationWizard();
            setTitle(wzd.getStepNoMsg(this) + Messages.confirmMigrationPageTile);
            setDescription(Messages.confirmMigrationPageDescription);
            isScriptSaved = false;
            MigrationConfiguration cfg = wzd.getMigrationConfig();
            btnSaveSchema.setSelection(cfg.getOfflineSrcCatalog() != null);
            wzd.setSaveSchema(btnSaveSchema.getSelection());
            if (isFirstVisible && !wzd.isLoadMigrationScript()) {
                cfg.setExportThreadCount(MigrationConfigPage.getDefaultExportThreadCount());
                cfg.setImportThreadCount(
                        MigrationConfigPage.getDefaultImpportThreadCountEachTable());
                cfg.setCommitCount(MigrationConfigPage.getCommitCount());
                cfg.setPageFetchCount(MigrationConfigPage.getPageFetchingCount());
                cfg.setMaxCountPerFile(MigrationConfigPage.getFileMaxSize());
                cfg.setImplicitEstimate(false);
            }
            postMigrationData();
        } catch (RuntimeException e) {
            LOG.error(LogUtil.getExceptionString(e));
            throw e;
        } finally {
            isFirstVisible = false;
        }
    }

    /**
     * Handle page leaving
     *
     * @param event PageChangingEvent
     */
    protected void handlePageLeaving(PageChangingEvent event) {
        if (!isGotoNextPage(event) && isScriptSaved) {
            this.getMigrationWizard().getMigrationConfig().buildConfigAndTargetSchema(false);
        }
        super.handlePageLeaving(event);
    }

    /** postMigrationData */
    protected void postMigrationData() {
        MigrationConfiguration cfg = getMigrationWizard().getMigrationConfig();
        styleRanges.clear();
        txtSummary.setText(getConfigSummary(cfg, styleRanges));
        for (StyleRange sr : styleRanges) {
            txtSummary.setStyleRange(sr);
        }
        setDDLText();
        switchText(false);
        setSpecialParametersForMysqlSource();
    }

    /** */
    protected void setSpecialParametersForMysqlSource() {
        MigrationConfiguration cfg = getMigrationWizard().getMigrationConfig();
        // Only MYSQL using these parameters
        if (cfg.getSourceDBType().getID() == DatabaseType.MYSQL.getID()) {
            String s1 =
                    MySQL2CUBRIDMigParas.getMigrationParamter(MySQL2CUBRIDMigParas.UNPARSED_TIME);
            cfg.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIME, s1);
            String s2 =
                    MySQL2CUBRIDMigParas.getMigrationParamter(MySQL2CUBRIDMigParas.UNPARSED_DATE);
            cfg.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_DATE, s2);
            String s3 =
                    MySQL2CUBRIDMigParas.getMigrationParamter(
                            MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP);
            cfg.putOtherParam(MySQL2CUBRIDMigParas.UNPARSED_TIMESTAMP, s3);
            String s4 =
                    MySQL2CUBRIDMigParas.getMigrationParamter(MySQL2CUBRIDMigParas.REPLAXE_CHAR0);
            cfg.putOtherParam(MySQL2CUBRIDMigParas.REPLAXE_CHAR0, s4);
        }
    }

    /** Prepare for saving migration script. */
    protected void prepare4SaveScript() {
        isScriptSaved = true;
        super.prepare4SaveScript();
    }

    protected boolean isSaveSchema() {
        return btnSaveSchema.getSelection();
    }
}
