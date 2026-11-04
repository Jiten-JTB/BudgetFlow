-- =============================================================================
-- identity_db — drops EVERYTHING in the identity-service schema (incl. Flyway history).
-- DESTRUCTIVE. Pre-production / local use only. Rebuild with db/schema.sql.
-- =============================================================================

DROP TABLE IF EXISTS inbox_message                CASCADE;
DROP TABLE IF EXISTS outbox_message               CASCADE;
DROP TABLE IF EXISTS oauth2_authorization_consent CASCADE;
DROP TABLE IF EXISTS oauth2_authorization         CASCADE;
DROP TABLE IF EXISTS oauth2_registered_client     CASCADE;
DROP TABLE IF EXISTS auth_audit_event             CASCADE;
DROP TABLE IF EXISTS users                        CASCADE;
DROP TABLE IF EXISTS flyway_schema_history        CASCADE;
