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

@DisplayName("BitConverter")
class BitConverterTest {

    private static final BitConverter CONVERTER = new BitConverter();

    private static DataTypeInstance bitColumn() {
        DataTypeInstance dti = new DataTypeInstance();
        dti.setName("bit");
        dti.setPrecision(10);
        return dti;
    }

    private static Object convert(Object value) {
        return CONVERTER.convert(value, bitColumn(), null);
    }

    @Test
    @DisplayName("bytes are handed back as they arrived, not copied through a string")
    void bytes_arePassedThrough() {
        byte[] source = {'a', 'b'};

        assertThat(convert(source)).isSameAs(source);
    }

    @Test
    @DisplayName("an empty byte array stays empty")
    void emptyBytes_stayEmpty() {
        assertThat((byte[]) convert(new byte[] {})).isEmpty();
    }

    @Test
    @DisplayName("chars become the bytes of the string they spell")
    void chars_becomeTheirStringBytes() {
        // The legacy test only asserted the result was a byte[]; these pin the bytes.
        assertThat((byte[]) convert(new char[] {'a', 'b'})).containsExactly((byte) 97, (byte) 98);
    }

    @Test
    @DisplayName("anything else is converted through its own toString")
    void otherValue_goesThroughToString() {
        assertThat((byte[]) convert("test"))
                .containsExactly((byte) 116, (byte) 101, (byte) 115, (byte) 116);
        assertThat((byte[]) convert(5)).containsExactly((byte) 53);
    }

    @Test
    @DisplayName("a null value is not handled, it is thrown on")
    void nullValue_throws() {
        assertThatThrownBy(() -> convert(null)).isInstanceOf(NullPointerException.class);
    }
}
