-- ============================================================
-- REF_SCHEMA: reference data
-- Per oracle/SEED_SPEC.md §3.5 required row
-- ============================================================

INSERT INTO e2e_ref_audit (audit_id, audit_message, created_at)
VALUES (1, 'reference audit row', DATE '2024-04-20');

COMMIT;
