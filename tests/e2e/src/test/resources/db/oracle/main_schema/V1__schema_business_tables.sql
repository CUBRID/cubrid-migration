-- ============================================================
-- MAIN_SCHEMA: business graph tables, sequences, indexes, check constraints
-- Per oracle/SEED_SPEC.md §3.1 ~ §3.4 and §4.1
--
-- This Flyway location runs as the MAIN_SCHEMA Oracle user, so all object
-- creation is unqualified. View, synonym, and PL/SQL DDL live in their own
-- migration files (V2, V4, V5).
-- ============================================================

-- ====== Sequences (§4.1) ==========================================
-- START WITH 5 because §3.1/§3.2 required rows occupy ids 1..4.
CREATE SEQUENCE e2e_customer_seq START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE e2e_order_seq    START WITH 5 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ====== §3.1 e2e_customer ========================================
CREATE TABLE e2e_customer (
    customer_id    NUMBER(10)    NOT NULL,
    customer_code  CHAR(4)       NOT NULL,
    customer_name  VARCHAR2(100) NOT NULL,
    customer_alias NVARCHAR2(60),
    status         CHAR(1),
    credit_limit   NUMBER(15,2),
    created_on     DATE,
    updated_on     DATE,
    CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
);

-- ====== §3.2 e2e_order ===========================================
CREATE TABLE e2e_order (
    order_id       NUMBER(10)    NOT NULL,
    customer_id    NUMBER(10)    NOT NULL,
    order_no       VARCHAR2(30),
    order_status   VARCHAR2(20),
    total_amount   NUMBER(18,2),
    ordered_at     DATE,
    settled_at     DATE,
    source_comment VARCHAR2(200),
    CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES e2e_customer(customer_id),
    CONSTRAINT ck_e2e_order_status   CHECK       (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
);

CREATE INDEX idx_e2e_order_customer      ON e2e_order(customer_id);
CREATE INDEX idxd_e2e_order_ordered_at   ON e2e_order(ordered_at DESC);
CREATE INDEX idxf_e2e_order_upper_status ON e2e_order(UPPER(order_status));

-- ====== §3.3 e2e_order_line ======================================
CREATE TABLE e2e_order_line (
    order_id   NUMBER(10) NOT NULL,
    line_no    NUMBER(5)  NOT NULL,
    sku        VARCHAR2(30),
    qty        NUMBER(10,0),
    unit_price NUMBER(15,2),
    line_note  VARCHAR2(200),
    CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES e2e_order(order_id),
    CONSTRAINT ck_e2e_order_line_qty   CHECK       (qty > 0)
);

-- ====== §3.4 e2e_employee ========================================
CREATE TABLE e2e_employee (
    employee_id NUMBER(10)    NOT NULL,
    manager_id  NUMBER(10),
    emp_name    VARCHAR2(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee         PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id) REFERENCES e2e_employee(employee_id)
);

-- ====== Comments (per oracle/SEED_SPEC.md §4.5) ===================
-- e2e_customer
COMMENT ON TABLE  e2e_customer                IS 'E2E master customer table';
COMMENT ON COLUMN e2e_customer.customer_code  IS 'business key';
COMMENT ON COLUMN e2e_customer.customer_alias IS '고객 별칭 (다국어 검증)';

-- e2e_order
COMMENT ON TABLE  e2e_order                IS 'Customer order transactions';
COMMENT ON COLUMN e2e_order.source_comment IS '주문 비고: 다국어/특수문자 검증';

-- e2e_order_line
COMMENT ON TABLE  e2e_order_line IS 'Order line items (composite PK)';

-- e2e_employee
COMMENT ON TABLE  e2e_employee            IS 'Employee hierarchy (self-FK)';
COMMENT ON COLUMN e2e_employee.manager_id IS 'Self-FK to direct manager';
