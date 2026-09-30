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
package com.cubrid.cubridmigration.cubrid.export.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cubrid.cubridmigration.core.dbobject.Column;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

@DisplayName("CUBRIDSetTypeHandler")
class CUBRIDSetTypeHandlerTest {

    private static final CUBRIDSetTypeHandler HANDLER = new CUBRIDSetTypeHandler();

    private ResultSet rs;
    private Column column;

    @BeforeEach
    void setUp() {
        rs = mock(ResultSet.class);
        column = new Column();
        column.setName("C1");
    }

    // The collection is read through cubrid.jdbc.driver.CUBRIDResultSet by reflection, and the
    // unit-test classpath carries no CUBRID driver, so only the failure path runs here. The E2E
    // suite covers the successful read against a real CUBRID.
    //
    // DEFECT: every failure is swallowed - the catch body only calls e.getMessage() and drops it -
    // so a collection column that cannot be read migrates as NULL with nothing logged
    // - see CUBRIDSetTypeHandler.getJdbcObject()
    @Test
    @DisplayName("driver class missing -> null, and nothing is reported")
    void missingDriver_returnsNullSilently() throws SQLException {
        assertThat(HANDLER.getJdbcObject(rs, column)).isNull();
    }
}
