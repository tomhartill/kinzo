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

## Checks

```sh
go vet ./...
go test ./...
```

## Layout

| Path | What lives there |
| --- | --- |
| `cmd/kinzo-api` | `main`: config, database, HTTP server, graceful shutdown |
| `internal/server` | Routes and handlers |
| `internal/database` | Connection pool and migrations |
| `internal/config` | Environment settings |
| `migrations` | SQL schema migrations |
