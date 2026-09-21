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
package com.cubrid.cubridmigration.cubrid.trans.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.datatype.DataTypeInstance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

@DisplayName("ClobConverter")
class ClobConverterTest {

    private static final ClobConverter CONVERTER = new ClobConverter();

    private static Object convert(Object value) {
        DataTypeInstance dti = new DataTypeInstance();
        dti.setName("clob");
        return CONVERTER.convert(value, dti, null);
    }

    @Test
    @DisplayName("a string value is carried through unchanged")
    void stringValue_isCarriedThrough() {
        assertThat(convert("test")).isEqualTo("test");
    }

    @Test
    @DisplayName("a reader is rendered by its own toString, so its characters are lost")
    void readerValue_losesItsCharacters() {
        // DEFECT: ClobConverter.convert() is `return obj.toString()`, with nothing CLOB-specific in
        // it. A JDBC
        // CLOB arrives as a Reader, so the target receives the object identity instead of the
        // text. Pinned as it behaves today.
        assertThat(convert(new StringReader("test")))
                .asString()
                .startsWith("java.io.StringReader@")
                .doesNotContain("test");
    }

    @Test
    @DisplayName("a null value is not handled, it is thrown on")
    void nullValue_throws() {
        assertThatThrownBy(() -> convert(null)).isInstanceOf(NullPointerException.class);
    }
}
