-- =============================================================
-- Oracle two-user container initialization script
-- Execution context: runs automatically as SYSDBA when gvenzl/oracle-xe starts
--                    (/container-entrypoint-initdb.d/, alphabetically first
--                    so any later init scripts can rely on the users existing).
-- Purpose          : create the REF_SCHEMA user with the privileges the
--                    Flyway ref_schema/ migrations need, and grant MAIN_SCHEMA
--                    the right to create synonyms that point at REF_SCHEMA
--                    objects. MAIN_SCHEMA itself is created automatically by
--                    the gvenzl APP_USER mechanism.
-- Related spec     : tests/e2e/docs/seed/oracle/SEED_SPEC.md §2 (Schema Setup)
-- Note             : object-level GRANTs live in
--                    ref_schema/V3__grants_to_main.sql, after the referenced
--                    objects are actually created.
-- =============================================================

-- REF_SCHEMA: cross-schema grant/synonym/reference target.
CREATE USER REF_SCHEMA IDENTIFIED BY cmt;
GRANT CONNECT                     TO REF_SCHEMA;
GRANT RESOURCE                    TO REF_SCHEMA;
GRANT UNLIMITED TABLESPACE        TO REF_SCHEMA;
GRANT CREATE TABLE                TO REF_SCHEMA;
GRANT CREATE SEQUENCE             TO REF_SCHEMA;
GRANT CREATE VIEW                 TO REF_SCHEMA;
GRANT CREATE SYNONYM              TO REF_SCHEMA;
GRANT CREATE PROCEDURE            TO REF_SCHEMA;

-- Allow MAIN_SCHEMA (gvenzl APP_USER) to create private synonyms that point
-- to REF_SCHEMA objects. Required by main_schema/V5__synonyms.sql.
GRANT CREATE SYNONYM              TO MAIN_SCHEMA;
