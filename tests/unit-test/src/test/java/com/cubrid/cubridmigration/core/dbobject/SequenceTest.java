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

import java.lang.reflect.Field;
import java.math.BigInteger;

@DisplayName("Sequence")
class SequenceTest {

    private static Sequence sequence() {
        return new Sequence(
                "seq",
                BigInteger.TEN,
                BigInteger.valueOf(100),
                BigInteger.valueOf(2),
                BigInteger.valueOf(5),
                true,
                7);
    }

    @Nested
    @DisplayName("clone()")
    class Clone {

        @Test
        @DisplayName("the constructor values, cache and bound flags, DDL and owner are copied")
        void definition_isCopied() {
            Sequence sequence = sequence();
            sequence.setNoCache(false);
            sequence.setNoMinValue(false);
            sequence.setNoMaxValue(false);
            sequence.setDDL("ddl");
            sequence.setOwner("owner");

            Sequence clone = (Sequence) sequence.clone();

            assertThat(clone).isNotSameAs(sequence);
            assertThat(clone.getName()).isEqualTo("seq");
            assertThat(clone.getMinValue()).isEqualTo(BigInteger.TEN);
            assertThat(clone.getMaxValue()).isEqualTo(BigInteger.valueOf(100));
            assertThat(clone.getIncrementBy()).isEqualTo(BigInteger.valueOf(2));
            assertThat(clone.getCurrentValue()).isEqualTo(BigInteger.valueOf(5));
            assertThat(clone.isCycleFlag()).isTrue();
            assertThat(clone.getCacheSize()).isEqualTo(7);
            assertThat(clone.isNoCache()).isFalse();
            assertThat(clone.isNoMinValue()).isFalse();
            assertThat(clone.isNoMaxValue()).isFalse();
            assertThat(clone.getDDL()).isEqualTo("ddl");
            assertThat(clone.getOwner()).isEqualTo("owner");
        }

        @Test
        @DisplayName("an unset bound stays unset in the clone instead of taking the default")
        void unsetBound_staysUnset() throws Exception {
            Sequence sequence = new Sequence();
            sequence.setName("seq");

            Sequence clone = (Sequence) sequence.clone();

            Field maxValue = Sequence.class.getDeclaredField("maxValue");
            maxValue.setAccessible(true);
            assertThat(maxValue.get(clone)).isNull();
        }

        @Test
        @DisplayName("the source and target owners are left out")
        void owners_areLeftOut() {
            Sequence sequence = sequence();
            sequence.setSourceOwner("source");
            sequence.setTargetOwner("target");

            Sequence clone = (Sequence) sequence.clone();

            assertThat(clone.getSourceOwner()).isNull();
            assertThat(clone.getTargetOwner()).isNull();
        }

        @Test
        @DisplayName("the comment is left out")
        void comment_isLeftOut() {
            // DEFECT: the comment is not copied, so the DDL MigrationConfiguration builds from the
            // clone before it sets the comment again has no COMMENT
            // - see Sequence.clone()
            Sequence sequence = sequence();
            sequence.setComment("note");

            Sequence clone = (Sequence) sequence.clone();

            assertThat(clone.getComment()).isNull();
        }
    }

    @Nested
    @DisplayName("getCurrentValue()")
    class GetCurrentValue {

        @Test
        @DisplayName("an unset value reads as a new 1 each time, and a set one as itself")
        void unsetValue_readsAsOne() {
            Sequence sequence = new Sequence();

            assertThat(sequence.getCurrentValue()).isEqualTo(BigInteger.ONE);
            assertThat(sequence.getCurrentValue()).isNotSameAs(sequence.getCurrentValue());
            assertThat(sequence().getCurrentValue()).isEqualTo(BigInteger.valueOf(5));
        }
    }

    @Nested
    @DisplayName("getIncrementBy()")
    class GetIncrementBy {

        @Test
        @DisplayName("an unset step reads as a new 1 each time, and a set one as itself")
        void unsetStep_readsAsOne() {
            Sequence sequence = new Sequence();

            assertThat(sequence.getIncrementBy()).isEqualTo(BigInteger.ONE);
            assertThat(sequence.getIncrementBy()).isNotSameAs(sequence.getIncrementBy());
            assertThat(sequence().getIncrementBy()).isEqualTo(BigInteger.valueOf(2));
        }
    }

    @Nested
    @DisplayName("getMaxValue()")
    class GetMaxValue {

        @Test
        @DisplayName("a set maximum reads as itself")
        void setMaximum_readsAsItself() {
            assertThat(sequence().getMaxValue()).isEqualTo(BigInteger.valueOf(100));
        }

        @Test
        @DisplayName("an unset maximum reads as 10^36")
        void unsetMaximum_readsAsTenToThe36() {
            // DEFECT: the CUBRID manual sets 10^37 for NOMAXVALUE on an ascending serial, so the
            // default looks swapped with the one of getMinValue()
            // - see Sequence.getMaxValue()
            assertThat(new Sequence().getMaxValue())
                    .isEqualTo(new BigInteger("1000000000000000000000000000000000000"))
                    .isEqualTo(BigInteger.TEN.pow(36));
        }
    }

    @Nested
    @DisplayName("getMinValue()")
    class GetMinValue {

        @Test
        @DisplayName("a set minimum reads as itself")
        void setMinimum_readsAsItself() {
            assertThat(sequence().getMinValue()).isEqualTo(BigInteger.TEN);
        }

        @Test
        @DisplayName("an unset minimum reads as -10^37")
        void unsetMinimum_readsAsMinusTenToThe37() {
            // DEFECT: the CUBRID manual sets -10^36 for NOMINVALUE on a descending serial, so the
            // default looks swapped with the one of getMaxValue()
            // - see Sequence.getMinValue()
            assertThat(new Sequence().getMinValue())
                    .isEqualTo(new BigInteger("-10000000000000000000000000000000000000"))
                    .isEqualTo(BigInteger.TEN.pow(37).negate());
        }
    }
}
