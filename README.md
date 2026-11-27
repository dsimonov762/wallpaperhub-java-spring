# wallpaperhub-java-spring

The four Wallpaper Hub services (account, media, catalog, notification) in **Java 25 with Spring Boot 4.2 and Maven**, one multi-module build.
It is an implementation of [wallpaperhub-template](https://github.com/dsimonov762/wallpaperhub-template): the template says *what* a service must do (contracts, `docs/SERVICE-SPEC.md`) and checks it (a conformance suite, a local environment with Keycloak, Kafka, databases and a gateway); this repository is one answer to *how*, in one language. Other languages get repositories of their own ([ADR 0023](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/adr/0023-service-repositories-one-per-language-and-framework-pinned.md) of the template).

| Module | What it is | State |
|---|---|---|
| [`platform/`](platform) | what the services share: request id, problem+json and localization, JWT validation, JSON logs, the management port, fail-fast configuration | written, **not yet built by anyone** ([docs/STATUS.md](docs/STATUS.md)) |
| [`account/`](account) | account-service ([WP-S1](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/ROADMAP.md#wp-s1-account-service)) | profiles on PostgreSQL (read, update with ETag, list, soft delete, outbox and audit rows); registration is still a stand-in. See [STATUS](docs/STATUS.md) |
| `media/`, `catalog/`, `notification/` | WP-S2, S3, S4 | not started |

## Toolchain

Java 25 (Temurin 25.0.5.1+2; the image builds with `eclipse-temurin:25.0.5.1_2-jdk-noble`), Maven through the wrapper (3.3.4, Maven 3.9.16: `.mvn/wrapper/maven-wrapper.properties`), Spring Boot 4.2.0 (the parent in [`pom.xml`](pom.xml); it manages the versions of everything else). Docker Desktop with WSL2 for the stack; clone into the WSL file system.

```
./mvnw -v          # the first call downloads Maven
make build         # ./mvnw verify: compile and run the unit tests
```

## Running a service against the template's environment

The template is pinned in [`.template-version`](.template-version) and checked out into `.template/` (ignored); `contracts/` is a committed copy of that version. Every `make` target that is not listed in `make help` above the line is the template's, run there with the service built from this repository.

```
make env                                   # once: creates .template/infra/.env
make up-obs                                # databases, Kafka, Keycloak, ... and Grafana, Loki, Tempo, Prometheus, Alloy (about 6.5 GB)
make run SVC=account                       # build the image from this repository, start it, wait for /health/ready
make conformance SVC=account ARGS="--lifecycle"
make down
```

With `make up` instead of `make up-obs` (about 5 GB) add `--no-telemetry`: the checks T1-T3 then do not look for the request in Tempo, Loki and Prometheus. The service exports its telemetry with the OpenTelemetry Java agent ([docs/CHEATSHEET.md](docs/CHEATSHEET.md)).

`make conformance` is the acceptance test of a service; CI runs the same steps ([`.github/workflows/ci.yml`](.github/workflows/ci.yml), the required check is the job `ok`).

## Moving to a newer template

Change the tag in `.template-version`, run `make template-sync` (it refreshes `contracts/`), review the diff, run `make build` and the conformance run, open a pull request. A contract is never edited here: it changes in the template first.

## Where to read more

[docs/STATUS.md](docs/STATUS.md) what exists and how well it is verified · [docs/HISTORY.md](docs/HISTORY.md) how it got here · [AGENTS.md](AGENTS.md) the rules (for people and AI agents) · [CONTRIBUTING.md](CONTRIBUTING.md) · [SECURITY.md](SECURITY.md) · the template's [ROADMAP](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/ROADMAP.md) for the work packages.
