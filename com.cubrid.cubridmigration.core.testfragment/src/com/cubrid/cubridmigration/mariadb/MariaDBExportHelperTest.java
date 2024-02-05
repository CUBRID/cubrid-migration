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

import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.template.TemplateParserTest;
import com.cubrid.cubridmigration.mariadb.export.MariaDBExportHelper;
import java.util.Locale;
import org.junit.Assert;
import org.junit.Test;

public class MariaDBExportHelperTest {

    @Test
    public void testMatchMariaDBLimit() {
        MariaDBExportHelper r = new MariaDBExportHelper();
        Assert.assertEquals(true, r.matchMariaDBLimit(" LIMIT 10 "));
        Assert.assertEquals(true, r.matchMariaDBLimit(" LIMIT 10 , 12"));
        Assert.assertEquals(false, r.matchMariaDBLimit(" LIMIT 10 12"));
        Assert.assertEquals(true, r.matchMariaDBLimit(" LIMIT 10 OFFSET 12"));
    }

    @Test
    public void testReplaceWithMariaDBLimit0() {
        MariaDBExportHelper r = new MariaDBExportHelper();
        Assert.assertEquals(" LIMIT 0 ss", r.replaceWithMariaDBLimit0(" LIMIT 10 ss"));
        Assert.assertEquals(" LIMIT 0 dd", r.replaceWithMariaDBLimit0(" LIMIT 10 , 12 dd"));
        Assert.assertEquals(" LIMIT 10 12", r.replaceWithMariaDBLimit0(" LIMIT 10 12"));
        Assert.assertEquals(" LIMIT 0 ", r.replaceWithMariaDBLimit0(" LIMIT 10 OFFSET 12"));
        Assert.assertEquals(" LIMIT ", r.replaceWithMariaDBLimit0(" LIMIT "));
    }

    @Test
    public void testLimit() {
        String sqlFilterPart = " limit";
        boolean match = sqlFilterPart.toLowerCase(Locale.ENGLISH).matches(".*?\\s+limit\\s*.*");
        Assert.assertEquals(true, match);
    }

    @Test
    public void testFillTablesRowCount() throws Exception {
        MigrationConfiguration config = TemplateParserTest.getMariaDBConfig();
        config.getSourceDBType().getExportHelper().fillTablesRowCount(config);
    }
}
