-- =============================================================================
-- orchestrator_db — drops EVERYTHING in the orchestrator-service schema (incl. Flyway history).
-- DESTRUCTIVE. Pre-production / local use only. Rebuild with db/schema.sql.
-- =============================================================================

DROP TABLE IF EXISTS inbox_message         CASCADE;
DROP TABLE IF EXISTS outbox_message        CASCADE;
DROP TABLE IF EXISTS saga_event            CASCADE;
DROP TABLE IF EXISTS saga_instance         CASCADE;
DROP TABLE IF EXISTS flyway_schema_history CASCADE;
