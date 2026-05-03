-- ============================================================
-- REF_SCHEMA: cross-schema grant
-- Per oracle/SEED_SPEC.md §2 (Schema Setup), executed after V1/V2 so that
-- the referenced object exists before grants are applied.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE
    ON e2e_ref_audit
    TO MAIN_SCHEMA;
