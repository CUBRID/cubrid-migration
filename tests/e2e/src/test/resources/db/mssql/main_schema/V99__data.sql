-- ============================================================
-- e2e_db (SQL Server): seed data. ISO date/time literals, N'..' Unicode
-- (multilingual NVARCHAR), and 0x.. binary literals.
-- ============================================================

-- e2e_customer (customer_alias NVARCHAR carries multilingual text)
INSERT INTO e2e_customer VALUES (1, 'C001', 'ALPHA CUSTOMER', N'한국 고객', 'A', 12500.75, '2024-01-10', '2024-06-01');
INSERT INTO e2e_customer VALUES (2, 'C002', 'BETA CUSTOMER', NULL, 'I', NULL, '2024-02-29', NULL);
INSERT INTO e2e_customer VALUES (3, 'C003', 'GAMMA CUSTOMER', N'별칭', 'D', 0, '2024-03-15', NULL);
INSERT INTO e2e_customer VALUES (4, 'C004', 'HIGH LIMIT CUST', N'高い顧客', 'A', 9999999999999.99, '2024-04-20', '2024-07-01');

-- e2e_order
INSERT INTO e2e_order VALUES (1000, 1, 'ORD-1000', 'DONE', 500.00, '2024-01-11', '2024-01-20', 'first order');
INSERT INTO e2e_order VALUES (1001, 1, 'ORD-1001', 'NEW', 1200.50, '2024-02-01', NULL, NULL);
INSERT INTO e2e_order VALUES (1002, 2, 'ORD-1002', 'HOLD', 0.00, '2024-03-01', NULL, 'on hold');
INSERT INTO e2e_order VALUES (1003, 3, 'ORD-1003', 'CANCEL', 75.25, '2024-03-20', NULL, NULL);

-- e2e_order_line
INSERT INTO e2e_order_line VALUES (1000, 1, 'SKU-A', 2, 100.00, NULL);
INSERT INTO e2e_order_line VALUES (1000, 2, 'SKU-B', 3, 100.00, 'bulk');
INSERT INTO e2e_order_line VALUES (1001, 1, 'SKU-C', 1, 1200.50, NULL);
INSERT INTO e2e_order_line VALUES (1002, 1, 'SKU-A', 5, 0.00, 'free');

-- e2e_employee (self-referencing manager_id; insert roots first)
INSERT INTO e2e_employee VALUES (1, NULL, 'ROOT MANAGER', '2020-01-01');
INSERT INTO e2e_employee VALUES (2, 1, 'ALICE', '2021-03-15');
INSERT INTO e2e_employee VALUES (3, 1, 'BOB', '2022-06-01');
INSERT INTO e2e_employee VALUES (4, 2, 'CAROL', '2023-09-10');

-- e2e_text_types
INSERT INTO e2e_text_types VALUES (1, 'CHAR10', 'varchar value', N'NCHAR', N'nvarchar 한글');
INSERT INTO e2e_text_types VALUES (2, NULL, NULL, NULL, NULL);
INSERT INTO e2e_text_types VALUES (3, 'AB', 'x', N'Y', N'z');

-- e2e_numeric_types (full SQL Server ranges; money/smallmoney at their limits)
INSERT INTO e2e_numeric_types VALUES (1, 1, 255, 32767, 2147483647, 9223372036854775807, 12345.6789, 123456.78, 922337203685477.5807, 214748.3647, 1.5, 1.5);
INSERT INTO e2e_numeric_types VALUES (2, 0, 0, -32768, -2147483648, -9223372036854775808, -12345.6789, -1.00, -922337203685477.5808, -214748.3648, -3.14159, -3.14);
INSERT INTO e2e_numeric_types VALUES (3, NULL, NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- e2e_temporal_types (TIME/DATETIME2 omitted; see V2 note on the CMT converter bug)
INSERT INTO e2e_temporal_types VALUES (1, '2024-01-10', '2024-01-10 08:30:00', '2024-01-10 08:30:00');
INSERT INTO e2e_temporal_types VALUES (2, NULL, NULL, NULL);
INSERT INTO e2e_temporal_types VALUES (3, '2023-12-31', '2023-12-31 23:59:59', '2023-12-31 23:59:00');

-- e2e_binary_types
INSERT INTO e2e_binary_types VALUES (1, 0x01020304, 0xDEADBEEF);
INSERT INTO e2e_binary_types VALUES (2, NULL, NULL);
INSERT INTO e2e_binary_types VALUES (3, 0x00000000, 0xFF);
