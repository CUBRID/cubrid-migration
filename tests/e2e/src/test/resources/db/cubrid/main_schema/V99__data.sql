-- ============================================================
-- MAIN_SCHEMA: seed data (business + type test + extensions)
-- Per docs/seed/cubrid/SEED_SPEC.md §3 (business) and §5 (type test).
--
-- Row IDs in the type test tables follow COMMON_SEED_CONTRACT.md §4:
--   1 = R_NULL  2 = R_EMPTY  3 = R_MIN  4 = R_MAX
--   5 = R_UNICODE (text only) / R_BOUNDARY (numeric, temporal, binary)
--   6 = R_BOUNDARY (text)     / R_REPRESENTATIVE (numeric)
-- ============================================================

-- =====================================================================
-- §3.1 e2e_customer
-- =====================================================================
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (1, 'C001', 'ALPHA CUSTOMER',      'Alpha Alias', 'A', 12500.75,           DATETIME '2024-01-10 00:00:00', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (2, 'C002', 'BETA CUSTOMER',       NULL,          'I', NULL,                DATETIME '2024-02-29 00:00:00', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (3, 'C003', '한국 고객',           '별칭-한글',   'D', 0,                   DATETIME '2024-03-15 00:00:00', NULL);
INSERT INTO e2e_customer (customer_id, customer_code, customer_name, customer_alias, status, credit_limit, created_on, updated_on)
VALUES (4, 'C004', 'HIGH LIMIT CUSTOMER', 'High',        'A', 9999999999999.99,    DATETIME '2024-04-20 00:00:00', NULL);

-- =====================================================================
-- §3.2 e2e_order
-- =====================================================================
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (1, 1, 'ORD-2024-001', 'NEW',    50.00,  DATETIME '2024-01-15 00:00:00', NULL,                            'first order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (2, 1, 'ORD-2024-002', 'DONE',   125.50, DATETIME '2024-02-01 00:00:00', DATETIME '2024-02-05 00:00:00', 'done-order');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (3, 2, 'ORD-2024-003', 'HOLD',   NULL,   DATETIME '2024-03-01 00:00:00', NULL,                            '  padded comment  ');
INSERT INTO e2e_order (order_id, customer_id, order_no, order_status, total_amount, ordered_at, settled_at, source_comment)
VALUES (4, 3, 'ORD-2024-004', 'CANCEL', 0,      DATETIME '2024-04-10 00:00:00', NULL,                            '취소 주문 !@#');

-- =====================================================================
-- §3.3 e2e_order_line  (composite PK)
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
-- §3.4 e2e_employee  (self-FK chain CEO -> Manager A -> Staff A1)
-- =====================================================================
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (1, NULL, 'CEO',       DATE '2020-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (2, 1,    'Manager A', DATE '2021-01-01');
INSERT INTO e2e_employee (employee_id, manager_id, emp_name, hired_on)
VALUES (3, 2,    'Staff A1',  DATE '2022-01-01');

-- =====================================================================
-- §5.1 e2e_text_types  (R_NULL/EMPTY/MIN/MAX/UNICODE/BOUNDARY)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY  (CUBRID preserves '' as empty string; NCHAR columns need N'' literal)
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (2, '', '', N'', N'', '', '');
-- id=3 R_MIN
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (3, 'A', 'A', N'A', N'A', 'A', 'A');
-- id=4 R_MAX  (ASCII at byte-counted columns; multi-byte at NCHAR VARYING/STRING/CLOB)
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (4, 'XYZ', 'ABCDEFGHIJ', N'XYZ', N'한국어 漢字 cafe',
        RPAD('STR', 1000, 'X'),
        RPAD('CLOB', 1000, 'X'));
-- id=5 R_UNICODE  (byte-counted columns NULL; unicode in wider columns)
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (5, NULL, NULL, NULL, N'한中Ω 漢字',
        '한국어 漢字 🚀 café',
        '한국어 漢字 cafe punctuation !@#');
-- id=6 R_BOUNDARY  (whitespace and embedded newlines)
INSERT INTO e2e_text_types (id, char_col, varchar_col, nchar_col, nvarchar_col, string_col, clob_col)
VALUES (6, '   ', ' A ', N'   ', N' A ',
        'line1' || CHR(10) || 'line2',
        'line1' || CHR(10) || 'line2');

-- =====================================================================
-- §5.2 e2e_numeric_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY/REPRESENTATIVE)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (2, 0, 0, 0, 0, 0, 0, 0, 0);
-- id=3 R_MIN
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (3, -32768, -2147483648, -9223372036854775808,
        -99999999999999.999999,
        -99999999999999999999999999999999999999,
        -3.4E38, -1.7E308, -1234567890.99);
-- id=4 R_MAX
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (4, 32767, 2147483647, 9223372036854775807,
        99999999999999.999999,
        99999999999999999999999999999999999999,
        3.4E38, 1.7E308, 1234567890.99);
-- id=5 R_BOUNDARY
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (5, 100, 42, 100000000000,
        1234567890.123456,
        12345678901234567890,
        3.14159, 2.7182818, 1234.56);
-- id=6 R_REPRESENTATIVE
INSERT INTO e2e_numeric_types (id, smallint_col, int_col, bigint_col, numeric_col, numeric_p0_col, float_col, double_col, monetary_col)
VALUES (6, 7, 100, 1000,
        42.424242,
        10000000000,
        6.283185, 1.41421356, 99.99);

-- =====================================================================
-- §5.3 e2e_temporal_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY)
-- TIMESTAMP-class columns are limited to CUBRID's 1970-01-02..2038-01-18 range.
-- DATETIME-class columns use the broader 0001..9999 range.
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, datetimetz_col, datetimeltz_col, timestamptz_col, timestampltz_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, datetimetz_col, datetimeltz_col, timestamptz_col, timestampltz_col)
VALUES (2,
        DATE '1970-01-01',
        TIME '00:00:00',
        DATETIME '1970-01-01 00:00:00.000',
        TIMESTAMP '1970-01-02 00:00:00',
        DATETIMETZ '1970-01-01 00:00:00.000 +00:00',
        DATETIMELTZ '1970-01-01 00:00:00.000',
        TIMESTAMPTZ '1970-01-02 00:00:00 +00:00',
        TIMESTAMPLTZ '1970-01-02 00:00:00');
-- id=3 R_MIN
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, datetimetz_col, datetimeltz_col, timestamptz_col, timestampltz_col)
VALUES (3,
        DATE '1900-01-01',
        TIME '00:00:01',
        DATETIME '1900-01-01 00:00:00.001',
        TIMESTAMP '1970-01-02 00:00:01',
        DATETIMETZ '1900-01-01 00:00:00.001 +00:00',
        DATETIMELTZ '1900-01-01 00:00:00.001',
        TIMESTAMPTZ '1970-01-02 00:00:01 +00:00',
        TIMESTAMPLTZ '1970-01-02 00:00:01');
-- id=4 R_MAX
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, datetimetz_col, datetimeltz_col, timestamptz_col, timestampltz_col)
VALUES (4,
        DATE '9999-12-31',
        TIME '23:59:59',
        DATETIME '9999-12-31 23:59:59.999',
        TIMESTAMP '2038-01-18 23:59:59',
        DATETIMETZ '9999-12-31 23:59:59.999 +14:00',
        DATETIMELTZ '9999-12-31 23:59:59.999',
        TIMESTAMPTZ '2038-01-18 23:59:59 +14:00',
        TIMESTAMPLTZ '2038-01-18 23:59:59');
-- id=5 R_BOUNDARY  (leap day, Asia/Seoul region zone)
INSERT INTO e2e_temporal_types (id, date_col, time_col, datetime_col, timestamp_col, datetimetz_col, datetimeltz_col, timestamptz_col, timestampltz_col)
VALUES (5,
        DATE '2024-02-29',
        TIME '12:34:56',
        DATETIME '2024-02-29 12:34:56.123',
        TIMESTAMP '2024-02-29 12:34:56',
        DATETIMETZ '2024-04-20 12:00:00.123 Asia/Seoul',
        DATETIMELTZ '2024-04-20 12:00:00.123',
        TIMESTAMPTZ '2024-04-20 12:00:00 +09:00',
        TIMESTAMPLTZ '2024-04-20 12:00:00');

-- =====================================================================
-- §5.4 e2e_binary_types  (R_NULL/EMPTY/MIN/MAX/BOUNDARY)
-- BIT(64): 64-bit fixed length. BIT VARYING(1024): up to 1024 bits.
-- BLOB literal via BIT_TO_BLOB on a hex BIT literal.
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_binary_types (id, bit_col, varbit_col, blob_col)
VALUES (1, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_binary_types (id, bit_col, varbit_col, blob_col)
VALUES (2, B'', B'', NULL);
-- id=3 R_MIN
INSERT INTO e2e_binary_types (id, bit_col, varbit_col, blob_col)
VALUES (3, X'0000000000000000', X'00', NULL);
-- id=4 R_MAX
INSERT INTO e2e_binary_types (id, bit_col, varbit_col, blob_col)
VALUES (4,
        X'FFFFFFFFFFFFFFFF',
        X'00FF00FF00FF00FF00FF00FF00FF00FF',
        BIT_TO_BLOB(X'00FF00FF00FF00FF00FF00FF00FF00FF'));
-- id=5 R_BOUNDARY
INSERT INTO e2e_binary_types (id, bit_col, varbit_col, blob_col)
VALUES (5,
        X'00000000CAFEBABE',
        X'CAFEBABE',
        BIT_TO_BLOB(X'CAFEBABE'));

-- =====================================================================
-- §5.5 e2e_cubrid_collection_types  (CUBRID extension)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_cubrid_collection_types (id, set_int_col, multiset_str_col, seq_double_col)
VALUES (1, NULL, NULL, NULL);
-- id=2 R_EMPTY
INSERT INTO e2e_cubrid_collection_types (id, set_int_col, multiset_str_col, seq_double_col)
VALUES (2, SET{}, MULTISET{}, SEQUENCE{});
-- id=3 R_SINGLE
INSERT INTO e2e_cubrid_collection_types (id, set_int_col, multiset_str_col, seq_double_col)
VALUES (3, SET{42}, MULTISET{'one'}, SEQUENCE{1.5});
-- id=4 R_MULTI
INSERT INTO e2e_cubrid_collection_types (id, set_int_col, multiset_str_col, seq_double_col)
VALUES (4, SET{1, 2, 3, 4, 5}, MULTISET{'a', 'b', 'a', 'c', 'a'}, SEQUENCE{1.1, 2.2, 3.3});
-- id=5 R_UNICODE
INSERT INTO e2e_cubrid_collection_types (id, set_int_col, multiset_str_col, seq_double_col)
VALUES (5, NULL, MULTISET{'한국', '漢字', '🚀'}, NULL);

-- =====================================================================
-- §5.6 e2e_cubrid_enum_types
-- =====================================================================
INSERT INTO e2e_cubrid_enum_types (id, status_enum) VALUES (1, NULL);
INSERT INTO e2e_cubrid_enum_types (id, status_enum) VALUES (2, 'NEW');
INSERT INTO e2e_cubrid_enum_types (id, status_enum) VALUES (3, 'CANCEL');
INSERT INTO e2e_cubrid_enum_types (id, status_enum) VALUES (4, 'DONE');

-- =====================================================================
-- §5.7 e2e_cubrid_json_types  (CUBRID 11+)
-- =====================================================================
INSERT INTO e2e_cubrid_json_types (id, payload) VALUES (1, NULL);
INSERT INTO e2e_cubrid_json_types (id, payload) VALUES (2, '{}');
INSERT INTO e2e_cubrid_json_types (id, payload) VALUES (3, '[]');
INSERT INTO e2e_cubrid_json_types (id, payload) VALUES (4, '{"a":[1,2,{"b":"한국"}]}');
INSERT INTO e2e_cubrid_json_types (id, payload)
VALUES (5, '{"deeply":{"nested":{"json":[{"value":1},{"value":2},{"value":3},{"value":4},{"value":5}]}},"unicode":["한국","漢字","emoji 🚀","accents café"],"numbers":[1.5,2.5,3.5,1e10,-1e-10],"flags":[true,false,null]}');
