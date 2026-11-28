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
package com.cubrid.cubridmigration.core.engine.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * {@code owner} is {@code null} for every table whenever the source doesn't support per-user
 * schemas (see {@code DatabaseType#isSupportMultiSchema} - e.g. CUBRID before 11.2, per {@code
 * MigrationConfiguration#resetSchemaInfo}, which explicitly nulls it out in that case), so this
 * pins that {@link ITableKeyedTask#tableKey} degrades to a bare table name instead of the literal
 * string {@code "null.NAME"} in that case - the same guard {@code
 * TableSplitPlanner#qualifiedTableName} already applies to the same "owner.name" shape.
 */
@DisplayName("ITableKeyedTask")
class ITableKeyedTaskTest {

    @Test
    @DisplayName("tableKey() joins a non-blank owner and name with a dot")
    void tableKey_joinsOwnerAndName() {
        assertThat(ITableKeyedTask.tableKey("OWNER", "TABLE")).isEqualTo("OWNER.TABLE");
    }

    @ParameterizedTest(name = "[{index}] owner={0} -> \"TABLE\"")
    @DisplayName(
            "a null or empty owner (a source with no multi-schema support, e.g. CUBRID before"
                    + " 11.2) -> bare table name, not \"null.TABLE\"")
    @NullAndEmptySource
    void tableKey_nullOrEmptyOwner_returnsBareName(String owner) {
        assertThat(ITableKeyedTask.tableKey(owner, "TABLE")).isEqualTo("TABLE");
    }
}
