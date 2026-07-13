-- Representative-row queries for the MSSQL → CUBRID migration test.
-- Format: each query starts with "-- @label <name>", body follows, terminated with ';'.
-- See com.cmt.e2e.framework.verify.RowQueries for parsing rules.
--
-- We connect to CUBRID as 'dba' so schema-qualification ("OWNER"."table")
-- is required. CMT upper-cases the MSSQL schema, so the CUBRID owner is DBO;
-- SQL Server preserves identifier case, so table names stay lowercase.
--
-- customer_alias (multilingual NVARCHAR) is intentionally NOT asserted here:
-- the e2e CUBRID database is created with the iso88591 charset, so multibyte
-- values are corrupted on the online round-trip. Multilingual data stays in
-- the seed and is verified through the unload dump (export side, correct).

-- @label customer business values (id=1)
SELECT customer_code, customer_name, status, credit_limit
FROM "DBO"."e2e_customer"
WHERE customer_id = 1;

-- @label order-line composite-key relationship (order_id=1000, line_no=1)
SELECT c.customer_code, o.order_no, l.sku, l.qty, l.unit_price
FROM "DBO"."e2e_customer" c
JOIN "DBO"."e2e_order" o ON o.customer_id = c.customer_id
JOIN "DBO"."e2e_order_line" l ON l.order_id = o.order_id
WHERE l.order_id = 1000 AND l.line_no = 1;

-- @label employee self-reference (Alice reports to Root Manager, employee_id=2)
SELECT e.emp_name, m.emp_name AS manager_name
FROM "DBO"."e2e_employee" e
JOIN "DBO"."e2e_employee" m ON m.employee_id = e.manager_id
WHERE e.employee_id = 2;

-- @label text type minimal row (id=3)
SELECT char_col, varchar_col, nvarchar_col
FROM "DBO"."e2e_text_types"
WHERE id = 3;

-- @label numeric type maximum row (id=1)
SELECT int_col, bigint_col, decimal_col, money_col
FROM "DBO"."e2e_numeric_types"
WHERE id = 1;

-- @label temporal representative row (id=1)
SELECT date_col, datetime_col
FROM "DBO"."e2e_temporal_types"
WHERE id = 1;
