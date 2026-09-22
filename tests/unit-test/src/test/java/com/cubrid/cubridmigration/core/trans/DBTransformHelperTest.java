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
package com.cubrid.cubridmigration.core.trans;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.Catalog;
import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.PK;
import com.cubrid.cubridmigration.core.dbobject.Table;
import com.cubrid.cubridmigration.core.dbobject.View;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.config.SourceEntryTableConfig;
import com.cubrid.cubridmigration.core.engine.config.SourceTableConfig;
import com.cubrid.cubridmigration.core.mapping.model.MapObject;
import com.cubrid.cubridmigration.core.mapping.model.VerifyInfo;
import com.cubrid.cubridmigration.cubrid.trans.ToCUBRIDDataConverterFacade;
import com.cubrid.cubridmigration.mysql.trans.MySQLDataTypeMappingHelper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@DisplayName("DBTransformHelper")
class DBTransformHelperTest {

    /**
     * The base leaves adjustPrecision to the dialects, so an instance has to supply one. A no-op
     * keeps these tests on the base's own code instead of a dialect's overrides; the precision the
     * dialects compute belongs to their own tests.
     */
    private static final DBTransformHelper HELPER =
            new DBTransformHelper(
                    new MySQLDataTypeMappingHelper(), ToCUBRIDDataConverterFacade.getIntance()) {
                @Override
                protected void adjustPrecision(
                        Column srcColumn, Column cubridColumn, MigrationConfiguration config) {}
            };

    private static MapObject mapping(String precision, String scale) {
        MapObject item = new MapObject();
        item.setPrecision(precision);
        item.setScale(scale);
        return item;
    }

    private static Column column(int precision, int scale) {
        Column column = new Column();
        column.setPrecision(precision);
        column.setScale(scale);
        return column;
    }

    @Nested
    @DisplayName("getCloneView()")
    class GetCloneView {

        @Test
        @DisplayName("the view keeps its name and its query, which needs no translating by default")
        void clonedView_keepsItsNameAndQuery() {
            View source = new View();
            source.setName("sourceView");
            source.setQuerySpec("Select * from \"game\";");
            source.setDDL("CREATE VIEW sourceView AS Select * from \"game\";");
            source.setColumns(new ArrayList<Column>());

            View target = HELPER.getCloneView(source, migrationConfig());

            assertThat(target).isNotSameAs(source);
            assertThat(target.getName()).isEqualTo("sourceView");
            assertThat(target.getQuerySpec()).isEqualTo("Select * from \"game\";");
            assertThat(target.getDDL()).isEqualTo(source.getDDL());
        }

        @Test
        @DisplayName("its columns are rebuilt as CUBRID columns belonging to the new view")
        void clonedView_rebuildsItsColumns() {
            View source = new View();
            source.setName("sourceView");
            source.setQuerySpec("Select * from \"game\";");
            List<Column> columns = new ArrayList<Column>();
            columns.add(column(new Table(), "F_NAME", "varchar", 20));
            source.setColumns(columns);

            View target = HELPER.getCloneView(source, migrationConfig());

            assertThat(target.getColumns()).extracting(Column::getName).containsExactly("f_name");
            assertThat(target.getColumns().get(0).getTableOrView()).isSameAs(target);
        }
    }

    @Nested
    @DisplayName("createCUBRIDTable()")
    class CreateCUBRIDTable {

        @Test
        @DisplayName("names come from the config, OID reuse from the source table")
        void targetTable_takesItsIdentityFromTheConfig() {
            Table source = sourceTable();
            source.setReuseOID(true);
            SourceEntryTableConfig config = tableConfig();
            config.setOwner("SRC_OWNER");
            config.setTargetOwner("TGT_OWNER");

            Table target = HELPER.createCUBRIDTable(config, source, migrationConfig());

            assertThat(target.getName()).isEqualTo("tgt_table");
            assertThat(target.getOwner()).isEqualTo("TGT_OWNER");
            assertThat(target.getSourceOwner()).isEqualTo("SRC_OWNER");
            assertThat(target.isReuseOID()).isTrue();
        }

        @Test
        @DisplayName("column with no config -> lowercased name on the new table")
        void columnWithoutConfig_isLowercasedAndReparented() {
            Table target =
                    HELPER.createCUBRIDTable(tableConfig(), sourceTable(), migrationConfig());

            assertThat(target.getColumns())
                    .extracting(Column::getName)
                    .containsExactly("f_name", "f_age");
            assertThat(target.getColumns()).allMatch(column -> column.getTableOrView() == target);
        }

        @Test
        @DisplayName("column config -> its target name verbatim, case and all")
        void columnConfig_namesTheTargetColumn() {
            SourceEntryTableConfig config = tableConfig();
            config.addColumnConfig("F_NAME", "Renamed_Col", true);

            Table target = HELPER.createCUBRIDTable(config, sourceTable(), migrationConfig());

            assertThat(target.getColumns())
                    .extracting(Column::getName)
                    .containsExactly("Renamed_Col", "f_age");
        }

        @Test
        @DisplayName(
                "column config matching is case-sensitive, so one differing only in case is"
                        + " ignored")
        void columnConfig_isMatchedCaseSensitively() {
            SourceEntryTableConfig config = tableConfig();
            config.addColumnConfig("f_name", "Renamed_Col", true);

            Table target = HELPER.createCUBRIDTable(config, sourceTable(), migrationConfig());

            assertThat(target.getColumns())
                    .extracting(Column::getName)
                    .containsExactly("f_name", "f_age");
        }

        @Test
        @DisplayName("primary key name and columns -> lowercased")
        void primaryKey_isLowercased() {
            Table target =
                    HELPER.createCUBRIDTable(tableConfig(), sourceTable(), migrationConfig());

            assertThat(target.getPk().getName()).isEqualTo("pk_src");
            assertThat(target.getPk().getPkColumns()).containsExactly("f_name");
        }

        @Test
        @DisplayName("primary key column -> the rename its column config asks for")
        void primaryKeyColumn_followsTheColumnConfig() {
            SourceEntryTableConfig config = tableConfig();
            config.addColumnConfig("F_NAME", "Renamed_Col", true);

            Table target = HELPER.createCUBRIDTable(config, sourceTable(), migrationConfig());

            assertThat(target.getPk().getPkColumns()).containsExactly("Renamed_Col");
        }

        @Test
        @DisplayName("a source without a primary key leaves the target without one")
        void sourceWithoutPrimaryKey_leavesTheTargetWithoutOne() {
            Table source = sourceTable();
            source.setPk(null);

            Table target = HELPER.createCUBRIDTable(tableConfig(), source, migrationConfig());

            assertThat(target.getPk()).isNull();
        }

        @Test
        @DisplayName("a config that is not an entry table still gets its columns and primary key")
        void nonEntryConfig_stillGetsColumnsAndPrimaryKey() {
            // Only the foreign keys, indexes and partitions sit behind the entry table check.
            SourceTableConfig config = new SourceTableConfig();
            config.setName("SRC_TABLE");
            config.setTarget("tgt_table");

            Table target = HELPER.createCUBRIDTable(config, sourceTable(), migrationConfig());

            assertThat(target.getColumns())
                    .extracting(Column::getName)
                    .containsExactly("f_name", "f_age");
            assertThat(target.getPk().getPkColumns()).containsExactly("f_name");
        }
    }

    @Nested
    @DisplayName("newTargetColumn()")
    class NewTargetColumn {

        @Test
        @DisplayName("SCHEMA.F_NAME -> f_name, cut at the first dot")
        void qualifiedName_losesEverythingUpToTheFirstDot() {
            Table target = new Table();
            target.setName("tgt_table");

            assertThat(newTargetColumnNamed("SCHEMA.F_NAME", target).getName()).isEqualTo("f_name");
            assertThat(newTargetColumnNamed("A.B.C", target).getName()).isEqualTo("b.c");
        }

        @Test
        @DisplayName("PLAIN -> plain, on the target table")
        void plainName_isLowercasedAndReparented() {
            Table target = new Table();
            target.setName("tgt_table");

            Column column = newTargetColumnNamed("PLAIN", target);

            assertThat(column.getName()).isEqualTo("plain");
            assertThat(column.getTableOrView()).isSameAs(target);
        }

        private Column newTargetColumnNamed(String sourceName, Table target) {
            Table source = new Table();
            source.setName("SRC_TABLE");
            return HELPER.newTargetColumn(
                    column(source, sourceName, "varchar", 20), target, migrationConfig());
        }
    }

    @Nested
    @DisplayName("checkPrecision()")
    class CheckPrecision {

        @ParameterizedTest(name = "[{index}] mapping={0}, source={1} target={2} -> fits")
        @DisplayName("a target wide enough for the source passes, and passing returns no finding")
        @CsvSource({
            // "n" and "p" both mean the precision travels from the source column.
            "n,   10,  10",
            "n,   10,  38",
            "p,   10,  10",
            "p,   10,  38",
            // A fixed number is the floor the target must reach.
            "20,  10,  20",
            "20,  10,  38",
        })
        void wideEnoughTarget_returnsNull(String mappingPrecision, int source, int target) {
            assertThat(
                            HELPER.checkPrecision(
                                    mapping(mappingPrecision, null),
                                    column(source, 0),
                                    column(target, 0)))
                    .isNull();
        }

        @ParameterizedTest(name = "[{index}] mapping={0}, source={1} target={2} -> too narrow")
        @DisplayName(
                "a target narrower than the source is refused as too short, not as a type clash")
        @CsvSource({
            "n,   38,  37",
            "p,   38,  37",
            "20,  10,  19",
        })
        void narrowerTarget_returnsNotEnoughLength(
                String mappingPrecision, int source, int target) {
            VerifyInfo info =
                    HELPER.checkPrecision(
                            mapping(mappingPrecision, null), column(source, 0), column(target, 0));

            assertThat(info.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("a mapping with no precision of its own imposes no floor")
        void blankMappingPrecision_returnsNull() {
            // The unparsed value falls back to -1, which every target precision clears.
            assertThat(HELPER.checkPrecision(mapping(null, null), column(38, 0), column(1, 0)))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("checkScale()")
    class CheckScale {

        @ParameterizedTest(
                name = "[{index}] mapping={0}, source=({1},{2}) target=({3},{4}) -> fits")
        @DisplayName("a target that keeps every digit passes")
        @CsvSource({
            // "n": only the scale has to be kept.
            "n,  38, 9,   38, 10",
            "n,  38, 9,   38, 9",
            // "s": the scale and the integer digits both have to be kept.
            "s,  37, 9,   38, 10",
            // A fixed number is the floor the target scale must reach.
            "11, 37, 9,   38, 12",
        })
        void enoughRoom_returnsNull(
                String mappingScale,
                int sourcePrecision,
                int sourceScale,
                int targetPrecision,
                int targetScale) {
            assertThat(
                            HELPER.checkScale(
                                    mapping(null, mappingScale),
                                    column(sourcePrecision, sourceScale),
                                    column(targetPrecision, targetScale)))
                    .isNull();
        }

        @ParameterizedTest(
                name = "[{index}] mapping={0}, source=({1},{2}) target=({3},{4}) -> too narrow")
        @DisplayName("a target that would drop digits is refused as too short")
        @CsvSource({
            // The scale itself shrinks.
            "n,  38, 10,  38, 9",
            "s,  38, 10,  38, 9",
            // The scale grows but the integer digits shrink: 38-10 < 38-9.
            "s,  38, 9,   38, 10",
            // Below the mapping's own floor.
            "11, 37, 9,   38, 10",
        })
        void notEnoughRoom_returnsNotEnoughLength(
                String mappingScale,
                int sourcePrecision,
                int sourceScale,
                int targetPrecision,
                int targetScale) {
            VerifyInfo info =
                    HELPER.checkScale(
                            mapping(null, mappingScale),
                            column(sourcePrecision, sourceScale),
                            column(targetPrecision, targetScale));

            assertThat(info.getResult()).isEqualTo(VerifyInfo.TYPE_NOENOUGH_LENGTH);
        }

        @Test
        @DisplayName("\"s\" guards the integer digits, which \"n\" does not look at")
        void scaleModeS_alsoGuardsTheIntegerDigits() {
            // Same columns, same widening scale: "n" accepts it, "s" refuses it.
            MapObject scaleOnly = mapping(null, "n");
            MapObject scaleAndIntegerDigits = mapping(null, "s");

            assertThat(HELPER.checkScale(scaleOnly, column(38, 9), column(38, 10))).isNull();
            assertThat(HELPER.checkScale(scaleAndIntegerDigits, column(38, 9), column(38, 10)))
                    .isNotNull();
        }

        @Test
        @DisplayName("a mapping with no scale of its own imposes no floor")
        void blankMappingScale_returnsNull() {
            assertThat(HELPER.checkScale(mapping(null, null), column(37, 9), column(38, 12)))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("getFitTargetFormatSQL()")
    class GetFitTargetFormatSQL {

        @Test
        @DisplayName("CUBRID's own SQL needs no translating, so it is handed back unchanged")
        void sql_isHandedBackUnchanged() {
            assertThat(HELPER.getFitTargetFormatSQL("Select * from \"game\";"))
                    .isEqualTo("Select * from \"game\";");
        }
    }

    /** getCUBRIDColumn reads the source catalog, so the configuration has to carry one. */
    private static MigrationConfiguration migrationConfig() {
        MigrationConfiguration config = new MigrationConfiguration();
        Catalog catalog = new Catalog();
        catalog.setSupportedDataType(new HashMap<>());
        config.setSrcCatalog(catalog, false);
        return config;
    }

    private static SourceEntryTableConfig tableConfig() {
        SourceEntryTableConfig config = new SourceEntryTableConfig();
        config.setName("SRC_TABLE");
        config.setTarget("tgt_table");
        return config;
    }

    /** An uppercase table with an uppercase primary key, so the lowercasing rules are visible. */
    private static Table sourceTable() {
        Table table = new Table();
        table.setName("SRC_TABLE");
        column(table, "F_NAME", "varchar", 20);
        column(table, "F_Age", "int", 11);

        PK pk = new PK(table);
        pk.setName("PK_SRC");
        pk.addColumn("F_NAME");
        table.setPk(pk);
        return table;
    }

    private static Column column(Table table, String name, String dataType, int precision) {
        Column column = new Column(table);
        column.setName(name);
        column.setDataType(dataType);
        column.setPrecision(precision);
        table.addColumn(column);
        return column;
    }
}
