package com.cmt.e2e.framework.command;

public record CommandResult(String stdout, String stderr, String combinedOutput, int exitCode, boolean timedOut) {
}
