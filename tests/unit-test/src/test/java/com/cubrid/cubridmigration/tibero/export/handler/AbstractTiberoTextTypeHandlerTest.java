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
package com.cubrid.cubridmigration.tibero.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Tests internal logic of AbstractTiberoTextTypeHandler via TestTextTypeHandler. */
@DisplayName("AbstractTiberoTextTypeHandler")
class AbstractTiberoTextTypeHandlerTest {

    static class TestTextTypeHandler extends AbstractTiberoTextTypeHandler {
        @Override
        protected String getTypeNameForError() {
            return "TEST";
        }
    }

    private static final TestTextTypeHandler HANDLER = new TestTextTypeHandler();

    @Nested
    @DisplayName("buildReadErrorMessage()")
    class BuildReadErrorMessage {

        @Test
        @DisplayName("returns \"Failed to read Tibero TEST value: COL\"")
        void returnsFormattedMessage() {
            assertThat(HANDLER.buildReadErrorMessage("MY_COL"))
                    .isEqualTo("Failed to read Tibero TEST value: MY_COL");
        }

        @Test
        @DisplayName("message contains column name")
        void colNameIncludedMessage() {
            assertThat(HANDLER.buildReadErrorMessage("JSON_DATA")).contains("JSON_DATA");
        }
    }

    @Nested
    @DisplayName("resolveCharsetByBom")
    class ResolveCharsetByBom {

        @Test
        @DisplayName("UTF-8 BOM (EF BB BF) -> UTF-8, offset=3")
        void utf8Bom_returnsUtf8Offset3() throws SQLException {
            byte[] bytes = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'i'};

            String result = HANDLER.decodeBinary(bytes);

            assertThat(result).isEqualTo("Hi");
        }

        @Test
        @DisplayName("UTF-16BE BOM (FE FF) -> UTF-16BE, offset=2")
        void utf16BeBom_returnsUtf16Be() throws SQLException {
            // "A" in UTF-16BE is 0x00 0x41
            byte[] bytes = new byte[] {(byte) 0xFE, (byte) 0xFF, 0x00, 0x41};

            String result = HANDLER.decodeBinary(bytes);

            assertThat(result).isEqualTo("A");
        }

        @Test
        @DisplayName("UTF-16LE BOM (FF FE) -> UTF-16LE, offset=2")
        void utf16LeBom_returnsUtf16Le() throws SQLException {
            // "A" in UTF-16LE is 0x41 0x00
            byte[] bytes = new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41, 0x00};

            String result = HANDLER.decodeBinary(bytes);

            assertThat(result).isEqualTo("A");
        }

        @Test
        @DisplayName("no BOM -> UTF-8 default, offset=0")
        void noBom_defaultsToUtf8() throws SQLException {
            byte[] bytes = "Hello".getBytes(StandardCharsets.UTF_8);

            String result = HANDLER.decodeBinary(bytes);

            assertThat(result).isEqualTo("Hello");
        }
    }

    @Nested
    @DisplayName("decodeBinary()")
    class DecodeBinary {

        @Test
        @DisplayName("null -> null")
        void nullBytes_returnsNull() throws SQLException {
            assertThat(HANDLER.decodeBinary(null)).isNull();
        }

        @Test
        @DisplayName("empty array -> empty string")
        void emptyBytes_returnsEmptyString() throws SQLException {
            assertThat(HANDLER.decodeBinary(new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("decodes UTF-8 Korean text")
        void koreanUtf8_decodedCorrectly() throws SQLException {
            byte[] bytes = "안녕".getBytes(StandardCharsets.UTF_8);

            assertThat(HANDLER.decodeBinary(bytes)).isEqualTo("안녕");
        }
    }

    @Nested
    @DisplayName("readFromInputStream")
    class ReadFromInputStream {

        @Test
        @DisplayName("null InputStream -> null")
        void nullInputStream_returnsNull() throws SQLException {
            assertThat(HANDLER.readFromInputStream(null)).isNull();
        }

        @Test
        @DisplayName("reads text from InputStream")
        void inputStream_readsText() throws SQLException {
            byte[] data = "test data".getBytes(StandardCharsets.UTF_8);

            String result = HANDLER.readFromInputStream(new ByteArrayInputStream(data));

            assertThat(result).isEqualTo("test data");
        }

        @Test
        @DisplayName("reads all data over 2048 bytes")
        void largeInputStream_readsAll() throws SQLException {
            String longStr = "X".repeat(5000);
            byte[] data = longStr.getBytes(StandardCharsets.UTF_8);

            String result = HANDLER.readFromInputStream(new ByteArrayInputStream(data));

            assertThat(result).hasSize(5000);
        }
    }

    @Nested
    @DisplayName("readTextValue(Object, ResultSet, String)")
    class ReadTextValueObjectDispatch {

        @Test
        @DisplayName("null -> null")
        void nullValue_returnsNull() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            assertThat(HANDLER.readTextValue(null, rs, "COL")).isNull();
        }

        @Test
        @DisplayName("String -> return as is")
        void stringValue_returnedAsIs() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            assertThat(HANDLER.readTextValue("hello", rs, "COL")).isEqualTo("hello");
        }

        @Test
        @DisplayName("other object -> delegate to rs.getString()")
        void otherObject_delegatesToGetString() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("COL")).thenReturn("from_rs");

            assertThat(HANDLER.readTextValue(new Object(), rs, "COL")).isEqualTo("from_rs");
        }
    }

    @Nested
    @DisplayName("readTextValue(ResultSet, Column) exception handling")
    class ReadTextValueExceptionHandling {

        @Test
        @DisplayName("SQLException -> rethrow")
        void sqlException_rethrown() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            Column col = new Column();
            col.setName("COL");
            when(rs.getObject("COL")).thenThrow(new SQLException("DB error"));

            assertThatThrownBy(() -> HANDLER.readTextValue(rs, col))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("DB error");
        }

        @Test
        @DisplayName("non-SQL exception -> wrap to SQLException with column name")
        void nonSqlException_wrappedWithColName() throws SQLException {
            ResultSet rs = mock(ResultSet.class);
            Column col = new Column();
            col.setName("MY_COL");
            when(rs.getObject("MY_COL")).thenThrow(new RuntimeException("unexpected"));

            assertThatThrownBy(() -> HANDLER.readTextValue(rs, col))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("MY_COL");
        }
    }
}
