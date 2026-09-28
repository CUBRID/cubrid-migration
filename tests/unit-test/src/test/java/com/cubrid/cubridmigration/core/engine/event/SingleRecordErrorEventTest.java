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
package com.cubrid.cubridmigration.core.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cubrid.cubridmigration.core.dbobject.Column;
import com.cubrid.cubridmigration.core.dbobject.Record;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SingleRecordErrorEvent")
class SingleRecordErrorEventTest {

    private static Column column(String name) {
        Column column = new Column();
        column.setName(name);
        column.setDataType("integer");
        return column;
    }

    private static Table table(Column... columns) {
        Table table = new Table();
        table.setName("test");
        for (Column column : columns) {
            table.addColumn(column);
        }
        return table;
    }

    @Test
    @DisplayName("the table, the error message and each column value read in one line")
    void toString_readsTheTableTheErrorAndTheValues() {
        Column f1 = column("f1");
        Column f2 = column("f2");
        table(f1, f2);
        Record record = new Record();
        record.addColumnValue(f1, "1");
        record.addColumnValue(f2, null);

        assertThat(new SingleRecordErrorEvent(record, new RuntimeException("exception")))
                .hasToString("[test]Error:exception;Values:[f1:1,f2:null]");
    }

    @Test
    @DisplayName("the table comes from the first column, so one outside a table leaves it out")
    void toString_takesTheTableFromTheFirstColumn() {
        Column f1 = column("f1");
        table(f1);
        Record record = new Record();
        record.addColumnValue(column("g"), 7);
        record.addColumnValue(f1, "1");

        assertThat(new SingleRecordErrorEvent(record, new RuntimeException("exception")))
                .hasToString("Error:exception;Values:[g:7,f1:1]");
    }

    @Test
    @DisplayName("a record without values reads as No columns table.")
    void toString_readsNoColumnsForAnEmptyRecord() {
        assertThat(new SingleRecordErrorEvent(new Record(), null)).hasToString("No columns table.");
    }

    @Test
    @DisplayName("a record with values but no error throws NullPointerException")
    void toString_throwsNullPointerExceptionWithoutAnError() {
        Column f1 = column("f1");
        table(f1);
        Record record = new Record();
        record.addColumnValue(f1, "1");
        SingleRecordErrorEvent event = new SingleRecordErrorEvent(record, null);

        assertThatThrownBy(event::toString).isInstanceOf(NullPointerException.class);
    }
}
