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
package com.cubrid.cubridmigration.testutil;

import com.cubrid.cubridmigration.core.connection.JDBCData;
import com.cubrid.cubridmigration.core.dbtype.DatabaseType;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Snapshot of the JDBC drivers every DatabaseType has registered, so a test can put them back. */
public final class DatabaseTypeDrivers {

    private final Map<DatabaseType, List<JDBCData>> drivers;

    private DatabaseTypeDrivers(Map<DatabaseType, List<JDBCData>> drivers) {
        this.drivers = drivers;
    }

    public static DatabaseTypeDrivers capture() {
        Map<DatabaseType, List<JDBCData>> drivers = new LinkedHashMap<>();
        for (DatabaseType type : DatabaseType.getAllTypes()) {
            drivers.put(type, type.getJDBCDatas());
        }
        return new DatabaseTypeDrivers(drivers);
    }

    public void restore() {
        drivers.forEach(
                (type, saved) -> {
                    List<JDBCData> live = registered(type);
                    live.clear();
                    live.addAll(saved);
                });
    }

    // getJDBCDatas() hands out a copy, so the live list is only reachable through the field.
    @SuppressWarnings("unchecked")
    private static List<JDBCData> registered(DatabaseType type) {
        try {
            Field field = DatabaseType.class.getDeclaredField("jdbcDatas");
            field.setAccessible(true);
            return (List<JDBCData>) field.get(type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
