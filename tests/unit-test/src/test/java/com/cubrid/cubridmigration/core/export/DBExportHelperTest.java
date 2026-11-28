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
package com.cubrid.cubridmigration.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbtype.DatabaseType;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@DisplayName("DBExportHelper")
class DBExportHelperTest {

    private static final String SQL = "SELECT id FROM owner.orders";

    /** Every dialect with the paging declaration it is expected to make today. */
    static Stream<Arguments> declaredPaging() {
        return Stream.of(
                Arguments.of("CUBRID", DatabaseType.CUBRID, true),
                Arguments.of("Tibero", DatabaseType.TIBERO, true),
                Arguments.of("Oracle", DatabaseType.ORACLE, false),
                Arguments.of("MySQL", DatabaseType.MYSQL, false),
                Arguments.of("MariaDB", DatabaseType.MARIADB, false),
                Arguments.of("MSSQL", DatabaseType.MSSQL, false),
                Arguments.of("Informix", DatabaseType.INFORMIX, false));
    }

    static Stream<Arguments> allDialects() {
        return Stream.of(DatabaseType.getAllTypes())
                .map(type -> Arguments.of(type.getName(), type));
    }

    @Nested
    @DisplayName("isPagingSupported()")
    class IsPagingSupported {

        @ParameterizedTest(name = "[{index}] {0} -> {2}")
        @DisplayName("only CUBRID and Tibero page; every other dialect reads a query in one go")
        @MethodSource("com.cubrid.cubridmigration.core.export.DBExportHelperTest#declaredPaging")
        void eachDialect_declaresExpectedPagingSupport(
                String label, DatabaseType type, boolean expected) {
            assertThat(type.getExportHelper().isPagingSupported()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName(
                "the declaration agrees with whether getPagedSelectSQL really changes the SQL"
                        + " from page to page")
        @MethodSource("com.cubrid.cubridmigration.core.export.DBExportHelperTest#allDialects")
        void declaration_matchesGetPagedSelectSQL(String label, DatabaseType type) {
            DBExportHelper helper = type.getExportHelper();

            String firstPage = helper.getPagedSelectSQL(SQL, 1000, 0, null);
            String secondPage = helper.getPagedSelectSQL(SQL, 1000, 1000, null);

            if (helper.isPagingSupported()) {
                // A paging dialect must hand back a different SQL for every page; otherwise the
                // export loop would re-read the same rows.
                assertThat(firstPage).isNotEqualTo(SQL);
                assertThat(secondPage).isNotEqualTo(firstPage);
            } else {
                // A dialect declared unpaged must not silently implement paging: the export loop
                // reads only the first page of it.
                assertThat(firstPage).isEqualTo(SQL);
                assertThat(secondPage).isEqualTo(SQL);
            }
        }
    }

    @Nested
    @DisplayName("getQuotedColumnName()")
    class GetQuotedColumnName {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a column is quoted exactly as getSelectSQL() quotes it")
        @MethodSource("com.cubrid.cubridmigration.core.export.DBExportHelperTest#allDialects")
        void anyDialect_matchesTheColumnInGetSelectSQL(String label, DatabaseType type) {
            DBExportHelper helper = type.getExportHelper();

            assertThat(helper.getSelectSQL(entryTable("Sales Dept", "Order Items", "order")))
                    .startsWith("SELECT " + helper.getQuotedColumnName("order") + " FROM ");
        }
    }

    @Nested
    @DisplayName("getQualifiedTableName()")
    class GetQualifiedTableName {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("owner and table are quoted exactly as getSelectSQL() quotes them")
        @MethodSource("com.cubrid.cubridmigration.core.export.DBExportHelperTest#allDialects")
        void ownerAndTable_matchesTheTableInGetSelectSQL(String label, DatabaseType type) {
            DBExportHelper helper = type.getExportHelper();

            assertThat(helper.getSelectSQL(entryTable("Sales Dept", "Order Items", "id")))
                    .endsWith(" FROM " + helper.getQualifiedTableName("Sales Dept", "Order Items"));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a null or blank owner adds no qualifier, as in getSelectSQL()")
        @MethodSource("com.cubrid.cubridmigration.core.export.DBExportHelperTest#allDialects")
        void blankOwner_matchesTheTableInGetSelectSQLWithoutOwner(String label, DatabaseType type) {
            DBExportHelper helper = type.getExportHelper();

            assertThat(helper.getSelectSQL(entryTable(null, "orders", "id")))
                    .endsWith(" FROM " + helper.getQualifiedTableName(null, "orders"));
            assertThat(helper.getSelectSQL(entryTable("  ", "orders", "id")))
                    .endsWith(" FROM " + helper.getQualifiedTableName("  ", "orders"));
        }
    }

    private static SourceEntryTableConfig entryTable(String owner, String name, String column) {
        SourceEntryTableConfig table = new SourceEntryTableConfig();
        table.setOwner(owner);
        table.setName(name);
        table.addColumnConfig(column, column, true);
        return table;
    }
}
