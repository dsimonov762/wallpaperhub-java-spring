# Status

**As of 2026-11-27.** Update this file in the same change as the work it describes. Verification levels: **Confirmed** (the owner ran it, or CI is green), **Sandbox** (run for real by an AI agent, not on the owner's machine), **Design only** (written, never run).

## In one paragraph

The repository has the build, the `platform` module and an `account` module. The scaffold and the observability and quality increments are confirmed by the owner. The account core (profiles on PostgreSQL) is new and **has never been compiled**: see the WP-S1 section below.

## WP-S1, increment 1 (account core): written, not run

Branch `wp/s1-account-core`. **Verification: nothing was compiled or run** (the sandbox has no Maven Central and no Docker; only JDK 21 was there). The first `./mvnw verify` and the conformance run are the first real tests; expect compile errors and report them.

What it contains: `users` on JPA (`ddl-auto=validate`, the schema of `contracts/db-schema`), `GET /users/me`, `PATCH /users/me` (ETag and `If-Match`, optimistic lock), `GET /users/{id}`, `GET /users` (admin; `q`, `status`, `sort`), soft `DELETE /users/{id}` (owner or admin). Each change writes its `audit_log` row and, for a new display name and for a deletion, its `outbox_event` row in the same transaction (plain SQL through `JdbcClient`; the payload field names are tested against `contracts/avro`). `/health/ready` also checks PostgreSQL. `POST /registrations` stays a 501 stand-in. CI runs `make seed` before the conformance suite.

Known gaps, on purpose, for later increments:

- Nothing publishes the outbox rows yet (the relay and the Avro producer are increment 3).
- `avatarUrl` and `bannerUrl` are always `null`; avatar and banner need the media client.
- Registration, the token endpoints, Redis cache-aside, the gRPC server, the purge job and the metrics of README section 11 are not started.
- Deleting a user does not yet disable them in Keycloak.
- Unknown JSON properties in a request body are ignored, not rejected (the contract says `additionalProperties: false`).
- A non-numeric `page` or `size` gives the generic 400, not a field-specific one.
- The nullable `avatarUrl` is written as plain JSON in the payload; whether the relay needs the Avro JSON union form is decided there.
- Unverified details to look at first if something fails: `JdbcClient` with a `null` jsonb parameter, package-private `@Transactional` service methods under Spring 6/7 proxies, `JsonMapper` as an injectable bean in Boot 4.

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

1. Run increment 1: `make fmt`, `./mvnw verify`, then `make down && make up-obs && make seed && make run SVC=account && make conformance SVC=account ARGS="--lifecycle"`; send the output of whatever fails.
2. Increment 2 part 2 (this change): run `make fmt`, commit the formatting diff, fix what Error Prone reports in `./mvnw verify`; then WP-S1.
3. WP-S1, next increments: Redis cache-aside and the gRPC server; the outbox relay with the Avro producer; the Keycloak admin client and registration; avatar and banner with media verification; token endpoints, purge job and metrics.
