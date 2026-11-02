#!/bin/sh
# Database-per-service on one Postgres instance: each service owns its DB and has no access to others.
# Runs only on first container start (empty data volume).
set -eu

for service in identity account transaction budget orchestrator notification reporting; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE ${service}_svc LOGIN PASSWORD '${SERVICE_DB_PASSWORD}';
CREATE DATABASE ${service}_db OWNER ${service}_svc;
REVOKE ALL ON DATABASE ${service}_db FROM PUBLIC;
SQL
done
