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

package com.cmt.e2e.framework.db.containers;

import com.cmt.e2e.framework.core.E2eTestProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single source of truth for Tibero scenario configuration.
 *
 * <p>Four required keys (image / hostname / license / faketime) come from {@link E2eTestProperties}
 * (system property → {@code e2e-test.properties} → no default). When any is missing or invalid
 * {@link #isAvailable()} returns false and the JUnit 5 {@code @EnabledIf} hook on Tibero TCs skips
 * the scenario; the rest of the suite is unaffected.
 *
 * <p>Centralising the lookups here ensures the {@code @EnabledIf} guard and the actual {@link
 * TiberoContainer} construction can never disagree about which config the test is using.
 */
public final class TiberoEnvironment {

    private static final Logger log = LoggerFactory.getLogger(TiberoEnvironment.class);

    public static final String IMAGE_KEY = "e2e.tibero.image";
    public static final String HOSTNAME_KEY = "e2e.tibero.hostname";
    public static final String LICENSE_KEY = "e2e.tibero.license";
    public static final String FAKETIME_KEY = "e2e.tibero.faketime";

    private TiberoEnvironment() {}

    /**
     * True when Tibero is fully provisioned. Logs an INFO line on skip so the surefire output
     * explains why (driver / config key / license file / faketime value).
     */
    public static boolean isAvailable() {
        if (!driverOnClasspath()) {
            log.info(
                    "[Tibero] skipping — JDBC driver com.tmax.tibero.jdbc.TbDriver "
                            + "not on classpath. Place tibero7-jdbc-*.jar at tests/e2e/lib/.");
            return false;
        }
        String missing = firstMissingRequiredKey();
        if (missing != null) {
            log.info(
                    "[Tibero] skipping — required config '{}' not set. "
                            + "Set it in tests/e2e/e2e-test.properties or pass -D{}=value.",
                    missing,
                    missing);
            return false;
        }
        Path lic = licensePath();
        if (!lic.isAbsolute()) {
            log.info(
                    "[Tibero] skipping — license path '{}' (from key '{}') "
                            + "must be absolute. Relative paths are no longer accepted; "
                            + "use a full path like /Users/you/.../license.xml.",
                    lic,
                    LICENSE_KEY);
            return false;
        }
        if (!Files.exists(lic)) {
            log.info(
                    "[Tibero] skipping — license file not found at {} " + "(from key '{}').",
                    lic,
                    LICENSE_KEY);
            return false;
        }
        Integer days = parseFaketimeDaysBack(rawValue(FAKETIME_KEY));
        if (days == null) {
            log.info(
                    "[Tibero] skipping — config '{}' must be a positive "
                            + "integer (= days back). Got: '{}'. Example: '100' is "
                            + "translated to FAKETIME=-100d.",
                    FAKETIME_KEY,
                    rawValue(FAKETIME_KEY));
            return false;
        }
        return true;
    }

    public static String image() {
        return required(IMAGE_KEY);
    }

    public static String hostname() {
        return required(HOSTNAME_KEY);
    }

    /**
     * Host-side absolute path to {@code license.xml}. Returned as-is — no resolution against cwd
     * ({@link #isAvailable()} rejects relative).
     */
    public static Path licensePath() {
        return Paths.get(required(LICENSE_KEY));
    }

    /** Days back for libfaketime; container env becomes {@code FAKETIME=-Nd}. */
    public static int faketimeDaysBack() {
        Integer days = parseFaketimeDaysBack(rawValue(FAKETIME_KEY));
        if (days == null) {
            throw new IllegalStateException(
                    "Tibero config key '"
                            + FAKETIME_KEY
                            + "' is not a positive "
                            + "integer. Got: '"
                            + rawValue(FAKETIME_KEY)
                            + "'.");
        }
        return days;
    }

    private static boolean driverOnClasspath() {
        try {
            Class.forName("com.tmax.tibero.jdbc.TbDriver");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String firstMissingRequiredKey() {
        for (String key : new String[] {IMAGE_KEY, HOSTNAME_KEY, LICENSE_KEY, FAKETIME_KEY}) {
            if (rawValue(key) == null) return key;
        }
        return null;
    }

    private static Integer parseFaketimeDaysBack(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int n = Integer.parseInt(raw.trim());
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String rawValue(String key) {
        String v = E2eTestProperties.get(key);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String required(String key) {
        String v = rawValue(key);
        if (v == null) {
            throw new IllegalStateException(
                    "Tibero config key '"
                            + key
                            + "' is not set. "
                            + "Set it in tests/e2e/e2e-test.properties or pass -D"
                            + key
                            + "=value.");
        }
        return v;
    }
}
