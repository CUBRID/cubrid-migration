-- ============================================================
-- MAIN_SCHEMA (MySQL): business graph tables, indexes, check constraints.
--
-- Mirrors the Oracle business dataset in MySQL 8.0 dialect. MySQL has no
-- sequences, so ids are inserted explicitly (see V99). utf8mb4 covers the
-- multilingual columns Oracle modelled with NVARCHAR2. CHECK constraints are
-- enforced from MySQL 8.0.16+.
-- ============================================================

CREATE TABLE e2e_customer (
    customer_id    INT           NOT NULL,
    customer_code  CHAR(4)       NOT NULL COMMENT 'business key',
    customer_name  VARCHAR(100)  NOT NULL,
    customer_alias VARCHAR(60)            COMMENT '고객 별칭 (다국어 검증)',
    status         CHAR(1),
    credit_limit   DECIMAL(15, 2),
    created_on     DATE,
    updated_on     DATE,
    CONSTRAINT pk_e2e_customer        PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code   UNIQUE      (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK       (status IN ('A', 'I', 'D'))
) COMMENT = 'E2E master customer table';

CREATE TABLE e2e_order (
    order_id       INT           NOT NULL,
    customer_id    INT           NOT NULL,
    order_no       VARCHAR(30),
    order_status   VARCHAR(20),
    total_amount   DECIMAL(18, 2),
    ordered_at     DATE,
    settled_at     DATE,
    source_comment VARCHAR(200)           COMMENT '주문 비고: 다국어/특수문자 검증',
    CONSTRAINT pk_e2e_order          PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES e2e_customer (customer_id),
    CONSTRAINT ck_e2e_order_status   CHECK       (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
) COMMENT = 'Customer order transactions';

CREATE INDEX idx_e2e_order_customer    ON e2e_order (customer_id);
CREATE INDEX idxd_e2e_order_ordered_at ON e2e_order (ordered_at DESC);
CREATE INDEX idxf_e2e_order_status     ON e2e_order ((UPPER(order_status)));

CREATE TABLE e2e_order_line (
    order_id   INT NOT NULL,
    line_no    INT NOT NULL,
    sku        VARCHAR(30),
    qty        INT,
    unit_price DECIMAL(15, 2),
    line_note  VARCHAR(200),
    CONSTRAINT pk_e2e_order_line       PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES e2e_order (order_id),
    CONSTRAINT ck_e2e_order_line_qty   CHECK       (qty > 0)
) COMMENT = 'Order line items (composite PK)';

CREATE TABLE e2e_employee (
    employee_id INT          NOT NULL,
    manager_id  INT                   COMMENT 'Self-FK to direct manager',
    emp_name    VARCHAR(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee         PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id) REFERENCES e2e_employee (employee_id)
) COMMENT = 'Employee hierarchy (self-FK)';
