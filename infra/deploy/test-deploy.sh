#!/usr/bin/env bash
# Static checks for the production stack. Usage: infra/deploy/test-deploy.sh
# Renders docker-compose.prod.yml with throwaway secrets and asserts the deployment rules in architecture.md §9.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fails=0
pass() { printf '  ok   %s\n' "$1"; }
fail() { printf '  FAIL %s\n' "$1"; fails=$((fails + 1)); }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

echo "generate-secrets.sh"
"$here/generate-secrets.sh" "$work/.env.prod" >/dev/null
env_file="$work/.env.prod"
check "writes the env file with owner-only permissions" '[ "$(stat -c %a "$env_file")" = 600 ]'
check "refuses to overwrite an existing env file" '! "$here/generate-secrets.sh" "$env_file" >/dev/null 2>&1'
for v in GRAFANA_ADMIN_PASSWORD POSTGRES_ADMIN_PASSWORD IDENTITY_DB_PASSWORD ACCOUNT_DB_PASSWORD TRANSACTION_DB_PASSWORD BUDGET_DB_PASSWORD \
         ORCHESTRATOR_DB_PASSWORD RABBITMQ_PASSWORD REDIS_PASSWORD IDENTITY_WEB_CLIENT_SECRET WEB_SESSION_SECRET IDENTITY_SIGNING_KEY_PEM; do
  check "sets $v" "grep -Eq '^$v=.{32,}' '$env_file'"
done
db_pw_count=$(grep -E '^[A-Z]+_DB_PASSWORD=' "$env_file" | cut -d= -f2 | sort -u | wc -l)
check "every service database has its own password" '[ "$db_pw_count" = 5 ]'
key_line=$(grep '^IDENTITY_SIGNING_KEY_PEM=' "$env_file" | cut -d= -f2-)
check "signing key is a single-line PKCS#8 RSA key" \
  'printf "%s\n" "-----BEGIN PRIVATE KEY-----" "$key_line" "-----END PRIVATE KEY-----" | fold -w 64 | openssl pkey -noout 2>/dev/null'
check "leaves a slot for the mailbox password (it can't be generated)" "grep -q '^SMTP_PASSWORD=$' '$env_file'"
check "signing key id is dated" "grep -Eq '^IDENTITY_SIGNING_KEY_ID=budgetflow-[0-9]{4}-[0-9]{2}$' '$env_file'"

echo "docker-compose.prod.yml"
cfg="$work/config.json"
IMAGE_TAG=test SMTP_PASSWORD=test-mailbox-password docker compose -f "$here/docker-compose.prod.yml" --env-file "$env_file" config --format json >"$cfg"
q() { jq -r "$1" "$cfg"; }

services=$(q '.services | keys[]' | sort | tr '\n' ' ')
check "runs the full stack" '[ "$services" = "account-service alloy api-gateway budget-service grafana identity-service loki orchestrator-service postgres postgres-backup prometheus rabbitmq redis transaction-service web " ]'
check "publishes no host ports (Traefik is the only way in)" '[ "$(q "[.services[] | select(.ports != null and (.ports | length) > 0)] | length")" = 0 ]'
check "app images come from GHCR at IMAGE_TAG" \
  '[ "$(q "[.services[] | select(.image | startswith(\"ghcr.io/\")) | select(.image | endswith(\":test\"))] | length")" = 7 ]'
check "nothing is built on the VPS" '[ "$(q "[.services[] | select(.build != null)] | length")" = 0 ]'
check "every service has a memory limit" '[ "$(q "[.services[] | select(.deploy.resources.limits.memory == null)] | length")" = 0 ]'
check "every service restarts on failure" '[ "$(q "[.services[] | select(.restart != \"unless-stopped\")] | length")" = 0 ]'
check "only web, gateway and identity join dokploy-network" \
  '[ "$(q "[.services | to_entries[] | select(.value.networks | has(\"dokploy-network\")) | .key] | sort | join(\",\")")" = "api-gateway,grafana,identity-service,web" ]'
check "dokploy-network is external" '[ "$(q ".networks[\"dokploy-network\"].external")" = true ]'
check "internal traffic uses budgetflow-prefixed aliases (no clashes on the shared network)" \
  '! q ".services[].environment // {} | to_entries[] | .value" | grep -Eq "//(postgres|rabbitmq|redis|identity-service|account-service|transaction-service|budget-service|api-gateway)[:/]"'

labels() { q ".services[\"$1\"].labels // {} | to_entries[] | \"\(.key)=\(.value)\""; }
check "web is routed at budgetflow.jitenjtb.com:3000" \
  'labels web | grep -q "rule=Host(\`budgetflow.jitenjtb.com\`)" && labels web | grep -q "loadbalancer.server.port=3000"'
check "gateway is routed at budgetflow-api.jitenjtb.com:8080" \
  'labels api-gateway | grep -q "rule=Host(\`budgetflow-api.jitenjtb.com\`)" && labels api-gateway | grep -q "loadbalancer.server.port=8080"'
auth_rule=$(labels identity-service | grep 'routers.budgetflow-auth.rule=' | cut -d= -f2-)
for p in "Path(\`/\`)" "PathPrefix(\`/oauth2/\`)" "PathPrefix(\`/.well-known/\`)" "PathPrefix(\`/connect/\`)" "Path(\`/userinfo\`)" "Path(\`/login\`)" \
         "Path(\`/logout\`)" "Path(\`/error\`)" "PathPrefix(\`/css/\`)" "PathPrefix(\`/fonts/\`)"; do
  check "auth domain allows $p" '[[ "$auth_rule" == *"$p"* ]]'
done
check "auth domain does not expose /api" '[[ "$auth_rule" != *"/api"* ]]'
check "no router targets a management port" \
  '! q ".services[].labels // {} | to_entries[] | select(.key | test(\"loadbalancer.server.port\")) | .value" | grep -Eq "^(8081|8111|8121|8131|8141|9001)$"'
check "every public router uses TLS via letsencrypt" \
  '[ "$(q "[.services[].labels // {} | to_entries[] | select(.key | test(\"routers\\\\.[a-z-]+-secure\\\\.tls\\\\.certresolver\"))] | length")" = 4 ]'
check "plain HTTP redirects to HTTPS" \
  '[ "$(q "[.services[].labels // {} | to_entries[] | select(.key | test(\"routers\\\\.[a-z-]+\\\\.middlewares$\")) | select(.value | test(\"redirect-to-https\"))] | length")" = 4 ]'

check "identity uses the public issuer and a real signing key" \
  '[ "$(q ".services[\"identity-service\"].environment.IDENTITY_ISSUER")" = https://budgetflow-auth.jitenjtb.com ] && [ -n "$(q ".services[\"identity-service\"].environment.IDENTITY_SIGNING_KEY_PEM")" ]'
check "identity runs without the local profile" '[ "$(q ".services[\"identity-service\"].environment.SPRING_PROFILES_ACTIVE // \"\"")" != local ]'
check "resource servers trust the public issuer" \
  '[ "$(q "[.services[] | .environment.IDENTITY_ISSUER // empty] | unique | join(\",\")")" = https://budgetflow-auth.jitenjtb.com ]'
check "web uses secure cookies and public URLs" \
  '[ "$(q ".services.web.environment.SECURE_COOKIES")" = true ] && [ "$(q ".services.web.environment.APP_URL")" = https://budgetflow.jitenjtb.com ]'
check "web callback is registered with identity" \
  '[ "$(q ".services[\"identity-service\"].environment.IDENTITY_WEB_REDIRECT_URIS")" = https://budgetflow.jitenjtb.com/api/auth/callback ]'
check "web and identity share the client secret" \
  '[ "$(q ".services.web.environment.AUTH_CLIENT_SECRET")" = "$(q ".services[\"identity-service\"].environment.IDENTITY_WEB_CLIENT_SECRET")" ]'
check "gateway CORS allows only the web origin" '[ "$(q ".services[\"api-gateway\"].environment.GATEWAY_CORS_ALLOWED_ORIGINS")" = https://budgetflow.jitenjtb.com ]'
check "postgres, rabbitmq and redis data are on named volumes" \
  '[ "$(q "[.volumes | keys[]] | sort | join(\",\")")" = "grafana-data,loki-data,postgres-backups,postgres-data,prometheus-data,rabbitmq-data,redis-data" ]'
echo "email"
idv() { q ".services[\"identity-service\"].environment.$1 // empty"; }
check "identity sends mail through Hostinger over SSL" \
  '[ "$(idv SPRING_MAIL_HOST)" = smtp.hostinger.com ] && [ "$(idv SPRING_MAIL_PORT)" = 465 ] && [ "$(idv SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE)" = true ]'
check "identity signs in to the mailbox as app@jitenjtb.com" '[ "$(idv SPRING_MAIL_USERNAME)" = app@jitenjtb.com ] && [ "$(idv SPRING_MAIL_PASSWORD)" = test-mailbox-password ]'
check "emails come from the same address" '[[ "$(idv IDENTITY_MAIL_FROM)" == *"<app@jitenjtb.com>" ]]'
check "verification links open the web app" '[ "$(idv IDENTITY_WEB_VERIFY_EMAIL_URL)" = https://budgetflow.jitenjtb.com/verify-email ]'
check "email verification is required" '[ "$(idv IDENTITY_EMAIL_VERIFICATION_REQUIRED)" = true ]'
check "compose refuses to start without the mailbox password" \
  '! IMAGE_TAG=test docker compose -f "$here/docker-compose.prod.yml" --env-file "$env_file" config >/dev/null 2>&1'

echo "observability"
check "grafana is routed at budgetflow-grafana.jitenjtb.com:3000" \
  'labels grafana | grep -q "rule=Host(\`budgetflow-grafana.jitenjtb.com\`)" && labels grafana | grep -q "loadbalancer.server.port=3000"'
for s in prometheus loki alloy; do
  check "$s is not routed" '[ -z "$(labels $s | grep traefik)" ]'
done
g() { q ".services.grafana.environment.$1 // empty"; }
check "grafana requires an admin password" '[ -n "$(g GF_SECURITY_ADMIN_PASSWORD)" ] && [ "$(g GF_SECURITY_ADMIN_PASSWORD)" != admin ]'
check "grafana has no sign-up, no anonymous access, secure cookies" \
  '[ "$(g GF_USERS_ALLOW_SIGN_UP)" = false ] && [ "$(g GF_AUTH_ANONYMOUS_ENABLED)" = false ] && [ "$(g GF_SECURITY_COOKIE_SECURE)" = true ]'
check "grafana knows its public URL" '[ "$(g GF_SERVER_ROOT_URL)" = https://budgetflow-grafana.jitenjtb.com ]'
check "alloy reads the docker socket read-only" \
  '[ "$(q ".services.alloy.volumes[] | select(.source == \"/var/run/docker.sock\") | .read_only")" = true ]'
check "alloy only collects this stack's logs" '[ -n "$(q ".services.alloy.environment.BUDGETFLOW_COMPOSE_PROJECT // empty")" ]'
check "every JVM service logs structured JSON" \
  '[ "$(q "[.services[] | select(.image | test(\"budgetflow-(identity|account|transaction|budget|orchestrator)-service|api-gateway\")) | select(.environment.LOGGING_STRUCTURED_FORMAT_CONSOLE == \"ecs\")] | length")" = 6 ]'
check "every JVM service records latency histograms" \
  '[ "$(q "[.services[] | select(.environment.SPRING_APPLICATION_JSON // \"\" | test(\"percentiles-histogram\"))] | length")" = 6 ]'
check "orchestrator has an internal alias for scraping" '[ "$(q ".services[\"orchestrator-service\"].networks.internal.aliases[0]")" = budgetflow-orchestrator ]'
prom="$here/observability/prometheus.yml"
for t in budgetflow-identity:9001 budgetflow-account:8111 budgetflow-transaction:8121 budgetflow-budget:8131 budgetflow-orchestrator:8141 budgetflow-gateway:8081 budgetflow-rabbitmq:15692; do
  check "prometheus scrapes $t" "grep -q '$t' '$prom'"
done
check "dashboards are valid JSON with a uid" \
  '(set -- "$here"/observability/grafana/dashboards/*.json; [ -f "$1" ] && for f; do jq -e .uid "$f" >/dev/null || exit 1; done)'

check "compose refuses to start without IMAGE_TAG" \
  '! docker compose -f "$here/docker-compose.prod.yml" --env-file "$env_file" config >/dev/null 2>&1'

echo
if [ "$fails" -gt 0 ]; then echo "$fails check(s) failed"; exit 1; fi
echo "all checks passed"
