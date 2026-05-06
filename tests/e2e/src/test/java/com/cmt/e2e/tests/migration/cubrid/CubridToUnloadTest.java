package com.cmt.e2e.tests.migration.cubrid;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** CUBRID → CMT {@code unload} (CUBRID LoadDB) dump. */
@DisplayName("CUBRID e2e dataset → CMT unload (LoadDB) dump")
class CubridToUnloadTest {

    @Nested
    @MigrationE2E(
        name = "cubrid_to_unload__split_schema__1t1f",
        options = {
            "file_prefix=demodb",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("split_schema=true, one_table_one_file=true (file_prefix=demodb)")
    class SplitSchema1t1f extends AbstractMigrationE2E {

        @Override protected Source source() { return Sources.cubridE2eSeed(); }
        @Override protected Target target() { return Targets.unload("demodb", true); }

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
