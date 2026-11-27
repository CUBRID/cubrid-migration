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
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BuildSchemaFilterFactory")
class BuildSchemaFilterFactoryTest {

    // The schema builders read the answer as "skip this object", so false is what keeps an object
    // in the catalog. The filter therefore answers false exactly for what the configuration
    // already selected, and true for everything else.
    private static MigrationConfiguration configWithTable(String tableName) {
        MigrationConfiguration config = new MigrationConfiguration();
        config.setSourceConParams(
                ConnParameters.getConParam(
                        "name",
                        "host",
                        33000,
                        "demodb",
                        DatabaseType.CUBRID,
                        "utf-8",
                        "u",
                        "p",
                        null,
                        null));
        SourceEntryTableConfig table = new SourceEntryTableConfig();
        table.setName(tableName);
        table.setTarget(tableName.toLowerCase(java.util.Locale.ENGLISH));
        table.setCreateNewTable(true);
        config.addExpEntryTableCfg(table);
        return config;
    }

    @Test
    @DisplayName("a table the configuration already selected -> false, so it is kept")
    void selectedTable_isKept() {
        IBuildSchemaFilter filter = BuildSchemaFilterFactory.from(configWithTable("ORDERS"));

        assertThat(filter.filter(null, "ORDERS")).isFalse();
    }

    @Test
    @DisplayName("a table the configuration does not carry -> true, so it is skipped")
    void unselectedTable_isSkipped() {
        IBuildSchemaFilter filter = BuildSchemaFilterFactory.from(configWithTable("ORDERS"));

        assertThat(filter.filter(null, "CUSTOMERS")).isTrue();
    }

    // The schema argument is only passed on for a source that is both online and multi-schema.
    // CUBRID is neither here, so the lookup runs without a schema and the same answer comes back
    // whatever the caller names.
    @Test
    @DisplayName("on a single-schema source the schema argument makes no difference")
    void singleSchemaSource_ignoresTheSchemaArgument() {
        IBuildSchemaFilter filter = BuildSchemaFilterFactory.from(configWithTable("ORDERS"));

        assertThat(filter.filter("hr", "ORDERS")).isEqualTo(filter.filter(null, "ORDERS"));
    }
}
