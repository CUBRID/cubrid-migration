-- Representative-row queries for the CUBRID → CUBRID migration test.
-- Same scenario shape as oracle_to_cubrid.sql but referenced columns
-- match the CUBRID seed (docs/seed/cubrid/SEED_SPEC.md §5).
--
-- We connect to the target CUBRID as 'dba'; schema-qualification
-- ("OWNER"."TABLE") is required.

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

-- @label text type R_MIN row (id=3) — CUBRID columns
SELECT char_col, varchar_col, nchar_col, nvarchar_col
FROM "MAIN_SCHEMA"."e2e_text_types"
WHERE id = 3;

-- @label numeric type R_REPRESENTATIVE row (id=6) — CUBRID columns
SELECT int_col, numeric_col, double_col
FROM "MAIN_SCHEMA"."e2e_numeric_types"
WHERE id = 6;

-- @label temporal date R_EPOCH row (id=2)
SELECT date_col
FROM "MAIN_SCHEMA"."e2e_temporal_types"
WHERE id = 2;

-- @label view is queryable (4 orders in dataset)
SELECT COUNT(*)
FROM "MAIN_SCHEMA"."e2e_order_summary_v";

-- @label ref_audit synonym access (1 row)
SELECT COUNT(*)
FROM "MAIN_SCHEMA"."e2e_ref_audit_syn";

-- @label i18n character battery (all scripts round-trip intact)
SELECT id, label, sample
FROM "MAIN_SCHEMA"."e2e_i18n"
ORDER BY id;
