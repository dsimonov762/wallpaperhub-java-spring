# Cheat sheet

The commands of every day. `make help` lists the same with one line each; targets that are not in this repository are the template's.

## Build and test

| What | Command |
|---|---|
| compile and run all unit tests | `make build` (`./mvnw verify`) |
| unit tests only | `make test` |
| jars without tests | `make package` |
| one module, with what it needs | `./mvnw -pl account -am verify` |
| (the database test `UserPersistenceTest` starts PostgreSQL with Docker; without Docker it is skipped) | |
| one test class | `./mvnw -pl platform -Dtest=PlatformRequestFilterTest test` |
| format the code | `make fmt` (Spotless, palantir-java-format); `make build` fails on unformatted code |
| build without the formatting check | `./mvnw verify -Dspotless.check.skip=true` (only to look at a test while you work; CI checks it) |

## Code quality

- **Spotless** (`make fmt`) formats Java: palantir-java-format, 4 spaces, 120 columns, unused imports removed. Run it before you commit; the IDE can use the same formatter (IntelliJ: the "palantir-java-format" plugin).
- **Error Prone** runs inside every compilation (main and test code). A message "[CheckName] ..." with `error:` fails the build, `warning:` is only printed; the link in the message explains the check. If it is a false positive: `@SuppressWarnings("CheckName")` on the smallest element, with a comment saying why.
- Both need the `add-exports` options of `.mvn/jvm.config`; they apply to Maven started through `./mvnw`. In the IDE, if its own Maven runs the build, give it the same options (IntelliJ: Settings, Build Tools, Maven, Runner, VM options, or let it use the wrapper).

## Run a service in Docker and check it

```
make env                                   # once
make up-obs                                # infrastructure and observability (about 6.5 GB); `make up` is the same without it (about 5 GB)
make seed                                  # once per `make up`: the development users (alice, admin, ...); GET /users/me needs alice's row
make run SVC=account                       # build the image, start it, wait for /health/ready
make conformance SVC=account ARGS="--lifecycle"     # `--no-telemetry` when only `make up` is running
make down
```

After a code change: `make run SVC=account` again (it rebuilds). Logs: `docker logs -f wallpaperhub-account-1`.
Telemetry: Grafana at <http://localhost:3000> (Explore: Tempo, Loki, Prometheus); the checks T1-T3 look for the request of the conformance run in all three.

## Failure injection (chaos)

```
make up-obs && make seed                   # infrastructure and the development users, if not running yet
make up-chaos                              # the real account and the gateway behind Toxiproxy, Prism mocks for media, catalog, notification
make chaos-run S=redis-down                # also: postgres-down, keycloak-down, ... (make chaos-list; the scenarios of media, catalog and notification need their services)
make run SVC=account                       # account with direct connections again
```

This repository has its own `up-chaos`: the template's starts all four services and cannot build the three that are not here. See `docs/CHAOS.md` of the template for the scenarios.

## Run a service in the IDE (host mode)

1. `make up-obs` (or `make up`), then `make host SVC=account`: the template stops the container of the service and prints how to start it yourself (`scripts/host-info.sh`; the environment is `infra/host/account.env` of the template, which already points `OTEL_EXPORTER_OTLP_ENDPOINT` at `localhost:4317`).
2. Start `account`'s application class in the IDE with that environment.
3. With telemetry: `make otel-agent` once, then add the VM option it prints: `-javaagent:<repository>/target/otel/opentelemetry-javaagent.jar`. Without the agent the service runs the same and exports nothing; `OTEL_SDK_DISABLED=true` makes that explicit.
4. Back to the container: `make unhost SVC=account`.

## Telemetry in one paragraph

The OpenTelemetry Java agent is started by `docker/entrypoint.sh` (`-javaagent`) and configured only by the `OTEL_*` variables of the contract. It creates the server span of each request (continuing `traceparent`), client spans, and the HTTP metric `http.server.request.duration`, and exports logs, traces and metrics over gRPC to Alloy. Code that needs the current ids reads `Span.current()` (`opentelemetry-api`, already in `platform`); `PlatformRequestFilter` does that once per request and puts `trace_id` and `span_id` into the log's MDC and into `problem+json`. Custom spans or metrics: use the API (`GlobalOpenTelemetry`), not the SDK. Version of the agent: `opentelemetry-javaagent.version` in the root `pom.xml`.

## gRPC

The `AccountService` of `contracts/proto/account/v1` listens on 9090 in the container (9101 on the host). Every call needs the token of a service account (role `internal`); server reflection is on, so `grpcurl` needs no `.proto`:

```
TOKEN=$(.template/scripts/token.sh --client catalog-svc)
grpcurl -plaintext -H "authorization: Bearer $TOKEN" localhost:9101 list
grpcurl -plaintext -H "authorization: Bearer $TOKEN" -d '{"id":"<uuid>"}' localhost:9101 wallpaperhub.account.v1.AccountService/GetUser
```

A user's token (`scripts/token.sh alice`) is answered with `PERMISSION_DENIED`, no token with `UNAUTHENTICATED`. The classes are generated from `contracts/proto` by `protobuf-maven-plugin` into `account/target/generated-sources/protobuf`; there is no copy of the `.proto` under `src/`.

## The template

| What | Command |
|---|---|
| which version is pinned | `cat .template-version` |
| move to a newer tag | edit `.template-version`, `make template-sync`, `make template-check` |
| everything else (`make seed`, `make token`, `make e2e-system`, ...) | `make help`, second list |

## Troubleshooting

| Symptom | Look at |
|---|---|
| `make run` times out | the status it prints: `000` no connection (see `docker logs`), `503` a dependency is missing (Keycloak not up, `make up` first) |
| T1-T3 fail, everything else passes | `docker logs wallpaperhub-alloy-1` (is the endpoint reached), then Grafana Explore; the metric needs up to 15 s (`OTEL_METRIC_EXPORT_INTERVAL`), the check waits 90 s |
| L1 fails with a line that is not JSON | the line is printed by the check: usually a JVM or agent notice; options belong in `docker/entrypoint.sh` |
| the container is `OOMKilled` | the agent needs about 100 MB: raise `mem_limit` of `x-app` in the template, not here |
