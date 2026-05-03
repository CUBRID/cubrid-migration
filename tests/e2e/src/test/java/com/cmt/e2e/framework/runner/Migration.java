package com.cmt.e2e.framework.runner;

import java.nio.file.Path;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.CommandRunner;
import com.cmt.e2e.framework.command.StartCommand;
import com.cmt.e2e.framework.env.CmtConsoleEnv;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-shot CMT runner: build db.conf → generate sanitized
 * script.xml → run {@code migration.sh start}. Source / Target must
 * already be started; lifecycle belongs to
 * {@link com.cmt.e2e.framework.junit.AbstractMigrationE2E}.
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
        log.info("[Migration] script.xml generated: {} (migration name: {})",
            generated.scriptXml(), generated.migrationName());

        StartCommand cmd = StartCommand.builder().script(generated.scriptXml()).build();
        CommandRunner runner = new CommandRunner(consoleHome.toFile());
        CommandResult result = runner.run(cmd);
        log.info("[Migration] start exited with {}", result.exitCode());

        return new MigrationOutcome(result, target, generated.migrationName(), scenarioName);
    }
}
