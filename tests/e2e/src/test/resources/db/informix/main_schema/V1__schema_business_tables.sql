-- ============================================================
-- e2e_db (Informix): business graph tables, indexes, check constraints.
--
-- Informix dialect: constraint names are a postfix (... CONSTRAINT name),
-- ids are inserted explicitly (no SERIAL), and Informix has no COMMENT ON
-- syntax so table/column comments are omitted. ASCII data only (the dev
-- image's default locale is not UTF-8; multilingual coverage lives with the
-- MySQL/MariaDB seeds).
-- ============================================================

CREATE TABLE e2e_customer (
    customer_id    INTEGER       NOT NULL,
    customer_code  CHAR(4)       NOT NULL,
    customer_name  VARCHAR(100)  NOT NULL,
    customer_alias VARCHAR(60),
    status         CHAR(1),
    credit_limit   DECIMAL(15, 2),
    created_on     DATE,
    updated_on     DATE,
    PRIMARY KEY (customer_id) CONSTRAINT pk_e2e_customer,
    UNIQUE (customer_code) CONSTRAINT uk_e2e_customer_code,
    CHECK (status IN ('A', 'I', 'D')) CONSTRAINT ck_e2e_customer_status
);

CREATE TABLE e2e_order (
    order_id       INTEGER       NOT NULL,
    customer_id    INTEGER       NOT NULL,
    order_no       VARCHAR(30),
    order_status   VARCHAR(20),
    total_amount   DECIMAL(18, 2),
    ordered_at     DATE,
    settled_at     DATE,
    source_comment VARCHAR(200),
    PRIMARY KEY (order_id) CONSTRAINT pk_e2e_order,
    FOREIGN KEY (customer_id) REFERENCES e2e_customer (customer_id) CONSTRAINT fk_e2e_order_customer,
    CHECK (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL')) CONSTRAINT ck_e2e_order_status
);

-- customer_id is already indexed by the foreign-key constraint above, so the
-- plain index goes on order_status (ASC); ordered_at gets a DESC index.
CREATE INDEX idx_e2e_order_status ON e2e_order (order_status);
CREATE INDEX idxd_e2e_order_ordered_at ON e2e_order (ordered_at DESC);

CREATE TABLE e2e_order_line (
    order_id   INTEGER NOT NULL,
    line_no    INTEGER NOT NULL,
    sku        VARCHAR(30),
    qty        INTEGER,
    unit_price DECIMAL(15, 2),
    line_note  VARCHAR(200),
    PRIMARY KEY (order_id, line_no) CONSTRAINT pk_e2e_order_line,
    FOREIGN KEY (order_id) REFERENCES e2e_order (order_id) CONSTRAINT fk_e2e_order_line_order,
    CHECK (qty > 0) CONSTRAINT ck_e2e_order_line_qty
);

CREATE TABLE e2e_employee (
    employee_id INTEGER      NOT NULL,
    manager_id  INTEGER,
    emp_name    VARCHAR(100) NOT NULL,
    hired_on    DATE,
    PRIMARY KEY (employee_id) CONSTRAINT pk_e2e_employee,
    FOREIGN KEY (manager_id) REFERENCES e2e_employee (employee_id) CONSTRAINT fk_e2e_employee_manager
);
