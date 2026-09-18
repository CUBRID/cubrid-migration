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
package com.cubrid.cubridmigration.cubrid;

import static com.cubrid.cubridmigration.testutil.TestTableFactory.addPartition;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createIndex;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createPartitionInfo;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createSequence;
import static com.cubrid.cubridmigration.testutil.TestTableFactory.createTable;

import static org.assertj.core.api.Assertions.assertThat;

import com.cubrid.cubridmigration.core.dbobject.FK;
import com.cubrid.cubridmigration.core.dbobject.Index;
import com.cubrid.cubridmigration.core.dbobject.PartitionInfo;
import com.cubrid.cubridmigration.core.dbobject.Schema;
import com.cubrid.cubridmigration.core.dbobject.Sequence;
import com.cubrid.cubridmigration.core.dbobject.Table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Arrays;

@DisplayName("CUBRIDSQLHelper")
class CUBRIDSQLHelperTest {

    private static final CUBRIDSQLHelper HELPER = CUBRIDSQLHelper.getInstance(null);

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("singleton returns same instance regardless of version")
        void singleton_returnsSameInstance() {
            assertThat(CUBRIDSQLHelper.getInstance(null))
                    .isSameAs(CUBRIDSQLHelper.getInstance("11.4"));
        }
    }

    @Nested
    @DisplayName("getDBQualifier()")
    class GetDBQualifier {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("a name is bracketed but an expression is passed through")
        @CsvSource({
            "c1,              [c1],",
            "MY_COLUMN,       [MY_COLUMN],",

            // An index can be defined on an expression, which must not be bracketed.
            "lower(c1),       lower(c1),",
            "'substr(c1,1,2)', 'substr(c1,1,2)',",
        })
        void nameOrExpression_returnsQualifier(String objectName, String expected) {
            assertThat(HELPER.getDBQualifier(objectName)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getFKDDL()")
    class GetFKDDL {

        @Test
        @DisplayName("owner prefix is emitted for the table and the referenced table alike")
        void withUserSchema_prefixesBothTables() {
            assertThat(HELPER.getFKDDL("o1", "t1", fk(), true))
                    .isEqualTo(
                            "ALTER /*+ NO_STATS */ TABLE [o1].[t1] ADD CONSTRAINT [fk1] FOREIGN"
                                    + " KEY ([c1]) REFERENCES [o1].[t2]([r1]) ON DELETE CASCADE ON"
                                    + " UPDATE CASCADE");
        }

        @Test
        @DisplayName("without addUserSchema the owner is dropped, not the table name")
        void withoutUserSchema_omitsOwner() {
            assertThat(HELPER.getFKDDL("o1", "t1", fk(), false))
                    .isEqualTo(
                            "ALTER /*+ NO_STATS */ TABLE [t1] ADD CONSTRAINT [fk1] FOREIGN KEY"
                                    + " ([c1]) REFERENCES [t2]([r1]) ON DELETE CASCADE ON UPDATE"
                                    + " CASCADE");
        }

        private FK fk() {
            FK fk = new FK();
            fk.setName("fk1");
            fk.setReferencedTableName("t2");
            fk.addRefColumnName("c1", "r1");
            return fk;
        }
    }

    @Nested
    @DisplayName("getIndexDDL()")
    class GetIndexDDL {

        @Test
        @DisplayName("a plain index brackets the name, the table and every column")
        void plainIndex_returnsCreateIndex() {
            assertThat(HELPER.getIndexDDL(null, "t1", createIndex("idx1", "c1", "c2"), "", false))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [idx1] ON [t1]([c1],[c2])");
        }

        @Test
        @DisplayName("owner prefix is emitted only when addUserSchema is set")
        void withUserSchema_prefixesTheTable() {
            Index index = createIndex("idx1", "c1");

            assertThat(HELPER.getIndexDDL("o1", "t1", index, "", true))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [idx1] ON [o1].[t1]([c1])");
            assertThat(HELPER.getIndexDDL("o1", "t1", index, "", false))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [idx1] ON [t1]([c1])");
        }

        @Test
        @DisplayName("a descending column carries DESC, an ascending one carries nothing")
        void descendingColumn_appendsDesc() {
            assertThat(HELPER.getIndexDDL(null, "t1", createIndex("idx1", "c1", false), "", false))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [idx1] ON [t1]([c1] DESC)");
        }

        @Test
        @DisplayName("the prefix is glued in front of the index name inside the brackets")
        void namePrefix_isPartOfTheQuotedName() {
            assertThat(HELPER.getIndexDDL(null, "t1", createIndex("idx1", "c1"), "pre_", false))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [pre_idx1] ON [t1]([c1])");
        }

        @Test
        @DisplayName("a comment is appended as a quoted COMMENT clause")
        void indexWithComment_appendsComment() {
            Index index = createIndex("idx1", "c1");
            index.setComment("hello");

            assertThat(HELPER.getIndexDDL(null, "t1", index, "", false))
                    .isEqualTo("CREATE /*+ NO_STATS */ INDEX [idx1] ON [t1]([c1]) COMMENT 'hello'");
        }
    }

    @Nested
    @DisplayName("getPKDDL()")
    class GetPKDDL {

        @Test
        @DisplayName("a named PK becomes an ALTER TABLE ADD CONSTRAINT")
        void namedPK_returnsAddConstraint() {
            assertThat(HELPER.getPKDDL("o1", "t1", "pk1", Arrays.asList("c1", "c2"), true))
                    .isEqualTo(
                            "ALTER /*+ NO_STATS */ TABLE [o1].[t1] ADD CONSTRAINT [pk1] PRIMARY"
                                    + " KEY([c1],[c2])");
        }

        @ParameterizedTest(name = "[{index}] pk name \"{0}\" -> no CONSTRAINT clause")
        @DisplayName("a blank PK name leaves the constraint unnamed")
        @CsvSource(
                nullValues = "null",
                value = {"null", "''", "'   '"})
        void blankPKName_omitsConstraintClause(String pkName) {
            assertThat(HELPER.getPKDDL(null, "t1", pkName, Arrays.asList("c1"), false))
                    .isEqualTo("ALTER /*+ NO_STATS */ TABLE [t1] ADD PRIMARY KEY([c1])");
        }
    }

    @Nested
    @DisplayName("getSequenceDDL()")
    class GetSequenceDDL {

        @Test
        @DisplayName("a serial carries its owner, bounds, cycle and cache state")
        void sequence_returnsCreateSerial() {
            assertThat(HELPER.getSequenceDDL(createSequence("seq1", "o1"), true))
                    .isEqualTo(
                            "CREATE SERIAL [o1].[seq1] START WITH 1 INCREMENT BY 1 MINVALUE 1"
                                    + " MAXVALUE  10000000000000000000000000000000000000 NOCYCLE"
                                    + " NOCACHE");
        }

        @Test
        @DisplayName("NOMINVALUE and NOMAXVALUE replace the bounds when they are unset")
        void unboundedSequence_returnsNoMinAndNoMaxValue() {
            Sequence sequence = createSequence("seq1", "o1");
            sequence.setNoMinValue(true);
            sequence.setNoMaxValue(true);

            assertThat(HELPER.getSequenceDDL(sequence, false))
                    .isEqualTo(
                            "CREATE SERIAL [seq1] START WITH 1 INCREMENT BY 1 NOMINVALUE "
                                    + " NOMAXVALUE  NOCYCLE NOCACHE");
        }

        @Test
        @DisplayName("a bound set without clearing its no-value flag never reaches the DDL")
        void boundWithoutClearingItsFlag_staysUnbounded() {
            // Sequence starts with isNoMinValue/isNoMaxValue true and setMinValue/setMaxValue
            // leave them alone, so a fetcher that sets only the values emits no bounds.
            Sequence sequence = new Sequence();
            sequence.setName("seq1");
            sequence.setCurrentValue(BigInteger.ONE);
            sequence.setIncrementBy(BigInteger.ONE);
            sequence.setMinValue(BigInteger.ONE);
            sequence.setMaxValue(BigInteger.TEN);

            assertThat(HELPER.getSequenceDDL(sequence, false))
                    .contains(" NOMINVALUE ")
                    .contains(" NOMAXVALUE ")
                    .doesNotContain("MINVALUE 1")
                    .doesNotContain("MAXVALUE  10");
        }

        @Test
        @DisplayName("a cycling serial with a cache reports both")
        void cyclingSequence_returnsCycleAndCache() {
            Sequence sequence = createSequence("seq1", "o1");
            sequence.setCycleFlag(true);
            sequence.setCacheSize(10);
            sequence.setCurrentValue(new BigInteger("5"));

            assertThat(HELPER.getSequenceDDL(sequence, false))
                    .contains(" START WITH 5 ")
                    .endsWith(" CYCLE CACHE 10");
        }

        @Test
        @DisplayName("unbounded and cycling with no cache renders every clause at once")
        void unboundedCyclingNoCache_returnsCreateSerial() {
            Sequence sequence = new Sequence();
            sequence.setName("test_sequence");
            sequence.setCurrentValue(BigInteger.ZERO);
            sequence.setIncrementBy(BigInteger.ONE);
            sequence.setCycleFlag(true);
            sequence.setCacheSize(0);

            assertThat(HELPER.getSequenceDDL(sequence, false))
                    .isEqualTo(
                            "CREATE SERIAL [test_sequence] START WITH 0 INCREMENT BY 1 NOMINVALUE "
                                    + " NOMAXVALUE  CYCLE NOCACHE");
        }

        @Test
        @DisplayName("bounded and non cycling with a cache renders every clause at once")
        void boundedNonCyclingWithCache_returnsCreateSerial() {
            Sequence sequence = new Sequence();
            sequence.setName("test_sequence");
            sequence.setCurrentValue(BigInteger.ZERO);
            sequence.setIncrementBy(BigInteger.ONE);
            sequence.setNoMinValue(false);
            sequence.setNoMaxValue(false);
            sequence.setMinValue(BigInteger.ZERO);
            sequence.setMaxValue(new BigInteger("1000"));
            sequence.setCycleFlag(false);
            sequence.setCacheSize(100);

            assertThat(HELPER.getSequenceDDL(sequence, false))
                    .isEqualTo(
                            "CREATE SERIAL [test_sequence] START WITH 0 INCREMENT BY 1 MINVALUE 0"
                                    + " MAXVALUE  1000 NOCYCLE CACHE 100");
        }

        @Test
        @DisplayName("a comment is appended as a quoted COMMENT clause")
        void sequenceWithComment_appendsComment() {
            Sequence sequence = createSequence("seq1", "o1");
            sequence.setComment("hello");

            assertThat(HELPER.getSequenceDDL(sequence, false)).endsWith(" NOCACHE COMMENT 'hello'");
        }

        @Test
        @DisplayName("null sequence -> empty string")
        void nullSequence_returnsEmptyString() {
            assertThat(HELPER.getSequenceDDL(null, true)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getTablePartitonDDL()")
    class GetTablePartitonDDL {

        @Test
        @DisplayName("RANGE lists every bound, MAXVALUE without parentheses")
        void rangePartition_returnsValuesLessThan() {
            PartitionInfo info = createPartitionInfo(PartitionInfo.PARTITION_METHOD_RANGE, "c1");
            addPartition(info, "p1", "100");
            addPartition(info, "p2", "MAXVALUE");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .isEqualTo(
                            "PARTITION BY RANGE (\"c1\")(\n"
                                    + "PARTITION \"p1\" VALUES LESS THAN (100),\n"
                                    + "PARTITION \"p2\" VALUES LESS THAN MAXVALUE\n"
                                    + ")");
        }

        @Test
        @DisplayName("LIST splits the description on commas and leaves the name unquoted")
        void listPartition_returnsValuesIn() {
            PartitionInfo info = createPartitionInfo(PartitionInfo.PARTITION_METHOD_LIST, "c1");
            addPartition(info, "p1", "'A','B'");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .isEqualTo(
                            "PARTITION BY LIST (\"c1\") ( \n"
                                    + "PARTITION p1 VALUES IN ('A','B')\n"
                                    + " ) ");
        }

        @Test
        @DisplayName("HASH reports the partition count instead of the bounds")
        void hashPartition_returnsPartitionCount() {
            PartitionInfo info = createPartitionInfo(PartitionInfo.PARTITION_METHOD_HASH, "c1");
            addPartition(info, "p1", "");
            addPartition(info, "p2", "");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .isEqualTo("PARTITION BY HASH (\"c1\") \nPARTITIONS 2");
        }

        @Test
        @DisplayName("LINEAR HASH is emitted as plain HASH")
        void linearHashPartition_returnsHash() {
            PartitionInfo info =
                    createPartitionInfo(PartitionInfo.PARTITION_METHOD_LINEARHASH, "c1");
            addPartition(info, "p1", "");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .startsWith("PARTITION BY HASH ");
        }

        @Test
        @DisplayName("a partition function replaces the quoted column with an expression")
        void partitionFunction_returnsExpression() {
            PartitionInfo info = createPartitionInfo(PartitionInfo.PARTITION_METHOD_RANGE, "c1");
            info.setPartitionFunc("ABS");
            addPartition(info, "p1", "100");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .startsWith("PARTITION BY RANGE (ABS(c1))(");
        }

        @ParameterizedTest(name = "[{index}] {0} -> the source DDL verbatim")
        @DisplayName("KEY and LINEAR KEY are handed back as the source wrote them")
        @ValueSource(strings = {"KEY", "LINEAR KEY"})
        void keyPartition_returnsSourceDDL(String method) {
            PartitionInfo info = createPartitionInfo(method, "c1");
            info.setDDL("PARTITION BY KEY(c1) PARTITIONS 4");

            assertThat(HELPER.getTablePartitonDDL(tableWith(info)))
                    .isEqualTo("PARTITION BY KEY(c1) PARTITIONS 4");
        }

        @Test
        @DisplayName("a table without partition info -> null")
        void tableWithoutPartitionInfo_returnsNull() {
            assertThat(HELPER.getTablePartitonDDL(createTable("t1", "c1"))).isNull();
        }

        private Table tableWith(PartitionInfo info) {
            Table table = createTable("t1", "c1");
            table.setPartitionInfo(info);
            return table;
        }
    }

    @Nested
    @DisplayName("getQuotedObjName()")
    class GetQuotedObjName {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
        @DisplayName("every name is bracketed verbatim, expressions included")
        @CsvSource({
            "t1,          [t1],",
            "MiXeD,       [MiXeD],",
            "'',          [],",
            "lower(c1),   [lower(c1)],",
        })
        void objectName_returnsBracketedName(String objectName, String expected) {
            assertThat(HELPER.getQuotedObjName(objectName)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("getTestSelectSQL()")
    class GetTestSelectSQL {

        @Test
        @DisplayName("the statement is wrapped in a never-true subquery")
        void selectSQL_isWrappedInAlwaysFalseFilter() {
            assertThat(HELPER.getTestSelectSQL("select * from t1"))
                    .isEqualTo("SELECT * FROM ( select * from t1 ) WHERE 1<>1");
        }
    }

    @Nested
    @DisplayName("getSchemaDDL()")
    class GetSchemaDDL {

        @Test
        @DisplayName("the target schema name is emitted unquoted")
        void schema_returnsCreateUser() {
            Schema schema = new Schema();
            schema.setTargetSchemaName("o1");

            assertThat(HELPER.getSchemaDDL(schema)).isEqualTo("CREATE USER o1");
        }
    }

    @Nested
    @DisplayName("getOwnerNameWithDot()")
    class GetOwnerNameWithDot {

        @ParameterizedTest(name = "[{index}] (\"{0}\", {1}) -> \"{2}\"")
        @DisplayName("the flag alone decides, the owner value is never inspected")
        @CsvSource(
                nullValues = "null",
                value = {
                    "o1,    true,   [o1].",
                    "o1,    false,  ''",
                    "'',    true,   [].",
                    "null,  true,   [null].",
                    "null,  false,  ''",
                })
        void ownerAndFlag_returnsPrefix(String owner, boolean addUserSchema, String expected) {
            assertThat(HELPER.getOwnerNameWithDot(owner, addUserSchema)).isEqualTo(expected);
        }
    }
}
