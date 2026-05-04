-- ============================================================
-- REF_SCHEMA: cross-schema reference object(s)
-- (per tibero/SEED_SPEC.md §3 — Oracle SPEC §3.5 reused).
-- Runs as REF_SCHEMA, so DDL is unqualified.
-- ============================================================

CREATE TABLE e2e_ref_audit (
    audit_id      NUMBER(10)    NOT NULL,
    audit_message VARCHAR2(200),
    created_at    DATE,
    CONSTRAINT pk_e2e_ref_audit PRIMARY KEY (audit_id)
);

COMMENT ON TABLE  e2e_ref_audit               IS 'Cross-schema reference audit table';
COMMENT ON COLUMN e2e_ref_audit.audit_id      IS 'Audit row identifier';
COMMENT ON COLUMN e2e_ref_audit.audit_message IS 'Audit message text';
