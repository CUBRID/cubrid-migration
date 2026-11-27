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

import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.PartitionTable;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Table;

import java.math.BigInteger;

/**
 * Test Table Factory.
 *
 * <p>Usage: {@code import static com.cubrid.cubridmigration.testutil.TestTableFactory.*;}
 */
public final class TestTableFactory {

    private TestTableFactory() {}

    public static Table createTable(String name, String... columnNames) {
        Table table = new Table();
        table.setName(name);
        for (String columnName : columnNames) {
            table.addColumn(TestColumnFactory.createColumn(columnName, "int", null, null));
        }
        return table;
    }

    /** Every column is ascending; use {@link #createIndex(String, String, boolean)} for DESC. */
    public static Index createIndex(String name, String... columnNames) {
        Index index = new Index();
        index.setName(name);
        for (String columnName : columnNames) {
            index.addColumn(columnName, true);
        }
        return index;
    }

    public static Index createIndex(String name, String columnName, boolean ascending) {
        Index index = new Index();
        index.setName(name);
        index.addColumn(columnName, ascending);
        return index;
    }

    /** Partitions are added with {@link #addPartition(PartitionInfo, String, String)}. */
    public static PartitionInfo createPartitionInfo(String method, String columnName) {
        PartitionInfo info = new PartitionInfo();
        info.setPartitionMethod(method);
        info.addPartitionColumn(TestColumnFactory.createColumn(columnName, "int", null, null));
        return info;
    }

    public static void addPartition(PartitionInfo info, String name, String description) {
        PartitionTable partition = new PartitionTable();
        partition.setPartitionName(name);
        partition.setPartitionDesc(description);
        info.addPartition(partition);
    }

    /**
     * A bounded serial. Sequence defaults isNoMinValue/isNoMaxValue to true and the value setters
     * do not clear them, so both flags are cleared here for the bounds to reach the DDL.
     */
    public static Sequence createSequence(String name, String owner) {
        Sequence sequence = new Sequence();
        sequence.setName(name);
        sequence.setOwner(owner);
        sequence.setCurrentValue(BigInteger.ONE);
        sequence.setIncrementBy(BigInteger.ONE);
        sequence.setMinValue(BigInteger.ONE);
        sequence.setMaxValue(new BigInteger("10000000000000000000000000000000000000"));
        sequence.setNoMinValue(false);
        sequence.setNoMaxValue(false);
        sequence.setCacheSize(0);
        return sequence;
    }
}
