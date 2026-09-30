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
package com.cubrid.cubridmigration.informix.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("InformixBSONTypeHandler")
class InformixBSONTypeHandlerTest {

    private static final InformixBSONTypeHandler HANDLER = new InformixBSONTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    private static byte[] bsonOf(BsonDocument document) {
        return new RawBsonDocument(document, new BsonDocumentCodec()).getByteBuffer().array();
    }

    @Test
    @DisplayName("BSON bytes -> relaxed-mode JSON")
    void bsonBytes_returnRelaxedJson() throws SQLException {
        BsonDocument document =
                new BsonDocument("k", new BsonInt32(1)).append("s", new BsonString("v"));
        when(rs.getBytes("C1")).thenReturn(bsonOf(document));

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("{\"k\": 1, \"s\": \"v\"}");
    }

    @Test
    @DisplayName("SQL NULL -> null")
    void sqlNull_returnsNull() throws SQLException {
        when(rs.getBytes("C1")).thenReturn(null);

        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }

    // DEFECT: bytes that are not valid BSON become an empty string rather than NULL or an error,
    // so a corrupt document is indistinguishable in the target from one that held empty text
    // - see InformixBSONTypeHandler.getJdbcObject()
    @Test
    @DisplayName("bytes that are not BSON -> the empty string")
    void invalidBson_returnsEmptyString() throws SQLException {
        when(rs.getBytes("C1")).thenReturn(new byte[] {1, 2, 3});

        assertThat(HANDLER.getJdbcObject(rs, column)).isEqualTo("");
    }
}
