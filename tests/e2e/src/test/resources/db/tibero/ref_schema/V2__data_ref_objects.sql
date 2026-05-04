-- ============================================================
-- REF_SCHEMA: reference data (one row, mirrors Oracle SPEC §3.5).
-- ============================================================

INSERT INTO e2e_ref_audit (audit_id, audit_message, created_at)
VALUES (1, 'reference audit row', DATE '2024-04-20');
