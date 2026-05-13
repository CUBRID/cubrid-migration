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

package com.cmt.e2e.framework.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Resolves keys via system property → {@code tests/e2e/e2e-test.properties} → caller-supplied
 * default. Empty / blank values at the first two levels fall through. The properties file is
 * gitignored; the committed {@code e2e-test.properties.example} lists known keys.
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
            log.debug(
                    "[E2eTestProperties] no {} found at {} — defaults will apply",
                    FILE_NAME,
                    file.toAbsolutePath());
            return p;
        }
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
            log.info("[E2eTestProperties] loaded {} keys from {}", p.size(), file.toAbsolutePath());
        } catch (IOException e) {
            // Malformed file shouldn't break tests — warn and fall through to defaults.
            log.warn(
                    "[E2eTestProperties] failed to read {} ({}); using defaults",
                    file.toAbsolutePath(),
                    e.getMessage());
        }
        return p;
    }
}
