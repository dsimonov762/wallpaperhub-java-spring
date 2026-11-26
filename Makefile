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

.PHONY: help build test package clean fmt template-sync template-check run github-plan github-apply github-check

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

template-sync: ## fetch the pinned template and refresh contracts/
	scripts/template.sh sync

template-check: ## contracts/ is identical to the pinned template
	scripts/template.sh check

run: ## build the image of SVC (default account) and start it with the infrastructure running (make up first)
	@scripts/template.sh ensure
	$(COMPOSE) up -d --build --no-deps $(SVC)
	bash $(TEMPLATE)/scripts/wait-healthy.sh 300

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
