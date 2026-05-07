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

package com.cmt.e2e.framework.verify;

import java.util.Map;

/**
 * Canonical CUBRID catalog queries for {@link CatalogSnapshot}. Each has an explicit {@code ORDER
 * BY} for determinism, filters DBA/PUBLIC owners and {@code flyway_%} (Flyway's history table is a
 * seed-side concern, not part of the migration contract). Map keys double as snapshot file names
 * ({@code snapshots/<scenario>/<key>.txt}).
 *
 * <p>Object kinds are split by category for diff clarity — tables / views, pk / fk / unique /
 * indexes, functions / procedures — so a mismatch immediately tells you which category broke.
 */
public final class CatalogQueries {

    private CatalogQueries() {}

    private static final Map<String, String> QUERIES =
            Map.ofEntries(
                    Map.entry(
                            "tables",
                            """
                            SELECT owner_name, class_name, comment
                            FROM db_class
                            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
                              AND class_name NOT LIKE 'flyway_%'
                              AND class_type = 'CLASS'
                            ORDER BY owner_name, class_name
                            """),
                    Map.entry(
                            "views",
                            """
                            SELECT owner_name, class_name, comment
                            FROM db_class
                            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
                              AND class_name NOT LIKE 'flyway_%'
                              AND class_type = 'VCLASS'
                            ORDER BY owner_name, class_name
                            """),
                    Map.entry(
                            "columns",
                            """
                            SELECT owner_name, class_name, attr_name, def_order,
                                   data_type, prec, scale, is_nullable, default_value, comment
                            FROM db_attribute
                            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
                              AND class_name NOT LIKE 'flyway_%'
                            ORDER BY owner_name, class_name, def_order
                            """),
                    Map.entry(
                            "pk",
                            """
                            SELECT i.owner_name, i.class_name, i.index_name,
                                   k.key_attr_name, k.key_order, k.asc_desc
                            FROM db_index i
                            JOIN db_index_key k
                              ON k.owner_name = i.owner_name
                             AND k.class_name = i.class_name
                             AND k.index_name = i.index_name
                            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
                              AND i.class_name NOT LIKE 'flyway_%'
                              AND i.is_primary_key = 'YES'
                            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
                            """),
                    Map.entry(
                            "fk",
                            """
                            SELECT i.owner_name, i.class_name, i.index_name,
                                   k.key_attr_name, k.key_order, k.asc_desc
                            FROM db_index i
                            JOIN db_index_key k
                              ON k.owner_name = i.owner_name
                             AND k.class_name = i.class_name
                             AND k.index_name = i.index_name
                            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
                              AND i.class_name NOT LIKE 'flyway_%'
                              AND i.is_foreign_key = 'YES'
                            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
                            """),
                    Map.entry(
                            "unique",
                            """
                            SELECT i.owner_name, i.class_name, i.index_name,
                                   k.key_attr_name, k.key_order, k.asc_desc
                            FROM db_index i
                            JOIN db_index_key k
                              ON k.owner_name = i.owner_name
                             AND k.class_name = i.class_name
                             AND k.index_name = i.index_name
                            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
                              AND i.class_name NOT LIKE 'flyway_%'
                              AND i.is_unique = 'YES'
                              AND i.is_primary_key = 'NO'
                            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
                            """),
                    Map.entry(
                            "indexes",
                            """
                            SELECT i.owner_name, i.class_name, i.index_name,
                                   k.key_attr_name, k.key_order, k.asc_desc, k.func
                            FROM db_index i
                            JOIN db_index_key k
                              ON k.owner_name = i.owner_name
                             AND k.class_name = i.class_name
                             AND k.index_name = i.index_name
                            WHERE i.owner_name NOT IN ('DBA', 'PUBLIC')
                              AND i.class_name NOT LIKE 'flyway_%'
                              AND i.is_unique = 'NO'
                              AND i.is_foreign_key = 'NO'
                            ORDER BY i.owner_name, i.class_name, i.index_name, k.key_order
                            """),
                    Map.entry(
                            "sequences",
                            """
                            SELECT name, current_val, increment_val, min_val, max_val, cyclic
                            FROM db_serial
                            ORDER BY name
                            """),
                    Map.entry(
                            "synonyms",
                            """
                            SELECT synonym_owner_name, synonym_name,
                                   target_owner_name, target_name
                            FROM db_synonym
                            WHERE synonym_owner_name NOT IN ('DBA', 'PUBLIC')
                            ORDER BY synonym_owner_name, synonym_name
                            """),
                    Map.entry(
                            "grants",
                            """
                            SELECT grantor_name, grantee_name, object_type, object_name,
                                   owner_name, auth_type, is_grantable
                            FROM db_auth
                            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
                            ORDER BY grantor_name, grantee_name, owner_name, object_name, auth_type
                            """),
                    Map.entry(
                            "functions",
                            """
                            SELECT owner, sp_name, authid
                            FROM db_stored_procedure
                            WHERE owner NOT IN ('DBA', 'PUBLIC')
                              AND sp_type = 'FUNCTION'
                            ORDER BY owner, sp_name
                            """),
                    Map.entry(
                            "procedures",
                            """
                            SELECT owner, sp_name, authid
                            FROM db_stored_procedure
                            WHERE owner NOT IN ('DBA', 'PUBLIC')
                              AND sp_type = 'PROCEDURE'
                            ORDER BY owner, sp_name
                            """));

    public static String byName(String name) {
        String sql = QUERIES.get(name);
        if (sql == null) {
            throw new IllegalArgumentException(
                    "Unknown catalog query: '" + name + "'. Known: " + QUERIES.keySet());
        }
        return sql;
    }
}
