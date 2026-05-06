package com.cmt.e2e.tests.migration.tibero;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** Tibero 7 → CMT {@code unload} (CUBRID LoadDB) dump. */
@DisplayName("Tibero e2e dataset → CMT unload (LoadDB) dump")
@EnabledIf("com.cmt.e2e.framework.db.containers.TiberoEnvironment#isAvailable")
class TiberoToUnloadTest {

    @Nested
    @MigrationE2E(
        name = "tibero_to_unload__split_per_table",
        options = {
            "file_prefix=tibero",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("split_schema=true, one_table_one_file=true (file_prefix=tibero)")
    class SplitPerTable extends AbstractMigrationE2E {

        @Override protected Source source() { return Sources.tiberoE2eSeed(); }
        @Override protected Target target() { return Targets.unload("tibero", true); }

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
        @DisplayName("Dump file tree matches snapshot")
        void dump_tree_matches_snapshot() {
            run().dumpfile().matchesSnapshot();
        }
    }
}
