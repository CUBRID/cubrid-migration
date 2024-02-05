/*
 * Copyright (c) 2016 CUBRID Corporation.
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
package com.cubrid.cubridmigration.mariadb;

import com.cubrid.cubridmigration.mariadb.trans.MariaDB2CUBRIDMigParas;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.TimeZone;
import org.junit.Test;

public class MariaDB2CUBRIDMigParasTest {

    @Test
    public void testMySQL2CUBRIDMigParas() throws Exception {
        // branch 1st
        // branch 2nd.
        InputStream is =
                MariaDB2CUBRIDMigParasTest.class.getResourceAsStream(
                        "/com/cubrid/cubridmigration/mariadb/MariaDB2CUBRIDParams.xml");
        InputStreamReader reader = new InputStreamReader(is, "utf-8");
        char[] buf = new char[1];
        StringBuffer sb = new StringBuffer();
        while (reader.read(buf) > 0) {
            sb.append(buf[0]);
        }
        MariaDB2CUBRIDMigParas.loadFromPreference(null);
        MariaDB2CUBRIDMigParas.loadFromPreference(sb.toString());
        MariaDB2CUBRIDMigParas.getReplacedDate(null, null);
        MariaDB2CUBRIDMigParas.getReplacedDate("2011-12-12", null);
        MariaDB2CUBRIDMigParas.getReplacedDate("2011-00-00", null);
        MariaDB2CUBRIDMigParas.getReplacedTime(null, null);
        MariaDB2CUBRIDMigParas.getReplacedTime("12:12:12", TimeZone.getDefault());
        MariaDB2CUBRIDMigParas.getReplacedTime("12:12:61", TimeZone.getDefault());
        MariaDB2CUBRIDMigParas.getReplacedTimestamp(null, TimeZone.getDefault());
        MariaDB2CUBRIDMigParas.getReplacedTimestamp("2011-12-12 12:12:12", TimeZone.getDefault());
        MariaDB2CUBRIDMigParas.getReplacedTimestamp(
                "2011-12-12 12:12:12.111", TimeZone.getDefault());
    }
}
