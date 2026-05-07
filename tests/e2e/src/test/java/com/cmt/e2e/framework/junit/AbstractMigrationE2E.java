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

package com.cmt.e2e.framework.junit;

import com.cmt.e2e.framework.core.WorkspaceCleaner;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.runner.Migration;
import com.cmt.e2e.framework.runner.MigrationOutcome;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for migration E2E tests. Runs source/target/migration once in {@code @BeforeAll} and
 * caches the outcome so each {@code @Test} verifies one fact against the same result.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractMigrationE2E {

    private static final Logger log = LoggerFactory.getLogger(AbstractMigrationE2E.class);

    private static final Path WORK_ROOT = Paths.get("target", "e2e", "runs");

    private Source source;
    private Target target;
    private MigrationOutcome cachedOutcome;

    protected abstract Source source();

    protected abstract Target target();

    @BeforeAll
    final void e2eStartup() throws Exception {
        new WorkspaceCleaner(CmtConsoleEnv.resolve().toFile()).cleanupOutput();

        this.source = source();
        this.target = target();
        log.info("[{}] starting source ({}) and target", scenarioName(), source.type());
        source.start();
        target.start();

        Path workDir = WORK_ROOT.resolve(scenarioName());
        log.info("[{}] running migration; workDir={}", scenarioName(), workDir);
        this.cachedOutcome = new Migration(source, target, scenarioName()).run(workDir);
        log.info("[{}] migration finished", scenarioName());
    }

    @AfterAll
    final void e2eShutdown() {
        if (target != null) {
            try {
                target.close();
            } catch (Exception e) {
                log.warn("target.close failed", e);
            }
        }
        if (source != null) {
            try {
                source.close();
            } catch (Exception e) {
                log.warn("source.close failed", e);
            }
        }
    }

    protected final MigrationOutcome run() {
        if (cachedOutcome == null) {
            throw new IllegalStateException(
                    "run() called before @BeforeAll completed — possible JUnit lifecycle"
                            + " misconfiguration.");
        }
        return cachedOutcome;
    }

    private String scenarioName() {
        MigrationE2E ann = getClass().getAnnotation(MigrationE2E.class);
        if (ann == null) {
            throw new IllegalStateException(
                    "Test class "
                            + getClass().getSimpleName()
                            + " is missing @MigrationE2E. Add @MigrationE2E(name ="
                            + " \"<scenario>\").");
        }
        return ann.name();
    }
}
