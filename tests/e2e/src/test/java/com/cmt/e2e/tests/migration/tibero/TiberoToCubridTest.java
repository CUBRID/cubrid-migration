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
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
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

package com.cmt.e2e.tests.migration.tibero;

import com.cmt.e2e.framework.junit.AbstractMigrationE2E;
import com.cmt.e2e.framework.junit.MigrationE2E;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.source.Sources;
import com.cmt.e2e.framework.target.Target;
import com.cmt.e2e.framework.target.Targets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;

/** Tibero 7 → CUBRID online migration. Snapshots: {@code snapshots/tibero_to_cubrid/}. */
@MigrationE2E(name = "tibero_to_cubrid")
@DisplayName("Tibero e2e dataset → CUBRID online migration")
@EnabledIf("com.cmt.e2e.framework.db.containers.TiberoEnvironment#isAvailable")
class TiberoToCubridTest extends AbstractMigrationE2E {

    @Override
    protected Source source() {
        return Sources.tiberoE2eSeed();
    }

    @Override
    protected Target target() {
        return Targets.cubridOnline().addSchema(true).build();
    }

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
    @DisplayName("Sequence current_val preserved through Tibero → CUBRID translation")
    void sequences_match_snapshot() {
        run().catalog().matchesSnapshot("sequences");
    }

    @Test
    @DisplayName("All synonyms preserved (cross-schema reference)")
    void synonyms_match_snapshot() {
        run().catalog().matchesSnapshot("synonyms");
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
    @DisplayName("Cross-schema GRANTs preserved")
    void grants_match_snapshot() {
        run().catalog().matchesSnapshot("grants");
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
