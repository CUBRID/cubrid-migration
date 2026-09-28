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
package com.cubrid.cubridmigration.core.dbobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.testutil.TestTableFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;

@DisplayName("Catalog")
class CatalogTest {

    private static Schema schema(String name) {
        Schema schema = new Schema();
        schema.setName(name);
        return schema;
    }

    private static Version version(int major, int minor) {
        Version version = new Version();
        version.setDbMajorVersion(major);
        version.setDbMinorVersion(minor);
        return version;
    }

    private static ConnParameters connection() {
        return ConnParameters.getConParam(
                "con",
                "localhost",
                33000,
                "demodb",
                DatabaseType.CUBRID,
                "UTF-8",
                "dba",
                "secret",
                "driver.jar",
                null);
    }

    private static Catalog cubrid() {
        Catalog catalog = new Catalog();
        catalog.setName("demodb");
        catalog.setHost("localhost");
        catalog.setPort(33000);
        catalog.setDatabaseType(DatabaseType.CUBRID);
        catalog.setVersion(version(11, 2));
        return catalog;
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("getDatabaseType()")
    class GetDatabaseType {

        @Test
        @DisplayName("the stored type comes back as its singleton")
        void storedType_comesBackAsItsSingleton() {
            Catalog catalog = new Catalog();
            catalog.setDatabaseType(DatabaseType.CUBRID);

            assertThat(catalog.getDatabaseType()).isSameAs(DatabaseType.CUBRID);
        }

        @Test
        @DisplayName("a catalog without a type reads as MySQL")
        void unsetType_readsAsMySQL() {
            // DEFECT: the type is kept as an int that starts at 0, which is the MySQL ID, so a
            // catalog nobody typed looks like MySQL and also passes isDbHasUserSchema()
            // - see Catalog.getDatabaseType()
            Catalog catalog = new Catalog();

            assertThat(catalog.getDatabaseType()).isSameAs(DatabaseType.MYSQL);
            assertThat(catalog.isDbHasUserSchema()).isTrue();
        }
    }

    @Nested
    @DisplayName("setSchemas()")
    class SetSchemas {

        @Test
        @DisplayName("each schema of the list is added and owned, nulls skipped")
        void listedSchemas_areAddedAndOwned() {
            Catalog catalog = new Catalog();
            Schema a = schema("a");
            Schema b = schema("b");

            catalog.setSchemas(Arrays.asList(a, null, b));

            assertThat(catalog.getSchemas()).containsExactly(a, b);
            assertThat(a.getCatalog()).isSameAs(catalog);
            assertThat(b.getCatalog()).isSameAs(catalog);
        }

        @Test
        @DisplayName("null removes every schema")
        void nullList_removesEverySchema() {
            Catalog catalog = new Catalog();
            catalog.addSchema(schema("a"));

            catalog.setSchemas(null);

            assertThat(catalog.getSchemas()).isEmpty();
        }

        @Test
        @DisplayName("a list is added to the schemas already there instead of replacing them")
        void list_isAddedToTheOldSchemas() {
            // DEFECT: only null clears the schemas, so setting a list appends it, setting the
            // same list twice lists each schema twice, and an empty list keeps the old ones
            // - see Catalog.setSchemas()
            Catalog catalog = new Catalog();
            Schema old = schema("old");
            catalog.addSchema(old);
            Schema a = schema("a");

            catalog.setSchemas(Arrays.asList(a));
            catalog.setSchemas(Arrays.asList(a));
            catalog.setSchemas(new ArrayList<Schema>());

            assertThat(catalog.getSchemas()).containsExactly(old, a, a);
        }

        @Test
        @DisplayName("the catalog's own schema list cannot be set back")
        void ownList_throwsConcurrentModificationException() {
            // DEFECT: the given list is walked while addSchema() appends to it when it is the
            // catalog's own list, so setting getSchemas() back fails
            // - see Catalog.setSchemas()
            Catalog catalog = new Catalog();
            catalog.addSchema(schema("a"));

            assertThatThrownBy(() -> catalog.setSchemas(catalog.getSchemas()))
                    .isInstanceOf(ConcurrentModificationException.class);
        }
    }

    @Nested
    @DisplayName("getSchemaByName()")
    class GetSchemaByName {

        @Test
        @DisplayName("the name matches ignoring case, the first match winning")
        void name_matchesIgnoringCase() {
            Catalog catalog = new Catalog();
            Schema upper = schema("A");
            catalog.addSchema(upper);
            catalog.addSchema(schema("a"));

            assertThat(catalog.getSchemaByName("a")).isSameAs(upper);
        }

        @Test
        @DisplayName("a null name gives the first schema")
        void nullName_givesTheFirstSchema() {
            Catalog catalog = new Catalog();
            Schema first = schema("first");
            catalog.addSchema(first);
            catalog.addSchema(schema("second"));

            assertThat(catalog.getSchemaByName(null)).isSameAs(first);
        }

        @Test
        @DisplayName("an unknown name, or any name in an empty catalog, gives null")
        void unknownNameOrEmptyCatalog_returnsNull() {
            Catalog catalog = new Catalog();
            assertThat(catalog.getSchemaByName(null)).isNull();

            catalog.addSchema(schema("a"));

            assertThat(catalog.getSchemaByName("zz")).isNull();
        }
    }

    @Nested
    @DisplayName("addSchema()")
    class AddSchema {

        @Test
        @DisplayName("a schema of another catalog is taken over but stays listed there")
        void schemaOfAnotherCatalog_isTakenOver() {
            Catalog first = new Catalog();
            Catalog second = new Catalog();
            Schema schema = schema("a");
            first.addSchema(schema);

            second.addSchema(schema);

            assertThat(schema.getCatalog()).isSameAs(second);
            assertThat(first.getSchemas()).containsExactly(schema);
            assertThat(second.getSchemas()).containsExactly(schema);
        }

        @Test
        @DisplayName("the same schema added twice is listed twice")
        void sameSchemaTwice_isListedTwice() {
            Catalog catalog = new Catalog();
            Schema schema = schema("a");

            catalog.addSchema(schema);
            catalog.addSchema(schema);

            assertThat(catalog.getSchemas()).containsExactly(schema, schema);
        }

        @Test
        @DisplayName("null is ignored")
        void nullSchema_isIgnored() {
            Catalog catalog = new Catalog();

            catalog.addSchema(null);

            assertThat(catalog.getSchemas()).isEmpty();
        }
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("catalogs with the same host, name and port hash alike")
        void sameHostNameAndPort_hashAlike() {
            Catalog first = new Catalog();
            Catalog second = new Catalog();
            assertThat(first.hashCode()).isEqualTo(second.hashCode());

            first.setHost("localhost");
            second.setHost("localhost");
            first.setName("testdb");
            second.setName("testdb");
            first.setPort(3306);
            second.setPort(3306);

            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        @DisplayName("the type, schemas and creation time leave the hash alone")
        void otherFields_leaveTheHashAlone() {
            Catalog catalog = new Catalog();
            catalog.setHost("localhost");
            catalog.setName("testdb");
            catalog.setPort(3306);
            int hash = catalog.hashCode();

            catalog.setDatabaseType(DatabaseType.CUBRID);
            catalog.addSchema(schema("a"));
            catalog.setCreateTime(5);

            assertThat(catalog.hashCode()).isEqualTo(hash);
        }
    }

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("the host, name and port decide equality")
        void hostNameAndPort_decideEquality() {
            Catalog first = new Catalog();
            Catalog second = new Catalog();
            assertThat(first)
                    .isEqualTo(first)
                    .isEqualTo(second)
                    .isNotEqualTo(12)
                    .isNotEqualTo(null);

            first.setHost("localhost");
            assertThat(second).isNotEqualTo(first);
            second.setHost("localhost");
            first.setName("testdb");
            assertThat(second).isNotEqualTo(first);
            second.setName("testdb");
            first.setPort(3306);
            assertThat(second).isNotEqualTo(first);
            second.setPort(3306);
            assertThat(first).isEqualTo(second);

            second.setName("testdb2");
            assertThat(first).isNotEqualTo(second);
            second.setName("testdb");
            second.setHost("localhost2");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("the name compares with case")
        void name_comparesWithCase() {
            Catalog upper = new Catalog();
            upper.setName("TESTDB");
            Catalog lower = new Catalog();
            lower.setName("testdb");

            assertThat(upper).isNotEqualTo(lower);
        }

        @Test
        @DisplayName("the type, schemas and creation time are not compared")
        void otherFields_areNotCompared() {
            Catalog first = cubrid();
            Catalog second = cubrid();
            second.setDatabaseType(DatabaseType.ORACLE);
            second.addSchema(schema("a"));
            second.setCreateTime(5);

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("a subclass is never equal")
        void subclass_isNeverEqual() {
            assertThat(new Catalog()).isNotEqualTo(new Catalog() {});
        }
    }

    @Nested
    @DisplayName("saveXML()")
    class SaveXML {

        @Test
        @DisplayName("the catalog is written without its connection, which is put back afterwards")
        void catalog_isWrittenWithoutItsConnection(@TempDir Path dir) throws Exception {
            Catalog catalog = cubrid();
            ConnParameters connection = connection();
            catalog.setConnectionParameters(connection);
            File file = dir.resolve("out/catalog.xml").toFile();

            catalog.saveXML(file);

            Catalog loaded = Catalog.loadXML(read(file));
            assertThat(loaded).isEqualTo(catalog);
            assertThat(loaded.getDatabaseType()).isSameAs(DatabaseType.CUBRID);
            assertThat(loaded.getConnectionParameters()).isNull();
            assertThat(read(file)).doesNotContain("secret");
            assertThat(catalog.getConnectionParameters()).isSameAs(connection);
        }

        @Test
        @DisplayName("an existing file is rejected, and the connection is still put back")
        void existingFile_isRejected(@TempDir Path dir) throws Exception {
            Catalog catalog = cubrid();
            ConnParameters connection = connection();
            catalog.setConnectionParameters(connection);
            File file = Files.write(dir.resolve("catalog.xml"), new byte[] {1}).toFile();

            assertThatThrownBy(() -> catalog.saveXML(file))
                    .isInstanceOf(RuntimeException.class)
                    .cause()
                    .isInstanceOf(IOException.class)
                    .hasMessage("Create file failed:" + file.getAbsolutePath());
            assertThat(catalog.getConnectionParameters()).isSameAs(connection);
        }

        @Test
        @DisplayName("the schemas lose their catalog and the columns their table")
        void backReferences_areLeftCleared(@TempDir Path dir) {
            // DEFECT: the back references are cleared for XMLEncoder and never put back, so a
            // saved catalog, such as the configuration's source catalog, loses them for good
            // - see Catalog.saveXML()
            Catalog catalog = cubrid();
            Schema schema = schema("public");
            catalog.addSchema(schema);
            Table table = TestTableFactory.createTable("t1", "c1");
            schema.addTable(table);

            catalog.saveXML(dir.resolve("catalog.xml").toFile());

            assertThat(schema.getCatalog()).isNull();
            assertThat(table.getColumns().get(0).getTableOrView()).isNull();
        }
    }

    @Nested
    @DisplayName("loadXML()")
    class LoadXML {

        @Test
        @DisplayName("a saved catalog reads back with its objects but schemas without a catalog")
        void savedCatalog_readsBack(@TempDir Path dir) throws Exception {
            Catalog catalog = cubrid();
            Schema schema = schema("public");
            catalog.addSchema(schema);
            schema.addTable(TestTableFactory.createTable("t1", "c1", "c2"));
            File file = dir.resolve("catalog.xml").toFile();
            catalog.saveXML(file);

            Catalog loaded = Catalog.loadXML(read(file));

            Schema loadedSchema = loaded.getSchemas().get(0);
            Table loadedTable = loadedSchema.getTables().get(0);
            assertThat(loadedSchema.getName()).isEqualTo("public");
            assertThat(loadedSchema.getCatalog()).isNull();
            assertThat(loadedTable.getColumns())
                    .extracting(Column::getName)
                    .containsExactly("c1", "c2");
            assertThat(loadedTable.getColumns().get(0).getTableOrView()).isSameAs(loadedTable);
            assertThat(loaded.getVersion().getDbMajorVersion()).isEqualTo(11);
            assertThat(loaded.getVersion().getDbMinorVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("XML holding no object fails on the first read")
        void xmlWithoutObject_throwsArrayIndexOutOfBoundsException() {
            assertThatThrownBy(() -> Catalog.loadXML("<?xml version=\"1.0\"?><java></java>"))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("XML holding another object cannot be cast")
        void otherObject_throwsClassCastException() {
            assertThatThrownBy(
                            () ->
                                    Catalog.loadXML(
                                            "<?xml"
                                                + " version=\"1.0\"?><java><string>x</string></java>"))
                    .isInstanceOf(ClassCastException.class);
        }
    }

    @Nested
    @DisplayName("isDbHasUserSchema()")
    class IsDbHasUserSchema {

        @ParameterizedTest(name = "[{index}] CUBRID {0}.{1} -> {2}")
        @DisplayName("CUBRID has user schemas from 11.2 on")
        @CsvSource({
            "11, 2, true",
            "11, 3, true",
            "12, 0, true",
            "11, 1, false",
            "10, 2, false",
            "9, 9, false"
        })
        void cubrid_hasUserSchemasFrom112(int major, int minor, boolean expected) {
            Catalog catalog = new Catalog();
            catalog.setDatabaseType(DatabaseType.CUBRID);
            catalog.setVersion(version(major, minor));

            assertThat(catalog.isDbHasUserSchema()).isEqualTo(expected);
        }

        @Test
        @DisplayName("any other type has user schemas, with or without a version")
        void otherType_hasUserSchemas() {
            Catalog catalog = new Catalog();
            catalog.setDatabaseType(DatabaseType.ORACLE);

            assertThat(catalog.isDbHasUserSchema()).isTrue();
        }

        @Test
        @DisplayName("CUBRID without a version fails with an NPE")
        void cubridWithoutVersion_throwsNullPointerException() {
            // DEFECT: the version is read without a null check, so a CUBRID catalog built
            // before its version is known cannot be asked
            // - see Catalog.isDbHasUserSchema()
            Catalog catalog = new Catalog();
            catalog.setDatabaseType(DatabaseType.CUBRID);

            assertThatThrownBy(catalog::isDbHasUserSchema).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("createCatalog()")
    class CreateCatalog {

        @Test
        @DisplayName("the copy takes the identity and settings, with new maps and schema list")
        void copy_takesTheIdentityAndSettings() {
            Catalog catalog = cubrid();
            catalog.setCreateSql("create database demodb");
            catalog.setDBAGroup(true);
            catalog.setCharset("utf8");
            ConnParameters connection = connection();
            catalog.setConnectionParameters(connection);
            Schema schema = schema("public");
            catalog.addSchema(schema);

            Catalog copy = catalog.createCatalog();

            assertThat(copy.getName()).isEqualTo("demodb");
            assertThat(copy.getHost()).isEqualTo("localhost");
            assertThat(copy.getDatabaseType()).isSameAs(DatabaseType.CUBRID);
            assertThat(copy.getVersion()).isSameAs(catalog.getVersion());
            assertThat(copy.getCreateSql()).isEqualTo("create database demodb");
            assertThat(copy.isDBAGroup()).isTrue();
            assertThat(copy.getConnectionParameters()).isSameAs(connection);
            assertThat(copy.getCharset()).isEqualTo("utf8");
            assertThat(copy.getAdditionalInfo()).isNotSameAs(catalog.getAdditionalInfo());
            assertThat(copy.getSupportedDataType()).isNotSameAs(catalog.getSupportedDataType());
            assertThat(copy.getSchemas()).isNotSameAs(catalog.getSchemas()).containsExactly(schema);
        }

        @Test
        @DisplayName("the creation time and object counts are not taken")
        void creationTimeAndCounts_areNotTaken() {
            Catalog catalog = cubrid();
            catalog.setCreateTime(5);
            catalog.getAllTablesCountMap().put("public", 3);

            Catalog copy = catalog.createCatalog();

            assertThat(copy.getCreateTime()).isNotEqualTo(5);
            assertThat(copy.getAllTablesCountMap()).isEmpty();
        }

        @Test
        @DisplayName("the shared schemas now point at the copy, in the original too")
        void sharedSchemas_pointAtTheCopy() {
            // DEFECT: the copy re-adds the same Schema objects with addSchema(), which points
            // them at the copy, so the original catalog's schemas no longer point back at it
            // - see Catalog.createCatalog()
            Catalog catalog = cubrid();
            Schema schema = schema("public");
            catalog.addSchema(schema);

            Catalog copy = catalog.createCatalog();

            assertThat(catalog.getSchemas().get(0).getCatalog()).isSameAs(copy);
        }

        @Test
        @DisplayName("the copy leaves out the port and so is not equal to the original")
        void copy_isNotEqualToTheOriginal() {
            // DEFECT: the port is not copied, so the copy compares and hashes differently from
            // the catalog it was made from
            // - see Catalog.createCatalog()
            Catalog catalog = cubrid();

            Catalog copy = catalog.createCatalog();

            assertThat(copy.getPort()).isZero();
            assertThat(copy).isNotEqualTo(catalog);
            assertThat(copy.hashCode()).isNotEqualTo(catalog.hashCode());
        }
    }
}
