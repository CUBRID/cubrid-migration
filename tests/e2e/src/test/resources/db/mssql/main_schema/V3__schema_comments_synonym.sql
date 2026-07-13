-- ============================================================
-- e2e_db (SQL Server): extended-property comments + a synonym.
-- CMT reads MS_Description extended properties as table/column comments, and
-- sys.synonyms as CUBRID synonyms.
-- ============================================================

EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'customer master',
    @level0type = N'SCHEMA', @level0name = N'dbo',
    @level1type = N'TABLE', @level1name = N'e2e_customer';

EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'business customer name',
    @level0type = N'SCHEMA', @level0name = N'dbo',
    @level1type = N'TABLE', @level1name = N'e2e_customer',
    @level2type = N'COLUMN', @level2name = N'customer_name';

CREATE SYNONYM syn_e2e_customer FOR dbo.e2e_customer;
