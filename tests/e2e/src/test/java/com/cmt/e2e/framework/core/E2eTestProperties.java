package com.cmt.e2e.framework.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves keys via system property → {@code tests/e2e/e2e-test.properties}
 * → caller-supplied default. Empty / blank values at the first two
 * levels fall through. The properties file is gitignored; the
 * committed {@code e2e-test.properties.example} lists known keys.
 */
public final class E2eTestProperties {

    private static final Logger log = LoggerFactory.getLogger(E2eTestProperties.class);
    private static final String FILE_NAME = "e2e-test.properties";
    private static final Properties PROPS = loadProps();

    private E2eTestProperties() {}

    public static String get(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys.trim();
        String file = PROPS.getProperty(key);
        if (file != null && !file.isBlank()) return file.trim();
        return defaultValue;
    }

    public static String get(String key) {
        return get(key, null);
    }

    private static Properties loadProps() {
        Properties p = new Properties();
        Path file = Paths.get(FILE_NAME);
        if (!Files.exists(file)) {
            log.debug("[E2eTestProperties] no {} found at {} — defaults will apply",
                FILE_NAME, file.toAbsolutePath());
            return p;
        }
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            log.info("[E2eTestProperties] loaded {} keys from {}",
                p.size(), file.toAbsolutePath());
        } catch (IOException e) {
            // Malformed file shouldn't break tests — warn and fall through to defaults.
            log.warn("[E2eTestProperties] failed to read {} ({}); using defaults",
                file.toAbsolutePath(), e.getMessage());
        }
        return p;
    }
}
