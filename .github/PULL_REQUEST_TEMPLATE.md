<!-- Title: "WP-<id>: what it does" (the packages are in docs/ROADMAP.md of the template), or a plain sentence for a fix. The title becomes the commit on master (squash merge). -->

## What and why

## Checklist

- [ ] `make build` passes (`./mvnw verify`)
- [ ] `make conformance SVC=<service> ARGS="--lifecycle --no-telemetry"` passes for the service I touched (needs `make up` and `make run SVC=<service>`), or I say why not
- [ ] `contracts/` is untouched (a contract changes in the template first; `make template-check` passes)
- [ ] Documentation (`docs/STATUS.md`, `docs/HISTORY.md`) is updated

## Verified

<!-- What ran, how, on what; what did not run. "Not verified" is a normal answer; an unverified "done" is not. -->
