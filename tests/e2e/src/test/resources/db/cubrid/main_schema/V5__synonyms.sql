-- ============================================================
-- MAIN_SCHEMA: synonyms (CUBRID 11+)
-- Per docs/seed/cubrid/SEED_SPEC.md §4.3
-- The target REF_SCHEMA.e2e_ref_audit and the cross-schema GRANT exist after
-- ref_schema/V1..V3.
-- ============================================================

CREATE SYNONYM e2e_ref_audit_syn
FOR REF_SCHEMA.e2e_ref_audit;
