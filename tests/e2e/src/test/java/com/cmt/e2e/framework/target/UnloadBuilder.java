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

package com.cmt.e2e.framework.target;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for the CMT {@code unload} (LoadDB dump) target. Each call adds a {@code
 * <target>.<key>=<value>} property that the runner writes into {@code db.conf}.
 *
 * <p>Defaults reflect framework-internal structural settings only ({@code type}, {@code output},
 * {@code charset}, {@code file_prefix}). Functional CMT options ({@code add_schema}, {@code
 * split_schema}, {@code one_table_one_file}, ...) are <strong>not</strong> pre-set — tests opt in
 * explicitly via the typed methods or {@link #option(String, String)}, so unspecified keys fall
 * back to CMT's own defaults.
 *
 * <p>Add a typed shortcut on this class only when an option starts to be reused across scenarios;
 * one-off cases stay on {@link #option(String, String)}.
 */
public final class UnloadBuilder {

    private final Map<String, String> options = new LinkedHashMap<>();

    UnloadBuilder(String filePrefix) {
        // Structural — required for our framework's unload runs.
        options.put("type", "unload");
        options.put("output", "./output");
        options.put("charset", "utf-8");
        options.put("file_prefix", filePrefix);
    }

    public UnloadBuilder addSchema(boolean value) {
        return option("add_schema", yn(value));
    }

    public UnloadBuilder splitSchema(boolean value) {
        return option("split_schema", yn(value));
    }

    public UnloadBuilder oneTableOneFile(boolean value) {
        return option("one_table_one_file", yn(value));
    }

    /** Escape hatch for any CMT option not (yet) promoted to a typed method. */
    public UnloadBuilder option(String key, String value) {
        options.put(key, value);
        return this;
    }

    public Target build() {
        return new DumpFileTarget(options);
    }

    private static String yn(boolean value) {
        return value ? "yes" : "no";
    }
}
