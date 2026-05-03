-- ============================================================
-- REF_SCHEMA: cross-schema grant
-- Per docs/seed/cubrid/SEED_SPEC.md §2 (Schema Setup).
-- Runs after V1/V2 so that the granted object exists.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE
    ON e2e_ref_audit
    TO MAIN_SCHEMA;
