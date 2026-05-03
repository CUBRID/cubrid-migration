package com.cmt.e2e.framework.env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Single source for {@code CMT_CONSOLE_HOME} resolution. Fails fast on missing or unusable install. */
public final class CmtConsoleEnv {

    public static final String ENV_VAR = "CMT_CONSOLE_HOME";

    private CmtConsoleEnv() {}

    /** @throws IllegalStateException if the env var is unset/blank or migration.sh is missing/non-executable */
    public static Path resolve() {
        String home = System.getenv(ENV_VAR);
        if (home == null || home.isBlank()) {
            throw new IllegalStateException(
                ENV_VAR + " is not set. "
                    + "Point it at the extracted CMT Console directory.");
        }
        Path path = Paths.get(home).toAbsolutePath().normalize();
        Path script = path.resolve("migration.sh");
        if (!Files.isExecutable(script)) {
            throw new IllegalStateException(
                ENV_VAR + " is invalid (migration.sh missing or not executable): "
                    + path);
        }
        return path;
    }
}
