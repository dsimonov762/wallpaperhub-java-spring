# Working rules

For people and AI agents. Read [README.md](README.md), then [docs/STATUS.md](docs/STATUS.md), then your work package in the template's [ROADMAP](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/ROADMAP.md).
The template's own [AGENTS.md](https://github.com/dsimonov762/wallpaperhub-template/blob/master/AGENTS.md) applies here too (one package per pull request, stay in your lane, be honest about verification); this file only adds what is specific to this repository.

1. **The contract is the template's.** `contracts/` is a copy of the pinned version; never edit it (`make template-check` fails). Change a contract in the template, get a new tag, then move `.template-version` here.
2. **A service is done when the conformance suite passes** against its container (`make run SVC=<name>`, `make conformance SVC=<name> ARGS="--lifecycle"` after `make up-obs`), not when its unit tests pass.
3. **What the services share goes into `platform/`, what one service needs stays in its module.** Move code into `platform/` when the second service needs it, not before.
4. **Versions are managed by Spring Boot.** Do not write a version next to a dependency Boot manages; to change one, change the Boot version or set a property in the parent POM with a comment saying why. Plugins: the same.
5. **No schema of its own.** A service never creates tables: the schema is `contracts/db-schema`, applied by `make schemas` (SERVICE-SPEC section 9). Hibernate runs with `ddl-auto=validate`, there is no Flyway.
6. **Nothing but JSON on stdout and stderr** (SERVICE-SPEC section 6). No `System.out.println`, no banner, and the JVM's own notices are avoided in `docker/entrypoint.sh`.
7. **Secrets** come from the environment and never appear in a log line, an exception message or a problem response.
8. **Be honest about verification.** Say what you ran (`./mvnw verify`, the conformance suite, on which machine) and what you did not. This repository was first written in an environment that could not compile Java: see [docs/STATUS.md](docs/STATUS.md).
9. **Documentation is part of the change:** [docs/STATUS.md](docs/STATUS.md) and [docs/HISTORY.md](docs/HISTORY.md) in the same pull request.
10. Pull requests: branch `wp/<id>-<short>` or `fix/<short>`, title `WP-<id>: ...` (it becomes the squash commit), CI job `ok` green.
