#!/usr/bin/env bash
# Gate: every backend test must actually run. Quarkus turns an unavailable container runtime into
# SKIPPED tests, which surefire and maven both report as success. Fail instead.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
reports=target/surefire-reports
[ -d "$reports" ] || { echo "FAIL: $reports does not exist — run ./mvnw test first"; exit 1; }

# Measured 2026-11-23 with the whole suite running. Raise it when tests are added; lowering it lets a
# missing suite pass, which is the whole thing this script is for.
MIN_tests=149

status=0
if skipped=$(grep -l "Skipped: [1-9]" "$reports"/*.txt 2>/dev/null); then
  echo "FAIL: tests were skipped; the container-backed API and authorization tests did not run:"
  echo "$skipped"
  grep -h "Skipped: [1-9]" "$reports"/*.txt
  status=1
fi

total=$(grep -ho 'tests="[0-9]*"' "$reports"/*.xml 2>/dev/null | grep -o '[0-9]*' | paste -sd+ - | bc)
: "${total:=0}"
if [ "$total" -lt "$MIN_tests" ]; then
  echo "FAIL: ran $total tests, expected at least $MIN_tests — an entire suite is missing"
  status=1
fi

[ "$status" -eq 0 ] && echo "OK: $total tests ran, none skipped"
exit "$status"
