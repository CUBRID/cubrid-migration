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

package com.cmt.e2e.framework.runner;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.StartCommand;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Single-shot CMT runner: build db.conf → generate sanitized script.xml → run {@code migration.sh
 * start}. Source / Target must already be started; lifecycle belongs to {@link
 * com.cmt.e2e.framework.junit.AbstractMigrationE2E}.
 */
public final class Migration {

    private static final Logger log = LoggerFactory.getLogger(Migration.class);

    private final Source source;
    private final Target target;
    private final String scenarioName;

    public Migration(Source source, Target target, String scenarioName) {
        if (source == null) throw new IllegalArgumentException("source");
        if (target == null) throw new IllegalArgumentException("target");
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }
        this.source = source;
        this.target = target;
        this.scenarioName = scenarioName;
    }

    /** {@code workDir} is the scratch dir for this run (script.xml + raw CMT output). */
    public MigrationOutcome run(Path workDir) throws Exception {
        Path consoleHome = CmtConsoleEnv.resolve();

        String dbConf = DbConfBuilder.build(source, target);
        log.debug("[Migration] db.conf built ({} chars)", dbConf.length());

        ScriptXmlBuilder.Result generated = ScriptXmlBuilder.generate(consoleHome, dbConf, workDir);
        log.info(
                "[Migration] script.xml generated: {} (migration name: {})",
                generated.scriptXml(),
                generated.migrationName());

        StartCommand cmd = StartCommand.builder().script(generated.scriptXml()).build();
        CommandRunner runner = new CommandRunner(consoleHome.toFile());
        CommandResult result = runner.run(cmd);
        log.info("[Migration] start exited with {}", result.exitCode());

        return new MigrationOutcome(result, target, generated.migrationName(), scenarioName);
    }
}
