-- ============================================================
-- e2e_db (SQL Server): a view. T-SQL requires CREATE VIEW to be the only
-- statement in its batch, so this file uses slash-mode ('/' on its own line
-- ends a statement); the runner strips the '/' and sends the batch alone.
-- ============================================================

CREATE VIEW v_customer_summary AS
    SELECT customer_id, customer_code, customer_name, status
    FROM dbo.e2e_customer;
/
