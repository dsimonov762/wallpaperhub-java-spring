# Status

**As of 2026-11-27.** Update this file in the same change as the work it describes. Verification levels: **Confirmed** (the owner ran it, or CI is green), **Sandbox** (run for real by an AI agent, not on the owner's machine), **Design only** (written, never run).

## In one paragraph

The repository has the build, the `platform` module and an `account` module. The scaffold and the observability and quality increments are confirmed by the owner. The account core (profiles on PostgreSQL) is new and **has never been compiled**: see the WP-S1 section below.

## WP-S1, increment 1 (account core): confirmed

Merged in pull request #6. **Confirmed** by the owner after `make fmt`: the local tests and CI pass (CI runs `make seed` and the conformance suite). `./mvnw verify` reported one Error Prone warning (`StringSplitter` in `UserSort`), fixed afterwards. `e2e/system/10-account.hurl` has not been run against it. The write paths have an integration test on a real PostgreSQL (next section), written but not yet run.

What it contains: `users` on JPA (`ddl-auto=validate`, the schema of `contracts/db-schema`), `GET /users/me`, `PATCH /users/me` (ETag and `If-Match`, optimistic lock), `GET /users/{id}`, `GET /users` (admin; `q`, `status`, `sort`), soft `DELETE /users/{id}` (owner or admin). Each change writes its `audit_log` row and, for a new display name and for a deletion, its `outbox_event` row in the same transaction (plain SQL through `JdbcClient`; the payload field names are tested against `contracts/avro`). `/health/ready` also checks PostgreSQL. `POST /registrations` stays a 501 stand-in. CI runs `make seed` before the conformance suite.

Known gaps, on purpose, for later increments:

- Nothing publishes the outbox rows yet (the relay and the Avro producer are increment 3).
- `avatarUrl` and `bannerUrl` are always `null`; avatar and banner need the media client.
- Registration, the token endpoints, Redis cache-aside, the gRPC server, the purge job and the metrics of README section 11 are not started.
- Deleting a user does not yet disable them in Keycloak.
- Unknown JSON properties in a request body are ignored, not rejected (the contract says `additionalProperties: false`).
- A non-numeric `page` or `size` gives the generic 400, not a field-specific one.
- The nullable `avatarUrl` is written as plain JSON in the payload; whether the relay needs the Avro JSON union form is decided there.

## Integration test on PostgreSQL: confirmed

Merged in pull request #8; **Confirmed** by the owner (local run with Docker and CI). [`UserPersistenceTest`](../account/src/test/java/dev/wallpaperhub/account/user/UserPersistenceTest.java) starts the image of the template's Compose file (`postgres:18.7-trixie`) with Testcontainers, loads `contracts/db-schema/postgres/account/V1__init.sql` into it, and runs `UserService` with `ddl-auto=validate`: the entity matches the contract schema; update, audit row and outbox row are written together; a failing outbox insert rolls the profile change back; delete is soft; list filters, pages and escapes `%` and `_`; `OutboxWriter` refuses to run outside a transaction.

Needs Docker: without it the tests skip themselves (`./mvnw test` on a machine without Docker stays green); on CI (`CI` is set) `DockerInCiTest` fails instead of letting them vanish silently.

## WP-S1, increment 2a (Redis cache-aside): confirmed

Merged in pull request #9; **confirmed** by the owner (local run with Docker, CI, and the chaos scenarios of `make up-chaos`).

- **Contract:** `cache:user:{id}`, JSON, 5 min TTL with plus or minus 20 % jitter, single-flight, invalidation on every write (`contracts/db-schema/redis/keys.yaml`, README 6.5). The cached value is `{"version": n, "user": {User schema}}`: the schema has no `version`, the `ETag` needs it. `GET /users/me` and `GET /users/{id}` both read through it (the public view is derived from the same entry).
- **Eviction after commit:** a transaction synchronization removes the key once the change is committed. An eviction that cannot reach Redis is remembered (up to 10 000 ids) and retried before the next read when Redis answers again.
- **Redis is optional:** any Redis failure is logged once and the request is served from PostgreSQL; Redis is then left alone for 5 s, so a dead Redis costs one slow command (200 ms) per 5 s and not one per request. `/health/ready` needs Redis to have been reachable **at least once** (SERVICE-SPEC section 2, like the JWKS), so a Redis outage does not take the service out of rotation (chaos `redis-down`: reads keep working).
- **Tests:** `UserCacheTest` (fake store: hit and miss, TTL jitter, unreadable entry, outage window, pending evictions, single-flight with 8 readers), `UserCacheRedisTest` (real Redis: key, TTL, hit without database, eviction on update and delete, no negative caching), `UserCacheRedisDownTest` (nothing listens on the port: reads and writes still work), `UserServiceTest` (eviction rules).
- **Known limits:** a reader that loaded the old row just before a commit can store it just after the eviction; it lives until its TTL (at most 6 minutes). The metric `cache_hit_ratio` (README section 11) comes with the metrics increment. Deletion by another instance needs no Kafka consumer: all instances share one Redis and the eviction is done by the instance that commits.
- **Chaos:** `make up-chaos` of this repository (the template's one tries to build all four services) starts the real account and the gateway behind Toxiproxy and Prism mocks for the other three; then `make chaos-run S=redis-down`. Written without running it. Its probes also read `/wallpapers/{id}` and `/notifications`, which the mocks answer. The load test is not run.

## WP-S1, increment 2b (gRPC server): written, not run

Branch `wp/s1-account-grpc`. **Verification: nothing was compiled or run** (no Docker, no Maven Central in the sandbox; only a `javac` syntax check). Expect compile errors first. Everything below the first bullet is a guess about Spring Boot 4.2's gRPC support, taken from its reference documentation.

- **Build:** `spring-boot-starter-grpc-server` (Boot 4.2 manages it, grpc-java and protobuf-java) and `io.github.ascopes:protobuf-maven-plugin` (managed and pre-configured by `spring-boot-starter-parent`). The only change to the plugin is `sourceDirectories` = `contracts/proto`, so the vendored contract is compiled in place. The Dockerfile copies `contracts/proto`. Error Prone skips `generated-sources` (root POM).
- **Contract:** `GetUser` (through the Redis cache), `GetUsersByIds` (at most 100 ids, unknown and deleted ids omitted, duplicates once), `ListUsersUpdatedSince` (oldest first, deleted users excluded, keyset paging on `(updated_at, id)` in chunks of 500 with flow control, stops when the client cancels). `avatar_url` is the empty string. Errors follow README 5.3 (`GrpcErrors`): invalid id is `INVALID_ARGUMENT`, unknown or deleted is `NOT_FOUND`, a database that cannot be reached is `UNAVAILABLE`, anything else is `INTERNAL` without details.
- **Security:** `InternalCallInterceptor` validates the `authorization: Bearer` token with the platform's `JwtDecoder` (signature, issuer, audience, expiry, `typ=Bearer`) and requires the realm role `internal`: no or bad token is `UNAUTHENTICATED`, a valid token without the role `PERMISSION_DENIED`, signing keys not fetchable `UNAVAILABLE`. The service is wrapped, not registered as a bean of its own (Boot exposes every `BindableService` bean), so there is no unauthenticated way in. Server reflection is on and not authenticated (it lists service names only).
- **Tests:** `InternalCallInterceptorTest` and `GrpcErrorsTest` (in-process, no Docker); `AccountGrpcServiceTest` (real PostgreSQL, in-process transport: fields, errors, the 100 limit, order, equal timestamps, chunking, early stop).
- **Guesses to check first if the build or the context fails:** the `sourceDirectories` element of the protobuf plugin (`combine.self="override"`); that Boot's own gRPC security and OAuth2 auto-configuration (`GrpcServerSecurityAutoConfiguration`, `GrpcServerOAuth2ResourceServerAutoConfiguration`) does not clash with the interceptor; that `spring.grpc.server.port=0` is enough to keep the test contexts from binding 9090 (they all start the gRPC server).
- **Not run:** a real call with a Keycloak token (`grpcurl`, see the CHEATSHEET), the load test, chaos `postgres-down` for the gRPC path.
- **503 when PostgreSQL is down (same branch, written, not run):** `ProblemExceptionHandler` answered 500 for any unexpected exception. The platform now has an `ExceptionClassifier` hook (a service registers a bean that recognises its own non-bug failures), `AppException.dependencyDown(service)` (`503 upstream-unavailable`, code `upstream.unavailable`, args `service`), and account's `DatabaseExceptionClassifier` uses `DatabaseFailures` (pool timeout, `CannotCreateTransactionException`, SQLSTATE class 08, refused or reset sockets, looked for in the whole chain of causes). `ProblemExceptionHandlerTest` and `GrpcErrorsTest` cover it; no test has taken a real database away. **Conflict with a chaos probe:** `postgres-down` expects `GET /users/me` to answer 503 during the outage, but the profile cache (Redis) can answer 200 for up to 5 minutes, which is arguably the better behaviour. If the scenario reports that, the template's probe should accept `[200, 503]` for it (a change in the template, not here); a write (`PATCH /users/me`) is a 503.

## What exists

The first increment (build, `platform`, `account` scaffold, conformance without T1-T3) is **Confirmed** by the owner: `./mvnw verify` passes and `make run SVC=account` + `make conformance SVC=account ARGS="--lifecycle --no-telemetry"` pass, locally and in CI (template v0.1.2). The rows below were written before that run; the OpenTelemetry row is new.

| Piece | Level | Notes |
|---|---|---|
| OpenTelemetry (increment 2): Java agent 2.33.1 fetched by `./mvnw -N dependency:copy` in `account/Dockerfile`, started by `docker/entrypoint.sh`; `opentelemetry-api` in `platform`; the request filter takes trace and span id of the active span; CI runs `make up-obs` and T1-T3 | **Confirmed** | the owner ran it locally and CI is green (T1-T3 pass with `make up-obs`); the JVM's class-data-sharing notice needed `-Xshare:off` (L1) |
| Quality tools (increment 2, part 2): Error Prone 2.53.0 in every compilation, Spotless 3.11.0 with palantir-java-format 2.90.0 (`make fmt`, checked by `./mvnw verify`), `.mvn/jvm.config` | **Confirmed** | the owner ran `make fmt` and `./mvnw verify` locally and CI is green; Error Prone reported one warning in `Startup` (a `char[]` copy in a loop), fixed |
| Parent POM (Boot 4.2.0, Java 25), Maven wrapper 3.3.4 with Maven 3.9.16 | Design only | the wrapper scripts are the ones of the 3.3.4 release of Apache Maven Wrapper, taken from its source tag; the plugin that normally generates them could not run |
| `contracts/` vendored from the template; `scripts/template.sh sync/ensure/check`; forwarding `Makefile` | **Sandbox** | the script and the Makefile ran against a local clone of the template; the tag `v0.1.0` does not exist yet |
| The logic of request ids, trace ids, `Accept-Language`, placeholder formatting | **Sandbox** | those classes were compiled with JDK 21 and run on their own; the placeholder formatter passes all 14 cases of `contracts/i18n/format-tests.json` |
| `platform`: problem+json and localization, JWT validation, JSON logs, management port, fail-fast configuration; unit tests | Design only | syntax checked with `javac` (no syntax errors); symbols against Spring Boot 4 libraries not checked |
| `account`: application class, three stand-in endpoints, Dockerfile (`account/Dockerfile`, Temurin 25.0.5.1+2) | Design only | the base image tags `25.0.5.1_2-jdk-noble` and `-jre-noble` follow Temurin's naming; only the `ubi10-minimal` variants were seen on Docker Hub |
| CI (`.github/workflows/ci.yml`), GitHub settings as code, Renovate | Design only / `github-setup.py check` passes (Sandbox) | never run on GitHub |

## What the first runs should show

1. `./mvnw verify`: compiles, unit tests pass.
2. `make template-check` (after the template tag exists, or with `.template-version` set to `master`).
3. `make run SVC=account` and `make conformance SVC=account ARGS="--lifecycle --no-telemetry"`: expected to pass M1-M4, C1, A1-A4, E1-E6, L1-L3, S1; T1-T3 pass with `make up-obs` and are skipped with `--no-telemetry`. If a check fails, that is the finding: send the output.

## Decisions taken here (and why)

- **Management port without Actuator.** `/health/live|ready|startup` are served by the JDK's HTTP server ([`ManagementServer`](../platform/src/main/java/dev/wallpaperhub/platform/management/ManagementServer.java)): Actuator answers with the content type `application/vnd.spring-boot.actuator.v3+json`, the checks expect `application/json`, and Actuator on a second port shares the application's security configuration. Readiness is composed of `ReadinessCheck` beans, so "ready only when the database is reachable" is explicit. Revisit if Actuator is wanted for metrics anyway.
- **`@PlatformApplication` instead of an auto-configuration.** The security filter chain must exist before Spring Boot decides whether to create its default one; user configuration is processed first.
- **Environment checked before Spring starts** ([`Startup`](../platform/src/main/java/dev/wallpaperhub/platform/Startup.java)): Spring's binding errors name properties, not variables, and accept empty values; C1 requires the variable's name.
- **`JAVA_TOOL_OPTIONS` read by the entrypoint** ([`docker/entrypoint.sh`](../docker/entrypoint.sh)): the JVM otherwise prints a non-JSON notice on stderr.
- **OpenTelemetry through the Java agent, not through Spring Boot's own OTLP export.** The contract gives `OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4317` with `grpc` for all signals; Boot's metrics export (Micrometer's OTLP registry) speaks HTTP only and would need another port and a convention switch for the metric name `http.server.request.duration` that T3 looks for. The agent reads the contract's `OTEL_*` variables as they are, names metrics by the OpenTelemetry conventions, and also gives the database, Kafka and gRPC spans that WP-S1 will need without code. Cost: about 100 MB more memory (watch `mem_limit: 640m` of the template's `x-app`; if the container is OOM-killed, raise it in the template) and a few seconds of start-up. `OTEL_SDK_DISABLED=true` starts the service without the agent. The agent's version is a property of the root POM (Renovate moves it; agent 3.x needs approval, it changes defaults).
- **`-Xshare:off` with the agent.** The agent appends to the bootstrap class path and the JVM then prints a class-data-sharing notice on stderr (not JSON, L1); the entrypoint turns sharing off whenever the agent is attached (found by the first CI run; the template's SERVICE-SPEC section 12 should mention it for other JVM services).
- **Error Prone and Spotless, no Checkstyle (yet).** Error Prone finds bugs, Spotless fixes formatting, both run in the normal build. Checkstyle would add style rules that the formatter mostly covers, and its Maven plugin (3.6.0, built for Checkstyle 9.3) has to be run with a Checkstyle 14 that I could not test against it in an environment without Maven Central; it can be added when someone has a rule that the two tools miss. Formatter: palantir-java-format (4 spaces, 120 columns), close to the style of the existing code; Spotless also removes unused imports.
- **`platform` knows only `opentelemetry-api`.** The filter reads `Span.current()`: with the agent the trace id in logs and in `problem+json` is the one of the real span (it continues the incoming `traceparent`); without it (unit tests, the IDE without the agent) the old fallback applies. The agent also puts `trace_id` and `span_id` into the MDC; the filter sets the same values.
- **Finding for the template:** README 5.3 has no problem type for HTTP 405; `forbidden` is used.

## Next

1. Run increment 2b: `make fmt`, `./mvnw verify` (Docker running; the first build downloads protoc and the gRPC plugin), then `make down && make up-obs && make seed && make run SVC=account && make conformance SVC=account ARGS="--lifecycle"`; then `make chaos-run S=postgres-down`, then the `grpcurl` calls of the CHEATSHEET (`list` and `GetUser` with `--client catalog-svc`, and with a user token to see `PERMISSION_DENIED`). Send the output of whatever fails.
2. WP-S1, next increments: the outbox relay with the Avro producer; the Keycloak admin client and registration; avatar and banner with media verification; token endpoints, purge job and metrics.
