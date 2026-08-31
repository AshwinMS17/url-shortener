# url-shortener

A URL shortener REST service with click analytics.

**Stack:** Java 17 · Spring Boot 3.3.4 · Maven
**Target deployment:** Kubernetes + AWS (DynamoDB)

See [REQUIREMENTS.md](REQUIREMENTS.md) for the full spec and the phased (Day 1–5) roadmap.

## Status

| Phase | Scope | Status |
|-------|-------|--------|
| Day 1–2 | In-memory REST API: shorten, redirect, stats, validation, error handling | ✅ Done |
| Day 3 | DynamoDB persistence behind the `dynamodb` Spring profile | ✅ Done |
| Day 4 | API-key authentication; real `ownerId` on records | ✅ Done |
| Day 5 | Dockerfile + Kubernetes manifests + AWS (EKS/DynamoDB) deploy | ✅ Done |

## Run

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080` (configurable via `app.base-url` / `server.port`).
By default it uses an in-memory store that resets on restart.

Creating short URLs requires an API key (see [Authentication](#authentication)); the default
config ships one throwaway local key, `dev-secret-change-me`.

### With DynamoDB persistence

Activate the `dynamodb` profile. Against a local LocalStack / DynamoDB Local:

```bash
docker run --rm -p 4566:4566 localstack/localstack:3.5

SPRING_PROFILES_ACTIVE=dynamodb \
DYNAMODB_ENDPOINT=http://localhost:4566 \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
DYNAMODB_CREATE_TABLE=true \
mvn spring-boot:run
```

Against real AWS, set only `SPRING_PROFILES_ACTIVE=dynamodb` and `AWS_REGION`; credentials
come from the default provider chain (env, shared profile, or the pod's IAM role on EKS),
and the table is provisioned by infrastructure-as-code (Day 5).

| Env var | Purpose | Default |
|---|---|---|
| `DYNAMODB_TABLE_NAME` | Table name | `url-shortener` |
| `AWS_REGION` | Region | `us-east-1` |
| `DYNAMODB_ENDPOINT` | Endpoint override for LocalStack/local | *(none → real AWS)* |
| `DYNAMODB_CREATE_TABLE` | Create the table on startup if missing | `false` |

## API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/shorten` | **`X-API-Key` required** | Body `{ "longUrl": "https://..." }`. Returns `201` with `{ code, shortUrl, longUrl }`. `401` if the key is missing or unknown. |
| `GET` | `/{code}` | public | `302` redirect to the original URL; increments the click counter. |
| `GET` | `/{code}/stats` | public | `{ code, longUrl, clickCount, createdAt }`. |
| `GET` | `/actuator/health`, `/actuator/info` | public | Actuator endpoints. |

### Authentication

`POST /shorten` requires an `X-API-Key` header. Keys are configured as
`app.api-keys.<owner-id>: <secret>` (see `application.yml`); the owner id is stored
as `ownerId` on every record that key creates. Enforcement is **fail-closed** — with
no keys configured, every `POST /shorten` returns `401`. In real environments the keys
come from a mounted Secret (Day 5).

### Example

```bash
curl -s -XPOST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dev-secret-change-me' \
  -d '{"longUrl":"https://example.com/very/long/path"}'
# -> {"code":"aZ3kP9","shortUrl":"http://localhost:8080/aZ3kP9","longUrl":"https://example.com/very/long/path"}

curl -s -XPOST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/x"}'
# -> 401 {"timestamp":"...","error":"Missing X-API-Key header"}

curl -s -i localhost:8080/aZ3kP9          # 302 Location: https://example.com/...  (no key needed)
curl -s localhost:8080/aZ3kP9/stats       # {"code":"aZ3kP9",...,"clickCount":1,...}
```

## Container & Kubernetes

```bash
docker build -t url-shortener:0.1.0 .
docker run --rm -p 8080:8080 -e LOCAL_DEV_API_KEY=dev-secret-change-me url-shortener:0.1.0
```

Multi-stage build on a layered Spring Boot jar; runtime image is a `17-jre` base
running as a non-root user. The app exposes `/actuator/health/liveness` and
`/actuator/health/readiness` for Kubernetes probes.

Kubernetes manifests and the full EKS + DynamoDB + IRSA walkthrough are in
[`k8s/`](k8s/README.md): `kubectl apply -k k8s/` once the image, table, IAM role,
and Secret are in place.

## Test

```bash
mvn clean verify
```

This runs the unit / in-memory tests. The DynamoDB integration test
(`DynamoDbUrlRepositoryTest`) needs a Docker daemon and **self-skips** when one
isn't reachable, so `mvn verify` stays green on a machine without Docker.

### Running the DynamoDB tests

`DynamoDbUrlRepositoryTest` uses [Testcontainers](https://java.testcontainers.org/)
to boot LocalStack and exercise the real DynamoDB API (save/get, the atomic
counter update expression, `@DynamoDbBean` mapping, concurrent increments).

This project uses [Colima](https://github.com/abiosoft/colima) as the Docker
runtime — a lightweight VM that installs and uninstalls cleanly (no Docker
Desktop, no privileged helper):

```bash
brew install colima docker      # one-time
colima start --cpu 2 --memory 4 --disk 20
```

Then run the full suite through the helper script:

```bash
./scripts/integration-tests.sh
```

The script exports the two Colima-specific hints Testcontainers needs
(`DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`) and runs `mvn verify`.
The Docker API version pin lives in `pom.xml` (surefire `<argLine>`), because
Colima's engine rejects the docker-java client's default. Extra Maven args pass
through: `./scripts/integration-tests.sh -Dtest=DynamoDbUrlRepositoryTest`.

Stop the VM when you're done (state is kept for next time):

```bash
colima stop
```

### Removing the Docker setup

Nothing here touches system files; removal is complete:

```bash
colima stop
colima delete            # deletes the VM and every image/container inside it
brew uninstall colima docker
brew autoremove          # drops the 'lima' dependency colima pulled in
rm -rf ~/.colima ~/.lima ~/.docker    # optional: leftover config/state
```
