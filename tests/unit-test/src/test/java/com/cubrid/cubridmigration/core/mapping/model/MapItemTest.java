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
package com.cubrid.cubridmigration.core.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.mapping.AbstractDataTypeMappingHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

@DisplayName("MapItem")
class MapItemTest {

    /** MapItem keeps a reference to its owner, so the tests need one to build items with. */
    private static final AbstractDataTypeMappingHelper OWNER =
            new AbstractDataTypeMappingHelper(
                    "MySQL2CUBRID", "/com/cubrid/cubridmigration/mysql/trans/MySQL2CUBRID.xml") {
                @Override
                public String getMapKey(String datatype, String precision, String scale) {
                    return datatype;
                }
            };

    private MapItem item;

    @BeforeEach
    void newItem() {
        item = new MapItem(OWNER);
    }

    @Nested
    @DisplayName("getFirstTarget()")
    class GetFirstTarget {

        @Test
        @DisplayName("the first of the targets the mapping offers")
        void withTargets_returnsTheFirst() {
            item.setAvailableTargetList(Arrays.asList(target("varchar"), target("clob")));

            assertThat(item.getFirstTarget().getDatatype()).isEqualTo("varchar");
        }

        @Test
        @DisplayName("nothing to migrate to yet -> null")
        void noTargets_returnsNull() {
            assertThat(item.getFirstTarget()).isNull();
        }
    }

    @Nested
    @DisplayName("clone()")
    class Clone {

        @Test
        @DisplayName("the copy carries the same source and target")
        void copy_carriesTheSameMapping() {
            item.setSource(target("text"));
            item.setTarget(target("varchar"));

            MapItem copy = item.clone();

            assertThat(copy).isNotSameAs(item);
            assertThat(copy.getSource().getDatatype()).isEqualTo("text");
            assertThat(copy.getTarget().getDatatype()).isEqualTo("varchar");
        }
    }

    private static MapObject target(String dataType) {
        MapObject object = new MapObject();
        object.setDatatype(dataType);
        return object;
    }
}
