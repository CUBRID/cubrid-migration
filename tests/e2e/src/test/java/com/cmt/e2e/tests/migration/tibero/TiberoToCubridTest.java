package com.cmt.e2e.tests.migration.tibero;

import java.nio.file.Path;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Tibero 7 → CUBRID online migration. Snapshots:
 *  {@code snapshots/tibero_to_cubrid/}. */
@MigrationE2E(name = "tibero_to_cubrid")
@DisplayName("Tibero e2e dataset → CUBRID online migration")
@EnabledIf("com.cmt.e2e.framework.db.containers.TiberoEnvironment#isAvailable")
class TiberoToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.tiberoE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    @Test
    @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
    void migration_succeeds() {
        run().expectSuccess().expectNoFatalStderr();
    }

    @Test
    @DisplayName("Migration report — Exported counts equal Imported counts")
    void migration_report_no_loss() {
        run().expectImportMatchesExport();
    }

    @Test
    @DisplayName("All target tables match snapshot")
    void tables_match_snapshot() {
        run().catalog().matchesSnapshot("tables");
    }

    @Test
    @DisplayName("All target views match snapshot")
    void views_match_snapshot() {
        run().catalog().matchesSnapshot("views");
    }

    @Test
    @DisplayName("All synonyms preserved (cross-schema reference)")
    void synonyms_match_snapshot() {
        run().catalog().matchesSnapshot("synonyms");
    }

    @Test
    @DisplayName("Cross-schema GRANTs preserved")
    void grants_match_snapshot() {
        run().catalog().matchesSnapshot("grants");
    }

    @Test
    @DisplayName("All stored functions preserved")
    void functions_match_snapshot() {
        run().catalog().matchesSnapshot("functions");
    }

    @Test
    @DisplayName("All stored procedures preserved")
    void procedures_match_snapshot() {
        run().catalog().matchesSnapshot("procedures");
    }

    @Test
    @DisplayName("Column types preserved through Tibero → CUBRID translation")
    void columns_match_snapshot() {
        run().catalog().matchesSnapshot("columns");
    }

    @Test
    @DisplayName("Primary keys preserved (with key columns and order)")
    void pk_match_snapshot() {
        run().catalog().matchesSnapshot("pk");
    }

    @Test
    @DisplayName("Foreign-key indexes preserved (with referencing columns)")
    void fk_match_snapshot() {
        run().catalog().matchesSnapshot("fk");
    }

    @Test
    @DisplayName("Unique non-PK indexes preserved (with key columns)")
    void unique_match_snapshot() {
        run().catalog().matchesSnapshot("unique");
    }

    @Test
    @DisplayName("Plain indexes preserved (asc/desc, function expressions)")
    void indexes_match_snapshot() {
        run().catalog().matchesSnapshot("indexes");
    }

    @Test
    @DisplayName("Tibero SEQUENCE current_val preserved as CUBRID SERIAL current_val")
    void serials_match_snapshot() {
        run().catalog().matchesSnapshot("serials");
    }

    @Test
    @DisplayName("Row counts per migrated table match")
    void row_counts_match_snapshot() {
        run().rowCounts().matchesSnapshot("row_counts");
    }

    @Test
    @DisplayName("Representative business rows preserved (8 spot-checks)")
    void representative_rows_match_snapshot() {
        run().queries(Path.of("src/test/resources/queries/tibero_to_cubrid.sql"))
             .matchesSnapshot("representative_rows");
    }
}
