-- ============================================================
-- REF_SCHEMA: cross-schema reference object(s)
-- Per docs/seed/cubrid/SEED_SPEC.md §3.5
-- Executed as REF_SCHEMA. CUBRID 의 user = schema 모델이라 unqualified 식별자.
-- ============================================================

CREATE TABLE e2e_ref_audit (
    audit_id      INT           NOT NULL,
    audit_message VARCHAR(200),
    created_at    DATETIME,
    CONSTRAINT pk_e2e_ref_audit PRIMARY KEY (audit_id)
);
