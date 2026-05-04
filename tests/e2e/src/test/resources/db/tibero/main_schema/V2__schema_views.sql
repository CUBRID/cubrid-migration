-- ============================================================
-- MAIN_SCHEMA: views (per tibero/SEED_SPEC.md §4.2 — Oracle SPEC §4.2 reused).
--
-- Schema-qualified table references in the view body are intentional: they
-- prevent the view from depending on the connection user that later opens it.
-- ============================================================

CREATE OR REPLACE VIEW e2e_order_summary_v AS
SELECT o.order_id,
       o.order_no,
       c.customer_code,
       c.customer_name,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM   MAIN_SCHEMA.e2e_order    o
JOIN   MAIN_SCHEMA.e2e_customer c
       ON c.customer_id = o.customer_id;

COMMENT ON TABLE  e2e_order_summary_v               IS 'Customer-order join summary view';
COMMENT ON COLUMN e2e_order_summary_v.customer_code IS 'Customer business key (from e2e_customer)';
