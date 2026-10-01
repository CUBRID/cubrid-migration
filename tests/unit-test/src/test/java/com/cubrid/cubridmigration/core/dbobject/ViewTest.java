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

@DisplayName("View")
class ViewTest {

    private static View view(String name, String owner) {
        View view = new View();
        view.setName(name);
        view.setOwner(owner);
        return view;
    }

    @Nested
    @DisplayName("getQuerySpec()")
    class GetQuerySpec {

        @Test
        @DisplayName("an unset query gives an empty string, a set one comes back as it is")
        void unsetQuery_givesEmptyString() {
            View view = new View();
            assertThat(view.getQuerySpec()).isEmpty();

            view.setQuerySpec("select 1");

            assertThat(view.getQuerySpec()).isEqualTo("select 1");
        }
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCode {

        @Test
        @DisplayName("views with the same name hash alike, and other names differently")
        void sameName_hashesAlike() {
            View first = new View();
            View second = new View();
            assertThat(first.hashCode()).isEqualTo(second.hashCode()).isEqualTo(31);

            first.setName("testview");
            second.setName("testview");
            assertThat(first.hashCode()).isEqualTo(second.hashCode());

            second.setName("testview2");
            assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
        }

        @Test
        @DisplayName("the owner and the query leave the hash alone")
        void ownerAndQuery_leaveTheHashAlone() {
            View view = view("v", "A");
            int hash = view.hashCode();

            view.setOwner("B");
            view.setQuerySpec("select 1");

            assertThat(view.hashCode()).isEqualTo(hash);
        }
    }

    @Nested
    @DisplayName("equals()")
    class Equals {

        @Test
        @DisplayName("the name decides equality")
        void name_decidesEquality() {
            View first = new View();
            View second = new View();
            assertThat(first)
                    .isEqualTo(first)
                    .isEqualTo(second)
                    .isNotEqualTo(12)
                    .isNotEqualTo(null);

            first.setName("testview");
            assertThat(second).isNotEqualTo(first);
            second.setName("testview");
            assertThat(first).isEqualTo(second);
            second.setName("testview2");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("a subclass is never equal")
        void subclass_isNeverEqual() {
            View view = view("V", null);
            View subclass = new View() {};
            subclass.setName("V");

            assertThat(view).isNotEqualTo(subclass);
        }

        @Test
        @DisplayName("views of different owners with the same name are equal")
        void viewsOfDifferentOwners_areEqual() {
            // DEFECT: only the name is compared, so a view of another owner counts as the same,
            // and targetViews.remove() in MigrationConfiguration can remove the wrong one
            // - see View.equals()
            assertThat(view("V", "A")).isEqualTo(view("V", "B"));
        }
    }
}
