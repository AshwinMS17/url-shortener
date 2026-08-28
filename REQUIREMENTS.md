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
| Day 3 | DynamoDB persistence behind the `dynamodb` Spring profile | ✅ Done |
| Day 4 | API-key authentication; real `ownerId` on records | ✅ Done |
| Day 5 | Dockerfile + Kubernetes manifests + AWS (EKS/DynamoDB) deploy | ⬜ Not started (`k8s/` dir is empty) |

---

## Day 1–2 — In-memory API (implemented)

### Endpoints

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/shorten` | Create a short code for a long URL. Body `{ "longUrl": "..." }`. **Requires a valid `X-API-Key` header** (Day 4); the key's owner id is stored on the record. Returns **201 Created** with `{ code, shortUrl, longUrl }` where `shortUrl = ${app.base-url}/${code}`. |
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

## Day 3 — DynamoDB persistence (implemented)

Branch `day3-dynamodb`.

- `ShortUrl` annotated with `@DynamoDbBean` + `@DynamoDbPartitionKey` on `getCode()`
  (partition key `code`). `clickCount` stays an `AtomicLong` internally; the enhanced
  client only sees the `long` getter/setter and maps it as a Number.
- `DynamoDbUrlRepository implements UrlRepository`, `@Profile("dynamodb")`.
  `InMemoryUrlRepository` is `@Profile("!dynamodb")` — exactly one is active. The service
  layer is unchanged (depends only on the interface).
- **Atomic click counter.** New interface method
  `Optional<ShortUrl> incrementClickCountAndGet(String code)` replaces the old
  read-modify-write in `recordClickAndGet`.
  - DynamoDB impl: low-level `UpdateItem` with
    `SET #cc = if_not_exists(#cc, :zero) + :one`, `attribute_exists(#c)` condition,
    `ReturnValue.ALL_NEW`; `ConditionalCheckFailedException` → `Optional.empty()`.
  - In-memory impl: `ConcurrentHashMap.computeIfPresent` + the record's `AtomicLong`.
- Two AWS clients wired in `DynamoDbConfig` (`@Profile("dynamodb")`):
  `DynamoDbEnhancedClient` for object-mapped put/get, low-level `DynamoDbClient` for the
  update expression. Credentials: static keys if `aws.dynamodb.access-key/secret-key` set
  (local), otherwise `DefaultCredentialsProvider` (env / profile / EKS IRSA).
- `AwsDynamoProperties` (`@ConfigurationProperties("aws.dynamodb")`): `table-name`,
  `region`, `endpoint`, `access-key`, `secret-key`, `create-table-on-startup`.
- `DynamoDbTableInitializer` creates the table on `ApplicationReadyEvent` when
  `create-table-on-startup=true` (local/dev only; real envs use IaC).
- `application.yml` `aws.dynamodb.*` block with env-var overrides
  (`DYNAMODB_TABLE_NAME`, `AWS_REGION`, `DYNAMODB_ENDPOINT`, `AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY`, `DYNAMODB_CREATE_TABLE`).
- Tests: `DynamoDbUrlRepositoryTest` — Testcontainers + LocalStack, 5 tests
  (wiring, save/read, missing-code, increment semantics, 50-way concurrent increment).
  `@Testcontainers(disabledWithoutDocker = true)` so `mvn verify` still passes with no Docker.
- Test infra:
  - `testcontainers.version` overridden to `1.20.6` (Spring Boot 3.3.4 pins 1.19.8, whose
    docker-java negotiates Docker API 1.32 — rejected by modern daemons).
  - `maven-surefire-plugin` `<argLine>-Dapi.version=1.44</argLine>` — pins the Remote API
    version for docker-java (Colima's engine has `MinAPIVersion 1.40`).
  - `scripts/integration-tests.sh` — ensures Colima is up, exports `DOCKER_HOST` +
    `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` (Colima's socket isn't at the default path and
    Ryuk needs the in-VM path), then runs `mvn verify`.
  - Local Docker runtime = Colima (see README "Removing the Docker setup" for teardown).
  - Verified: `./scripts/integration-tests.sh` → 7 tests, 0 skipped, BUILD SUCCESS;
    plain `mvn verify` (no Docker env) → 5 skipped, BUILD SUCCESS.

## Day 4 — API-key auth (implemented)

Branch `day4-api-keys`.

- **`ApiKeyProperties`** (`@ConfigurationProperties("app")`): binds `app.api-keys` as a
  map of `<owner-id> -> <secret>`. `ownerForPresentedKey` compares the presented key
  against every configured secret with `MessageDigest.isEqual` and **no early exit**, so
  timing doesn't leak which prefix matched.
- **`ApiKeyAuthInterceptor`** (`HandlerInterceptor`): reads `X-API-Key`, resolves the
  owner, and on failure throws `ApiKeyUnauthorizedException`. On success it stashes the
  owner id in the `apiKeyOwnerId` request attribute.
- **`WebConfig`** registers the interceptor for **`/shorten` only**. Redirect and stats
  stay public (a short link has to work for anyone).
- **`UrlController.shorten`** now takes `@RequestAttribute(OWNER_ATTRIBUTE) String ownerId`
  instead of the old header param; that value is passed to `UrlService.createShortUrl` and
  stored as `ShortUrl.ownerId`.
- **`GlobalExceptionHandler`**: `ApiKeyUnauthorizedException` → **401** with the same
  `{ timestamp, error }` body as the other handlers. (`403` was considered; `401` fits
  "no/!bad credentials" better since there's no notion of an authenticated-but-forbidden
  caller yet.)
- **Fail-closed**: with `app.api-keys` empty, every `POST /shorten` is 401.
- `application.yml` ships one throwaway local key
  `local-dev: ${LOCAL_DEV_API_KEY:dev-secret-change-me}`. Real environments inject the map
  from a mounted Secret (Day 5).
- Tests: `ApiKeyAuthTest` (`@SpringBootTest` + `MockMvc`, 5 cases) — missing key → 401,
  unknown key → 401, valid key → 201 **and** `ownerId` stamped on the stored record,
  redirect still public, stats still public.

Not done (candidates, not in the original scope): owner-scoped stats (only the owner can
see a code's stats), a `GET /urls` listing per owner, key rotation/hashing at rest.

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
