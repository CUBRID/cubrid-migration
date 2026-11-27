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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

@DisplayName("SchemaSelection")
class SchemaSelectionTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("names are trimmed and deduplicated, in the order they arrived")
        void names_areTrimmedAndDeduplicated() {
            SchemaSelection selection =
                    SchemaSelection.of(Arrays.asList(" b ", "a", null, "b", ""));

            assertThat(selection.asSet()).containsExactly("b", "a");
        }

        @Test
        @DisplayName("the selection cannot be changed once made")
        void selection_isImmutable() {
            SchemaSelection selection = SchemaSelection.of(Collections.singletonList("a"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> selection.asSet().add("b"));
        }

        @Test
        @DisplayName("two selections holding the same names are equal")
        void sameNames_areEqual() {
            assertThat(SchemaSelection.of(Arrays.asList("a", "b")))
                    .isEqualTo(SchemaSelection.of(Arrays.asList("a", "b")))
                    .hasSameHashCodeAs(SchemaSelection.of(Arrays.asList("a", "b")));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a selection with nothing usable in it is the shared empty one")
        @MethodSource(
                "com.cubrid.cubridmigration.core.engine.config.SchemaSelectionTest#nothingUsable")
        void nothingUsable_returnsTheSharedEmptySelection(String label, Collection<String> raw) {
            assertThat(SchemaSelection.of(raw)).isSameAs(SchemaSelection.empty());
        }
    }

    @Nested
    @DisplayName("contains()")
    class Contains {

        private final SchemaSelection selection = SchemaSelection.of(Arrays.asList("a", "b"));

        @Test
        @DisplayName("the name is trimmed before it is looked up, as it was when stored")
        void name_isTrimmedBeforeLookup() {
            assertThat(selection.contains("a")).isTrue();
            assertThat(selection.contains(" a ")).isTrue();
        }

        @Test
        @DisplayName("a name that was never selected -> false, and so is no name at all")
        void unselectedName_returnsFalse() {
            assertThat(selection.contains("z")).isFalse();
            assertThat(selection.contains(null)).isFalse();
        }
    }

    static Stream<Object[]> nothingUsable() {
        return Stream.of(
                new Object[] {"null", null},
                new Object[] {"empty", new ArrayList<String>()},
                new Object[] {"blank only", Arrays.asList("  ", "")},
                new Object[] {"null only", Collections.<String>singletonList(null)});
    }
}
