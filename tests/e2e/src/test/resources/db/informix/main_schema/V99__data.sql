-- ============================================================
-- e2e_db (Informix): seed data. ASCII only. Informix literals:
--   DATE      -> MDY(month, day, year)   (locale-independent)
--   DATETIME  -> DATETIME (...) YEAR TO ...
--   BOOLEAN   -> 't' / 'f'
-- Numeric minimums avoid Informix's NULL-sentinel values
-- (SMALLINT -32768 / INTEGER -2147483648 / BIGINT -9223372036854775808).
-- ============================================================

-- e2e_customer
INSERT INTO e2e_customer VALUES (1, 'C001', 'ALPHA CUSTOMER', 'Alpha Alias', 'A', 12500.75, MDY(1, 10, 2024), MDY(6, 1, 2024));
INSERT INTO e2e_customer VALUES (2, 'C002', 'BETA CUSTOMER', NULL, 'I', NULL, MDY(2, 29, 2024), NULL);
INSERT INTO e2e_customer VALUES (3, 'C003', 'GAMMA CUSTOMER', 'Gamma', 'D', 0, MDY(3, 15, 2024), NULL);
INSERT INTO e2e_customer VALUES (4, 'C004', 'HIGH LIMIT CUST', 'High', 'A', 9999999999999.99, MDY(4, 20, 2024), MDY(7, 1, 2024));

-- e2e_order
INSERT INTO e2e_order VALUES (1000, 1, 'ORD-1000', 'DONE', 500.00, MDY(1, 11, 2024), MDY(1, 20, 2024), 'first order');
INSERT INTO e2e_order VALUES (1001, 1, 'ORD-1001', 'NEW', 1200.50, MDY(2, 1, 2024), NULL, NULL);
INSERT INTO e2e_order VALUES (1002, 2, 'ORD-1002', 'HOLD', 0.00, MDY(3, 1, 2024), NULL, 'on hold');
INSERT INTO e2e_order VALUES (1003, 3, 'ORD-1003', 'CANCEL', 75.25, MDY(3, 20, 2024), NULL, NULL);

-- e2e_order_line
INSERT INTO e2e_order_line VALUES (1000, 1, 'SKU-A', 2, 100.00, NULL);
INSERT INTO e2e_order_line VALUES (1000, 2, 'SKU-B', 3, 100.00, 'bulk');
INSERT INTO e2e_order_line VALUES (1001, 1, 'SKU-C', 1, 1200.50, NULL);
INSERT INTO e2e_order_line VALUES (1002, 1, 'SKU-A', 5, 0.00, 'free');

-- e2e_employee (self-referencing manager_id; insert roots first)
INSERT INTO e2e_employee VALUES (1, NULL, 'ROOT MANAGER', MDY(1, 1, 2020));
INSERT INTO e2e_employee VALUES (2, 1, 'ALICE', MDY(3, 15, 2021));
INSERT INTO e2e_employee VALUES (3, 1, 'BOB', MDY(6, 1, 2022));
INSERT INTO e2e_employee VALUES (4, 2, 'CAROL', MDY(9, 10, 2023));

-- e2e_text_types
INSERT INTO e2e_text_types VALUES (1, 'CHAR10', 'varchar value', 'lvarchar long value');
INSERT INTO e2e_text_types VALUES (2, NULL, NULL, NULL);
INSERT INTO e2e_text_types VALUES (3, 'AB', 'x', 'y');

-- e2e_numeric_types
INSERT INTO e2e_numeric_types VALUES (1, 32767, 2147483647, 9223372036854775807, 12345.6789, 99999999999999.99, 1.5, 2.718281828, 't');
INSERT INTO e2e_numeric_types VALUES (2, -32767, -2147483647, -9223372036854775807, -12345.6789, -1.00, -1.5, -3.14159, 'f');
INSERT INTO e2e_numeric_types VALUES (3, 0, 0, 0, 0, 0, 0, 0, NULL);

-- e2e_temporal_types
INSERT INTO e2e_temporal_types VALUES (1, MDY(1, 10, 2024), DATETIME (2024-01-10 08:30:00) YEAR TO SECOND, DATETIME (2024-02-29 12:34:56.12345) YEAR TO FRACTION(5));
INSERT INTO e2e_temporal_types VALUES (2, NULL, NULL, NULL);
INSERT INTO e2e_temporal_types VALUES (3, MDY(12, 31, 2023), DATETIME (2023-12-31 23:59:59) YEAR TO SECOND, DATETIME (2023-12-31 23:59:59.99999) YEAR TO FRACTION(5));
