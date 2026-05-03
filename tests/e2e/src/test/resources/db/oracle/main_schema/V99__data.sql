-- ============================================================
-- MAIN_SCHEMA: seed data (business + type test)
-- Per oracle/SEED_SPEC.md §3 (business required rows) and §5 (type test
-- SQL-ready values).
--
-- Row IDs in the type test tables follow the canonical battery codes
-- defined by COMMON_SEED_CONTRACT.md §4:
--   1 = R_NULL  2 = R_EMPTY  3 = R_MIN  4 = R_MAX
--   5 = R_UNICODE (text only) / R_BOUNDARY (numeric, temporal, binary)
--   6 = R_BOUNDARY (text)     / R_REPRESENTATIVE (numeric)
-- Each insert below is annotated with its row code.
-- ============================================================

-- =====================================================================
-- §3.1 e2e_customer
-- =====================================================================
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (1, 'C001', 'ALPHA CUSTOMER',      'Alpha Alias', 'A', 12500.75,           DATE '2024-01-10', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (2, 'C002', 'BETA CUSTOMER',       NULL,          'I', NULL,                DATE '2024-02-29', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (3, 'C003', '한국 고객',           '별칭-한글',   'D', 0,                   DATE '2024-03-15', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (4, 'C004', 'HIGH LIMIT CUSTOMER', 'High',        'A', 9999999999999.99,    DATE '2024-04-20', NULL);

-- =====================================================================
-- §3.2 e2e_order
-- =====================================================================
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (1, 1, 'ORD-2024-001', 'NEW',    50.00,  DATE '2024-01-15', NULL,              'first order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (2, 1, 'ORD-2024-002', 'DONE',   125.50, DATE '2024-02-01', DATE '2024-02-05', 'done-order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (3, 2, 'ORD-2024-003', 'HOLD',   NULL,   DATE '2024-03-01', NULL,              '  padded comment  ');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (4, 3, 'ORD-2024-004', 'CANCEL', 0,      DATE '2024-04-10', NULL,              '취소 주문 !@#');

-- =====================================================================
-- §3.3 e2e_order_line  (composite PK, distinct order_id-line_no pairs)
-- =====================================================================
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (1, 1, 'SKU-ALPHA',  2,  25.00,  'normal');
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (2, 1, 'SKU-BETA',   1,  125.50, NULL);
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (3, 1, 'SKU-HOLD',   10, 9.99,   'hold line');
INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note)
VALUES (4, 1, 'SKU-CANCEL', 1,  0,      'cancelled');

-- =====================================================================
-- §3.4 e2e_employee  (self-reference FK forms a 3-level chain)
-- =====================================================================
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (1, NULL, 'CEO',       DATE '2020-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (2, 1,    'Manager A', DATE '2021-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (3, 2,    'Staff A1',  DATE '2022-01-01');

-- =====================================================================
-- §5.1 e2e_text_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_UNICODE, R_BOUNDARY)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY  (Oracle stores '' as NULL for VARCHAR2; intentional per §5.1 note)
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (2, ' ', ' ', '', '', ' ', '', EMPTY_CLOB(), EMPTY_CLOB());
-- id=3 R_MIN
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (3, 'A', 'A', 'A', 'A', N'A', N'A', 'A', N'A');
-- id=4 R_MAX
-- char_char_col, varchar_char_col, and nchar_col use ASCII content because CMT
-- maps Oracle CHAR(n CHAR), VARCHAR2(n CHAR), and NCHAR(n) to byte-counted CUBRID
-- CHAR(n)/VARCHAR(n); n multi-byte chars overflow when n equals the byte budget.
-- Multi-byte coverage stays in NVARCHAR2(40)/CLOB/NCLOB columns (CMT maps these
-- to wider CUBRID varchar/clob types that fit the values).
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (4, 'XYZ', 'XYZ', 'ABCDEFGHIJ', 'ABCDEFGHIJ', N'1234567890', N'한국어 漢字 cafe',
        TO_CLOB(RPAD('CLOB', 1000, 'X')), TO_NCLOB(RPAD(N'NCLOB', 1000, N'가')));
-- id=5 R_UNICODE
-- char_char_col / varchar_char_col are NULL for the same byte-vs-char reason;
-- unicode coverage lives in nchar_col / nvarchar_col / clob_col / nclob_col.
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (5, NULL, NULL, NULL, NULL, N'한中Ω', N'한국 漢字 cafe',
        TO_CLOB('한국어 漢字 cafe punctuation !@#'),
        TO_NCLOB(N'한국어 漢字 cafe punctuation !@#'));
-- id=6 R_BOUNDARY  (whitespace and embedded newlines)
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col, nclob_col)
VALUES (6, '   ', '   ', ' A ', '  A  ', N'   ', N' A ',
        TO_CLOB('line1' || CHR(10) || 'line2'),
        TO_NCLOB(N'line1' || CHR(10) || N'line2'));

-- =====================================================================
-- §5.2 e2e_numeric_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY, R_REPRESENTATIVE)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
-- id=3 R_MIN
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (3, -2147483647, -9999999999999999.9999,
        -99999999999999999999999999999999999999, -99999999999999.999999, -99999900,
        -1E20, -1E20, -1E20, -3.4E38F, -1.7E308D);
-- id=4 R_MAX
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (4, 2147483647, 9999999999999999.9999,
        99999999999999999999999999999999999999, 99999999999999.999999, 99999900,
        1E20, 1E20, 1E20, 3.4E38F, 1.7E308D);
-- id=5 R_BOUNDARY  (binary float/double special values per §5.2 SQL-ready table)
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (5, 42, 9876543210.1234,
        12345678901234567890123456789012345678, 1234567890.123456, 1234.56,
        0.000001, 3.14159265, 2.7182818,
        BINARY_FLOAT_INFINITY, BINARY_DOUBLE_INFINITY);
-- id=6 R_REPRESENTATIVE
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (6, 7, 100.2500,
        10000000000000000000000000000000000001, 42.424242, -1234.56,
        -0.000001, 6.283185, 1.41421,
        BINARY_FLOAT_NAN, BINARY_DOUBLE_NAN);

-- =====================================================================
-- §5.3 e2e_temporal_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (2, DATE '1970-01-01',
        TIMESTAMP '1970-01-01 00:00:00.000000',
        TIMESTAMP '1970-01-01 00:00:00.000000000',
        TIMESTAMP '1970-01-01 00:00:00.000000',
        TO_TIMESTAMP_TZ('1970-01-01 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '0 0:0:0.000000' DAY TO SECOND,
        INTERVAL '0-0' YEAR TO MONTH);
-- id=3 R_MIN
-- ts*_col and tsltz6_col are constrained to within CUBRID TIMESTAMP range
-- (1970-01-02 .. 2038-01-18). CMT maps Oracle TIMESTAMP(n) to CUBRID TIMESTAMP,
-- so values outside that range fail online migration. date_col keeps the broader
-- 1900-01-01 boundary because Oracle DATE maps to CUBRID DATETIME (full range).
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (3, DATE '1900-01-01',
        TIMESTAMP '1970-01-02 00:00:00.000001',
        TIMESTAMP '1970-01-02 00:00:00.000000001',
        TIMESTAMP '1970-01-02 00:00:00.000001',
        TO_TIMESTAMP_TZ('1970-01-02 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '-10 23:59:59.999999' DAY TO SECOND,
        INTERVAL '-10-11' YEAR TO MONTH);
-- id=4 R_MAX
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (4, DATE '9999-12-31',
        TIMESTAMP '2038-01-18 23:59:59.999999',
        TIMESTAMP '2038-01-18 23:59:59.999999999',
        TIMESTAMP '2038-01-18 23:59:59.999999',
        TO_TIMESTAMP_TZ('2038-01-18 23:59:59 +14:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '10 23:59:59.999999' DAY TO SECOND,
        INTERVAL '10-11' YEAR TO MONTH);
-- id=5 R_BOUNDARY  (leap day, non-UTC zone)
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (5, DATE '2024-02-29',
        TIMESTAMP '2024-02-29 12:34:56.123456',
        TIMESTAMP '2024-02-29 12:34:56.123456789',
        TIMESTAMP '2024-02-29 12:34:56.123456',
        TO_TIMESTAMP_TZ('2024-04-20 12:00:00 +09:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '1 02:03:04.567890' DAY TO SECOND,
        INTERVAL '2-6' YEAR TO MONTH);

-- =====================================================================
-- §5.4 e2e_binary_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (1, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (2, HEXTORAW(''), EMPTY_BLOB());
-- id=3 R_MIN
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (3, HEXTORAW('00'), TO_BLOB(HEXTORAW('00')));
-- id=4 R_MAX  (RAW(16) at full 16-byte width)
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (4, HEXTORAW('00FF00FF00FF00FF00FF00FF00FF00FF'),
        TO_BLOB(HEXTORAW('00FF00FF00FF00FF')));
-- id=5 R_BOUNDARY
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (5, HEXTORAW('CAFEBABE'), TO_BLOB(HEXTORAW('CAFEBABE')));

-- =====================================================================
-- §5.5 e2e_oracle_locator_types  (Oracle extension)
-- ROWID literals cannot be hardcoded; pull a real ROWID from the
-- already-inserted e2e_customer row (customer_id=1).
-- =====================================================================
INSERT INTO e2e_oracle_locator_types (id, rowid_col, urowid_col)
SELECT 1, src.ROWID, src.ROWID
  FROM e2e_customer src
 WHERE src.customer_id = 1;

COMMIT;
