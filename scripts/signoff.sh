#!/usr/bin/env bash
# Local CI: run the checks on this machine and post them to GitHub with gh-signoff
# (https://github.com/basecamp/gh-signoff), as statuses signoff/unit and signoff/emulator.
#
# Usage: scripts/signoff.sh [unit] [emulator]   (default: both)
# Needs: gh + `gh extension install basecamp/gh-signoff`, a running emulator for "emulator",
# a clean working tree, and HEAD pushed (a signoff must describe exactly the pushed commit).
set -euo pipefail
cd "$(dirname "$0")/.."

contexts=("$@"); [ ${#contexts[@]} -eq 0 ] && contexts=(unit emulator)
[ -z "$(git status --porcelain)" ] || { echo "Working tree is dirty; commit or stash first."; exit 1; }

set_animations() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$k" "$1"; done; }

run_unit() { ./gradlew testDebugUnitTest lintDebug; }

run_emulator() {
  adb get-state >/dev/null 2>&1 || { echo "No emulator/device connected."; return 1; }
  set_animations 0
  trap 'set_animations 1' RETURN # leave the emulator usable for manual review
  ./gradlew connectedDebugAndroidTest && scripts/e2e-smoke.sh
}

for ctx in "${contexts[@]}"; do
  echo "== $ctx"
  if "run_$ctx"; then
    gh signoff "$ctx"
  else
    gh signoff fail "$ctx" --description "Local $ctx checks failed"
    exit 1
  fi
done
gh signoff status
