-- ============================================================
-- e2e_db (SQL Server): stored routines (one function + one procedure).
-- Slash-mode keeps each CREATE in its own batch. CMT does not migrate MSSQL
-- procedures/functions (the base fetcher has no-op builders), so these serve
-- as a documented baseline: the functions/procedures snapshots stay empty.
-- ============================================================

CREATE FUNCTION dbo.fn_customer_label(@p_id INT) RETURNS VARCHAR(200)
AS
BEGIN
    DECLARE @label VARCHAR(200);
    SELECT @label = customer_code + ':' + customer_name
        FROM dbo.e2e_customer WHERE customer_id = @p_id;
    RETURN @label;
END;
/

CREATE PROCEDURE dbo.usp_upsert_customer @p_id INT, @p_name VARCHAR(100)
AS
BEGIN
    UPDATE dbo.e2e_customer SET customer_name = @p_name WHERE customer_id = @p_id;
END;
/
