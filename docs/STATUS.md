# Status

**As of 2026-11-26.** Update this file in the same change as the work it describes. Verification levels: **Confirmed** (the owner ran it, or CI is green), **Sandbox** (run for real by an AI agent, not on the owner's machine), **Design only** (written, never run).

## In one paragraph

The repository is the first increment of WP-S0 for Java: the build, the `platform` module and an `account` module with three stand-in endpoints, written so that the conformance suite of the template can run against it. **It was written in an environment that could not reach Maven Central, so nothing here has been compiled by its author.** The first `./mvnw verify` is the first build; expect a handful of compile errors (Spring Boot 4 renamed and moved many classes) and report them.

## What exists

| Piece | Level | Notes |
|---|---|---|
| Parent POM (Boot 4.2.0, Java 25), Maven wrapper 3.3.4 with Maven 3.9.16 | Design only | the wrapper scripts are the ones of the 3.3.4 release of Apache Maven Wrapper, taken from its source tag; the plugin that normally generates them could not run |
| `contracts/` vendored from the template; `scripts/template.sh sync/ensure/check`; forwarding `Makefile` | **Sandbox** | the script and the Makefile ran against a local clone of the template; the tag `v0.1.0` does not exist yet |
| The logic of request ids, trace ids, `Accept-Language`, placeholder formatting | **Sandbox** | those classes were compiled with JDK 21 and run on their own; the placeholder formatter passes all 14 cases of `contracts/i18n/format-tests.json` |
| `platform`: problem+json and localization, JWT validation, JSON logs, management port, fail-fast configuration; unit tests | Design only | syntax checked with `javac` (no syntax errors); symbols against Spring Boot 4 libraries not checked |
| `account`: application class, three stand-in endpoints, Dockerfile (`account/Dockerfile`, Temurin 25.0.5.1+2) | Design only | the base image tags `25.0.5.1_2-jdk-noble` and `-jre-noble` follow Temurin's naming; only the `ubi10-minimal` variants were seen on Docker Hub |
| CI (`.github/workflows/ci.yml`), GitHub settings as code, Renovate | Design only / `github-setup.py check` passes (Sandbox) | never run on GitHub |

## What the first runs should show

1. `./mvnw verify`: compiles, unit tests pass.
2. `make template-check` (after the template tag exists, or with `.template-version` set to `master`).
3. `make run SVC=account` and `make conformance SVC=account ARGS="--lifecycle --no-telemetry"`: expected to pass M1-M4, C1, A1-A4, E1-E6, L1-L3, S1; T1-T3 are skipped (no OpenTelemetry yet). If a check fails, that is the finding: send the output.

## Decisions taken here (and why)

- **Management port without Actuator.** `/health/live|ready|startup` are served by the JDK's HTTP server ([`ManagementServer`](../platform/src/main/java/dev/wallpaperhub/platform/management/ManagementServer.java)): Actuator answers with the content type `application/vnd.spring-boot.actuator.v3+json`, the checks expect `application/json`, and Actuator on a second port shares the application's security configuration. Readiness is composed of `ReadinessCheck` beans, so "ready only when the database is reachable" is explicit. Revisit if Actuator is wanted for metrics anyway.
- **`@PlatformApplication` instead of an auto-configuration.** The security filter chain must exist before Spring Boot decides whether to create its default one; user configuration is processed first.
- **Environment checked before Spring starts** ([`Startup`](../platform/src/main/java/dev/wallpaperhub/platform/Startup.java)): Spring's binding errors name properties, not variables, and accept empty values; C1 requires the variable's name.
- **`JAVA_TOOL_OPTIONS` read by the entrypoint** ([`docker/entrypoint.sh`](../docker/entrypoint.sh)): the JVM otherwise prints a non-JSON notice on stderr.
- **No trace propagation yet.** The trace id is taken from `traceparent` or generated (E2, L2); real spans arrive with OpenTelemetry.
- **Finding for the template:** README 5.3 has no problem type for HTTP 405; `forbidden` is used.

## Next

1. First build and conformance run (the owner), fix what they show.
2. Increment 2: OpenTelemetry (T1-T3; `OTEL_*` variables to Spring Boot's OTLP export, trace ids from the active span), formatter and linter (Spotless, Checkstyle, ErrorProne with versions taken from Maven Central at that time), `docs/CHEATSHEET.md`.
3. WP-S1: account-service proper (OpenAPI-generated DTOs, JPA with `ddl-auto=validate`, Redis, the gRPC server, outbox and relay, Keycloak admin client).
