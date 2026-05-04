-- =============================================================
-- Tibero two-user bootstrap (runs as SYS).
-- Mirrors db/oracle/init/00_prepare_database.sql — Tibero is
-- Oracle-syntax compatible for CREATE USER / GRANT.
--
-- Spec: tests/e2e/docs/seed/tibero/SEED_SPEC.md §2 (Schema Setup).
-- Object-level GRANTs live in ref_schema/V3__grants_to_main.sql,
-- after the referenced objects are actually created.
-- =============================================================

-- MAIN_SCHEMA: primary migration target. CREATE SYNONYM privilege is
-- needed by main_schema/V5__synonyms.sql.
CREATE USER MAIN_SCHEMA IDENTIFIED BY cmt;
GRANT CONNECT              TO MAIN_SCHEMA;
GRANT RESOURCE             TO MAIN_SCHEMA;
GRANT UNLIMITED TABLESPACE TO MAIN_SCHEMA;
GRANT CREATE TABLE         TO MAIN_SCHEMA;
GRANT CREATE SEQUENCE      TO MAIN_SCHEMA;
GRANT CREATE VIEW          TO MAIN_SCHEMA;
GRANT CREATE SYNONYM       TO MAIN_SCHEMA;
GRANT CREATE PROCEDURE     TO MAIN_SCHEMA;

-- REF_SCHEMA: cross-schema grant/synonym/reference target.
CREATE USER REF_SCHEMA IDENTIFIED BY cmt;
GRANT CONNECT              TO REF_SCHEMA;
GRANT RESOURCE             TO REF_SCHEMA;
GRANT UNLIMITED TABLESPACE TO REF_SCHEMA;
GRANT CREATE TABLE         TO REF_SCHEMA;
GRANT CREATE SEQUENCE      TO REF_SCHEMA;
GRANT CREATE VIEW          TO REF_SCHEMA;
GRANT CREATE SYNONYM       TO REF_SCHEMA;
GRANT CREATE PROCEDURE     TO REF_SCHEMA;
