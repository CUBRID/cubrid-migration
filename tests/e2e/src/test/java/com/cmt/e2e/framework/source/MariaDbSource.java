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
import com.cmt.e2e.framework.db.containers.MariaDbContainer;
import com.cmt.e2e.framework.db.init.MariaDbDatabaseInitializer;

/** MariaDB 11 source with single-schema ({@code main_schema}) e2e seed. */
final class MariaDbSource implements Source {

    private final MariaDbContainer container;
    private boolean started;

    MariaDbSource() {
        this.container = MariaDbContainer.withMainSchema();
    }

    @Override
    public void start() {
        if (started) return;
        container.start();
        MariaDbDatabaseInitializer.of(container).migrate("mariadb/main_schema");
        started = true;
    }

    @Override
    public ConnectionConfig connection() {
        return new ConnectionConfig(
                DB.MARIADB,
                container.getHost(),
                container.getDatabasePort(),
                container.getDatabaseName(),
                container.getUser(),
                container.getPassword(),
                "utf-8",
                null);
    }

    @Override
    public DB type() {
        return DB.MARIADB;
    }

    @Override
    public void close() {
        if (started) {
            container.close();
        }
    }
}
