# wallpaperhub-java-spring. Own targets: build and test the Java code, run one service in Docker, keep the template pinned.
# Every other target is the template's (make up, make conformance SVC=account, make e2e-system, make chaos-run, ...): it is
# forwarded to the pinned checkout in .template/ with the service built from THIS repository (docs/adr/0023 of the template).
SHELL := /usr/bin/env bash
.DEFAULT_GOAL := help

TEMPLATE := .template
MVNW := ./mvnw -B -ntp
SVC ?= account
COMPOSE := docker compose --project-directory $(TEMPLATE)/infra -f $(TEMPLATE)/infra/docker-compose.yml --profile infra --profile apps

# What the template's Compose file builds: the root of this repository, and the Dockerfile of each service inside it.
# Only the services that exist here; the others keep their defaults (a Prism mock stands in for them).
export ACCOUNT_DIR := $(CURDIR)
export ACCOUNT_DOCKERFILE := account/Dockerfile

.PHONY: help build test package clean fmt otel-agent template-sync template-check run up-chaos github-plan github-apply github-check

help: ## this list, then the template's
	@echo "wallpaperhub-java-spring (template pinned to $$(cat .template-version))"
	@grep -E '^[a-z-]+:.*## ' $(firstword $(MAKEFILE_LIST)) | awk -F':.*## ' '{printf "  %-16s %s\n", $$1, $$2}'
	@echo; echo "Everything else is forwarded to the template; its targets:"
	@scripts/template.sh ensure && $(MAKE) --no-print-directory -C $(TEMPLATE) help

build: ## compile and run every test (./mvnw verify)
	$(MVNW) verify

test: ## unit tests only
	$(MVNW) test

package: ## jars without tests
	$(MVNW) -DskipTests package

clean: ## remove target/ folders
	$(MVNW) clean

fmt: ## format the Java code (Spotless, palantir-java-format); `make build` fails on unformatted code
	$(MVNW) spotless:apply

otel-agent: ## download the OpenTelemetry Java agent to target/otel/ (to run a service in the IDE with telemetry: docs/CHEATSHEET.md)
	$(MVNW) -N dependency:copy
	@echo "VM option for the IDE: -javaagent:$(CURDIR)/target/otel/opentelemetry-javaagent.jar"

template-sync: ## fetch the pinned template and refresh contracts/
	scripts/template.sh sync

template-check: ## contracts/ is identical to the pinned template
	scripts/template.sh check

run: ## build the image of SVC (default account), start it with the infrastructure running (make up first) and wait for /health/ready
	@scripts/template.sh ensure
	$(COMPOSE) up -d --build --no-deps $(SVC)
	bash $(TEMPLATE)/scripts/wait-healthy.sh 300
	bash $(TEMPLATE)/scripts/wait-ready.sh $(SVC) 180

# The template's up-chaos starts all four services of the template (profile apps) and fails here: the other three are not in this repository.
# This one starts the real account (built from this repository) and the gateway behind Toxiproxy, and Prism mocks for the other three
# (they answer to the service names media, catalog and notification, like in `make up-mocks`). account-mock is scaled to 0: it would answer
# to the name `account` as well. Needs `make up` or `make up-obs` (and `make seed` for alice's row) first.
CHAOS_COMPOSE := docker compose --project-directory $(TEMPLATE)/infra -f $(TEMPLATE)/infra/docker-compose.yml -f $(TEMPLATE)/infra/docker-compose.chaos.yml --profile infra --profile schemas

up-chaos: ## account (real) and the gateway behind Toxiproxy, mocks for the other services; then: make chaos-run S=redis-down
	@scripts/template.sh ensure
	@$(MAKE) --no-print-directory -C $(TEMPLATE) env
	$(CHAOS_COMPOSE) up -d --build --scale account-mock=0 toxiproxy account media-mock catalog-mock notification-mock gateway
	bash $(TEMPLATE)/scripts/wait-healthy.sh 600
	bash $(TEMPLATE)/scripts/wait-ready.sh account 180
	@echo "Toxiproxy API: http://localhost:$${PORT_TOXIPROXY:-8474}   try: make chaos-run S=redis-down   (account with direct connections again: make run SVC=account)"

github-plan: ## print the repository settings that github-apply would send
	python3 scripts/github-setup.py apply --dry-run

github-apply: ## apply .github/repository.json, rulesets and labels to the GitHub repository
	python3 scripts/github-setup.py apply

github-check: ## the settings files are consistent with the workflow (offline)
	python3 scripts/github-setup.py check

# Anything else: the template's target, run in .template/ (command-line variables such as SVC= and ARGS= are passed on).
.DEFAULT:
	@scripts/template.sh ensure
	@$(MAKE) --no-print-directory -C $(TEMPLATE) $@
