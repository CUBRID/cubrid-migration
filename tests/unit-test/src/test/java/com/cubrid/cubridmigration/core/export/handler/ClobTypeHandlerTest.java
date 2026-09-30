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
package com.cubrid.cubridmigration.core.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("ClobTypeHandler")
class ClobTypeHandlerTest {

    private static final ClobTypeHandler HANDLER = new ClobTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    @Nested
    @DisplayName("getJdbcObject()")
    class GetJdbcObject {

        @Test
        @DisplayName("character stream -> its whole text")
        void characterStream_returnsWholeText() throws SQLException {
            when(rs.getCharacterStream("C1")).thenReturn(new StringReader("hello"));

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("hello");
        }

        @Test
        @DisplayName("SQL NULL -> null")
        void sqlNull_returnsNull() throws SQLException {
            when(rs.getCharacterStream("C1")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
        }
    }

    @Nested
    @DisplayName("getCharObject()")
    class GetCharObject {

        @Test
        @DisplayName("text longer than the 1024-char buffer is read in full")
        void textLongerThanBuffer_isReadInFull() throws SQLException {
            String text = "x".repeat(3000);

            assertThat(HANDLER.getCharObject(new StringReader(text))).isEqualTo(text);
        }

        @Test
        @DisplayName("null reader -> null")
        void nullReader_returnsNull() throws SQLException {
            assertThat(HANDLER.getCharObject(null)).isNull();
        }

        @Test
        @DisplayName("a reader that fails -> SQLException wrapping the IOException")
        void failingReader_isWrappedInSqlException() {
            Reader failing =
                    new Reader() {
                        public int read(char[] buf, int off, int len) throws IOException {
                            throw new IOException("stream broke");
                        }

                        public void close() {}
                    };

            assertThatThrownBy(() -> HANDLER.getCharObject(failing))
                    .isInstanceOf(SQLException.class)
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("the reader is closed once the text has been taken")
        void reader_isClosedAfterReading() throws SQLException {
            boolean[] closed = {false};
            Reader reader =
                    new StringReader("abc") {
                        public void close() {
                            closed[0] = true;
                            super.close();
                        }
                    };

            HANDLER.getCharObject(reader);

            assertThat(closed[0]).isTrue();
        }
    }
}
