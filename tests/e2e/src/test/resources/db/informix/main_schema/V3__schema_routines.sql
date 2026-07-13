-- ============================================================
-- e2e_db (Informix): stored routines (SPL). One function + one procedure,
-- mirroring the other engines. Slash-mode ('/' on its own line ends a
-- statement) keeps the internal ';' inside the routine bodies intact.
-- ============================================================

CREATE FUNCTION e2e_customer_label_fn(p_customer_id INTEGER) RETURNING VARCHAR(200);
    DEFINE v_label VARCHAR(200);
    SELECT customer_code || ':' || customer_name INTO v_label
        FROM e2e_customer WHERE customer_id = p_customer_id;
    RETURN v_label;
END FUNCTION;
/

CREATE PROCEDURE e2e_upsert_customer_proc(p_customer_id INTEGER, p_customer_name VARCHAR(100));
    UPDATE e2e_customer SET customer_name = p_customer_name WHERE customer_id = p_customer_id;
END PROCEDURE;
/
