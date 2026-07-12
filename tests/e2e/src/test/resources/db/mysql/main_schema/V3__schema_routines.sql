-- ============================================================
-- MAIN_SCHEMA (MySQL): stored routines. Mirrors Oracle's one-function +
-- one-procedure pair. Bodies stay simple — the point is exercising CMT's
-- routine extraction path, not realistic logic.
-- ============================================================

CREATE FUNCTION e2e_customer_label_fn(p_customer_id INT)
    RETURNS VARCHAR(200)
    DETERMINISTIC
    READS SQL DATA
BEGIN
    DECLARE v_label VARCHAR(200);
    SELECT CONCAT(customer_code, ':', customer_name)
      INTO v_label
      FROM e2e_customer
     WHERE customer_id = p_customer_id;
    RETURN v_label;
END;

CREATE PROCEDURE e2e_upsert_customer_proc(
    IN p_customer_id   INT,
    IN p_customer_name VARCHAR(100)
)
BEGIN
    UPDATE e2e_customer
       SET customer_name = p_customer_name
     WHERE customer_id = p_customer_id;
END;
