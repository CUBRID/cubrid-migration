package com.cmt.e2e.framework.command;

import java.util.List;

public interface Command {
    /**
     * Returns the command as an executable string list.
     * Example: {@code ["./migration.sh", "script", "-s", "<source>", "-t", "<target>", ...]}
     */
    List<String> build();
}
