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

import com.cubrid.cubridmigration.core.dbobject.PlcsqlFunction;
import com.cubrid.cubridmigration.core.dbobject.PlcsqlProcedure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationConfiguration")
class MigrationConfigurationTest {

    private static final String PROCEDURE_SOURCE_DDL =
            "CREATE OR REPLACE PROCEDURE TEST_PROC IS\n" + "BEGIN\n" + "    NULL;\n" + "END;";

    private static final String FUNCTION_SOURCE_DDL =
            "CREATE OR REPLACE FUNCTION TEST_FUNC RETURN NUMBER IS\n"
                    + "BEGIN\n"
                    + "    RETURN 1;\n"
                    + "END;";

    @Nested
    @DisplayName("parsingProcedureFunction()")
    class ParsingProcedureFunction {

        @Test
        @DisplayName("rebuilds procedure body when only header is present")
        void parsingProcedureFunction_rebuildsProcedureBodyWhenMissing() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpPlcsqlProcedureCfg(
                    "SRC",
                    "TAR",
                    "TEST_PROC",
                    "test_proc",
                    "DEFINER",
                    false,
                    PROCEDURE_SOURCE_DDL,
                    null,
                    null,
                    null);

            PlcsqlProcedure target = new PlcsqlProcedure();
            target.setOwner("SRC");
            target.setTargetOwner("TAR");
            target.setName("TEST_PROC");
            target.setTargetName("test_proc");
            target.setHeaderDDL("PROCEDURE [tar].[test_proc]");
            target.setBodyDDL("");
            config.addTargetPlcsqlProcedureSchema(target);

            config.parsingProcedureFunction(true);

            PlcsqlProcedure parsed = config.getTargetPlcsqlProcedureSchema("SRC", "TEST_PROC");
            assertThat(parsed.getHeaderDDL()).contains("PROCEDURE");
            assertThat(parsed.getBodyDDL()).isNotBlank();
            assertThat(parsed.getBodyDDL()).contains("BEGIN");
            assertThat(parsed.getBodyDDL()).contains("NULL;");
        }

        @Test
        @DisplayName("rebuilds function body when only header is present")
        void parsingProcedureFunction_rebuildsFunctionBodyWhenMissing() {
            MigrationConfiguration config = new MigrationConfiguration();
            config.addExpPlcsqlFunctionCfg(
                    "SRC",
                    "TAR",
                    "TEST_FUNC",
                    "test_func",
                    "DEFINER",
                    false,
                    FUNCTION_SOURCE_DDL,
                    null,
                    null,
                    null);

            PlcsqlFunction target = new PlcsqlFunction();
            target.setOwner("SRC");
            target.setTargetOwner("TAR");
            target.setName("TEST_FUNC");
            target.setTargetName("test_func");
            target.setHeaderDDL("FUNCTION [tar].[test_func] RETURN NUMERIC");
            target.setBodyDDL("   ");
            config.addTargetPlcsqlFunctionSchema(target);

            config.parsingProcedureFunction(true);

            PlcsqlFunction parsed = config.getTargetPlcsqlFunctionSchema("SRC", "TEST_FUNC");
            assertThat(parsed.getHeaderDDL()).contains("FUNCTION");
            assertThat(parsed.getBodyDDL()).isNotBlank();
            assertThat(parsed.getBodyDDL()).contains("BEGIN");
            assertThat(parsed.getBodyDDL()).contains("RETURN 1;");
        }
    }
}
