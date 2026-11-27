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
package com.cubrid.cubridmigration.core.dbobject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Grant")
class GrantTest {

    private static Grant grant() {
        return new Grant("owner", "grantor", "grantee", "tbl", "classOwner", "SELECT", true, "ddl");
    }

    @Nested
    @DisplayName("clone()")
    class Clone {

        @Test
        @DisplayName("the clone is a new grant with the eight constructor fields")
        void clone_copiesTheConstructorFields() {
            Grant grant = grant();

            Grant clone = (Grant) grant.clone();

            assertThat(clone).isNotSameAs(grant);
            assertThat(clone.getOwner()).isEqualTo("owner");
            assertThat(clone.getGrantorName()).isEqualTo("grantor");
            assertThat(clone.getGranteeName()).isEqualTo("grantee");
            assertThat(clone.getClassName()).isEqualTo("tbl");
            assertThat(clone.getClassOwner()).isEqualTo("classOwner");
            assertThat(clone.getAuthType()).isEqualTo("SELECT");
            assertThat(clone.isGrantable()).isTrue();
            assertThat(clone.getDDL()).isEqualTo("ddl");
        }

        @Test
        @DisplayName("the set name and the source owners are left out")
        void nameAndSourceOwners_areLeftOut() {
            Grant grant = grant();
            grant.setName("custom");
            grant.setSourceOwner("sourceOwner");
            grant.setSourceObjectOwner("sourceObjectOwner");

            Grant clone = (Grant) grant.clone();

            assertThat(clone.getName()).isEqualTo("SELECT ON classOwner.tbl TO grantee");
            assertThat(clone.getSourceOwner()).isNull();
            assertThat(clone.getSourceObjectOwner()).isNull();
        }
    }

    @Nested
    @DisplayName("getName()")
    class GetName {

        @Test
        @DisplayName("without a set name it reads auth ON owner.table TO grantee")
        void unsetName_isBuiltFromTheGrant() {
            assertThat(grant().getName()).isEqualTo("SELECT ON classOwner.tbl TO grantee");
        }

        @Test
        @DisplayName("a set name is returned as it is")
        void setName_isReturned() {
            Grant grant = grant();
            grant.setName("custom");

            assertThat(grant.getName()).isEqualTo("custom");
        }

        @Test
        @DisplayName("an empty grant reads its null parts as null")
        void emptyGrant_readsNullParts() {
            assertThat(new Grant().getName()).isEqualTo("null ON .null TO null");
        }

        @Test
        @DisplayName("without a class owner the dot stays in front of the table")
        void missingClassOwner_leavesALoneDot() {
            // DEFECT: a null class owner becomes an empty string but its dot is kept, so the
            // name reads ON .tbl
            // - see Grant.getName()
            Grant grant = grant();
            grant.setClassOwner(null);

            assertThat(grant.getName()).isEqualTo("SELECT ON .tbl TO grantee");
        }
    }
}
