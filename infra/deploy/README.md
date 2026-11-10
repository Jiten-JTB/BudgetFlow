# Deploying Budget Flow (Hostinger VPS + Dokploy)

The whole stack is one Dokploy **Compose** service built from [docker-compose.prod.yml](docker-compose.prod.yml).
Images are built and tested in GitHub Actions ([release.yml](../../.github/workflows/release.yml)) and pulled from GHCR, so the shared VPS never compiles anything.

> Target: Dokploy panel `admin.conceptkart.co.in` (VPS 89.116.21.116). Treat it as shared. Every panel step below is done by the owner, not by automation. Nothing here changes Dokploy itself, its Traefik, or other projects.

| Domain | Container | Port |
|---|---|---|
| `budgetflow.jitenjtb.com` | web | 3000 |
| `budgetflow-api.jitenjtb.com` | api-gateway | 8080 |
| `budgetflow-auth.jitenjtb.com` | identity-service (path allow-list) | 9000 |
| `budgetflow-grafana.jitenjtb.com` | grafana (login required) | 3000 |

Routing is set by Traefik labels in the compose file, not by Dokploy's **Domains** tab. Adding the domains there too would create duplicate routers.

## 0. Preflight (SSH to the VPS; read-only)

```sh
free -h; nproc; df -h /            # stack uses ~1.6 GB at idle; memory caps total ~5 GB
docker network ls | grep dokploy-network
docker exec dokploy-traefik cat /etc/traefik/traefik.yml   # entrypoints web/websecure, certresolver letsencrypt,
                                                          # no forwardedHeaders.insecure (gateway rate limits use the client IP)
grep -rl redirect-to-https /etc/dokploy/traefik/dynamic/  # middleware the HTTP routers use
```

If an entrypoint or resolver name differs, change the labels in the compose file to match before deploying.

## 1. Secrets (once, on your machine)

```sh
infra/deploy/generate-secrets.sh          # writes infra/deploy/.env.prod (git-ignored, mode 600)
```

Store the file in a password manager. Losing `IDENTITY_SIGNING_KEY_PEM` signs everyone out; losing the database passwords means resetting roles by hand.

## 2. Release images

1. Make sure CI is green on `main`, then tag and push it: `v0.1.0`.
2. Wait for the **release** workflow. It reruns all tests, then pushes 7 images: `ghcr.io/jiten-jtb/budgetflow-<name>:v0.1.0`.
3. First release only: GitHub → your profile → Packages → each `budgetflow-*` package → Package settings → Change visibility → **Public**. The repo is public, so this exposes nothing new. The alternative is adding a GHCR registry with a `read:packages` token in Dokploy.

## 3. Create the service in Dokploy (first time)

1. Projects → **Create Project**: `Budget Flow`.
2. Inside it → **Create Service → Compose**: name `budgetflow`, compose type **Docker Compose**.
3. Provider: **Git**, repository `https://github.com/Jiten-JTB/BudgetFlow.git`, branch `main`, compose path `./infra/deploy/docker-compose.prod.yml`.
4. **Environment**: paste `.env.prod` and set `IMAGE_TAG=v0.1.0`.
5. Leave **Domains** empty. Leave auto-deploy off: a push to `main` must not deploy by itself.
6. **Deploy**. The first start takes about 2 minutes. Postgres creates the 5 databases and roles, then Flyway creates every schema.

## 4. Verify

```sh
curl -sI http://budgetflow.jitenjtb.com | head -1                                     # 301 → https
curl -s https://budgetflow-auth.jitenjtb.com/.well-known/openid-configuration | jq -r .issuer
                                                                                      # https://budgetflow-auth.jitenjtb.com
curl -s -o /dev/null -w '%{http_code}\n' https://budgetflow-auth.jitenjtb.com/api/v1/users   # 404 (allow-list)
curl -s -o /dev/null -w '%{http_code}\n' https://budgetflow-api.jitenjtb.com/api/v1/accounts # 401
```

Then sign up in the browser, add an account and an expense, and check that the balance moves.

## 5. Upgrade and roll back

- **Upgrade**: release a new tag, change `IMAGE_TAG` in Dokploy's Environment, and Deploy. Flyway migrations run on start.
- **Roll back**: set the previous tag and Deploy. This is safe only if the newer release added no destructive migration. Keep migrations additive, and flag any that are not in the release notes.

## 6. Backups

The `postgres-backup` container dumps every database daily, plus the roles, into the `postgres-backups` volume. It keeps `BACKUP_RETENTION_DAYS` days of dumps (default 14).

These dumps sit on the same disk as the database. Copy them off the VPS on a schedule. You can use Dokploy's volume backups to S3 if the panel owner agrees, or `scp` from your machine:

```sh
docker run --rm -v budgetflow_postgres-backups:/b alpine ls -lh /b      # volume name = <dokploy app name>_postgres-backups
```

To restore one database, for example `account_db` from a given dump:

```sh
docker exec -it <postgres-container> sh -c \
  'pg_restore --clean --if-exists --no-owner --role=account_svc -U "$POSTGRES_USER" -d account_db /path/to/account_db-<ts>.dump'
```

Before restoring, stop the service that owns that database, then start it again afterwards. A restore from these dumps into a fresh database was tested locally.

## 7. Rotating secrets

- **Signing key**:
  1. Generate a new key and a new `IDENTITY_SIGNING_KEY_ID`.
  2. Deploy.
  3. Existing tokens stop verifying, so users sign in again. The web app redirects them automatically.
- **Web client secret**: change `IDENTITY_WEB_CLIENT_SECRET`, then Deploy. Web and identity read the same variable.
- **Session secret**: change `WEB_SESSION_SECRET`, then Deploy. Everyone is signed out.
- **Database password**:
  1. Run `ALTER ROLE <svc>_svc PASSWORD '<new>'` as the admin.
  2. Update the variable.
  3. Deploy.

## 8. Operations

- **Logs**: Dokploy → service → Logs. Docker rotates them at 3 × 10 MB per container.
- **Health**: `docker ps` shows the healthcheck state. Actuator listens on the management ports, which are internal only.
- **Postgres and RabbitMQ access**: `docker exec` into the container over SSH. Nothing is published on the host.

## 9. Monitoring (Grafana)

`https://budgetflow-grafana.jitenjtb.com`: sign in as `admin` with `GRAFANA_ADMIN_PASSWORD`. Sign-up and anonymous access are off. The **Budget Flow → Budget Flow overview** dashboard shows:
- **Health and traffic:** services up, request rate, 4xx and 5xx rates, p95 latency.
- **Sagas and messaging:** failed sagas, dead-letter queues, queue depth.
- **Resources:** heap, CPU, DB connections, GC pause time.
- **Logs:** error logs and all logs.

How it works:
- **Metrics:** Prometheus scrapes each service's management port over the private network, and RabbitMQ per queue. It keeps 15 days of data, capped at 2 GB.
  - Actuator answers anonymously only on the management port, checked by the connection's local port. The public ports still return 401.
- **Logs:** Alloy reads container logs through the Docker socket (read-only), keeping only this compose project's containers, and ships them to Loki, which keeps 14 days.
  - JVM services log ECS JSON, so `level` is a label. For example: `{service="orchestrator-service", level="ERROR"}`.
- **Dashboards and data sources** are provisioned from `observability/` and read-only in the UI. To change one, edit the JSON and redeploy.

Adding it to an existing deployment:
1. Add `GRAFANA_ADMIN_PASSWORD=<openssl rand -hex 32>` to Dokploy's Environment.
2. Release a tag that includes the management-port change (v0.1.1 or later) and set `IMAGE_TAG` to it.
3. Deploy.

Not set up yet: alert notifications (they need a contact point such as email or Telegram) and traces (Tempo/OTel).

## Local verification

`infra/deploy/test-deploy.sh` renders the compose file with throwaway secrets and checks the rules in architecture.md §9.
