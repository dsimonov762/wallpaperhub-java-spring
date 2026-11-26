# Contributing

Take one work package, stay in its lane, open one pull request, say what you verified. The rules are in [AGENTS.md](AGENTS.md); the packages are in the template's [ROADMAP](https://github.com/dsimonov762/wallpaperhub-template/blob/master/docs/ROADMAP.md).

- **Branch** from the latest `master`: `wp/<id>-<short-name>`, `fix/<short-name>`, `docs/<short-name>`.
- **Title:** `WP-<id>: what it does`. The pull request is squash-merged and the title becomes the commit on `master`.
- **Checks:** `make build`; for a service, the conformance run ([README.md](README.md)). CI must be green: `ok` is the one required check.
- **Contracts** change in the template, never here.
- **Merge:** squash only, linear history; resolve every conversation. Approvals are not required while there is one maintainer.
- Security problems: not an issue, see [SECURITY.md](SECURITY.md).
