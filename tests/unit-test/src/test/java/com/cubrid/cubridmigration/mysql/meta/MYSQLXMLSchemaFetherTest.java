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
import com.cubrid.cubridmigration.core.dbobject.SchemaCatalog;
import com.cubrid.cubridmigration.core.dbobject.SchemaEntry;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.mysql.MysqlXmlDumpSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("MYSQLXMLSchemaFether")
class MYSQLXMLSchemaFetherTest {

    private static final String DUMP =
            "<?xml version=\"1.0\"?>\n"
                    + "<mysqldump>\n"
                    + "<database name=\"testdb\">\n"
                    + "<table_structure name=\"orders\">\n"
                    + "  <field Field=\"id\" Type=\"int(11)\" Null=\"NO\" />\n"
                    + "</table_structure>\n"
                    + "</database>\n"
                    + "</mysqldump>\n";

    private MYSQLXMLSchemaFether fether;

    @TempDir private Path dumpDir;

    @BeforeEach
    void setUp() {
        fether = new MYSQLXMLSchemaFether();
    }

    private MysqlXmlDumpSource dumpFile(String charset) throws Exception {
        Path file = dumpDir.resolve("dump.xml");
        Files.writeString(file, DUMP);
        return new MysqlXmlDumpSource(file.toString(), charset);
    }

    @Test
    @DisplayName("the dump file is parsed into a catalog")
    void dumpFile_isParsedIntoACatalog() throws Exception {
        Catalog catalog = fether.fetchSchema(dumpFile("utf-8"), null);

        assertThat(catalog.getName()).isEqualTo("testdb");
        assertThat(catalog.getSchemas().get(0).getTables())
                .extracting(Table::getName)
                .containsExactly("orders");
    }

    // A dump file carries no encoding of its own that CMT can trust, so the charset the user
    // picked for the file is what the catalog is tagged with.
    @Test
    @DisplayName("the catalog takes the charset the dump source was opened with")
    void catalog_takesTheSourceCharset() throws Exception {
        Catalog catalog = fether.fetchSchema(dumpFile("euc-kr"), null);

        assertThat(catalog.getCharset()).isEqualTo("euc-kr");
    }

    @Test
    @DisplayName("fetchSchemaNames() reduces the parsed catalog to its schema entries")
    void fetchSchemaNames_reducesToSchemaEntries() throws Exception {
        SchemaCatalog schemaCatalog = fether.fetchSchemaNames(dumpFile("utf-8"));

        assertThat(schemaCatalog.getName()).isEqualTo("testdb");
        assertThat(schemaCatalog.getSchemas()).containsExactly(new SchemaEntry("testdb", false));
    }

    // A dump holds one database and nothing to select from, so asking for named schemas gives
    // back the same whole catalog the plain fetch does.
    @Test
    @DisplayName("fetchSchemaObjects() ignores the names asked for and parses the whole dump")
    void fetchSchemaObjects_ignoresTheNamesAskedFor() throws Exception {
        MysqlXmlDumpSource source = dumpFile("utf-8");

        Catalog catalog =
                fether.fetchSchemaObjects(source, null, java.util.List.of("NOSUCHSCHEMA"));

        assertThat(catalog.getName()).isEqualTo("testdb");
    }

    @Test
    @DisplayName("a dump file that is not there -> RuntimeException")
    void missingDumpFile_throwsRuntimeException() {
        MysqlXmlDumpSource missing =
                new MysqlXmlDumpSource(dumpDir.resolve("absent.xml").toString(), "utf-8");

        assertThatThrownBy(() -> fether.fetchSchema(missing, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("cancel() while nothing is running is a no-op")
    void cancelWhileIdle_doesNothing() {
        assertThatCode(fether::cancel).doesNotThrowAnyException();
    }

    // The fetcher refuses a second fetch while one is in flight, and the guard has to be cleared
    // once the first finishes or every later fetch would be rejected.
    @Test
    @DisplayName("a finished fetch leaves the fetcher ready for the next one")
    void finishedFetch_leavesTheFetcherReusable() throws Exception {
        MysqlXmlDumpSource source = dumpFile("utf-8");

        fether.fetchSchema(source, null);

        assertThat(fether.fetchSchema(source, null).getName()).isEqualTo("testdb");
    }
}
