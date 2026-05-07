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

/** JDBC connection identity for CMT + verify layer. {@code timezone} may be null. */
public record ConnectionConfig(
        DB type,
        String host,
        int port,
        String dbname,
        String user,
        String password,
        String charset,
        String timezone) {
    public ConnectionConfig {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (host == null || host.isBlank())
            throw new IllegalArgumentException("host must not be blank");
        if (port <= 0) throw new IllegalArgumentException("port must be positive");
        if (dbname == null || dbname.isBlank())
            throw new IllegalArgumentException("dbname must not be blank");
        if (user == null) throw new IllegalArgumentException("user must not be null");
        if (password == null)
            throw new IllegalArgumentException("password must not be null (use \"\" for none)");
        if (charset == null || charset.isBlank())
            throw new IllegalArgumentException("charset must not be blank");
        // timezone may be null
    }

    /**
     * CUBRID JDBC URL — verify layer uses this to introspect the target. CUBRID driver rejects
     * empty password slot, so omit it when needed.
     */
    public String cubridJdbcUrl() {
        if (type != DB.CUBRID) {
            throw new IllegalStateException(
                    "cubridJdbcUrl() is CUBRID-specific (got " + type + ")");
        }
        if (password.isEmpty()) {
            return String.format("jdbc:cubrid:%s:%d:%s:%s::", host, port, dbname, user);
        }
        return String.format("jdbc:cubrid:%s:%d:%s:%s:%s::", host, port, dbname, user, password);
    }
}
