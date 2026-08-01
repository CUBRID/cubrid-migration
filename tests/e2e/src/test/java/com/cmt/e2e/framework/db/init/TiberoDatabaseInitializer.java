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

package com.cmt.e2e.framework.db.init;

import com.cmt.e2e.framework.db.containers.TiberoContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raw-JDBC seed runner for Tibero (Flyway has no Tibero plugin). Two phases mirror {@code
 * OracleSource}: {@link #initAsSys} runs DBA-only DDL; {@link #migrateAs} applies schema/data files
 * in lexical order.
 */
public final class TiberoDatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(TiberoDatabaseInitializer.class);

    private final TiberoContainer container;

    private TiberoDatabaseInitializer(TiberoContainer container) {
        this.container = container;
    }

    public static TiberoDatabaseInitializer of(TiberoContainer container) {
        if (container == null) throw new IllegalArgumentException("container must not be null");
        return new TiberoDatabaseInitializer(container);
    }

    /** Run init scripts as {@code SYS} (e.g. {@code CREATE USER}). */
    public TiberoDatabaseInitializer initAsSys(String classpathDir) {
        log.info("[TiberoDatabaseInitializer] init as SYS — classpathDir='{}'", classpathDir);
        ClasspathSqlRunner.runDirectory(
                container.getJdbcUrl(null, null),
                container.getDbaUser(),
                container.getDbaPassword(),
                classpathDir);
        return this;
    }

    /** Apply schema/data scripts as a non-DBA user. */
    public TiberoDatabaseInitializer migrateAs(String user, String password, String classpathDir) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user must not be blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        log.info(
                "[TiberoDatabaseInitializer] migrate as user='{}' — classpathDir='{}'",
                user,
                classpathDir);
        ClasspathSqlRunner.runDirectory(
                container.getJdbcUrl(null, null), user, password, classpathDir);
        return this;
    }
}
