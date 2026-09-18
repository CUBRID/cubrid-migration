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
package com.cubrid.cubridmigration.core.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SQLHelper")
class SQLHelperTest {

    private static final SQLHelper HELPER = new PassThroughHelper();

    @Nested
    @DisplayName("getViewQuerySpec()")
    class GetViewQuerySpec {

        @Test
        @DisplayName("MySQL view DDL -> everything from the first select onwards")
        void mysqlViewDDL_returnsQuerySpec() {
            String viewDDL =
                    "CREATE ALGORITHM=UNDEFINED DEFINER=`mydbadmin`@`192.168.1.175` SQL SECURITY"
                            + " DEFINER VIEW `tgt_view` AS select `tgt`.`d` AS `d`,`tgt`.`ff` AS"
                            + " `ff` from `tgt` ";

            assertThat(HELPER.getViewQuerySpec(viewDDL))
                    .isEqualTo("select `tgt`.`d` AS `d`,`tgt`.`ff` AS `ff` from `tgt` ");
        }

        @Test
        @DisplayName("a view name carrying a column list is still matched")
        void viewNameWithColumnList_returnsQuerySpec() {
            String viewDDL = "create view [tgtview(c1,c2)] AS select `tgt`.`d` AS `d` from `tgt`";

            assertThat(HELPER.getViewQuerySpec(viewDDL))
                    .isEqualTo("select `tgt`.`d` AS `d` from `tgt`");
        }

        @Test
        @DisplayName("a newline between AS and select is matched too")
        void multilineViewDDL_returnsQuerySpec() {
            String viewDDL = "CREATE VIEW `v` AS\nselect `t`.`c` AS `c`\nfrom `t`";

            assertThat(HELPER.getViewQuerySpec(viewDDL))
                    .isEqualTo("select `t`.`c` AS `c`\nfrom `t`");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"\"")
        @DisplayName("a string with no view header yields nothing")
        @ValueSource(strings = {"ss", "select * from t", "CREATE TABLE t (c int)"})
        void withoutViewHeader_returnsEmptyString(String viewDDL) {
            assertThat(HELPER.getViewQuerySpec(viewDDL)).isEmpty();
        }

        @ParameterizedTest(name = "[{index}] null or empty -> \"\"")
        @DisplayName("null and empty are answered without parsing")
        @NullAndEmptySource
        void nullOrEmptyViewDDL_returnsEmptyString(String viewDDL) {
            assertThat(HELPER.getViewQuerySpec(viewDDL)).isEmpty();
        }
    }

    /**
     * The two abstract members are not consulted by getViewQuerySpec(); they pass their input on.
     */
    private static class PassThroughHelper extends SQLHelper {

        @Override
        public String getQuotedObjName(String objectName) {
            return objectName;
        }

        @Override
        public String getTestSelectSQL(String sql) {
            return sql;
        }
    }
}
