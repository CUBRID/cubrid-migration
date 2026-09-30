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
package com.cubrid.cubridmigration.core.dbmetadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.connection.ConnParameters;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.mysql.MysqlXmlDumpSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DBSchemaInfoFetcherFactory")
class DBSchemaInfoFetcherFactoryTest {

    private static ConnParameters liveConnection() {
        return ConnParameters.getConParam(
                "name",
                "host",
                33000,
                "demodb",
                DatabaseType.CUBRID,
                "utf-8",
                "u",
                "p",
                null,
                null);
    }

    @Test
    @DisplayName("connection parameters -> the JDBC facade")
    void connectionParameters_giveTheJdbcFacade() {
        assertThat(DBSchemaInfoFetcherFactory.createFetcher(liveConnection()))
                .isInstanceOf(JDBCDBSchemaFetcherFacade.class);
    }

    @Test
    @DisplayName("a mysqldump XML file -> the XML fetcher")
    void mysqlXmlDump_givesTheXmlFetcher() {
        assertThat(
                        DBSchemaInfoFetcherFactory.createFetcher(
                                new MysqlXmlDumpSource("/tmp/dump.xml", "utf-8")))
                .isInstanceOf(com.cubrid.cubridmigration.mysql.meta.MYSQLXMLSchemaFether.class);
    }

    // Neither branch matches, so the caller gets a fetcher that answers null to everything rather
    // than a null reference or an exception.
    @Test
    @DisplayName("an unknown source -> a fetcher that answers null to every call")
    void unknownSource_givesAFetcherThatAnswersNull() {
        IDBSource source = new IDBSource() {};

        IDBSchemaInfoFetcher fetcher = DBSchemaInfoFetcherFactory.createFetcher(source);

        assertThat(fetcher.fetchSchema(source, null)).isNull();
        assertThat(fetcher.fetchSchemaNames(source)).isNull();
        assertThat(fetcher.fetchSchemaObjects(source, null, null)).isNull();
    }

    @Test
    @DisplayName("null -> the same do-nothing fetcher, not a null reference")
    void nullSource_givesTheSameDoNothingFetcher() {
        assertThat(DBSchemaInfoFetcherFactory.createFetcher(null))
                .isSameAs(DBSchemaInfoFetcherFactory.createFetcher(new IDBSource() {}));
    }
}
