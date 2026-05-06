package com.cmt.e2e.framework.verify;

import java.util.Map;

/**
 * Canonical CUBRID catalog queries for {@link CatalogSnapshot}. Each
 * has an explicit {@code ORDER BY} for determinism, filters DBA/PUBLIC
 * owners and {@code flyway_%} (Flyway's history table is a seed-side
 * concern, not part of the migration contract). Map keys double as
 * snapshot file names ({@code snapshots/<scenario>/<key>.txt}).
 *
 * <p>Object kinds are split by category for diff clarity — tables /
 * views, pk / fk / unique / indexes, functions / procedures — so a
 * mismatch immediately tells you which category broke.
 */
public final class CatalogQueries {

    private CatalogQueries() {}

    private static final Map<String, String> QUERIES = Map.ofEntries(
        Map.entry("tables", """
            SELECT owner_name, class_name, comment
            FROM db_class
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
              AND class_type = 'CLASS'
            ORDER BY owner_name, class_name
            """),
        Map.entry("views", """
            SELECT owner_name, class_name, comment
            FROM db_class
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
              AND class_type = 'VCLASS'
            ORDER BY owner_name, class_name
            """),
        Map.entry("columns", """
            SELECT owner_name, class_name, attr_name, def_order,
                   data_type, prec, scale, is_nullable, default_value, comment
            FROM db_attribute
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
              AND class_name NOT LIKE 'flyway_%'
            ORDER BY owner_name, class_name, def_order
            """),
        Map.entry("pk", """
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
        Map.entry("fk", """
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
        Map.entry("unique", """
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
        Map.entry("indexes", """
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
        Map.entry("serials", """
            SELECT name, current_val, increment_val, min_val, max_val, cyclic
            FROM db_serial
            ORDER BY name
            """),
        Map.entry("synonyms", """
            SELECT synonym_owner_name, synonym_name,
                   target_owner_name, target_name
            FROM db_synonym
            WHERE synonym_owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY synonym_owner_name, synonym_name
            """),
        Map.entry("grants", """
            SELECT grantor_name, grantee_name, object_type, object_name,
                   owner_name, auth_type, is_grantable
            FROM db_auth
            WHERE owner_name NOT IN ('DBA', 'PUBLIC')
            ORDER BY grantor_name, grantee_name, owner_name, object_name, auth_type
            """),
        Map.entry("functions", """
            SELECT owner, sp_name, authid
            FROM db_stored_procedure
            WHERE owner NOT IN ('DBA', 'PUBLIC')
              AND sp_type = 'FUNCTION'
            ORDER BY owner, sp_name
            """),
        Map.entry("procedures", """
            SELECT owner, sp_name, authid
            FROM db_stored_procedure
            WHERE owner NOT IN ('DBA', 'PUBLIC')
              AND sp_type = 'PROCEDURE'
            ORDER BY owner, sp_name
            """)
    );

    public static String byName(String name) {
        String sql = QUERIES.get(name);
        if (sql == null) {
            throw new IllegalArgumentException(
                "Unknown catalog query: '" + name + "'. Known: " + QUERIES.keySet());
        }
        return sql;
    }

}
