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

package com.cmt.e2e.framework.source;

import com.cmt.e2e.framework.db.JdbcDriverJars.DB;
import com.cmt.e2e.framework.db.containers.TiberoContainer;
import com.cmt.e2e.framework.db.init.TiberoDatabaseInitializer;

/**
 * Tibero 6 source with two-user seed (REF + MAIN). Init and seed run over raw JDBC after boot
 * ({@link TiberoDatabaseInitializer}, Flyway has no Tibero plugin). Order matters: SYS init → REF →
 * MAIN, since MAIN's V5 synonym points at REF's V1 object.
 */
final class TiberoSource implements Source {

    private final TiberoContainer container;
    private boolean started;

    TiberoSource() {
        this.container = TiberoContainer.create();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        TiberoDatabaseInitializer.of(container)
                .initAsSys("db/tibero/init")
                .migrateAs(
                        container.getRefUser(), container.getRefPassword(), "db/tibero/ref_schema")
                .migrateAs(
                        container.getMainUser(),
                        container.getMainPassword(),
                        "db/tibero/main_schema");
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
                DB.TIBERO,
                container.getHost(),
                container.getDatabasePort(),
                container.getSid(),
                container.getMainUser(),
                container.getMainPassword(),
                "utf-8",
                "GMT+00:00");
    }

    @Override
    public DB type() {
        return DB.TIBERO;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
