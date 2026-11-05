-- =============================================================================
-- transaction_db — drops EVERYTHING in the transaction-service schema (incl. Flyway history).
-- DESTRUCTIVE. Pre-production / local use only. Rebuild with db/schema.sql.
-- =============================================================================

DROP TABLE IF EXISTS inbox_message         CASCADE;
DROP TABLE IF EXISTS outbox_message        CASCADE;
DROP TABLE IF EXISTS money_transaction     CASCADE;
DROP TABLE IF EXISTS income_source         CASCADE;
DROP TABLE IF EXISTS category              CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
