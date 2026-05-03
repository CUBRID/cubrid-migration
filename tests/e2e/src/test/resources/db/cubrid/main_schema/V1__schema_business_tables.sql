-- ============================================================
-- MAIN_SCHEMA: business graph tables, serials, indexes, check constraints
-- Per docs/seed/cubrid/SEED_SPEC.md §3.1 ~ §3.4 and §4.1
-- Executed as MAIN_SCHEMA. View, synonyms, type-test tables, extensions live in
-- their own files (V2, V3, V4, V5).
-- ============================================================

-- ====== Serials (§4.1) =============================================
-- START WITH 5: §3.1/§3.2 의 required row id 가 1..4 이므로 다음 발급 값.
CREATE SERIAL e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SERIAL e2e_order_seq    START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ====== §3.1 e2e_customer ===========================================
-- CUBRID comment: inline `COMMENT 'text'` on table and column (no
-- standalone COMMENT ON ... statement).
CREATE TABLE e2e_customer (
    customer_id    INT           NOT NULL,
    customer_code  CHAR(4)       NOT NULL COMMENT 'business key',
    customer_name  VARCHAR(100)  NOT NULL,
    customer_alias VARCHAR(60),
    status         CHAR(1),
    credit_limit   NUMERIC(15, 2),
    created_on     DATETIME,
    updated_on     DATETIME,
    CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
) COMMENT 'E2E master customer table';

-- ====== §3.2 e2e_order ==============================================
CREATE TABLE e2e_order (
    order_id       INT           NOT NULL,
    customer_id    INT           NOT NULL,
    order_no       VARCHAR(30),
    order_status   VARCHAR(20),
    total_amount   NUMERIC(18, 2),
    ordered_at     DATETIME,
    settled_at     DATETIME,
    source_comment VARCHAR(200),
    CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES e2e_customer(customer_id),
    CONSTRAINT ck_e2e_order_status   CHECK       (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
);

CREATE INDEX         idx_e2e_order_customer      ON e2e_order(customer_id);
CREATE INDEX         idxd_e2e_order_ordered_at   ON e2e_order(ordered_at DESC);
CREATE INDEX         idxf_e2e_order_upper_status ON e2e_order(UPPER(order_status));
CREATE REVERSE INDEX idxr_e2e_order_no           ON e2e_order(order_no);

-- ====== §3.3 e2e_order_line =========================================
CREATE TABLE e2e_order_line (
    order_id   INT      NOT NULL,
    line_no    SMALLINT NOT NULL,
    sku        VARCHAR(30),
    qty        INT,
    unit_price NUMERIC(15, 2),
    line_note  VARCHAR(200),
    CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES e2e_order(order_id),
    CONSTRAINT ck_e2e_order_line_qty   CHECK       (qty > 0)
);

-- ====== §3.4 e2e_employee ===========================================
CREATE TABLE e2e_employee (
    employee_id INT          NOT NULL,
    manager_id  INT,
    emp_name    VARCHAR(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee         PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id) REFERENCES e2e_employee(employee_id)
);
