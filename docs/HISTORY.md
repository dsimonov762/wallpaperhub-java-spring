# History

Append one line per finished piece of work, newest last.

- 2026-11-26: repository created from the template's decisions D-1 (Java 25, Spring Boot 4.2.0, Maven wrapper 3.3.4) and D-2 ([ADR 0023](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/adr/0023-service-repositories-one-per-language-and-framework-pinned.md)). First increment: parent POM, `platform`, an `account` scaffold, Dockerfile, CI, template pinning. Written without being able to compile; first build pending.
- 2026-11-27: increment 2, part 1: OpenTelemetry through the Java agent (2.33.1, `account/Dockerfile`, `docker/entrypoint.sh`), `opentelemetry-api` in `platform` for the trace id of the active span, `make otel-agent` for the IDE, CI with `make up-obs` and T1-T3. Not yet run (see STATUS). Why the agent and not Spring Boot's own OTLP export: STATUS, decisions. Spotless, Checkstyle and ErrorProne follow as a separate pull request.
- 2026-11-27: OpenTelemetry confirmed by the owner (local run and CI, T1-T3 pass); `-Xshare:off` with the agent (the JVM's class-data-sharing notice broke L1).
- 2026-11-27: increment 2, part 2: Error Prone 2.53.0, Spotless 3.11.0 with palantir-java-format 2.90.0, `.mvn/jvm.config`, `make fmt`, Renovate for the three versions. Checkstyle left out on purpose (STATUS, decisions). Written without running it.
- 2026-11-27: quality tools confirmed by the owner (`make fmt`, `./mvnw verify`, CI). Renovate no longer looks up the modules of this repository (`dev.wallpaperhub:*`).
- 2026-11-27: WP-S1 increment 1 (account core): `users` on JPA with `ddl-auto=validate`, profile read/update/list/delete, ETag and `If-Match`, audit and outbox rows in the same transaction, PostgreSQL readiness, `make seed` in CI. Written without compiling or running (see STATUS).
- 2026-11-27: WP-S1 increment 1 confirmed by the owner (`make fmt`, local tests, CI). Error Prone `StringSplitter` warning in `UserSort` fixed (no `String.split(String)`).
