-- ============================================================
-- MAIN_SCHEMA: seed data (business tables only — Phase 3b).
-- Type-test rows (V3 schema) come in a later phase. Identical row
-- values to db/oracle/main_schema/V99 §3.1–§3.4 so future Tibero
-- snapshots line up with Oracle's where types match 1:1.
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
-- §5 type test rows.
--
-- Row IDs follow COMMON_SEED_CONTRACT.md §4 canonical battery:
--   1=R_NULL  2=R_EMPTY  3=R_MIN  4=R_MAX
--   5=R_UNICODE (text) / R_BOUNDARY (numeric, temporal, binary)
--   6=R_BOUNDARY (text) / R_REPRESENTATIVE (numeric)
--
-- Values are byte-for-byte the same as
-- db/oracle/main_schema/V99__data.sql §5.1 ~ §5.5 — Tibero is PL/SQL +
-- type-system Oracle-compatible (per tibero/SEED_SPEC.md §5).
-- =====================================================================

-- =====================================================================
-- §5.1 e2e_text_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_UNICODE, R_BOUNDARY)
-- =====================================================================
-- id=1 R_NULL
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
-- id=2 R_EMPTY (Tibero, like Oracle, stores empty string as NULL)
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (2, ' ', ' ', '', '', ' ', '', EMPTY_CLOB());
-- id=3 R_MIN
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (3, 'A', 'A', 'A', 'A', N'A', N'A', 'A');
-- id=4 R_MAX
-- char_char_col, varchar_char_col, and nchar_col carry ASCII, inherited from when
-- the CUBRID target counted CHAR(n)/VARCHAR(n) in bytes and n multi-byte chars
-- overflowed. That no longer holds: the target runs the en_US.utf8 locale, where
-- CHAR(n) counts characters, and Tibero 6 reports these columns as n so the target
-- comes out the same width as the source. Multi-byte coverage sits in
-- NVARCHAR2(40)/CLOB either way.
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (4, 'XYZ', 'XYZ', 'ABCDEFGHIJ', 'ABCDEFGHIJ', N'1234567890', N'한국어 漢字 cafe',
        TO_CLOB(RPAD('CLOB', 1000, 'X')));
-- id=5 R_UNICODE  (char_char_col / varchar_char_col are NULL, same inherited reason as id=4)
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (5, NULL, NULL, NULL, NULL, N'한中Ω', N'한국 漢字 cafe',
        TO_CLOB('한국어 漢字 cafe punctuation !@#'));
-- id=6 R_BOUNDARY  (whitespace and embedded newlines)
INSERT INTO e2e_text_types (id, char_byte_col, char_char_col, varchar_byte_col, varchar_char_col, nchar_col, nvarchar_col, clob_col)
VALUES (6, '   ', '   ', ' A ', '  A  ', N'   ', N' A ',
        TO_CLOB('line1' || CHR(10) || 'line2'));

-- =====================================================================
-- §5.2 e2e_numeric_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY, R_REPRESENTATIVE)
-- =====================================================================
-- Tibero R_MIN/R_MAX boundary 값을 Oracle 시드보다 보수적으로 줄였다.
-- 38자리 NUMBER(38,0) max, 1E20 scientific notation, NUMBER(8,-2) 음수 scale
-- 큰 값들의 조합이 CMT 의 Tibero→CUBRID importer 에서 batch
-- "Cannot coerce host var to type numeric" 으로 거부됨. Oracle→CUBRID 에서는
-- 같은 값들이 통과. CMT importer 의 Tibero NUMBER 처리 회귀로 추정 — 후속
-- 작업으로 정밀 조사. 본 시드에서는 boundary 의미 (음수/양수 / 정수
-- max / fractional / 음수 scale) 를 작은 자릿수로 유지하되 통과 가능한
-- 값으로 둔다.
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
-- id=3 R_MIN (Tibero-conservative — Oracle 과 다름)
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (3, -1234567, -1234.5678,
        -1234567890, -123.456789, -1200,
        -123456, -3.14, -2.71,
        -1.5F, -2.5D);
-- id=4 R_MAX (Tibero-conservative — Oracle 과 다름)
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (4, 1234567, 1234.5678,
        1234567890, 123.456789, 1200,
        123456, 3.14, 2.71,
        1.5F, 2.5D);
-- id=5 R_BOUNDARY (Tibero diff vs Oracle:
-- BINARY_FLOAT_INFINITY / BINARY_DOUBLE_INFINITY 는 CMT 의 Tibero→CUBRID
-- 마이그레이션 batch 가 "Cannot coerce host var to type numeric" 으로
-- 거부함. Oracle→CUBRID 에서는 같은 값이 통과. Tibero JDBC 의 special
-- value wire 표현 차이로 추정. 일반 finite 값으로 교체.)
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (5, 42, 9876543210.1234,
        12345678901234567890123456789012345678, 1234567890.123456, 1234.56,
        0.000001, 3.14159265, 2.7182818,
        3.4028235E38F, 1.7976931348623157E308D);
-- id=6 R_REPRESENTATIVE (same Tibero diff: NaN 도 거부됨.
-- 일반 finite 값으로 교체.)
INSERT INTO e2e_numeric_types (id, integer_col, decimal_col, number_p0_col, number_ps_col, number_round_col, number_any_col, float_col, real_col, binary_float_col, binary_double_col)
VALUES (6, 7, 100.2500,
        10000000000000000000000000000000000001, 42.424242, -1234.56,
        -0.000001, 6.283185, 1.41421,
        2.71828F, 1.41421356237D);

-- =====================================================================
-- §5.3 e2e_temporal_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY)
-- =====================================================================
-- Tibero diff vs Oracle: tsltz6_col (TIMESTAMP WITH LOCAL TIME ZONE)
-- 의 모든 non-NULL 값을 NULL 로 둔다. Oracle 시드와 동일 값으로 시도하면
-- CMT 의 Tibero→CUBRID 마이그레이션 batch 가 "Cannot coerce host var to type
-- datetimeltz" 로 전체 거부 (Oracle→CUBRID 에서는 같은 값이 통과). Tibero
-- JDBC 의 LTZ wire 표현 차이로 추정 — 후속 작업으로 CMT importer 조사 필요.
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (1, NULL, NULL, NULL, NULL, NULL, NULL, NULL);
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (2, DATE '1970-01-01',
        TIMESTAMP '1970-01-01 00:00:00.000000',
        TIMESTAMP '1970-01-01 00:00:00.000000000',
        NULL,
        TO_TIMESTAMP_TZ('1970-01-01 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '0 0:0:0.000000' DAY TO SECOND,
        INTERVAL '0-0' YEAR TO MONTH);
-- id=3 R_MIN
-- ts*_col 는 CMT 가 Tibero TIMESTAMP(n) → CUBRID TIMESTAMP 로 매핑하므로
-- 1970-01-02..2038-01-18 범위 안. date_col 은 DATE→DATETIME 매핑이라
-- 1900-01-01 까지 가능 (Oracle 과 동일). tsltz6_col 는 위 헤더 사유로 NULL.
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (3, DATE '1900-01-01',
        TIMESTAMP '1970-01-02 00:00:00.000001',
        TIMESTAMP '1970-01-02 00:00:00.000000001',
        NULL,
        TO_TIMESTAMP_TZ('1970-01-02 00:00:00 +00:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '-10 23:59:59.999999' DAY TO SECOND,
        INTERVAL '-10-11' YEAR TO MONTH);
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (4, DATE '9999-12-31',
        TIMESTAMP '2038-01-18 23:59:59.999999',
        TIMESTAMP '2038-01-18 23:59:59.999999999',
        NULL,
        TO_TIMESTAMP_TZ('2038-01-18 23:59:59 +14:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '10 23:59:59.999999' DAY TO SECOND,
        INTERVAL '10-11' YEAR TO MONTH);
-- id=5 R_BOUNDARY  (leap day, non-UTC zone)
INSERT INTO e2e_temporal_types (id, date_col, ts6_col, ts9_col, tsltz6_col, tstz9_col, interval_ds_col, interval_ym_col)
VALUES (5, DATE '2024-02-29',
        TIMESTAMP '2024-02-29 12:34:56.123456',
        TIMESTAMP '2024-02-29 12:34:56.123456789',
        NULL,
        TO_TIMESTAMP_TZ('2024-04-20 12:00:00 +09:00', 'YYYY-MM-DD HH24:MI:SS TZH:TZM'),
        INTERVAL '1 02:03:04.567890' DAY TO SECOND,
        INTERVAL '2-6' YEAR TO MONTH);

-- =====================================================================
-- §5.4 e2e_binary_types  (R_NULL, R_EMPTY, R_MIN, R_MAX, R_BOUNDARY)
-- =====================================================================
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (1, NULL, NULL);
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (2, HEXTORAW(''), EMPTY_BLOB());
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (3, HEXTORAW('00'), TO_BLOB(HEXTORAW('00')));
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (4, HEXTORAW('00FF00FF00FF00FF00FF00FF00FF00FF'),
        TO_BLOB(HEXTORAW('00FF00FF00FF00FF')));
INSERT INTO e2e_binary_types (id, raw_col, blob_col)
VALUES (5, HEXTORAW('CAFEBABE'), TO_BLOB(HEXTORAW('CAFEBABE')));

-- =====================================================================
-- §5.5 e2e_oracle_locator_types  (Oracle extension reused)
-- Tibero 의 ROWID 는 컨테이너의 block/slot allocation 에 따라 매 부팅
-- 마다 달라지는 비결정 값이라 (Oracle XE 와 달리) snapshot 비교에
-- 부적합. dump_tree_matches_snapshot 이 매번 첫 글자 1~2 자가 다른
-- ROWID 로 fail. 본 phase 에서는 row 자체는 INSERT 하되 rowid_col 은
-- NULL 로 두어 deterministic dump 를 보장한다.
--
-- ROWID 컬럼 type round-trip (=> CUBRID STRING) 자체는
-- snapshots/.../columns.txt 에서 이미 검증됨. 향후 ROWID
-- 패턴 sanitizer 를 framework 에 추가하면 실제 ROWID 값을 유지한 채
-- 비교 가능 — 그 시점에 SELECT src.ROWID 형태로 복구.
-- =====================================================================
INSERT INTO e2e_oracle_locator_types (id, rowid_col)
VALUES (1, NULL);

COMMIT;
