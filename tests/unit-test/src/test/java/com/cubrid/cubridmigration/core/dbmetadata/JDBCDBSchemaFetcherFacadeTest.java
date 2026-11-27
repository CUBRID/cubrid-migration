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
 * - Neither the name of the copyright holder nor the names of its contributors
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
package com.cubrid.cubridmigration.core.dbmetadata;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JDBCDBSchemaFetcherFacade")
class JDBCDBSchemaFetcherFacadeTest {

    // The facade only holds a cancel hook while a fetch is in flight, and the UI can press cancel
    // at any time, so cancelling an idle facade has to be harmless rather than a null dereference.
    @Test
    @DisplayName("cancel() while nothing is running is a no-op")
    void cancelWhileIdle_doesNothing() {
        JDBCDBSchemaFetcherFacade facade = new JDBCDBSchemaFetcherFacade();

        assertThatCode(facade::cancel).doesNotThrowAnyException();
    }

    // The facade takes the general IDBSource but casts straight to ConnParameters, so a source of
    // any other kind fails on the cast rather than being rejected with a message of its own.
    @Test
    @DisplayName("a source that is not connection parameters -> ClassCastException")
    void nonJdbcSource_throwsClassCastException() {
        JDBCDBSchemaFetcherFacade facade = new JDBCDBSchemaFetcherFacade();
        IDBSource source = new IDBSource() {};

        assertThatThrownBy(() -> facade.fetchSchema(source, null))
                .isInstanceOf(ClassCastException.class);
    }
}
