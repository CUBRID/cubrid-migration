-- ============================================================
-- MAIN_SCHEMA: PL/SQL routines
-- Per oracle/SEED_SPEC.md §4.4
--
-- Spec calls for one function and one procedure only. Bodies stay
-- intentionally simple — what we are exercising is the CMT routine
-- extraction path, not realistic PL/SQL logic.
--
-- Flyway's default Oracle parser handles `BEGIN ... END;` blocks and
-- the trailing `/` line as a statement terminator.
-- ============================================================

CREATE OR REPLACE FUNCTION e2e_customer_label_fn(p_customer_id IN NUMBER)
    RETURN VARCHAR2
AS
    v_label VARCHAR2(200);
BEGIN
    SELECT customer_code || ':' || customer_name
      INTO v_label
      FROM e2e_customer
     WHERE customer_id = p_customer_id;
    RETURN v_label;
END;
/

CREATE OR REPLACE PROCEDURE e2e_upsert_customer_proc(
    p_customer_id   IN NUMBER,
    p_customer_name IN VARCHAR2
)
AS
BEGIN
    UPDATE e2e_customer
       SET customer_name = p_customer_name
     WHERE customer_id = p_customer_id;
END;
/
