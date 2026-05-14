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

package com.cmt.e2e.framework.runner;

import com.cmt.e2e.framework.db.JdbcDriverJars;
import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.source.ConnectionConfig;
import com.cmt.e2e.framework.source.Source;
import com.cmt.e2e.framework.target.Target;

import java.util.Map;

/**
 * Builds the {@code db.conf} text for {@code migration.sh script -s ... -t ...}. The {@code .type}
 * value comes from {@link DB#name()} lowercased.
 */
public final class DbConfBuilder {

    public static final String SOURCE_NAME = "regen_source";
    public static final String TARGET_NAME = "regen_target";

    private DbConfBuilder() {}

    public static String build(Source source, Target target) {
        StringBuilder sb = new StringBuilder();
        appendSource(sb, source.connection());
        appendTarget(sb, target);
        return sb.toString();
    }

    private static void appendSource(StringBuilder sb, ConnectionConfig c) {
        prop(sb, SOURCE_NAME + ".type", dbConfType(c.type()));
        prop(sb, SOURCE_NAME + ".driver", driverPath(c.type()));
        prop(sb, SOURCE_NAME + ".host", c.host());
        prop(sb, SOURCE_NAME + ".port", Integer.toString(c.port()));
        prop(sb, SOURCE_NAME + ".dbname", c.dbname());
        prop(sb, SOURCE_NAME + ".user", c.user());
        prop(sb, SOURCE_NAME + ".password", c.password());
        prop(sb, SOURCE_NAME + ".charset", c.charset());
        if (c.timezone() != null) {
            prop(sb, SOURCE_NAME + ".timezone", c.timezone());
        }
    }

    private static void appendTarget(StringBuilder sb, Target target) {
        if (!target.isDumpfile()) {
            appendOnlineConnection(sb, target.connection());
        }
        appendOptions(sb, target.options());
    }

    private static void appendOnlineConnection(StringBuilder sb, ConnectionConfig c) {
        prop(sb, TARGET_NAME + ".type", dbConfType(c.type()));
        prop(sb, TARGET_NAME + ".driver", driverPath(c.type()));
        prop(sb, TARGET_NAME + ".host", c.host());
        prop(sb, TARGET_NAME + ".port", Integer.toString(c.port()));
        prop(sb, TARGET_NAME + ".dbname", c.dbname());
        prop(sb, TARGET_NAME + ".user", c.user());
        prop(sb, TARGET_NAME + ".password", c.password());
        prop(sb, TARGET_NAME + ".charset", c.charset());
    }

    private static void appendOptions(StringBuilder sb, Map<String, String> options) {
        for (Map.Entry<String, String> e : options.entrySet()) {
            prop(sb, TARGET_NAME + "." + e.getKey(), e.getValue());
        }
    }

    private static String dbConfType(DB db) {
        return db.name().toLowerCase();
    }

    private static String driverPath(DB db) {
        return JdbcDriverJars.latest(db).toAbsolutePath().toString();
    }

    private static void prop(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value == null ? "" : value).append('\n');
    }
}
