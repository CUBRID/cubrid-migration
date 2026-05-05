package com.cmt.e2e.tests.migration.oracle;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Oracle 11g → CMT {@code unload} (CUBRID LoadDB) dump. Outer class is
 * the namespace for this source/target shape; each option combination
 * is a {@code @Nested} class with its own {@code @MigrationE2E}.
 */
@DisplayName("Oracle e2e dataset → CMT unload (LoadDB) dump")
class OracleToUnloadTest {

    @Nested
    @MigrationE2E(
        name = "oracle_to_unload__split_per_table",
        options = {
            "file_prefix=XE",
            "split_schema=true",
            "one_table_one_file=true",
        })
    @DisplayName("split_schema=true, one_table_one_file=true (file_prefix=XE)")
    class SplitPerTable extends AbstractMigrationE2E {

        @Override protected Source source() { return Sources.oracleE2eSeed(); }
        @Override protected Target target() { return Targets.unload("XE", true); }

        @Test
        @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
        void migration_succeeds() {
            run().expectSuccess().expectNoFatalStderr();
        }

        @Test
        @DisplayName("Dump file tree matches snapshot")
        void dump_tree_matches_snapshot() {
            run().dumpfile().matchesSnapshot();
        }
    }

}
