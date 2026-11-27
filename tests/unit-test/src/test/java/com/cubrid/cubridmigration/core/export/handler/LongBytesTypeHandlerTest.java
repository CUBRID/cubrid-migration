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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("LongBytesTypeHandler")
class LongBytesTypeHandlerTest {

    private static final LongBytesTypeHandler HANDLER = new LongBytesTypeHandler();

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
        @DisplayName("binary stream -> its bytes")
        void binaryStream_returnsBytes() throws SQLException {
            when(rs.getBinaryStream("C1")).thenReturn(new ByteArrayInputStream(new byte[] {4, 5}));

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo(new byte[] {4, 5});
        }

        @Test
        @DisplayName("SQL NULL -> null")
        void sqlNull_returnsNull() throws SQLException {
            when(rs.getBinaryStream("C1")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
        }
    }

    @Nested
    @DisplayName("getBinaryObject()")
    class GetBinaryObject {

        @Test
        @DisplayName("content longer than the 1024-byte buffer is read in full")
        void contentLongerThanBuffer_isReadInFull() throws SQLException {
            byte[] content = new byte[3000];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 127);
            }

            assertThat(HANDLER.getBinaryObject(new ByteArrayInputStream(content)))
                    .isEqualTo(content);
        }

        @Test
        @DisplayName("null stream -> null")
        void nullStream_returnsNull() throws SQLException {
            assertThat(HANDLER.getBinaryObject(null)).isNull();
        }

        @Test
        @DisplayName("a stream that fails -> SQLException wrapping the IOException")
        void failingStream_isWrappedInSqlException() {
            InputStream failing =
                    new InputStream() {
                        public int read() throws IOException {
                            throw new IOException("stream broke");
                        }
                    };

            assertThatThrownBy(() -> HANDLER.getBinaryObject(failing))
                    .isInstanceOf(SQLException.class)
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("getBytesFromByteArray()")
    class GetBytesFromByteArray {

        @Test
        @DisplayName("boxed bytes -> the primitive array")
        void boxedBytes_returnPrimitiveArray() {
            assertThat(HANDLER.getBytesFromByteArray(new Byte[] {1, 2}))
                    .isEqualTo(new byte[] {1, 2});
        }

        @Test
        @DisplayName("null -> null")
        void nullArray_returnsNull() {
            assertThat(HANDLER.getBytesFromByteArray(null)).isNull();
        }
    }
}
