#!/usr/bin/env bash
# The test classpath has jackson-module-kotlin; if the runtime classpath does not, the shipped jar
# renames every is* property (isOwner -> owner) and every test that asserts the wire format is lying.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
dir=target/quarkus-app/lib/main
[ -d "$dir" ] || { echo "FAIL: $dir missing — run ./mvnw -DskipTests package first"; exit 1; }
if ls "$dir"/com.fasterxml.jackson.module.jackson-module-kotlin-*.jar >/dev/null 2>&1; then
  echo "OK: jackson-module-kotlin is on the runtime classpath"; exit 0
fi
echo "FAIL: jackson-module-kotlin is not on the runtime classpath."
echo "The artifact will serialize is* properties without the is prefix."
exit 1
