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

package com.cmt.e2e.tests.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.cmt.e2e.framework.command.CommandResult;
import com.cmt.e2e.framework.command.RawCommand;
import com.cmt.e2e.framework.core.CmtTestContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Functional tests for {@code migration.sh} dispatch branches and first-run filesystem contracts.
 * No DB required — runs in seconds and gates the rest of the E2E suite.
 */
@DisplayName("migration.sh dispatch + first-run filesystem contracts")
public class CliTest {

    @RegisterExtension final CmtTestContext ctx = new CmtTestContext();

    @Test
    @DisplayName("lists all subcommands when called with no args")
    void should_listAllSubcommands_when_calledWithoutArgs() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("start", "script", "log", "report")
                .contains("CUBRID Migration Toolkit");
    }

    // 'start' alone blocks on stdin; pass a nonexistent path to reach the
    // "script not found → printHelp" exit. -sd is unique to start help.
    @Test
    @DisplayName("dispatches to StartCommandHandler when 'start' subcommand")
    void should_dispatchToStartHandler_when_invokedWithStartSubcommand() throws Exception {
        CommandResult result =
                ctx.commandRunner()
                        .run(
                                new RawCommand(
                                        "./migration.sh",
                                        "start",
                                        "/__nonexistent_for_smoke__.xml"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("Usage in Linux: migration.sh start")
                .contains("-sd");
    }

    @Test
    @DisplayName("dispatches to ScriptCommandHandler when 'script' subcommand")
    void should_dispatchToScriptHandler_when_invokedWithScriptSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "script"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("Usage in Linux: migration.sh script")
                .contains("-schema");
    }

    @Test
    @DisplayName("dispatches to LogCommandHandler when 'log' subcommand")
    void should_dispatchToLogHandler_when_invokedWithLogSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "log"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("Usage in Linux: migration.sh log")
                .contains("-ps");
    }

    @Test
    @DisplayName("dispatches to ReportCommandHandler when 'report' subcommand")
    void should_dispatchToReportHandler_when_invokedWithReportSubcommand() throws Exception {
        CommandResult result = ctx.commandRunner().run(new RawCommand("./migration.sh", "report"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("Usage in Linux: migration.sh report")
                .contains("-ao");
    }

    @Test
    @DisplayName("falls back to start help on unknown command")
    void should_fallbackToStartHelp_when_unknownCommand() throws Exception {
        CommandResult result =
                ctx.commandRunner()
                        .run(new RawCommand("./migration.sh", "bogus_command_that_does_not_exist"));

        assertThat(result.exitCode()).isZero();
        assertThat(result.combinedOutput())
                .contains("The migration script isn't exists!")
                .contains("Usage in Linux: migration.sh start");
    }

    @Test
    @DisplayName("creates workspace/cmt/log and workspace/cmt/report on any invocation")
    void should_createWorkspaceDirectories_onAnyInvocation() throws Exception {
        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(ctx.cmtConsoleHome().resolve("workspace/cmt/log")).isDirectory();
        assertThat(ctx.cmtConsoleHome().resolve("workspace/cmt/report")).isDirectory();
    }

    @Test
    @DisplayName("appends to cubrid-migration.log on every invocation")
    void should_appendToLogFile_onAnyInvocation() throws Exception {
        Path logFile = ctx.cmtConsoleHome().resolve("workspace/cmt/log/cubrid-migration.log");
        long sizeBefore = Files.exists(logFile) ? Files.size(logFile) : 0L;

        ctx.commandRunner().run(new RawCommand("./migration.sh"));

        assertThat(logFile).exists();
        assertThat(Files.size(logFile)).isGreaterThan(sizeBefore);
    }
}
