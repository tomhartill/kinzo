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

Planned for email and phone code sign-in
([spec](../docs/specs/email-and-phone-sign-in.md)), not read yet:

| Variable | Default | Meaning |
| --- | --- | --- |
| `KINZO_OTP_PROVIDER` | `log` | `twilio` sends real codes; `log` prints them to the API log |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN` | empty | Twilio account |
| `TWILIO_VERIFY_SERVICE_SID` | empty | The Verify service that sends SMS and WhatsApp codes |
| `KINZO_PHONE_COUNTRIES` | `GB` | Countries codes can be sent to, comma separated |
| `KINZO_OTP_TEST_NUMBERS` | empty | `+447700900123:123456,...`: numbers that always accept that code and get no message |

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
| `internal/auth` | Google and Facebook sign-in, token helpers (phone codes will go here too) |
| `internal/store` | Database queries: accounts, sessions, friends |
| `internal/database` | Connection pool and migrations |
| `internal/config` | Environment settings |
| `migrations` | SQL schema migrations |
