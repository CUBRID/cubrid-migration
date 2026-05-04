-- ============================================================
-- MAIN_SCHEMA: PL/SQL routines.
-- Per docs/seed/tibero/SEED_SPEC.md §4.4 (Oracle SPEC §4.4 정본 재사용).
-- Tibero 는 PL/SQL Oracle 호환이므로 본문 / 시그니처 모두 동일하다.
--
-- Statement separator: trailing "/" 라인. ClasspathSqlRunner 의 slash-mode
-- splitter 가 이를 인식한다 (한 파일에 여러 PL/SQL 블록이 안전하게 들어감).
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
