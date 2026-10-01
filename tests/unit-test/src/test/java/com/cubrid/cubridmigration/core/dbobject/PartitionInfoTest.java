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

import java.util.ArrayList;
import java.util.List;

@DisplayName("PartitionInfo")
class PartitionInfoTest {

    private static PartitionTable partition(String name, int index) {
        PartitionTable partition = new PartitionTable();
        partition.setPartitionName(name);
        partition.setPartitionIdx(index);
        return partition;
    }

    private static PartitionInfo hashPartitions() {
        PartitionInfo info = new PartitionInfo();
        info.setPartitionColumnCount(1);
        info.setPartitionCount(4);
        info.setDDL("partition by hash (f1) partitions(4) ");
        info.setPartitionExp("f1");
        info.setPartitionFunc("");
        info.setPartitionMethod(PartitionInfo.PARTITION_METHOD_HASH);
        Column column = new Column();
        column.setName("f1");
        column.setDataType("varchar(100)");
        List<Column> columns = new ArrayList<>();
        columns.add(column);
        info.setPartitionColumns(columns);
        List<PartitionTable> partitions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            partitions.add(partition("part_" + i, i));
        }
        info.setPartitions(partitions);
        for (int i = 0; i < 4; i++) {
            info.addSubPartition(partition("sub_part_" + i, i));
        }
        return info;
    }

    @Nested
    @DisplayName("addPartition()")
    class AddPartition {

        @Test
        @DisplayName("each partition gets its position from 0 and the count follows the list")
        void partition_getsItsPositionAndCount() {
            PartitionInfo info = new PartitionInfo();
            info.setPartitionCount(42);
            PartitionTable first = partition("p1", 7);
            PartitionTable second = partition("p2", 9);

            info.addPartition(first);
            info.addPartition(second);

            assertThat(first.getPartitionIdx()).isZero();
            assertThat(second.getPartitionIdx()).isEqualTo(1);
            assertThat(info.getPartitionCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("clone()")
    class Clone {

        @Test
        @DisplayName("the clone keeps the settings and gets its own partition and column lists")
        void clone_hasItsOwnLists() {
            PartitionInfo info = hashPartitions();

            PartitionInfo clone = (PartitionInfo) info.clone();

            assertThat(clone.getPartitionColumnCount()).isEqualTo(1);
            assertThat(clone.getPartitionCount()).isEqualTo(4);
            assertThat(clone.getDDL()).isEqualTo("partition by hash (f1) partitions(4) ");
            assertThat(clone.getPartitionExp()).isEqualTo("f1");
            assertThat(clone.getPartitionFunc()).isEmpty();
            assertThat(clone.getPartitionMethod()).isEqualTo(PartitionInfo.PARTITION_METHOD_HASH);
            assertThat(clone.getPartitions())
                    .isNotSameAs(info.getPartitions())
                    .containsExactlyElementsOf(info.getPartitions());
            assertThat(clone.getPartitionColumns()).isNotSameAs(info.getPartitionColumns());
            assertThat(clone.getSubPartitionColumns()).isNotSameAs(info.getSubPartitionColumns());
        }

        @Test
        @DisplayName("the clone shares its sub partition list with the original")
        void clone_sharesTheSubPartitions() {
            // DEFECT: the sub partitions are the only list not replaced, so adding a sub
            // partition to the clone adds it to the original as well
            // - see PartitionInfo.clone()
            PartitionInfo info = hashPartitions();

            PartitionInfo clone = (PartitionInfo) info.clone();
            clone.addSubPartition(partition("sub_part_4", 4));

            assertThat(clone.getSubPartitions()).isSameAs(info.getSubPartitions());
            assertThat(info.getSubPartitions()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("setPartitionExp()")
    class SetPartitionExp {

        @Test
        @DisplayName("the function is taken from the expression, replacing the one set before")
        void function_isTakenFromTheExpression() {
            PartitionInfo info = new PartitionInfo();
            info.setPartitionFunc("OLD");

            info.setPartitionExp("YEAR(d)");

            assertThat(info.getPartitionExp()).isEqualTo("YEAR(d)");
            assertThat(info.getPartitionFunc()).isEqualTo("YEAR");
        }

        @Test
        @DisplayName("an expression without a function, or null, leaves no function")
        void plainOrNullExpression_leavesNoFunction() {
            PartitionInfo info = new PartitionInfo();

            info.setPartitionExp("f1");
            assertThat(info.getPartitionFunc()).isNull();

            info.setPartitionExp(null);
            assertThat(info.getPartitionFunc()).isNull();
        }
    }
}
