# Kinzo API

Go HTTP API backed by Postgres. The contract is [`../openapi/kinzo.yaml`](../openapi/kinzo.yaml).

## Run locally

```sh
docker compose up db                       # from the repo root
cd api
DATABASE_URL="postgres://kinzo:kinzo@localhost:5432/kinzo?sslmode=disable" go run ./cmd/kinzo-api
curl localhost:8080/healthz
```

Migrations in `migrations/` are embedded in the binary and applied on start
([goose](https://github.com/pressly/goose) format: `-- +goose Up` / `-- +goose Down`).
Add a new numbered file for every schema change; never edit one that has shipped.

## Settings

| Variable | Default | Meaning |
| --- | --- | --- |
| `DATABASE_URL` | required | Postgres connection string |
| `KINZO_ADDR` | `:8080` | Listen address |
| `KINZO_PUBLIC_URL` | `http://localhost:8080` | Where phones reach the API. Google and Facebook send people back here after sign-in |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | empty (Google off) | The "Web application" OAuth client |
| `GOOGLE_EXTRA_AUDIENCES` | empty | Other Google client IDs whose ID tokens to accept, comma separated |
| `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` | empty (Facebook off) | The Facebook app |
| `FACEBOOK_GRAPH_VERSION` | `v24.0` | Graph API version |

Setting these up is a one-off; [`docs/sign-in.md`](../docs/sign-in.md) walks through it.

## Checks

```sh
go vet ./...
go test ./...
# The account and friends tests need a throwaway Postgres (they wipe it):
KINZO_TEST_DATABASE_URL="postgres://kinzo:kinzo@localhost:5432/kinzo?sslmode=disable" go test ./...
```

## Layout

| Path | What lives there |
| --- | --- |
| `cmd/kinzo-api` | `main`: config, database, HTTP server, graceful shutdown |
| `internal/server` | Routes and handlers |
| `internal/auth` | Google and Facebook sign-in, token helpers |
| `internal/store` | Database queries: accounts, sessions, friends |
| `internal/database` | Connection pool and migrations |
| `internal/config` | Environment settings |
| `migrations` | SQL schema migrations |
