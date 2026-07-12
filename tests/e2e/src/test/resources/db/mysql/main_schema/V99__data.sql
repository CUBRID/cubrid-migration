-- ============================================================
-- MAIN_SCHEMA (MySQL): seed data (business + type battery).
--
-- Type-table row ids follow the canonical battery codes:
--   1 = R_NULL  2 = R_EMPTY  3 = R_MIN  4 = R_MAX  5 = R_UNICODE/R_BOUNDARY
-- Values stay inside the CUBRID target-type ranges so online migration
-- succeeds (TIMESTAMP within 1970..2038, TIME within a 24h clock, etc.).
-- ============================================================

-- ====== business =================================================
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on) VALUES
    (1, 'C001', 'ALPHA CUSTOMER',      'Alpha Alias', 'A', 12500.75,        '2024-01-10', NULL),
    (2, 'C002', 'BETA CUSTOMER',       NULL,          'I', NULL,            '2024-02-29', NULL),
    (3, 'C003', '한국 고객',            '별칭-한글',    'D', 0,               '2024-03-15', NULL),
    (4, 'C004', 'HIGH LIMIT CUSTOMER', 'High',        'A', 9999999999999.99, '2024-04-20', NULL);

INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment) VALUES
    (1, 1, 'ORD-2024-001', 'NEW',    50.00,  '2024-01-15', NULL,         'first order'),
    (2, 1, 'ORD-2024-002', 'DONE',   125.50, '2024-02-01', '2024-02-05', 'done-order'),
    (3, 2, 'ORD-2024-003', 'HOLD',   NULL,   '2024-03-01', NULL,         '  padded comment  '),
    (4, 3, 'ORD-2024-004', 'CANCEL', 0,      '2024-04-10', NULL,         '취소 주문 !@#');

INSERT INTO e2e_order_line (order_id, line_no, sku, qty, unit_price, line_note) VALUES
    (1, 1, 'SKU-ALPHA',  2,  25.00,  'normal'),
    (2, 1, 'SKU-BETA',   1,  125.50, NULL),
    (3, 1, 'SKU-HOLD',   10, 9.99,   'hold line'),
    (4, 1, 'SKU-CANCEL', 1,  1.00,   'cancelled');

INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on) VALUES
    (1, NULL, 'CEO',       '2020-01-01'),
    (2, 1,    'Manager A', '2021-01-01'),
    (3, 2,    'Staff A1',  '2022-01-01');

-- ====== e2e_text_types ===========================================
INSERT INTO e2e_text_types (id, char_col, varchar_col, tinytext_col, text_col, mediumtext_col, longtext_col) VALUES
    (1, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, '', '', '', '', '', ''),
    (3, 'A', 'A', 'A', 'A', 'A', 'A'),
    (4, 'ABCDEFGHIJ', REPEAT('X', 100), REPEAT('t', 255), REPEAT('T', 2000), REPEAT('M', 4000), REPEAT('L', 8000)),
    (5, '한中Ω', '한국 漢字 café', '日本語 テスト', '한국어 漢字 café !@#', '多国語 テキスト', 'юникод текст'),
    (6, '   ', '  A  ', ' tab\there ', CONCAT('line1', CHAR(10), 'line2'), '   trailing   ', ' embedded  spaces ');

-- ====== e2e_numeric_types ========================================
INSERT INTO e2e_numeric_types (id, tinyint_col, smallint_col, mediumint_col, int_col, bigint_col, decimal_col, float_col, double_col, bit_col) VALUES
    (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, 0, 0, 0, 0, 0, 0, 0, 0, b'00000000'),
    (3, -128, -32768, -8388608, -2147483648, -9223372036854775808, -9999999999999999.9999, -3.402823E+38, -1.7976931348623157E+308, b'00000001'),
    (4, 127, 32767, 8388607, 2147483647, 9223372036854775807, 9999999999999999.9999, 3.402823E+38, 1.7976931348623157E+308, b'11111111'),
    (5, 42, 12345, 1234567, 100000, 9876543210, 100.2500, 3.14159, 2.718281828459045, b'10101010');

-- ====== e2e_temporal_types =======================================
INSERT INTO e2e_temporal_types (id, date_col, datetime_col, timestamp_col, time_col, year_col) VALUES
    (1, NULL, NULL, NULL, NULL, NULL),
    (2, '1970-01-01', '1970-01-01 00:00:00.000000', '1970-01-01 00:00:01.000000', '00:00:00.000000', 1970),
    (3, '1000-01-01', '1000-01-01 00:00:00.000000', '1970-01-02 00:00:00.000001', '00:00:00.000001', 1901),
    (4, '9999-12-31', '9999-12-31 23:59:59.999999', '2038-01-18 23:59:59.999999', '23:59:59.999999', 2155),
    (5, '2024-02-29', '2024-02-29 12:34:56.123456', '2024-04-20 12:00:00.000000', '12:34:56.123456', 2024);

-- ====== e2e_binary_types =========================================
INSERT INTO e2e_binary_types (id, binary_col, varbinary_col, tinyblob_col, blob_col, mediumblob_col, longblob_col) VALUES
    (1, NULL, NULL, NULL, NULL, NULL, NULL),
    (2, x'00000000000000000000000000000000', x'', x'', x'', x'', x''),
    (3, x'00', x'00', x'00', x'00', x'00', x'00'),
    (4, x'00FF00FF00FF00FF00FF00FF00FF00FF', x'00FF00FF00FF00FF', x'DEADBEEF', x'00FF00FF00FF00FF', x'CAFED00D', x'0011223344556677'),
    (5, x'CAFEBABE', x'CAFEBABE', x'CAFEBABE', x'CAFEBABE', x'CAFEBABE', x'CAFEBABE');
