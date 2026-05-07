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

package com.cmt.e2e.framework.command;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/** Runs a CMT Console command as a child process and captures stdout, stderr, and the exit code. */
public class CommandRunner {
    private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);

    private static final int DEFAULT_PROCESS_TIMEOUT_SECONDS = 300;
    private static final int STREAM_READ_TIMEOUT_SECONDS = 5;
    private final File workDir;

    public CommandRunner(File workDir) {
        this.workDir = workDir;
    }

    public CommandResult run(Command command) throws IOException, InterruptedException {
        long timeoutSeconds = DEFAULT_PROCESS_TIMEOUT_SECONDS;
        List<String> commandList = command.build();

        log.debug("Executing command list: {}", commandList);
        log.debug("CWD: {}", workDir.getAbsolutePath());
        log.debug("CMD: {}", String.join(" ", commandList));

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        processBuilder.directory(workDir);
        Process process = processBuilder.start();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        SharedOutputBuffer buffer = new SharedOutputBuffer();

        try {
            Future<?> stdoutTask =
                    executor.submit(
                            () -> readStream(process.getInputStream(), buffer::appendStdoutLine));
            Future<?> stderrTask =
                    executor.submit(
                            () -> readStream(process.getErrorStream(), buffer::appendStderrLine));

            boolean finishedInTime = process.waitFor(timeoutSeconds, SECONDS);
            if (!finishedInTime) {
                process.destroyForcibly();
                process.waitFor();
            }

            waitForStreamReadersOrThrow(stdoutTask, stderrTask);

            CommandResult result =
                    new CommandResult(
                            buffer.stdout(),
                            buffer.stderr(),
                            buffer.combined(),
                            finishedInTime ? process.exitValue() : -1,
                            !finishedInTime);
            log.debug(
                    "Command finished with exitCode: {}, timeOut: {}",
                    result.exitCode(),
                    result.timedOut());
            // Persist full child-process output to the per-test log (hidden
            // from console by the threshold filter on the CONSOLE appender).
            if (!result.stdout().isEmpty()) {
                log.info(
                        "----- CMT stdout (exit={}) -----\n{}-----",
                        result.exitCode(),
                        result.stdout());
            }
            if (!result.stderr().isEmpty()) {
                log.info(
                        "----- CMT stderr (exit={}) -----\n{}-----",
                        result.exitCode(),
                        result.stderr());
            }
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private void readStream(InputStream stream, LineAppender appender) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appender.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read process stream", e);
        }
    }

    private void waitForStreamReadersOrThrow(Future<?> stdoutTask, Future<?> stderrTask)
            throws IOException, InterruptedException {
        try {
            stdoutTask.get(STREAM_READ_TIMEOUT_SECONDS, SECONDS);
            stderrTask.get(STREAM_READ_TIMEOUT_SECONDS, SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to capture process output", e);
        }
    }

    @FunctionalInterface
    private interface LineAppender {
        void append(String line);
    }

    private static final class SharedOutputBuffer {
        private final StringBuilder stdout = new StringBuilder();
        private final StringBuilder stderr = new StringBuilder();
        private final StringBuilder combined = new StringBuilder();

        synchronized void appendStdoutLine(String line) {
            stdout.append(line).append("\n");
            combined.append(line).append("\n");
        }

        synchronized void appendStderrLine(String line) {
            stderr.append(line).append("\n");
            combined.append(line).append("\n");
        }

        synchronized String stdout() {
            return stdout.toString();
        }

        synchronized String stderr() {
            return stderr.toString();
        }

        synchronized String combined() {
            return combined.toString();
        }
    }
}
