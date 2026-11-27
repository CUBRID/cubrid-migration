package com.cubrid.cubridmigration.core.engine.exporter.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.engine.RecordExportedListener;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.mysql.meta.MySQLXMLSchemaParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

@DisplayName("PerformMYSQLXMLDataReader")
class PerformMYSQLXMLDataReaderTest {

    @Test
    @DisplayName("same-named tables are routed to their respective database sections")
    void sameNamedTables_areRoutedByDatabase() throws Exception {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig firstTable = sourceTable("db1", "shared");
        SourceEntryTableConfig secondTable = sourceTable("db2", "shared");
        config.addExpEntryTableCfg(firstTable);
        config.addExpEntryTableCfg(secondTable);

        List<SourceEntryTableConfig> startedTables = new ArrayList<>();
        PerformMYSQLXMLDataReader reader = new PerformMYSQLXMLDataReader();
        reader.setConfig(config);
        reader.setOneNewRecord(
                new RecordExportedListener() {
                    @Override
                    public void processRecords(String tableName, List<Record> records) {}

                    @Override
                    public void startExportTable(String tableName) {}

                    @Override
                    public void endExportTable(String tableName) {}

                    @Override
                    public void startExportTable(SourceEntryTableConfig table) {
                        startedTables.add(table);
                    }
                });

        reader.startElement("", "", "database", named("db1"));
        reader.startElement("", "", "table_data", named("shared"));
        reader.endElement("", "", "table_data");
        reader.startElement("", "", "database", named("db2"));
        reader.startElement("", "", "table_data", named("shared"));
        reader.endElement("", "", "table_data");

        assertThat(startedTables).containsExactly(firstTable, secondTable);
        assertThat(reader.isParsingCompleted()).isTrue();
    }

    @Test
    @DisplayName("parsing is complete only after every configured table has finished")
    void parsingCompleted_countsEachDatabaseTableSeparately() throws Exception {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig first = sourceTable("db1", "shared");
        SourceEntryTableConfig second = sourceTable("db2", "shared");
        config.addExpEntryTableCfg(first);
        config.addExpEntryTableCfg(second);

        PerformMYSQLXMLDataReader reader = noOpReader(config);

        reader.startElement("", "", "database", named("db1"));
        reader.startElement("", "", "table_data", named("shared"));
        assertThat(reader.isParsingCompleted()).isFalse();
        reader.endElement("", "", "table_data");

        reader.startElement("", "", "table_data", named("shared"));
        reader.endElement("", "", "table_data");
        assertThat(reader.isParsingCompleted()).isFalse();

        reader.startElement("", "", "database", named("db2"));
        reader.startElement("", "", "table_data", named("shared"));
        reader.endElement("", "", "table_data");
        assertThat(reader.isParsingCompleted()).isTrue();
    }

    @Test
    @DisplayName("a single-database dump without a database element still works")
    void singleDatabaseDump_noSchemaName_tableIsSelected() throws Exception {
        MigrationConfiguration config = new MigrationConfiguration();
        SourceEntryTableConfig table = sourceTable(null, "orders");
        config.addExpEntryTableCfg(table);

        List<SourceEntryTableConfig> startedTables = new ArrayList<>();
        PerformMYSQLXMLDataReader reader = new PerformMYSQLXMLDataReader();
        reader.setConfig(config);
        reader.setOneNewRecord(
                new RecordExportedListener() {
                    @Override
                    public void processRecords(String tableName, List<Record> records) {}

                    @Override
                    public void startExportTable(String tableName) {}

                    @Override
                    public void endExportTable(String tableName) {}

                    @Override
                    public void startExportTable(SourceEntryTableConfig t) {
                        startedTables.add(t);
                    }
                });

        reader.startElement("", "", "table_data", named("orders"));
        reader.endElement("", "", "table_data");

        assertThat(startedTables).containsExactly(table);
        assertThat(reader.isParsingCompleted()).isTrue();
    }

    @Test
    @DisplayName(
            "rows use the selected database's column type, including through a legacy listener")
    void convertsRowsUsingTheirOwnTableMetadata() throws Exception {
        String xml =
                "<mysqldump><database name='DB1'><table_structure name='shared'>"
                        + "<field Field='col3' Type='datetime' Null='YES'/></table_structure>"
                        + "</database><database name='DB2'><table_structure name='shared'>"
                        + "<field Field='col3' Type='text' Null='YES'/></table_structure>"
                        + "</database></mysqldump>";
        MySQLXMLSchemaParser parser = new MySQLXMLSchemaParser();
        SAXParserFactory.newInstance()
                .newSAXParser()
                .parse(new InputSource(new StringReader(xml)), parser);
        MigrationConfiguration config = new MigrationConfiguration();
        config.setSourceType("xml");
        config.setSrcCatalog(parser.getCatalog(), true);

        List<Record> exported = new ArrayList<>();
        RecordExportedListener listener =
                new RecordExportedListener() {
                    @Override
                    public void processRecords(String tableName, List<Record> records) {
                        assertThat(tableName).isEqualTo("shared");
                        exported.addAll(records);
                    }

                    @Override
                    public void startExportTable(String tableName) {}

                    @Override
                    public void endExportTable(String tableName) {}
                };
        String value = "2026-01-01 10:00:00";
        List<List<String[]>> rows =
                Collections.singletonList(Collections.singletonList(new String[] {"col3", value}));
        for (String owner : new String[] {"DB1", "DB2"}) {
            new XMLDataParsingTask(
                            config, listener, config.getExpEntryTableCfg(owner, "shared"), rows)
                    .run();
        }

        assertThat(exported).hasSize(2);
        assertThat(exported.get(0).getColumnValueMap().get("col3"))
                .isInstanceOf(java.sql.Timestamp.class);
        assertThat(exported.get(1).getColumnValueMap()).containsEntry("col3", value);
    }

    private static SourceEntryTableConfig sourceTable(String owner, String name) {
        SourceEntryTableConfig table = new SourceEntryTableConfig();
        table.setOwner(owner);
        table.setName(name);
        table.setMigrateData(true);
        return table;
    }

    private static Attributes named(String name) {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "name", "name", "CDATA", name);
        return attributes;
    }

    private static PerformMYSQLXMLDataReader noOpReader(MigrationConfiguration config) {
        PerformMYSQLXMLDataReader reader = new PerformMYSQLXMLDataReader();
        reader.setConfig(config);
        reader.setOneNewRecord(
                new RecordExportedListener() {
                    @Override
                    public void processRecords(String tableName, List<Record> records) {}

                    @Override
                    public void startExportTable(String tableName) {}

                    @Override
                    public void endExportTable(String tableName) {}
                });
        return reader;
    }
}
