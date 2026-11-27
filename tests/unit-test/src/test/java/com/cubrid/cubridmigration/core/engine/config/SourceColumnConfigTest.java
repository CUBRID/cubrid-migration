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
package com.cubrid.cubridmigration.core.engine.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SourceColumnConfig")
class SourceColumnConfigTest {

    @Nested
    @DisplayName("setReplaceExpression()")
    class SetReplaceExpression {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the expression is read as semicolon separated old:new pairs")
        @CsvSource(
                quoteCharacter = '"',
                nullValues = "null",
                value = {
                    "\"b:c\",          \"b:c\"",

                    // An entry with no colon names no replacement, so it is dropped.
                    "\"a;b:c\",        \"b:c\"",

                    // The split is on the first colon, so the rest is the new value.
                    "\"k:a:b\",        \"k:a:b\"",

                    // Nothing after the colon replaces the value with an empty string.
                    "\"d:\",           \"d:\"",

                    // The last entry wins when a value is named twice.
                    "\"x:1;x:2\",      \"x:2\"",

                    // The whole expression the legacy suite used.
                    "\"a;b:c;d:a:;d:;\", \"b:c;d:\"",
                })
        void expression_isReadAsOldToNewPairs(String expression, String expected) {
            SourceColumnConfig config = new SourceColumnConfig();

            config.setReplaceExpression(expression);

            assertThat(config.getReplaceExp()).isEqualTo(expected);
        }

        @Test
        @DisplayName("a value with no replacement of its own is migrated as it stands")
        void unnamedValue_isKeptAsItIs() {
            SourceColumnConfig config = new SourceColumnConfig();
            config.setReplaceExpression("a;b:c;d:a:;d:;");

            assertThat(config.getReplaceValue("b")).isEqualTo("c");
            assertThat(config.getReplaceValue("d")).isEmpty();
            assertThat(config.getReplaceValue("a")).isEqualTo("a");
            assertThat(config.getReplaceValue("z")).isEqualTo("z");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("no expression clears whatever was there")
        @CsvSource(
                nullValues = "null",
                value = {"null", "''"})
        void noExpression_clearsTheReplacements(String expression) {
            SourceColumnConfig config = new SourceColumnConfig();
            config.setReplaceExpression("b:c");

            config.setReplaceExpression(expression);

            assertThat(config.getReplaceExp()).isEmpty();
        }
    }
}
