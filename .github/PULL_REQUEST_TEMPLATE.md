<!-- Title: "WP-<id>: what it does" (the packages are in docs/ROADMAP.md of the template), or a plain sentence for a fix. The title becomes the commit on master (squash merge). -->

## What and why

## Checklist

- [ ] `make build` passes (`./mvnw verify`)
- [ ] `make conformance SVC=<service> ARGS="--lifecycle"` passes for the service I touched (needs `make up-obs` and `make run SVC=<service>`; `--no-telemetry` with `make up` skips T1-T3), or I say why not
- [ ] `contracts/` is untouched (a contract changes in the template first; `make template-check` passes)
- [ ] Documentation (`docs/STATUS.md`, `docs/HISTORY.md`) is updated

## Verified

<!-- What ran, how, on what; what did not run. "Not verified" is a normal answer; an unverified "done" is not. -->
