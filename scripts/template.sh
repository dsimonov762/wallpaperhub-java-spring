#!/usr/bin/env bash
# The template repository, at the version this repository is pinned to (docs/adr/0023 of the template).
#
#   scripts/template.sh sync     fetch the pinned ref into .template/ and refresh the vendored contracts/
#   scripts/template.sh ensure   sync only when .template/ is missing or is not the pinned ref (the Makefile calls this)
#   scripts/template.sh check    fail when contracts/ differs from the pinned template (CI runs this)
#
# .template-version holds a tag (v0.1.0) or, while the template is still moving, a branch (master).
# TEMPLATE_REPO overrides the URL, e.g. git@github-dsimonov762:dsimonov762/wallpaperhub-template.git for an SSH alias.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="${TEMPLATE_REPO:-https://github.com/dsimonov762/wallpaperhub-template.git}"
REF="$(tr -d '[:space:]' < .template-version)"
DIR=".template"
MARK=".template.ref"

die() { echo "template.sh: $*" >&2; exit 1; }
[ -n "$REF" ] || die ".template-version is empty"

fetch() {
  mkdir -p "$DIR"
  [ -d "$DIR/.git" ] || git -C "$DIR" init -q
  if git -C "$DIR" remote get-url origin > /dev/null 2>&1; then
    git -C "$DIR" remote set-url origin "$REPO"
  else
    git -C "$DIR" remote add origin "$REPO"
  fi
  echo "template: fetching $REF from $REPO"
  git -C "$DIR" fetch -q --depth 1 origin "$REF" || die "cannot fetch '$REF' from $REPO (does the tag exist? is TEMPLATE_REPO right?)"
  git -C "$DIR" checkout -q --detach --force FETCH_HEAD
  echo "$REF" > "$MARK"
  echo "template: $REF is $(git -C "$DIR" rev-parse --short HEAD)"
}

vendor() {
  rm -rf contracts
  cp -R "$DIR/contracts" contracts
  echo "template: contracts/ refreshed from $REF; review and commit the difference"
}

ensure() {
  if [ -d "$DIR/.git" ] && [ -f "$MARK" ] && [ "$(cat "$MARK")" = "$REF" ]; then return 0; fi
  fetch
}

case "${1:-}" in
  sync) fetch; vendor ;;
  ensure) ensure ;;
  check)
    ensure
    if diff -r -q "$DIR/contracts" contracts > /dev/null 2>&1; then
      echo "template: contracts/ is identical to $REF of the template"
    else
      diff -r -q "$DIR/contracts" contracts || true
      die "contracts/ differs from $REF of the template: run scripts/template.sh sync (a contract is changed in the template, never here)"
    fi ;;
  *) die "usage: scripts/template.sh sync|ensure|check" ;;
esac
