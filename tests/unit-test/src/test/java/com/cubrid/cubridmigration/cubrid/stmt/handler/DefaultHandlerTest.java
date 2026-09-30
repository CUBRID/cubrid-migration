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
package com.cubrid.cubridmigration.cubrid.stmt.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

@DisplayName("DefaultHandler")
class DefaultHandlerTest {

    private static final DefaultHandler HANDLER = new DefaultHandler();

    private PreparedStatement stmt;
    private Column column;

    @BeforeEach
    void setUp() {
        stmt = mock(PreparedStatement.class);
        column = new Column();
        column.setName("C1");
    }

    private ColumnValue valueOf(Object value) {
        return new ColumnValue(column, value);
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @Test
        @DisplayName("text -> setString at the next parameter index")
        void text_setsStringAtNextIndex() throws SQLException {
            HANDLER.handle(stmt, 0, valueOf("abc"));

            verify(stmt).setString(1, "abc");
        }

        @Test
        @DisplayName("non-string value -> rendered with String.valueOf()")
        void nonStringValue_isRenderedByStringValueOf() throws SQLException {
            HANDLER.handle(stmt, 0, valueOf(7));

            verify(stmt).setString(1, "7");
        }

        // String.valueOf() turns a null reference into the four-character text "null", so a null
        // driven straight through handle() is stored as that text rather than as SQL NULL.
        // CUBRIDParameterSetter routes null to setNull() first, which is what keeps this off the
        // normal migration path.
        @Test
        @DisplayName("null -> the text \"null\", not SQL NULL")
        void nullValue_isStoredAsTheTextNull() throws SQLException {
            HANDLER.handle(stmt, 0, valueOf(null));

            verify(stmt).setString(1, "null");
        }
    }

    @Nested
    @DisplayName("setNull()")
    class SetNull {

        @Test
        @DisplayName("writes Types.NULL at the next parameter index")
        void writesTypesNullAtNextIndex() throws SQLException {
            HANDLER.setNull(stmt, 2);

            verify(stmt).setNull(3, Types.NULL);
        }
    }

    @Nested
    @DisplayName("isNullOrEmpty()")
    class IsNullOrEmpty {

        @Test
        @DisplayName("null and the empty string are the only two that count as empty")
        void nullAndEmptyString_areEmpty() {
            assertThat(HANDLER.isNullOrEmpty(null)).isTrue();
            assertThat(HANDLER.isNullOrEmpty("")).isTrue();
        }

        @Test
        @DisplayName("a blank string is not empty")
        void blankString_isNotEmpty() {
            assertThat(HANDLER.isNullOrEmpty(" ")).isFalse();
        }

        @Test
        @DisplayName("zero is not empty")
        void zero_isNotEmpty() {
            assertThat(HANDLER.isNullOrEmpty(0)).isFalse();
        }
    }
}
