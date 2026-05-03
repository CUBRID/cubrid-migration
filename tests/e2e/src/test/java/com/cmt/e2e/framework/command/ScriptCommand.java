package com.cmt.e2e.framework.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScriptCommand extends AbstractCmtCommand {

    private ScriptCommand(List<String> options) {
        super("script", options);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<String> options = new ArrayList<>();

        public Builder sourceConfig(String sourceConfigName) {
            options.addAll(Arrays.asList("-s", sourceConfigName));
            return this;
        }

        public Builder targetConfig(String targetConfigName) {
            options.addAll(Arrays.asList("-t", targetConfigName));
            return this;
        }

        public Builder outputDir(String outputPath) {
            options.addAll(Arrays.asList("-o", outputPath));
            return this;
        }

        public ScriptCommand build() {
            return new ScriptCommand(options);
        }
    }
}
