-- Representative-row queries for the MySQL → CUBRID migration test.
-- Format: each query starts with "-- @label <name>", body follows, terminated with ';'.
-- See com.cmt.e2e.framework.verify.RowQueries for parsing rules.
--
-- We connect to CUBRID as 'dba' so schema-qualification ("OWNER"."table")
-- is required. CMT preserves the MySQL user (main_schema) as the CUBRID
-- owner name MAIN_SCHEMA (uppercase); table names keep their MySQL case.

-- @label customer business values (id=1)
SELECT customer_code, customer_name, customer_alias, status, credit_limit
FROM "MAIN_SCHEMA"."e2e_customer"
WHERE customer_id = 1;

-- @label order-line composite-key relationship (order_id=1, line_no=1)
SELECT c.customer_code, o.order_no, l.sku, l.qty, l.unit_price
FROM "MAIN_SCHEMA"."e2e_customer" c
JOIN "MAIN_SCHEMA"."e2e_order" o ON o.customer_id = c.customer_id
JOIN "MAIN_SCHEMA"."e2e_order_line" l ON l.order_id = o.order_id
WHERE l.order_id = 1 AND l.line_no = 1;

-- @label employee self-reference (Manager A reports to CEO, employee_id=2)
SELECT e.emp_name, m.emp_name AS manager_name
FROM "MAIN_SCHEMA"."e2e_employee" e
JOIN "MAIN_SCHEMA"."e2e_employee" m ON m.employee_id = e.manager_id
WHERE e.employee_id = 2;

-- @label text type R_MIN row (id=3)
SELECT char_col, varchar_col, text_col
FROM "MAIN_SCHEMA"."e2e_text_types"
WHERE id = 3;

-- @label numeric type R_REPRESENTATIVE row (id=5)
SELECT int_col, bigint_col, decimal_col
FROM "MAIN_SCHEMA"."e2e_numeric_types"
WHERE id = 5;

-- @label temporal R_EPOCH row (id=2)
SELECT date_col, year_col
FROM "MAIN_SCHEMA"."e2e_temporal_types"
WHERE id = 2;
