package com.cmt.e2e.tests.migration.cubrid;

import java.nio.file.Path;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CUBRID → CUBRID online migration. Snapshots: {@code snapshots/cubrid_to_cubrid/}. */
@MigrationE2E(name = "cubrid_to_cubrid")
@DisplayName("CUB-ON: CUBRID e2e dataset → CUBRID online migration")
class CubridToCubridTest extends AbstractMigrationE2E {

    @Override protected Source source() { return Sources.cubridE2eSeed(); }
    @Override protected Target target() { return Targets.cubridOnline(); }

    @Test
    @DisplayName("CMT exits 0 with MIGRATION RESULT: SUCCESS, no fatal stderr")
    void migration_succeeds() {
        run().expectSuccess().expectNoFatalStderr();
    }

    @Test
    @DisplayName("All target classes (tables/views) match snapshot")
    void classes_match_snapshot() {
        run().catalog().matchesSnapshot("classes");
    }

    @Test
    @DisplayName("All synonyms preserved (cross-schema reference)")
    void synonyms_match_snapshot() {
        run().catalog().matchesSnapshot("synonyms");
    }

    @Test
    @DisplayName("All stored routines (FUNCTION/PROCEDURE) preserved")
    void routines_match_snapshot() {
        run().catalog().matchesSnapshot("routines");
    }

    @Test
    @DisplayName("Table + column COMMENTs preserved (incl. multi-byte unicode)")
    void comments_match_snapshot() {
        run().catalog().matchesSnapshot("comments");
    }

    @Test
    @DisplayName("Cross-schema GRANTs preserved")
    void grants_match_snapshot() {
        run().catalog().matchesSnapshot("grants");
    }

    @Test
    @DisplayName("Column types preserved through CUBRID → CUBRID translation")
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
    @DisplayName("Plain indexes preserved (asc/desc; idxf_* anti-coverage stripped)")
    void indexes_match_snapshot() {
        run().catalog().matchesSnapshot("indexes");
    }

    @Test
    @DisplayName("CUBRID SERIAL current_val preserved")
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
        run().queries(Path.of("src/test/resources/queries/cubrid_to_cubrid.sql"))
             .matchesSnapshot("representative_rows");
    }
}
