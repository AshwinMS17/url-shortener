# url-shortener

A URL shortener REST service with click analytics.

**Stack:** Java 17 · Spring Boot 3.3.4 · Maven
**Target deployment:** Kubernetes + AWS (DynamoDB)

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full spec and the phased (Day 1–5) roadmap.

## Status

| Phase | Scope | Status |
|-------|-------|--------|
| Day 1–2 | In-memory REST API: shorten, redirect, stats, validation, error handling | ✅ Done |
| Day 3 | DynamoDB persistence behind the `dynamodb` Spring profile | ⬜ |
| Day 4 | API-key authentication; real `ownerId` on records | ⬜ |
| Day 5 | Dockerfile + Kubernetes manifests + AWS (EKS/DynamoDB) deploy | ⬜ |

## Run

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080` (configurable via `app.base-url` / `server.port`).

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/shorten` | Body `{ "longUrl": "https://..." }`. Optional `X-API-Key` header. Returns `201` with `{ code, shortUrl, longUrl }`. |
| `GET` | `/{code}` | `302` redirect to the original URL; increments the click counter. |
| `GET` | `/{code}/stats` | `{ code, longUrl, clickCount, createdAt }`. |
| `GET` | `/actuator/health`, `/actuator/info` | Actuator endpoints. |

### Example

```bash
curl -s -XPOST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/very/long/path"}'
# -> {"code":"aZ3kP9","shortUrl":"http://localhost:8080/aZ3kP9","longUrl":"https://example.com/very/long/path"}

curl -s -i localhost:8080/aZ3kP9          # 302 Location: https://example.com/...
curl -s localhost:8080/aZ3kP9/stats       # {"code":"aZ3kP9",...,"clickCount":1,...}
```

## Test

```bash
mvn clean verify
```
