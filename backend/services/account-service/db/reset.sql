-- =============================================================================
-- account_db — drops EVERYTHING in the account-service schema (incl. Flyway history).
-- DESTRUCTIVE. Pre-production / local use only. Rebuild with db/schema.sql.
-- =============================================================================

DROP TABLE IF EXISTS inbox_message         CASCADE;
DROP TABLE IF EXISTS outbox_message        CASCADE;
DROP TABLE IF EXISTS operation_tombstone   CASCADE;
DROP TABLE IF EXISTS balance_entry         CASCADE;
DROP TABLE IF EXISTS account               CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
