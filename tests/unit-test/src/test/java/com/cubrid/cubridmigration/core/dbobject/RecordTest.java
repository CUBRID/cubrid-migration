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
import static org.assertj.core.api.Assertions.entry;

import com.cubrid.cubridmigration.core.dbobject.Record.ColumnValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DisplayName("Record")
class RecordTest {

    private static Column column(String name) {
        Column column = new Column();
        column.setName(name);
        column.setDataType("integer");
        return column;
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("the values read as \"[name=value,...]\" with the quotes, null as null")
        void values_readInQuotedBrackets() {
            Record record = new Record();
            List<ColumnValue> values = new ArrayList<>();
            record.setColumnValueList(values);
            record.addColumnValue(column("f0"), "0");
            record.addColumnValue(column("f1"), null);
            record.addColumnValue(column("f2"), "2");

            assertThat(record).hasToString("\"[f0=0,f1=null,f2=2]\"");
            assertThat(record.getColumnValueList()).isSameAs(values);
        }

        @Test
        @DisplayName("an empty record reads as \"[]\" with the quotes")
        void emptyRecord_readsAsQuotedBrackets() {
            assertThat(new Record()).hasToString("\"[]\"");
        }
    }

    @Nested
    @DisplayName("getColumnValueMap()")
    class GetColumnValueMap {

        @Test
        @DisplayName("each name maps to its value, the last one winning, names compared with case")
        void names_mapToTheLastValue() {
            Record record = new Record();
            record.addColumnValue(column("A"), 1);
            record.addColumnValue(column("A"), 2);
            record.addColumnValue(column("a"), 3);

            Map<String, Object> map = record.getColumnValueMap();

            assertThat(map).containsOnly(entry("A", 2), entry("a", 3));
            assertThat(record.getColumnValueMap()).isNotSameAs(map);
        }
    }
}
