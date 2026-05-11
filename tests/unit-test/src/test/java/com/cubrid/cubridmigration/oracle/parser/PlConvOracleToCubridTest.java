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
package com.cubrid.cubridmigration.oracle.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlConvOracleToCubrid")
public class PlConvOracleToCubridTest {

    @Nested
    @DisplayName("getProcedureDDL()")
    class GetProcedureDDL {

        @Test
        @DisplayName("valid procedure parses and splits header/body around IS")
        void validProcedure_splitsHeaderAndBody() {
            String text = "CREATE OR REPLACE PROCEDURE HELLO IS BEGIN NULL; END;";

            ProcedureDDL result = PlConvOracleToCubrid.getProcedureDDL(text, false);

            assertThat(result.getHeader()).contains("PROCEDURE HELLO");
            assertThat(result.getBody()).startsWith("IS");
            assertThat(result.getBody()).contains("END;");
            assertThat(result.hasUnsupportedType()).isFalse();
            assertThat(result.hasSyntaxError()).isFalse();
            assertThat(result.getSyntaxErrorMessage()).isNull();
        }

        @Test
        @DisplayName("valid function parses and splits header/body around IS")
        void validFunction_splitsHeaderAndBody() {
            String text =
                    "CREATE OR REPLACE FUNCTION ADD_ONE (P NUMBER) RETURN NUMBER IS "
                            + "BEGIN RETURN P + 1; END;";

            ProcedureDDL result = PlConvOracleToCubrid.getProcedureDDL(text, false);

            assertThat(result.getHeader()).contains("FUNCTION ADD_ONE");
            assertThat(result.getHeader()).contains("RETURN NUMBER");
            assertThat(result.getBody()).startsWith("IS");
            assertThat(result.getBody()).contains("RETURN P + 1;");
            assertThat(result.hasSyntaxError()).isFalse();
        }

        @Test
        @DisplayName("wrapped routine flags syntax error and returns empty DDL")
        void wrappedRoutine_flagsSyntaxError() {
            String text =
                    "FUNCTION SYS_INTERNAL_FUNC wrapped\n"
                            + "abcd\n"
                            + "0123456789abcdef0123456789abcdef\n";

            ProcedureDDL[] holder = new ProcedureDDL[1];
            assertThatCode(() -> holder[0] = PlConvOracleToCubrid.getProcedureDDL(text, false))
                    .doesNotThrowAnyException();

            assertThat(holder[0]).isNotNull();
            assertThat(holder[0].getHeader()).isEmpty();
            assertThat(holder[0].getBody()).isEmpty();
            assertThat(holder[0].hasUnsupportedType()).isFalse();
            assertThat(holder[0].hasSyntaxError()).isTrue();
            assertThat(holder[0].getSyntaxErrorMessage()).contains("wrapped");
        }

        @Test
        @DisplayName("unsupported syntax after AS keyword returns best-effort header and body")
        void unsupportedSyntaxAfterAs_returnsBestEffortOutput() {
            String text =
                    "CREATE OR REPLACE PROCEDURE EXCEPTION_TEST AS\n"
                            + "    e_zero_div EXCEPTION;\n"
                            + "    PRAGMA EXCEPTION_INIT(e_zero_div, -1476);\n"
                            + "BEGIN\n"
                            + "    NULL;\n"
                            + "END;";

            ProcedureDDL result = PlConvOracleToCubrid.getProcedureDDL(text, false);

            assertThat(result.hasSyntaxError()).isFalse();
            assertThat(result.getHeader()).contains("PROCEDURE EXCEPTION_TEST");
            assertThat(result.getBody()).startsWith("AS");
            assertThat(result.getBody()).contains("PRAGMA EXCEPTION_INIT");
        }
    }
}
