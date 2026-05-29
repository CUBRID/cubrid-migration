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
package com.cubrid.cubridmigration.core.engine.template.reader.node;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.template.TemplateTags;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.helpers.AttributesImpl;

@DisplayName("TargetNodeHandler")
class TargetNodeHandlerTest {

    @ParameterizedTest(name = "type=\"dir\" keeps destType {0}")
    @ValueSource(
            ints = {
                MigrationConfiguration.DEST_CSV,
                MigrationConfiguration.DEST_SQL,
                MigrationConfiguration.DEST_XLS,
                MigrationConfiguration.DEST_DB_UNLOAD
            })
    @DisplayName("file target does not overwrite the format set by file_repository")
    void dirTarget_keepsFileRepositoryDestType(int destType) {
        MigrationConfiguration config = new MigrationConfiguration();
        config.setDestType(destType);

        new TargetNodeHandler(config).processAttributes(attributes(TemplateTags.VALUE_DIR));

        assertThat(config.getDestType()).isEqualTo(destType);
    }

    @Test
    @DisplayName("online target sets DEST_ONLINE")
    void onlineTarget_setsOnlineDestType() {
        MigrationConfiguration config = new MigrationConfiguration();
        config.setDestType(MigrationConfiguration.DEST_CSV);

        new TargetNodeHandler(config).processAttributes(attributes(TemplateTags.VALUE_ONLINE));

        assertThat(config.getDestType()).isEqualTo(MigrationConfiguration.DEST_ONLINE);
    }

    private static AttributesImpl attributes(String type) {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", TemplateTags.ATTR_VERSION, TemplateTags.ATTR_VERSION, "CDATA", "");
        attrs.addAttribute("", TemplateTags.ATTR_TYPE, TemplateTags.ATTR_TYPE, "CDATA", type);
        return attrs;
    }
}
