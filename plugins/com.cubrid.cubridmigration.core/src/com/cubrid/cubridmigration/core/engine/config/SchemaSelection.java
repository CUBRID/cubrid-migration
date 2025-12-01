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
package com.cubrid.cubridmigration.core.engine.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable set-like value object for selected schema names. */
public final class SchemaSelection {

    private static final SchemaSelection EMPTY =
            new SchemaSelection(Collections.<String>emptySet());

    private final Set<String> schemaNames;

    private SchemaSelection(Set<String> schemaNames) {
        this.schemaNames = schemaNames;
    }

    public static SchemaSelection empty() {
        return EMPTY;
    }

    public static SchemaSelection of(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        LinkedHashSet<String> tmp = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                tmp.add(trimmed);
            }
        }
        if (tmp.isEmpty()) {
            return EMPTY;
        }
        return new SchemaSelection(Collections.unmodifiableSet(tmp));
    }

    public boolean isEmpty() {
        return schemaNames.isEmpty();
    }

    public boolean contains(String schemaName) {
        if (schemaName == null) {
            return false;
        }
        return schemaNames.contains(schemaName.trim());
    }

    public Set<String> asSet() {
        return schemaNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SchemaSelection)) {
            return false;
        }
        SchemaSelection that = (SchemaSelection) o;
        return Objects.equals(schemaNames, that.schemaNames);
    }

    @Override
    public int hashCode() {
        return schemaNames.hashCode();
    }

    @Override
    public String toString() {
        return "SchemaSelection" + schemaNames;
    }
}
