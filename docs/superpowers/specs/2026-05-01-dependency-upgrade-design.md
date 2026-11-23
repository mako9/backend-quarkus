# Dependency Upgrade to Quarkus 3.33.1 LTS

**Date:** 2026-05-01  
**Branch:** upgrade-dependencies  
**Scope:** Required fixes only — version bumps, removed/renamed config, and one incorrect import

---

## Context

The project is a Quarkus + Kotlin backend using PostgreSQL, Keycloak OIDC, Hibernate ORM with Panache, and Liquibase. The `upgrade-dependencies` branch already has a partial upgrade (Quarkus 3.5.1 → 3.19.0, Kotlin 1.9.20 → 2.1.0, hibernate-types → hypersistence-utils). This spec covers the remaining changes to reach Quarkus 3.33.1 LTS.

---

## Changes

### 1. `pom.xml` — Version bumps + correctness fixes

| Item | From | To | Reason |
|---|---|---|---|
| `quarkus.platform.version` | `3.19.0` | `3.33.1` | Upgrade to current LTS |
| `kotlin.version` | `2.1.0` | `2.3.20` | Latest stable Kotlin |
| `hypersistence-utils-hibernate-63` version | `3.9.2` | `3.15.2` | Latest; still correct artifact (covers Hibernate 6.3–6.6) |
| `quarkus-plugin.version` property | present | **removed** | Unused — the plugin already references `${quarkus.platform.version}` |
| `kotlin-maven-allopen` option: `javax.ws.rs.Path` | `javax.*` | `jakarta.ws.rs.Path` | Was wrong since Quarkus 3.0 moved to Jakarta EE 10 |
| `kotlin-maven-allopen` option: `javax.enterprise.context.ApplicationScoped` | `javax.*` | `jakarta.enterprise.context.ApplicationScoped` | Same reason |

### 2. `application.properties` — Removed config keys

| Key | From | To | Reason |
|---|---|---|---|
| CORS enable | `quarkus.http.cors=true` | `quarkus.http.cors.enabled=true` | Old key removed in Quarkus 3.28 |
| Policy enforcer enable | `quarkus.keycloak.policy-enforcer.enable=true` | `quarkus.keycloak.policy-enforcer.enabled=true` | `.enable` suffix deprecated → `.enabled` in 3.26+ |

### 3. `pom.xml` native profile — Removed property

| Item | From | To | Reason |
|---|---|---|---|
| Native build flag | `<quarkus.package.type>native</quarkus.package.type>` | `<quarkus.native.enabled>true</quarkus.native.enabled>` | `quarkus.package.type` removed in Quarkus 3.x |

### 4. Code — `org.apache.http.HttpStatus` import

**Files:** `src/main/kotlin/api/controller/ItemController.kt`, `src/main/kotlin/api/controller/CommunityController.kt`

Both files import `org.apache.http.HttpStatus` solely to use `SC_NO_CONTENT` (204) and `SC_CREATED` (201) in `@ResponseStatus` annotations. This library is not in the Quarkus 3.33 BOM.

**Fix:** Replace with inline integer literals — `@ResponseStatus(204)` and `@ResponseStatus(201)`. Remove the import.

---

## Out of Scope

- Constructor injection refactor (user chose required fixes only)
- Any code cleanup not needed for the upgrade to compile and run
