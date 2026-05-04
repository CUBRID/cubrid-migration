-- ============================================================
-- REF_SCHEMA: cross-schema grant
-- (per tibero/SEED_SPEC.md §2 — Oracle SPEC §2 reused).
-- Runs after V1/V2 so the referenced object exists before grants apply.
-- ============================================================

GRANT SELECT, INSERT, UPDATE, DELETE
    ON e2e_ref_audit
    TO MAIN_SCHEMA;
