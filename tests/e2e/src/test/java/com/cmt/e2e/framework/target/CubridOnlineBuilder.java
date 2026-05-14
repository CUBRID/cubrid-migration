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
 * Fluent builder for the CUBRID online target. Connection-derived properties (host, port, user,
 * ...) are emitted by the runner from {@link Target#connection()}; this builder only holds
 * functional CMT options (e.g. {@code add_schema}).
 *
 * <p>Nothing is pre-set — tests opt in explicitly so unspecified keys fall back to CMT's own
 * defaults.
 */
public final class CubridOnlineBuilder {

    private final Map<String, String> options = new LinkedHashMap<>();

    CubridOnlineBuilder() {}

    public CubridOnlineBuilder addSchema(boolean value) {
        return option("add_schema", yn(value));
    }

    /** Escape hatch for any CMT option not (yet) promoted to a typed method. */
    public CubridOnlineBuilder option(String key, String value) {
        options.put(key, value);
        return this;
    }

    public Target build() {
        return new CubridOnlineTarget(options);
    }

    private static String yn(boolean value) {
        return value ? "yes" : "no";
    }
}
