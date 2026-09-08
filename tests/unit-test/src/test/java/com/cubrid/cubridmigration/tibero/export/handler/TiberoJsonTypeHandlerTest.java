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
package com.cubrid.cubridmigration.tibero.export.handler;

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
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("TiberoJsonTypeHandler")
class TiberoJsonTypeHandlerTest {

    private static final TiberoJsonTypeHandler HANDLER = new TiberoJsonTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("JSON_COL");
    }

    @Test
    @DisplayName("getTypeNameForError() -> \"JSON\"")
    void getTypeNameForError_returnsJson() {
        assertThat(HANDLER.getTypeNameForError()).isEqualTo("JSON");
    }

    @Test
    @DisplayName("null value -> null")
    void nullValue_returnsNull() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    @Test
    @DisplayName("String object -> return as is via readTextValue")
    void stringValue_returnedAsIs() throws SQLException {
        when(rs.getObject("JSON_COL")).thenReturn("{\"key\":\"value\"}");

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"key\":\"value\"}");
    }

    @Nested
    @DisplayName("byte[] handling")
    class ByteArrayDispatch {

        @Test
        @DisplayName("byte[] with getString -> prefer getString")
        void byteArray_getStringSucceeds_returnsString() throws SQLException {
            when(rs.getObject("JSON_COL")).thenReturn("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
            when(rs.getString("JSON_COL")).thenReturn("{\"a\":1}");

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"a\":1}");
        }

        @Test
        @DisplayName("byte[] with null getString -> decodeBinary as UTF-8")
        void byteArray_getStringNull_decodesBytes() throws SQLException {
            byte[] bytes = "{\"b\":2}".getBytes(StandardCharsets.UTF_8);
            when(rs.getObject("JSON_COL")).thenReturn(bytes);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"b\":2}");
        }
    }

    @Nested
    @DisplayName("InputStream handling")
    class InputStreamDispatch {

        @Test
        @DisplayName("InputStream with getString -> prefer getString")
        void inputStream_getStringSucceeds_returnsString() throws SQLException {
            ByteArrayInputStream stream =
                    new ByteArrayInputStream("{\"c\":3}".getBytes(StandardCharsets.UTF_8));
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn("{\"c\":3}");

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"c\":3}");
        }

        @Test
        @DisplayName("InputStream with null getString -> read from stream")
        void inputStream_getStringNull_readsStream() throws SQLException {
            byte[] data = "{\"d\":4}".getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            when(rs.getObject("JSON_COL")).thenReturn(stream);
            when(rs.getString("JSON_COL")).thenReturn(null);

            assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"d\":4}");
        }
    }

    @Test
    @DisplayName("Blob with null getString -> read from binaryStream")
    void blobValue_readFromBlobStream() throws Exception {
        Blob blob = mock(Blob.class);
        byte[] data = "{\"e\":5}".getBytes(StandardCharsets.UTF_8);
        when(rs.getObject("JSON_COL")).thenReturn(blob);
        when(rs.getString("JSON_COL")).thenReturn(null);
        when(blob.getBinaryStream()).thenReturn(new ByteArrayInputStream(data));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"e\":5}");
    }

    @Test
    @DisplayName("SQLException -> rethrow")
    void sqlException_rethrown() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new SQLException("json error"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessage("json error");
    }

    @Test
    @DisplayName("non-SQL exception -> wrap to SQLException with column name")
    void runtimeException_wrappedWithColName() throws SQLException {
        when(rs.getObject("JSON_COL")).thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> HANDLER.getJdbcObject(rs, column))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("JSON_COL");
    }
}
