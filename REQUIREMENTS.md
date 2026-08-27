# URL Shortener — Requirements & Roadmap

> **Provenance:** This document was reconstructed by Claude Code on 2026-08-27 from the
> existing source and the phased ("Day 1–5") comments embedded throughout it. The original
> requirements chat (claude.ai) was not available to import. If you still have that chat,
> paste it in and we'll reconcile any gaps or wording differences.

## Overview

A URL shortener REST service with click analytics.
**Stack:** Java 17, Spring Boot 3.3.4 (spring-boot-starter-parent).
**Target deployment:** Kubernetes + AWS (DynamoDB for storage).

Build is phased so each stage runs on its own before the next is wired in:

| Phase | Scope | Status |
|-------|-------|--------|
| Day 1–2 | In-memory REST API: shorten, redirect, stats, validation, error handling | ✅ Done |
| Day 3 | DynamoDB persistence behind the `dynamodb` Spring profile | ⬜ Not started |
| Day 4 | API-key authentication; real `ownerId` on records | ⬜ Not started |
| Day 5 | Dockerfile + Kubernetes manifests + AWS (EKS/DynamoDB) deploy | ⬜ Not started (`k8s/` dir is empty) |

---

## Day 1–2 — In-memory API (implemented)

### Endpoints

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/shorten` | Create a short code for a long URL. Body `{ "longUrl": "..." }`. Optional `X-API-Key` header (accepted, **not enforced yet**, defaults to `anonymous`). Returns **201 Created** with `{ code, shortUrl, longUrl }` where `shortUrl = ${app.base-url}/${code}`. |
| `GET` | `/{code}` | **302 Found** redirect to the stored long URL via `Location` header. Increments the click counter as a side effect. |
| `GET` | `/{code}/stats` | **200 OK** with `{ code, longUrl, clickCount, createdAt }`. |
| `GET` | `/actuator/health`, `/actuator/info` | Actuator, exposed; health details never shown. |

### Short-code generation

- 6 characters, alphabet `A–Za–z0–9` (base62).
- `SecureRandom`.
- On collision, retry up to 5 attempts; if all collide, throw `IllegalStateException`.

### Validation & errors

- `longUrl`: `@NotBlank`; must match `^https?://.+` (i.e. start with `http://` or `https://`).
- `GlobalExceptionHandler` (`@RestControllerAdvice`):
  - `UrlNotFoundException` → **404**, body `{ timestamp, error }`.
  - `MethodArgumentNotValidException` → **400**, body `{ timestamp, error }` (first field error, `"<field>: <message>"`).

### Storage

- `UrlRepository` interface: `save`, `findByCode`, `existsByCode`.
- `InMemoryUrlRepository`: thread-safe `ConcurrentHashMap`, active when the `dynamodb` profile is **not** set (`@Profile("!dynamodb")`). Nothing persists across restarts.
- `ShortUrl` model: `code`, `longUrl`, `ownerId`, `createdAt` (`Instant`), `clickCount` (`AtomicLong`). Kept as a plain POJO now; DynamoDB annotations added in Day 3 without changing its shape.

### Config (`application.yml`)

- `server.port: 8080`
- `app.base-url: http://localhost:8080`
- Actuator exposure limited to `health,info`.

### Tests

- `UrlShortenerFlowTest` (`@SpringBootTest`): create → look up → click ×2 → assert `clickCount == 2`; unknown code throws `UrlNotFoundException`. (2 tests, passing.)

---

## Day 3 — DynamoDB persistence (planned)

- Dependency already present: `software.amazon.awssdk:dynamodb-enhanced:2.28.11`.
- Add `@DynamoDbBean` / `@DynamoDbPartitionKey` to `ShortUrl`; `code` is the partition key.
- New `DynamoDbUrlRepository implements UrlRepository`, active on `@Profile("dynamodb")`. Rest of the app is untouched (service depends only on the interface).
- `recordClickAndGet` — the `repository.save(...)` after `incrementClickCount()` becomes a real persisted write (currently a no-op for the map). Consider an atomic DynamoDB update-expression for the counter rather than read-modify-write.
- Config to add (commented stub already in `application.yml`):
  ```yaml
  aws:
    dynamodb:
      table-name: ${DYNAMODB_TABLE_NAME:url-shortener}
      region: ${AWS_REGION:us-east-1}
  ```

## Day 4 — API-key auth (planned)

- `X-API-Key` header is currently accepted on `POST /shorten` but not checked.
- Validate it against a set of known keys; reject unknown/missing keys (401/403).
- Use the API key as the real `ownerId` stored on `ShortUrl` (field already exists).

## Day 5 — Containerize & deploy (planned)

- `Dockerfile` for the Spring Boot app.
- Kubernetes manifests in `k8s/` (currently empty): Deployment, Service, and likely ConfigMap/Secret (AWS region, table name, API keys) and Ingress. Wire actuator health to readiness/liveness probes.
- AWS: DynamoDB table `url-shortener` (partition key `code`); run on EKS. IAM/IRSA for DynamoDB access.

---

## Open questions to confirm against the original chat

- Redirect status: **302** is implemented. Was **301** intended for any case?
- Custom/vanity short codes — in scope at all?
- Expiry / TTL on short URLs?
- Rate limiting?
- Per-owner listing endpoint (`GET /urls` for an API key)?
- Analytics depth: just a total counter, or per-day / referrer / geo breakdown?
