-- =============================================================================
-- budget_db — drops EVERYTHING in the budget-service schema (incl. Flyway history).
-- DESTRUCTIVE. Pre-production / local use only. Rebuild with db/schema.sql.
-- =============================================================================

DROP TABLE IF EXISTS inbox_message         CASCADE;
DROP TABLE IF EXISTS outbox_message        CASCADE;
DROP TABLE IF EXISTS budget_operation      CASCADE;
DROP TABLE IF EXISTS budget_consumption    CASCADE;
DROP TABLE IF EXISTS budget_period_usage   CASCADE;
DROP TABLE IF EXISTS budget                CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
