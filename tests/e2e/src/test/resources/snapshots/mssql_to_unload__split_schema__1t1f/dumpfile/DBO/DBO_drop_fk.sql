ALTER TABLE [DBO].[e2e_employee] DROP CONSTRAINT [fk_e2e_employee_manager];
ALTER TABLE [DBO].[e2e_order] DROP CONSTRAINT [fk_e2e_order_customer];
ALTER TABLE [DBO].[e2e_order_line] DROP CONSTRAINT [fk_e2e_order_line_order];
