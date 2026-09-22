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

import java.util.Arrays;
import java.util.Collections;

@DisplayName("CSVSettings")
class CSVSettingsTest {

    @Nested
    @DisplayName("setNullStrings()")
    class SetNullStrings {

        @Test
        @DisplayName("a CSV starts out reading the three spellings of NULL that CMT writes")
        void freshSettings_readTheThreeDefaultSpellings() {
            assertThat(new CSVSettings().getNullStrings()).containsExactly("\\N", "NULL", "(NULL)");
        }

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @DisplayName("the semicolon separated form is split and deduplicated")
        @CsvSource(
                quoteCharacter = '"',
                value = {
                    "\"A;B\",   \"A,B\"",

                    // The same spelling twice is stored once.
                    "\"A;B;A\", \"A,B\"",
                })
        void separatedForm_isSplitAndDeduplicated(String raw, String expected) {
            CSVSettings settings = new CSVSettings();

            settings.setNullStrings(raw);

            assertThat(settings.getNullStrings()).containsExactly(expected.split(","));
        }

        @Test
        @DisplayName("a list is deduplicated the same way, and replaces what was there")
        void list_isDeduplicatedAndReplaces() {
            CSVSettings settings = new CSVSettings();

            settings.setNullStrings(Arrays.asList("A", "B", "A"));

            assertThat(settings.getNullStrings()).containsExactly("A", "B");
        }

        @Test
        @DisplayName("an empty form leaves nothing recognised as NULL")
        void emptyForm_recognisesNothing() {
            CSVSettings settings = new CSVSettings();

            settings.setNullStrings("");

            assertThat(settings.getNullStrings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("two untouched settings read a CSV the same way")
        void untouchedSettings_areEqual() {
            assertThat(new CSVSettings()).isEqualTo(new CSVSettings());
        }

        @Test
        @DisplayName("the NULL spellings are compared as a set, so their order does not matter")
        void nullStringOrder_doesNotMatter() {
            CSVSettings reordered = new CSVSettings();
            reordered.setNullStrings(Arrays.asList("NULL", "\\N", "(NULL)"));

            assertThat(reordered).isEqualTo(new CSVSettings());
        }

        @Test
        @DisplayName(
                "the charset is compared without regard to case, since a CSV reader ignores it")
        void charsetCase_doesNotMatter() {
            CSVSettings lowercased = new CSVSettings();
            lowercased.setCharset("utf-8");

            assertThat(lowercased).isEqualTo(new CSVSettings());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("any setting that changes how the file is read makes them different")
        @CsvSource({"separator", "quote", "escape", "charset", "nullStrings"})
        void differentSetting_makesThemUnequal(String changed) {
            CSVSettings settings = new CSVSettings();
            switch (changed) {
                case "separator":
                    settings.setSeparateChar(';');
                    break;
                case "quote":
                    settings.setQuoteChar('\'');
                    break;
                case "escape":
                    settings.setEscapeChar('|');
                    break;
                case "charset":
                    settings.setCharset("EUC-KR");
                    break;
                default:
                    settings.setNullStrings(Collections.singletonList("NULL"));
            }

            assertThat(settings).isNotEqualTo(new CSVSettings());
        }

        @Test
        @DisplayName("nothing to compare against, or something that is not a CSV setting -> false")
        void otherThanSettings_isNotEqual() {
            // DEFECT: CSVSettings overrides equals() without hashCode(), so equal instances do not
            // share a hash code and would land in different buckets. Nothing puts CSVSettings in a
            // hash based collection today, and asserting on identity hash codes would not be
            // deterministic, so the contract break is only recorded here
            // - see CSVSettings.equals()
            assertThat(new CSVSettings()).isNotEqualTo(null).isNotEqualTo("not a setting");
        }
    }

    @Nested
    @DisplayName("clone()")
    class Clone {

        @Test
        @DisplayName("the copy reads a CSV the same way but is a separate object")
        void copy_readsTheSameWayAndIsSeparate() {
            CSVSettings original = new CSVSettings();
            original.setSeparateChar(';');
            original.setCharset("EUC-KR");

            CSVSettings copy = original.clone();

            assertThat(copy).isEqualTo(original).isNotSameAs(original);
        }
    }
}
