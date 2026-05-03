-- ============================================================
-- MAIN_SCHEMA: views
-- Per docs/seed/cubrid/SEED_SPEC.md §4.2
-- ============================================================

CREATE VIEW e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM   MAIN_SCHEMA.e2e_order    o
INNER JOIN MAIN_SCHEMA.e2e_customer c
    ON c.customer_id = o.customer_id;
