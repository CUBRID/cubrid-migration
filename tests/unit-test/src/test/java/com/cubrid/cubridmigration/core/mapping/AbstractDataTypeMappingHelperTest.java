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
package com.cubrid.cubridmigration.core.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.cubrid.cubridmigration.core.mapping.model.MapItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@DisplayName("AbstractDataTypeMappingHelper")
class AbstractDataTypeMappingHelperTest {

    private static final String MAPPING_FILE =
            "/com/cubrid/cubridmigration/mysql/trans/MySQL2CUBRID.xml";

    /**
     * The base is abstract only in getMapKey. Keying on the type name alone keeps these tests on
     * the base's own code, so no dialect's key rules can move the answers.
     */
    private static class NameKeyedHelper extends AbstractDataTypeMappingHelper {

        NameKeyedHelper() {
            super("MySQL2CUBRID", MAPPING_FILE);
        }

        @Override
        public String getMapKey(String datatype, String precision, String scale) {
            return datatype;
        }
    }

    private AbstractDataTypeMappingHelper helper;

    @BeforeEach
    void loadMappingFile() {
        // The map is mutable and two of these tests replace it, so each gets its own helper.
        helper = new NameKeyedHelper();
    }

    @Nested
    @DisplayName("mapping file loading")
    class MappingFileLoading {

        @Test
        @DisplayName(
                "the file is read at construction and the preferences start out as its defaults")
        void construction_readsTheFileIntoBothMaps() {
            assertThat(helper.getName()).isEqualTo("MySQL2CUBRID");
            assertThat(helper.getPreferenceConfigMap())
                    .hasSameSizeAs(helper.getXmlConfigMap())
                    .containsOnlyKeys(helper.getXmlConfigMap().keySet());
        }

        @Test
        @DisplayName("the key decides how many entries the file yields, not how many it declares")
        void keyFunction_decidesTheEntryCount() {
            // MySQL2CUBRID.xml declares bit twice, once for the one-bit case. Keyed on the name
            // alone the two collapse into one, where MySQLDataTypeMappingHelper keeps them apart.
            assertThat(helper.getXmlConfigMap()).hasSize(39);
        }

        @Test
        @DisplayName("a preference file replaces the preferences")
        void preferenceFile_replacesThePreferences() throws Exception {
            helper.getPreferenceConfigMap().clear();

            helper.loadFromPreference(mappingFileContents());

            assertThat(helper.getPreferenceConfigMap()).hasSize(39);
        }

        @Test
        @DisplayName("restoring puts every entry back on the first target its mapping offers")
        void restoreDefault_putsBackTheFirstTargets() {
            helper.getPreferenceConfigMap().clear();

            assertThat(helper.restoreDefault()).hasSize(39);
            assertThat(helper.getTargetFromPreference("varchar").getDatatype())
                    .isEqualTo(
                            helper.getXmlConfigMap().get("varchar").getFirstTarget().getDatatype());
        }

        @Test
        @DisplayName("nothing to load -> the preferences stay as they were")
        void blankPreference_changesNothing() throws Exception {
            helper.loadFromPreference("   ");

            assertThat(helper.getPreferenceConfigMap()).hasSize(39);
        }

        @Test
        @DisplayName("a preference of a type CUBRID migration does not know is refused")
        void foreignPreference_isRefused() {
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> helper.loadFromPreference("<x>some other tool's file</x>"))
                    .withMessage("Invalid mapping configuration file.");
        }

        private String mappingFileContents() throws Exception {
            try (InputStream in =
                    AbstractDataTypeMappingHelper.class.getResourceAsStream(MAPPING_FILE)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    @Nested
    @DisplayName("getTargetFromPreference()")
    class GetTargetFromPreference {

        @Test
        @DisplayName("a known type -> the target its preference names")
        void knownType_returnsItsTarget() {
            assertThat(helper.getTargetFromPreference("varchar").getDatatype())
                    .isEqualTo("varchar");
        }

        @Test
        @DisplayName("a type with no entry -> null, since there is nothing to migrate it to")
        void unknownType_returnsNull() {
            assertThat(helper.getTargetFromPreference("no_such_type")).isNull();
        }

        @Test
        @DisplayName("the overload that takes a width needs one, and says so by throwing")
        void missingPrecision_throws() {
            assertThatNullPointerException()
                    .isThrownBy(() -> helper.getTargetFromPreference("varchar", null, null));
        }
    }

    @Nested
    @DisplayName("getXmlConfigMapItem()")
    class GetXmlConfigMapItem {

        @Test
        @DisplayName("a known type -> the entry the file declares for it, targets and all")
        void knownType_returnsItsEntry() {
            MapItem item = helper.getXmlConfigMapItem("varchar", "200", null);

            assertThat(item.getSource().getDatatype()).isEqualTo("varchar");
            assertThat(item.getFirstTarget().getDatatype()).isEqualTo("varchar");
        }

        @Test
        @DisplayName("a type the file does not declare -> null")
        void unknownType_returnsNull() {
            assertThat(helper.getXmlConfigMapItem("no_such_type", null, null)).isNull();
        }
    }
}
