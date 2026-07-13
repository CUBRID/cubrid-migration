-- Representative-row queries for the Informix → CUBRID migration test.
-- Format: each query starts with "-- @label <name>", body follows, terminated with ';'.
-- See com.cmt.e2e.framework.verify.RowQueries for parsing rules.
--
-- We connect to CUBRID as 'dba' so schema-qualification ("OWNER"."table")
-- is required. CMT preserves the Informix owner as INFORMIX (uppercase);
-- Informix lower-cases unquoted identifiers, so table names stay lowercase.

-- @label customer business values (id=1)
SELECT customer_code, customer_name, customer_alias, status, credit_limit
FROM "INFORMIX"."e2e_customer"
WHERE customer_id = 1;

-- @label order-line composite-key relationship (order_id=1000, line_no=1)
SELECT c.customer_code, o.order_no, l.sku, l.qty, l.unit_price
FROM "INFORMIX"."e2e_customer" c
JOIN "INFORMIX"."e2e_order" o ON o.customer_id = c.customer_id
JOIN "INFORMIX"."e2e_order_line" l ON l.order_id = o.order_id
WHERE l.order_id = 1000 AND l.line_no = 1;

-- @label employee self-reference (Alice reports to Root Manager, employee_id=2)
SELECT e.emp_name, m.emp_name AS manager_name
FROM "INFORMIX"."e2e_employee" e
JOIN "INFORMIX"."e2e_employee" m ON m.employee_id = e.manager_id
WHERE e.employee_id = 2;

-- @label text type minimal row (id=3)
SELECT char_col, varchar_col, lvarchar_col
FROM "INFORMIX"."e2e_text_types"
WHERE id = 3;

-- @label numeric type maximum row (id=1)
SELECT int_col, bigint_col, decimal_col
FROM "INFORMIX"."e2e_numeric_types"
WHERE id = 1;

-- @label temporal representative row (id=1)
SELECT date_col, dt_sec_col
FROM "INFORMIX"."e2e_temporal_types"
WHERE id = 1;
