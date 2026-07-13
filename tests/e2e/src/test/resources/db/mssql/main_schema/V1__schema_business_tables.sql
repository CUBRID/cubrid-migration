-- ============================================================
-- e2e_db (SQL Server): business graph tables, indexes, check constraints.
--
-- T-SQL dialect: named constraints use the standard prefix form
-- (CONSTRAINT name PRIMARY KEY ...). SQL Server does not auto-index FK
-- columns, so the plain indexes sit on non-key columns. NVARCHAR carries
-- multilingual data. Ids are inserted explicitly (no IDENTITY) for stable
-- snapshots.
-- ============================================================

CREATE TABLE e2e_customer (
    customer_id    INT            NOT NULL,
    customer_code  CHAR(4)        NOT NULL,
    customer_name  VARCHAR(100)   NOT NULL,
    customer_alias NVARCHAR(60),
    status         CHAR(1),
    credit_limit   DECIMAL(15, 2),
    created_on     DATE,
    updated_on     DATE,
    CONSTRAINT pk_e2e_customer PRIMARY KEY (customer_id),
    CONSTRAINT uk_e2e_customer_code UNIQUE (customer_code),
    CONSTRAINT ck_e2e_customer_status CHECK (status IN ('A', 'I', 'D'))
);

CREATE TABLE e2e_order (
    order_id       INT            NOT NULL,
    customer_id    INT            NOT NULL,
    order_no       VARCHAR(30),
    order_status   VARCHAR(20),
    total_amount   DECIMAL(18, 2),
    ordered_at     DATE,
    settled_at     DATE,
    source_comment VARCHAR(200),
    CONSTRAINT pk_e2e_order PRIMARY KEY (order_id),
    CONSTRAINT fk_e2e_order_customer FOREIGN KEY (customer_id) REFERENCES e2e_customer (customer_id),
    CONSTRAINT ck_e2e_order_status CHECK (order_status IN ('NEW', 'HOLD', 'DONE', 'CANCEL'))
);

CREATE INDEX idx_e2e_order_status ON e2e_order (order_status);
CREATE INDEX idxd_e2e_order_ordered_at ON e2e_order (ordered_at DESC);

CREATE TABLE e2e_order_line (
    order_id   INT NOT NULL,
    line_no    INT NOT NULL,
    sku        VARCHAR(30),
    qty        INT,
    unit_price DECIMAL(15, 2),
    line_note  VARCHAR(200),
    CONSTRAINT pk_e2e_order_line PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_e2e_order_line_order FOREIGN KEY (order_id) REFERENCES e2e_order (order_id),
    CONSTRAINT ck_e2e_order_line_qty CHECK (qty > 0)
);

CREATE TABLE e2e_employee (
    employee_id INT          NOT NULL,
    manager_id  INT,
    emp_name    VARCHAR(100) NOT NULL,
    hired_on    DATE,
    CONSTRAINT pk_e2e_employee PRIMARY KEY (employee_id),
    CONSTRAINT fk_e2e_employee_manager FOREIGN KEY (manager_id) REFERENCES e2e_employee (employee_id)
);
