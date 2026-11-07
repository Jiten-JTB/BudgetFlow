#!/usr/bin/env bash
# Generates every production secret once. Usage: infra/deploy/generate-secrets.sh [out-file]
# Paste the result into Dokploy (Compose service → Environment). Keep a copy in a password manager; never commit it.
set -euo pipefail

out="${1:-$(dirname "$0")/.env.prod}"
if [ -e "$out" ]; then
  echo "refusing to overwrite $out (rotating secrets needs a plan, see README.md)" >&2
  exit 1
fi
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

secret() { openssl rand -hex 32; }   # hex: safe in URLs, SQL literals and .env files without quoting
# PKCS#8 RSA key as one base64 line: identity-service strips PEM headers and whitespace.
signing_key="$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 2>/dev/null \
  | grep -v -- '-----' | tr -d '\n')"

umask 077
cat >"$out" <<EOF
# Budget Flow production environment. Generated $(date -u +%Y-%m-%dT%H:%MZ).
# Release to deploy: a tag built by .github/workflows/release.yml, e.g. v1.0.0
IMAGE_TAG=

POSTGRES_ADMIN_USER=budgetflow_admin
POSTGRES_ADMIN_PASSWORD=$(secret)
IDENTITY_DB_PASSWORD=$(secret)
ACCOUNT_DB_PASSWORD=$(secret)
TRANSACTION_DB_PASSWORD=$(secret)
BUDGET_DB_PASSWORD=$(secret)
ORCHESTRATOR_DB_PASSWORD=$(secret)

RABBITMQ_USER=budgetflow
RABBITMQ_PASSWORD=$(secret)
REDIS_PASSWORD=$(secret)

IDENTITY_WEB_CLIENT_SECRET=$(secret)
WEB_SESSION_SECRET=$(secret)
IDENTITY_SIGNING_KEY_ID=budgetflow-$(date -u +%Y-%m)
IDENTITY_SIGNING_KEY_PEM=${signing_key}

# Days of daily database dumps kept on the VPS
BACKUP_RETENTION_DAYS=14
EOF
chmod 600 "$out"
echo "wrote $out"
