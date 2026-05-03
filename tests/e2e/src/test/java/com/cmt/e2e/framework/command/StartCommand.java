package com.cmt.e2e.framework.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StartCommand extends AbstractCmtCommand {

    private StartCommand(List<String> options) {
        super("start", options);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> options = new ArrayList<>();

        public Builder script(Path scriptPath) {
            options.add(scriptPath.toAbsolutePath().toString());
            return this;
        }

        public StartCommand build() {
            return new StartCommand(options);
        }
    }
}
