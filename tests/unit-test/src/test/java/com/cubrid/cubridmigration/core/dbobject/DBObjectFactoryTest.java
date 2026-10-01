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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DBObjectFactory")
class DBObjectFactoryTest {

    private static final DBObjectFactory FACTORY = new DBObjectFactory();

    private static SourcePlsqlProcedure source(String authid) {
        return new SourcePlsqlProcedure() {
            @Override
            public String getOwner() {
                return "scott";
            }

            @Override
            public String getName() {
                return "calc";
            }

            @Override
            public String getAuthid() {
                return authid;
            }

            @Override
            public String getProcedureType() {
                return "FUNCTION";
            }

            @Override
            public String getDDL() {
                return "CREATE FUNCTION calc";
            }

            @Override
            public void setDDL(String ddl) {}
        };
    }

    @Nested
    @DisplayName("createPlcsqlFunction()")
    class CreatePlcsqlFunction {

        @Test
        @DisplayName("the owner, name and source DDL are taken, with owner rights")
        void sourceFields_areTakenWithOwnerRights() {
            PlcsqlFunction function = FACTORY.createPlcsqlFunction(source("CURRENT_USER"));

            assertThat(function.getOwner()).isEqualTo("scott");
            assertThat(function.getName()).isEqualTo("calc");
            assertThat(function.getSourceDDL()).isEqualTo("CREATE FUNCTION calc");
            assertThat(function.getAuthid()).isEqualTo("OWNER");
            assertThat(function.isAuthidChanged()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> unchanged")
        @DisplayName("only CURRENT_USER in capitals marks the rights as changed")
        @ValueSource(strings = {"current_user", "OWNER", "DEFINER"})
        @NullSource
        void otherAuthid_isNotAChange(String authid) {
            PlcsqlFunction function = FACTORY.createPlcsqlFunction(source(authid));

            assertThat(function.getAuthid()).isEqualTo("OWNER");
            assertThat(function.isAuthidChanged()).isFalse();
        }
    }

    @Nested
    @DisplayName("createPlcsqlProcedure()")
    class CreatePlcsqlProcedure {

        @Test
        @DisplayName("the owner, name and source DDL are taken, with owner rights")
        void sourceFields_areTakenWithOwnerRights() {
            PlcsqlProcedure procedure = FACTORY.createPlcsqlProcedure(source("CURRENT_USER"));

            assertThat(procedure.getOwner()).isEqualTo("scott");
            assertThat(procedure.getName()).isEqualTo("calc");
            assertThat(procedure.getSourceDDL()).isEqualTo("CREATE FUNCTION calc");
            assertThat(procedure.getAuthid()).isEqualTo("OWNER");
            assertThat(procedure.isAuthidChanged()).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> unchanged")
        @DisplayName("only CURRENT_USER in capitals marks the rights as changed")
        @ValueSource(strings = {"current_user", "OWNER", "DEFINER"})
        @NullSource
        void otherAuthid_isNotAChange(String authid) {
            PlcsqlProcedure procedure = FACTORY.createPlcsqlProcedure(source(authid));

            assertThat(procedure.getAuthid()).isEqualTo("OWNER");
            assertThat(procedure.isAuthidChanged()).isFalse();
        }
    }
}
