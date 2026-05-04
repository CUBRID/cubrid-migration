-- ============================================================
-- MAIN_SCHEMA: synonyms (per tibero/SEED_SPEC.md §4.3 — Oracle SPEC §4.3 reused).
--
-- The CREATE SYNONYM privilege was granted to MAIN_SCHEMA in
-- init/00_prepare_database.sql. The target REF_SCHEMA.e2e_ref_audit object
-- is created and granted to MAIN_SCHEMA in ref_schema/V1..V3.
-- ============================================================

CREATE OR REPLACE SYNONYM e2e_ref_audit_syn
FOR REF_SCHEMA.e2e_ref_audit;
