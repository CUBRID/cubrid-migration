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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.mysql.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import java.io.StringReader;
import java.sql.DatabaseMetaData;

import javax.xml.parsers.SAXParserFactory;

@DisplayName("MySQLXMLSchemaParser")
class MySQLXMLSchemaParserTest {

    /** Wraps table_structure children in the surrounding elements a real dump carries. */
    private static String dumpOf(String tableBody) {
        return "<?xml version=\"1.0\"?>\n"
                + "<mysqldump>\n"
                + "<database name=\"testdb\">\n"
                + "<table_structure name=\"orders\">\n"
                + tableBody
                + "</table_structure>\n"
                + "</database>\n"
                + "</mysqldump>\n";
    }

    private static Catalog parse(String xml) throws Exception {
        MySQLXMLSchemaParser parser = new MySQLXMLSchemaParser();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(false);
        factory.newSAXParser().parse(new InputSource(new StringReader(xml)), parser);
        return parser.getCatalog();
    }

    private static Table firstTable(String tableBody) throws Exception {
        return parse(dumpOf(tableBody)).getSchemas().get(0).getTables().get(0);
    }

    private static Column column(Table table, String name) {
        return table.getColumnByName(name);
    }

    @Nested
    @DisplayName("database and tables")
    class DatabaseAndTables {

        @Test
        @DisplayName("the database name becomes both the catalog and the schema name")
        void databaseName_namesBothCatalogAndSchema() throws Exception {
            Catalog catalog =
                    parse(dumpOf("  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"));

            assertThat(catalog.getName()).isEqualTo("testdb");
            assertThat(catalog.getSchemas()).extracting(Schema::getName).containsExactly("testdb");
        }

        @Test
        @DisplayName("the dump is read as a MySQL catalog")
        void catalog_isMarkedAsMySQL() throws Exception {
            Catalog catalog =
                    parse(dumpOf("  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"));

            assertThat(catalog.getDatabaseType()).isEqualTo(DatabaseType.MYSQL);
        }

        @Test
        @DisplayName("each table_structure becomes a table on the schema")
        void tableStructure_becomesATable() throws Exception {
            assertThat(
                            firstTable("  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n")
                                    .getName())
                    .isEqualTo("orders");
        }

        // Only the structure section describes columns. Once the data section opens, the field
        // elements inside it carry values rather than definitions and are stepped over.
        @Test
        @DisplayName("field elements inside table_data are not read as columns")
        void fieldsInsideTableData_areNotColumns() throws Exception {
            Catalog catalog =
                    parse(
                            "<?xml version=\"1.0\"?>\n<mysqldump>\n<database name=\"testdb\">\n"
                                    + "<table_structure name=\"orders\">\n"
                                    + "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                    + "</table_structure>\n"
                                    + "<table_data name=\"orders\">\n"
                                    + "  <row><field name=\"ghost\">1</field></row>\n"
                                    + "</table_data>\n"
                                    + "</database>\n</mysqldump>\n");

            assertThat(catalog.getSchemas().get(0).getTables().get(0).getColumns())
                    .extracting(Column::getName)
                    .containsExactly("a");
        }
    }

    @Nested
    @DisplayName("column definitions")
    class ColumnDefinitions {

        @Test
        @DisplayName("the declared type is split into its name, precision and scale")
        void declaredType_isSplitIntoItsParts() throws Exception {
            Table table =
                    firstTable("  <field Field=\"amt\" Type=\"decimal(10,2)\" Null=\"YES\" />\n");

            Column amt = column(table, "amt");
            assertThat(amt.getShownDataType()).isEqualTo("decimal(10,2)");
            assertThat(amt.getDataType()).isEqualTo("decimal");
            assertThat(amt.getPrecision()).isEqualTo(10);
            assertThat(amt.getScale()).isEqualTo(2);
            assertThat(amt.getCharLength()).isEqualTo(10);
        }

        @Test
        @DisplayName("Null=\"YES\" is the only spelling that makes a column nullable")
        void nullAttribute_decidesNullability() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                    + "  <field Field=\"b\" Type=\"int(11)\" Null=\"NO\" />\n");

            assertThat(column(table, "a").isNullable()).isTrue();
            assertThat(column(table, "b").isNullable()).isFalse();
        }

        @Test
        @DisplayName("Extra=\"auto_increment\" marks the column auto-increment")
        void autoIncrementExtra_marksTheColumn() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\""
                                    + " Extra=\"auto_increment\" />\n"
                                    + "  <field Field=\"b\" Type=\"int(11)\" Null=\"NO\" Extra=\"\""
                                    + " />\n");

            assertThat(column(table, "a").isAutoIncrement()).isTrue();
            assertThat(column(table, "b").isAutoIncrement()).isFalse();
        }

        @Test
        @DisplayName("the Default attribute is carried across as the default value")
        void defaultAttribute_becomesTheDefaultValue() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" Default=\"7\""
                                    + " />\n");

            assertThat(column(table, "a").getDefaultValue()).isEqualTo("7");
        }

        // A scale wider than the precision cannot be rendered, so the precision is widened the
        // same way the JDBC fetcher widens it.
        @Test
        @DisplayName("a scale wider than the precision -> precision widened to 16")
        void scaleWiderThanPrecision_widensPrecisionToSixteen() throws Exception {
            Table table =
                    firstTable("  <field Field=\"a\" Type=\"decimal(2,5)\" Null=\"YES\" />\n");

            assertThat(column(table, "a").getPrecision()).isEqualTo(16);
            assertThat(column(table, "a").getScale()).isEqualTo(5);
        }

        // A dump never states a byte length, so every VARCHAR takes the same 255 regardless of
        // the character length it declares.
        @Test
        @DisplayName("a VARCHAR gets a byte length of 255 whatever its declared length")
        void varchar_getsTwoHundredAndFiftyFiveBytes() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"varchar(50)\" Null=\"YES\" />\n"
                                    + "  <field Field=\"b\" Type=\"int(11)\" Null=\"YES\" />\n");

            assertThat(column(table, "a").getByteLength()).isEqualTo(255);
            assertThat(column(table, "b").getByteLength()).isZero();
        }

        // A type with no length parses to a precision of -1, which is below the scale of 0 and so
        // trips the widening rule above. The column ends up claiming 16 digits it never declared.
        @Test
        @DisplayName("a type with no length -> precision 16, though the dump declared none")
        void typeWithoutLength_endsUpClaimingSixteen() throws Exception {
            Table table = firstTable("  <field Field=\"a\" Type=\"varchar\" Null=\"YES\" />\n");

            assertThat(column(table, "a").getPrecision()).isEqualTo(16);
            assertThat(column(table, "a").getCharLength()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("keys and indexes")
    class KeysAndIndexes {

        @Test
        @DisplayName("a key named PRIMARY becomes the primary key, not an index")
        void primaryKey_becomesTheKey() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"0\" Key_name=\"PRIMARY\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"BTREE\" />\n");

            assertThat(table.getPk().getName()).isEqualTo("PRIMARY");
            assertThat(table.getPk().getPkColumns()).containsExactly("a");
            assertThat(table.getIndexes()).isEmpty();
        }

        @Test
        @DisplayName("several rows of one key name build one multi-column key")
        void repeatedKeyName_buildsOneMultiColumnKey() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <field Field=\"b\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"0\" Key_name=\"PRIMARY\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"BTREE\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"0\" Key_name=\"PRIMARY\""
                                + " Column_name=\"b\" Collation=\"A\" Index_type=\"BTREE\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"1\" Key_name=\"ix\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"BTREE\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"1\" Key_name=\"ix\""
                                + " Column_name=\"b\" Collation=\"A\" Index_type=\"BTREE\" />\n");

            assertThat(table.getPk().getPkColumns()).containsExactly("a", "b");
            assertThat(table.getIndexes()).hasSize(1);
            assertThat(table.getIndexes().get(0).getColumnNames()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("a unique BTREE index is clustered and unique")
        void uniqueBtree_isClusteredAndUnique() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"0\" Key_name=\"uq\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"BTREE\" />\n");

            Index index = table.getIndexes().get(0);
            assertThat(index.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexClustered);
            assertThat(index.isUnique()).isTrue();
        }

        @Test
        @DisplayName("a non-unique BTREE index is clustered but not unique")
        void nonUniqueBtree_isClusteredOnly() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"1\" Key_name=\"ix\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"BTREE\" />\n");

            Index index = table.getIndexes().get(0);
            assertThat(index.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexClustered);
            assertThat(index.isUnique()).isFalse();
        }

        // DEFECT: uniqueness is only picked up on the BTREE branch, so a unique index of any
        // other kind is migrated as an ordinary one and the constraint is lost
        // - see MySQLXMLSchemaParser.startElement()
        @Test
        @DisplayName("a unique index that is not BTREE loses its uniqueness")
        void uniqueNonBtree_losesItsUniqueness() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"0\" Key_name=\"uq\""
                                + " Column_name=\"a\" Collation=\"A\" Index_type=\"HASH\" />\n");

            Index index = table.getIndexes().get(0);
            assertThat(index.getIndexType()).isEqualTo(DatabaseMetaData.tableIndexOther);
            assertThat(index.isUnique()).isFalse();
        }

        @Test
        @DisplayName("Collation=\"D\" -> descending, anything missing -> ascending")
        void collation_decidesTheSortDirection() throws Exception {
            Table descending =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                + "  <key Table=\"orders\" Non_unique=\"1\" Key_name=\"ix\""
                                + " Column_name=\"a\" Collation=\"D\" Index_type=\"BTREE\" />\n");
            Table unstated =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"NO\" />\n"
                                    + "  <key Table=\"orders\" Non_unique=\"1\" Key_name=\"ix\""
                                    + " Column_name=\"a\" Index_type=\"BTREE\" />\n");

            assertThat(descending.getIndexes().get(0).getColumnOrderRules()).containsExactly(false);
            assertThat(unstated.getIndexes().get(0).getColumnOrderRules()).containsExactly(true);
        }
    }

    @Nested
    @DisplayName("row counts")
    class RowCounts {

        @Test
        @DisplayName("the Rows attribute sets the count when no data section follows")
        void rowsAttribute_setsTheCount() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                + "  <options Name=\"orders\" Engine=\"InnoDB\" Rows=\"42\" />\n");

            assertThat(table.getTableRowCount()).isEqualTo(42L);
        }

        @Test
        @DisplayName("no Rows attribute -> the count stays at zero")
        void missingRowsAttribute_leavesTheCountAtZero() throws Exception {
            Table table =
                    firstTable(
                            "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                    + "  <options Name=\"orders\" Engine=\"InnoDB\" />\n");

            assertThat(table.getTableRowCount()).isZero();
        }

        // The rows actually present in the dump are counted as they close, and that count
        // replaces whatever the Rows attribute claimed.
        @Test
        @DisplayName("rows present in the data section override the Rows attribute")
        void countedRows_overrideTheRowsAttribute() throws Exception {
            Catalog catalog =
                    parse(
                            "<?xml version=\"1.0\"?>\n<mysqldump>\n<database name=\"testdb\">\n"
                                    + "<table_structure name=\"orders\">\n"
                                    + "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                    + "  <options Name=\"orders\" Rows=\"42\" />\n"
                                    + "</table_structure>\n"
                                    + "<table_data name=\"orders\">\n"
                                    + "  <row><field name=\"a\">1</field></row>\n"
                                    + "  <row><field name=\"a\">2</field></row>\n"
                                    + "</table_data>\n"
                                    + "</database>\n</mysqldump>\n");

            assertThat(catalog.getSchemas().get(0).getTables().get(0).getTableRowCount())
                    .isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("views")
    class Views {

        // DEFECT: a table_structure marked as a view is taken off the table list and a View is
        // built from it, but that View is never added to the schema, so the object disappears
        // from the migration entirely
        // - see MySQLXMLSchemaParser.startElement()
        @Test
        @DisplayName("a structure marked VIEW is dropped from the tables and never becomes a view")
        void viewMarkedStructure_disappears() throws Exception {
            Catalog catalog =
                    parse(
                            dumpOf(
                                    "  <field Field=\"a\" Type=\"int(11)\" Null=\"YES\" />\n"
                                            + "  <options Name=\"orders\" Comment=\"VIEW\" />\n"));

            Schema schema = catalog.getSchemas().get(0);
            assertThat(schema.getTables()).isEmpty();
            assertThat(schema.getViews()).isEmpty();
        }
    }

    @Nested
    @DisplayName("fatalError()")
    class FatalError {

        // mysqldump writes control characters straight into the XML, which the parser reports as
        // fatal. Those are swallowed so the rest of the dump still loads.
        @Test
        @DisplayName("an invalid XML character is swallowed so the dump keeps loading")
        void invalidXmlCharacter_isSwallowed() {
            MySQLXMLSchemaParser parser = new MySQLXMLSchemaParser();

            assertThatCode(
                            () ->
                                    parser.fatalError(
                                            new SAXParseException(
                                                    "An invalid XML character (Unicode: 0x3) was"
                                                            + " found",
                                                    null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("any other fatal error is raised")
        void otherFatalError_isRaised() {
            MySQLXMLSchemaParser parser = new MySQLXMLSchemaParser();

            assertThatThrownBy(
                            () ->
                                    parser.fatalError(
                                            new SAXParseException(
                                                    "Element type must be followed", null)))
                    .isInstanceOf(SAXParseException.class);
        }
    }
}
