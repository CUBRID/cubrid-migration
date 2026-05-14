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

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.CubridContainer;
import com.cmt.e2e.framework.source.ConnectionConfig;

import java.util.Map;

/**
 * CUBRID online target — empty CUBRID container that CMT migrates into. Connects as {@code dba} so
 * CMT can {@code CREATE USER} and {@code GRANT} during {@code add_schema}.
 */
final class CubridOnlineTarget implements Target {

    private final CubridContainer container;
    private final Map<String, String> options;
    private boolean started;

    CubridOnlineTarget(Map<String, String> options) {
        this.container = CubridContainer.withEmptyDb();
        this.options = Map.copyOf(options);
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
                DB.CUBRID,
                container.getHost(),
                container.getDatabasePort(),
                container.getDatabaseName(),
                "dba",
                "",
                "utf-8",
                null);
    }

    @Override
    public boolean isDumpfile() {
        return false;
    }

    @Override
    public Map<String, String> options() {
        return options;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
