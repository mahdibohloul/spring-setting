# Spring Setting

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.23-blue.svg)](https://kotlinlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mahdibohloul/spring-setting-core)](https://search.maven.org/artifact/io.github.mahdibohloul/spring-setting-core)

A Spring Boot library for managing application settings with multi-level storage support. Inspired by the Spring Data
pattern, it provides a unified interface for accessing settings from memory, Redis, and MongoDB with automatic fallback
capabilities. An optional admin layer exposes a secure REST API for reading and mutating settings at runtime without
redeployment.

## Features

- 🚀 **Multi-level Storage**: Memory (Caffeine) → Redis → MongoDB fallback chain
- ⚡ **Reactive Programming**: Built on Project Reactor for non-blocking operations
- 🔧 **Auto-configuration**: Spring Boot auto-configuration for all modules
- 🎯 **Composite Pattern**: Chain multiple repositories for hierarchical storage
- 📦 **Modular Design**: Use only the storage backends you need
- 🛡️ **Type Safety**: Kotlin-first with full type safety
- ✅ **Bean Validation**: Optional Jakarta Bean Validation support for settings
- 🔑 **Admin REST API**: Optional HTTP layer for managing settings at runtime
- 🔐 **Pluggable Auth**: Keycloak JWT, HTTP Basic, or NOOP auth for the admin surface
- 📋 **Audit Log**: Optional change history with before/after values, caller identity, and one-click revert

## Quick Start

### 1. Add Dependencies

```kotlin
dependencies {
  // Core library (required)
  implementation("io.github.mahdibohloul:spring-setting-core:0.11.1")

  // Choose your storage backends
  implementation("io.github.mahdibohloul:spring-setting-memory:0.11.1")
  implementation("io.github.mahdibohloul:spring-setting-redis:0.11.1")
  implementation("io.github.mahdibohloul:spring-setting-mongodb:0.11.1")

  // Optional: headless admin facade (list / read / patch / replace / delete)
  implementation("io.github.mahdibohloul:spring-setting-admin:0.11.1")

  // Optional: expose the admin facade over HTTP (requires spring-boot-starter-webflux)
  implementation("io.github.mahdibohloul:spring-setting-admin-webflux:0.11.1")

  // Optional: back the HTTP admin layer with Keycloak JWT auth
  implementation("io.github.mahdibohloul:spring-setting-admin-keycloak:0.11.1")

  // Optional: enable Bean Validation for settings
  implementation("org.springframework.boot:spring-boot-starter-validation")
}
```

> **Note:** `spring-setting-admin-webflux` and `spring-setting-admin-keycloak` declare WebFlux and Spring Security
> as `compileOnly` — they will not force those stacks onto applications that do not need them. The consuming
> application must provide `spring-boot-starter-webflux` and `spring-boot-starter-security` on its own classpath
> for the admin endpoints to activate.

### 2. Define Your Settings

```kotlin
data class DatabaseConfig(
  val host: String,
  val port: Int,
  val database: String,
) : Setting
```

### 3. Use the Setting Service

```kotlin
@Service
class MyService(
  private val settingService: SettingService,
) {
  fun getDatabaseConfig(): Mono<DatabaseConfig> =
    settingService.findByName("database.config", DatabaseConfig::class)

  fun saveDatabaseConfig(config: DatabaseConfig): Mono<Void> =
    settingService.save("database.config", config)
}
```

### 4. Validation (Optional)

When `spring-boot-starter-validation` is on the classpath the library automatically validates settings using
Jakarta Bean Validation. Validation runs **before save** (to reject invalid data) and **on load** (to fail fast on
corrupted or legacy data).

```kotlin
data class PaymentSetting(
  @DecimalMax(value = "1000000", message = "Amount must be less than or equal to 1,000,000")
  val maxAmount: BigDecimal,

  @Future(message = "Expiry date must be in the future")
  val expiryDate: LocalDate,
) : Setting
```

If no `Validator` bean is present validation is silently skipped.

---

## Architecture

### Multi-level Storage

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Memory Cache  │───▶│      Redis      │───▶│     MongoDB     │
│   (Caffeine)    │    │   (Fast Cache)  │    │  (Persistent)   │
│   Fastest       │    │   Distributed   │    │   Reliable      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Admin Layer

```
Consumer Application
        │
        ▼
spring-setting-admin          ← headless facade: list / read / patch / replace / delete / history / revert
        │                        audit SPI (SettingAuditLog, SettingAuditPrincipalProvider)
        ▼
spring-setting-mongodb        ← optional: MongoDB-backed audit log + TransactionalOperator
        │
        ▼
spring-setting-admin-webflux  ← HTTP endpoints + Spring Security + per-type ACL
        │                        history & revert endpoints + reactive principal provider
        ▼
spring-setting-admin-keycloak ← optional: Keycloak JWT decoder + realm-role converter
```

### Repository Pattern

```kotlin
interface SettingRepository {
  fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T>
  fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void>
  fun <T : Setting> save(key: String, setting: T): Mono<Void>
}
```

---

## Modules

### Core (`spring-setting-core`)

Provides the base interfaces, composite pattern implementation, and optional Bean Validation integration.
`reactor-core` is an `api` dependency — consuming modules receive Reactor types on their compile classpath
without redeclaring it. `slf4j-api` is `compileOnly`; logging is provided by whatever SLF4J binding the host
application supplies.

### Memory (`spring-setting-memory`)

Caffeine-based in-memory cache with configurable TTL.

```yaml
spring:
  setting:
    memory:
      maximum-size: 10000       # default
      expire-after-write: PT1M  # default
```

### Redis (`spring-setting-redis`)

Reactive Redis implementation for distributed caching.

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### MongoDB (`spring-setting-mongodb`)

Reactive MongoDB implementation for persistent storage.

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/myapp
```

---

### Admin (`spring-setting-admin`)

Transport-agnostic façade for managing settings at runtime: list types, read current values, patch with
JSON Merge Patch (RFC 7396), replace, and delete — with no HTTP or security assumptions.

**Key SPIs:**

- `SettingTypeDescriptor` — register one bean per `Setting` type to opt it into the admin API.
- `SettingAdminService` — headless service returning JSON strings.
- `SettingAdminAuthorizer` — replace the allow-all default with your own auth-context-aware implementation.

**Usage:**

```kotlin
// Describe your setting (recommended: same package as the Setting class).
@Component
class DelaySignalPolicySettingDescriptor : SettingTypeDescriptor<DelaySignalPolicySetting> {
  override val settingClass = DelaySignalPolicySetting::class
  override fun default() = DelaySignalPolicySetting()
}
```

Setting persistence keys are derived via `SettingHelper.getSettingName(...)`, so values written through the
admin API are immediately visible to `SettingService.loadSetting(...)` and `@InjectSetting` consumers.

---

### Admin WebFlux (`spring-setting-admin-webflux`)

Spring WebFlux REST + Spring Security layer on top of `spring-setting-admin`. Only activates in reactive
(`WebApplicationType.REACTIVE`) applications and only when `spring.setting.admin.web.enabled=true` is set.
Both WebFlux and Spring Security must be provided by the consuming application.

**Endpoints** (base path configurable via `spring.setting.admin.web.base-path`, default `/spring-setting/admin`):

| Method   | Path                                       | Operation                                          |
|----------|--------------------------------------------|----------------------------------------------------|
| `GET`    | `{basePath}/settings`                      | List all registered type names                     |
| `GET`    | `{basePath}/settings/{type}`               | Read current value (or default)                    |
| `PATCH`  | `{basePath}/settings/{type}`               | RFC 7396 JSON Merge Patch                          |
| `PUT`    | `{basePath}/settings/{type}`               | Full replace                                       |
| `DELETE` | `{basePath}/settings/{type}`               | Reset to default                                   |
| `GET`    | `{basePath}/settings/{type}/history`       | Audit history (newest-first, `?limit=20`)          |
| `POST`   | `{basePath}/settings/{type}/revert/{id}`   | Revert to the `previousValue` of that audit entry  |
| `GET`    | `{basePath}/features`                      | Feature catalogue for UI consumers                 |
| `GET`    | `{basePath}/me`                            | Current caller's principal and roles               |

> **Audit endpoints** are only registered when `spring.setting.audit.enabled=true`. Without that flag the
> routes are entirely absent.

**Auth modes** (`spring.setting.admin.web.auth.mode`):

| Mode | Description |
|---|---|
| `keycloak` (default) | OAuth2 Resource Server — validates JWT Bearer tokens using a `ReactiveJwtDecoder` bean. Provided by `spring-setting-admin-keycloak` or any custom `ReactiveJwtDecoder` bean. Returns `401` for missing/invalid tokens — **does not redirect to a login page**. |
| `basic` | HTTP Basic against a hardcoded user. **Non-production only.** |
| `noop` | No authentication. A synthetic `Authentication` carrying configured roles is injected so the per-type ACL still runs. Intended for integration tests and local dev. |

> **Resource Server vs OAuth2 Client:** The admin layer is an OAuth2 Resource Server. It validates Bearer
> tokens — it never redirects a browser to a login page. To call the endpoints, obtain an access token from
> your authorization server first (e.g. via Postman, `curl`, or your SPA's auth flow) and pass it as
> `Authorization: Bearer <token>`.

**Per-type per-operation ACL:**

```yaml
spring:
  setting:
    admin:
      acl:
        global:
          list: [ops-tribe, ops-manager, tech-manager]
        defaults:
          read:    [ops-tribe, ops-manager, tech-manager]
          patch:   [ops-manager, tech-manager]
          replace: [ops-manager, tech-manager]
          delete:  [tech-manager]
          history: [ops-tribe, ops-manager, tech-manager]   # who may view change history
          revert:  [ops-manager, tech-manager]              # who may revert to a previous value
        profiles:
          # Narrow the default for a specific setting type (key = simple class name).
          DelayWeeklyEnforcementPolicySetting:
            patch:  [tech-manager]
            delete: [tech-manager]
            revert: [tech-manager]
```

**CORS:**

```yaml
spring:
  setting:
    admin:
      web:
        cors:
          allowed-origins: ["https://admin.example.com"]
          allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
          allowed-headers: [Authorization, Content-Type]
          max-age: PT1H
```

**Feature SPI** — register additional `AdminFeatureDescriptor` beans to expose extra admin operations in a
dynamic UI without changing the library.

**`UserDetailsServiceAutoConfiguration` warning:** `@EnableWebFluxSecurity` causes Spring Security's shared
`AuthenticationConfiguration` to register an `ObjectPostProcessor` bean, which Spring Boot's servlet-security
auto-configuration interprets as a signal to create an in-memory user with a random password. This module
registers a no-op `UserDetailsService` bean to suppress that warning automatically — no exclusion is needed
in the consuming application.

---

### Audit Log

The audit log is an **opt-in** feature that records every PATCH, PUT, and DELETE operation with:

- the **previous** and **new** JSON value
- the **caller identity** (resolved from Spring Security context in the WebFlux layer)
- a **timestamp**

#### Enabling

Add `spring.setting.audit.enabled=true` and include `spring-setting-mongodb` on the classpath to activate
MongoDB-backed persistence:

```yaml
spring:
  setting:
    audit:
      enabled: true
      mongodb:
        collection-name: setting_audit_logs  # default
        max-entries-per-type: 50             # default; 0 or negative disables eviction
```

Without `spring.setting.audit.enabled=true` a `NoopSettingAuditLog` is used and the history/revert
endpoints are not registered — existing behaviour is completely unchanged.

#### How it works

1. **After** a setting is successfully saved (or deleted), an `AuditEntry` is written with the before
   and after values.
2. **Retention** — after each write the oldest entries beyond `max-entries-per-type` are evicted.
   Eviction failures are logged and swallowed; they never affect the setting save.
3. **Transactionality** — when `spring-setting-mongodb` detects a `ReactiveMongoTransactionManager`
   bean (i.e. a MongoDB replica set with transaction management configured), the setting-save and
   audit-write are wrapped in a single atomic transaction. Both commit or both roll back.
   Without a transaction manager the audit write is best-effort.

#### Revert

`POST {basePath}/settings/{type}/revert/{entryId}` reads the `previousValue` from the named audit
entry and calls `replace()` with it. The revert itself is also audited as a REPLACE, giving a full
chain of custody in the history.

#### Custom audit principal provider

The library ships a `ReactiveSecurityAuditPrincipalProvider` that reads the authenticated username
from `ReactiveSecurityContextHolder`. To customise how the caller identity is resolved, register
your own `SettingAuditPrincipalProvider` bean:

```kotlin
@Bean
fun myPrincipalProvider(): SettingAuditPrincipalProvider =
  SettingAuditPrincipalProvider {
    ReactiveSecurityContextHolder.getContext()
      .map { ctx -> ctx.authentication?.name ?: "system" }
  }
```

#### Custom audit log backend

To store audit entries in a different backend, implement `SettingAuditLog` and register it as a bean:

```kotlin
@Bean
fun customAuditLog(): SettingAuditLog = MyCustomAuditLog()
```

The MongoDB implementation (`MongoSettingAuditLog`) is skipped when a `SettingAuditLog` bean is
already present (`@ConditionalOnMissingBean`).

---

### Admin Keycloak (`spring-setting-admin-keycloak`)

Wires Keycloak as the JWT issuer for `spring-setting-admin-webflux`. Provides a `NimbusReactiveJwtDecoder`
pointed at the realm's JWKS endpoint and a `JwtAuthenticationConverter` that extracts roles from
`realm_access.roles`.

**Configuration:**

```yaml
spring:
  setting:
    admin:
      keycloak:
        enabled: true
        issuer-uri: https://auth.example.com/realms/my-realm  # required — no default
        authority-prefix: ""   # leave empty to match ACL role names 1:1 (recommended)
```

`issuer-uri` has no default and must be set explicitly. The JWKS endpoint is derived automatically as
`{issuerUri}/protocol/openid-connect/certs`.

**Verify your Keycloak configuration** before enabling:

```bash
# Confirm the realm is reachable and inspect supported grant types / token format.
curl -s https://auth.example.com/realms/my-realm/.well-known/openid-configuration | jq .

# Confirm the JWKS endpoint returns public keys.
curl -s https://auth.example.com/realms/my-realm/protocol/openid-connect/certs | jq .
```

By default Keycloak issues signed JWT access tokens (RS256). If your realm is configured to issue opaque
(reference) tokens you will need a custom `ReactiveJwtDecoder` backed by token introspection instead.

---

## Configuration Reference

### Core

```yaml
spring:
  setting:
    create-default-instance: true  # create default instance when none exists
```

### Memory

```yaml
spring:
  setting:
    memory:
      maximum-size: 10000    # max entries in cache
      expire-after-write: PT1M  # TTL after write
```

### Redis

```yaml
spring:
  setting:
    redis:
      prefix: "setting:"  # key prefix
      ttl: PT5M           # key TTL
```

### Audit Log

```yaml
spring:
  setting:
    audit:
      enabled: false                          # opt-in; false by default
      mongodb:
        collection-name: setting_audit_logs   # MongoDB collection for audit entries
        max-entries-per-type: 50              # retention cap per setting type; ≤0 disables eviction
```

### Admin WebFlux — full example

```yaml
spring:
  setting:
    admin:
      web:
        enabled: true
        base-path: /spring-setting/admin
        auth:
          mode: keycloak          # keycloak | basic | noop
          basic:                  # used only when mode=basic
            username: admin
            password: admin
            roles: [ops-tribe, ops-manager, tech-manager]
          noop:                   # used only when mode=noop
            roles: [ops-tribe, ops-manager, tech-manager]
            synthetic-principal: dev-noop
        cors:
          allowed-origins: []
          allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
          allowed-headers: [Authorization, Content-Type]
          exposed-headers: []
          allow-credentials: false
          max-age: PT1H
      acl:
        global:
          list: []
        defaults:
          read:    []
          patch:   []
          replace: []
          delete:  []
          history: []
          revert:  []
        profiles: {}
      keycloak:
        enabled: false
        issuer-uri: ""
        authority-prefix: ""
```

---

## Composite Repository Configuration

The library auto-configures individual repository beans but leaves the composite wiring to the consuming
application for full transparency and control.

```kotlin
@Configuration
class SettingConfiguration {

  // Three-tier: Memory → Redis → MongoDB
  @Bean
  @Primary
  fun compositeSettingRepository(
    memoryRepo: SettingRepository,
    redisRepo: SettingRepository,
    mongoRepo: SettingRepository,
  ): SettingRepository = CompositeSettingRepository(
    listOf(memoryRepo, redisRepo, mongoRepo)
  )
}
```

Other common combinations:

```kotlin
// Memory + MongoDB (no Redis)
CompositeSettingRepository(listOf(memoryRepo, mongoRepo))

// Memory only (dev / tests)
CompositeSettingRepository(listOf(memoryRepo))
```

---

## Building from Source

```bash
git clone https://github.com/mahdibohloul/spring-setting.git
cd spring-setting
./gradlew build
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Submit a pull request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by [Spring Data](https://spring.io/projects/spring-data) patterns
- Built with [Project Reactor](https://projectreactor.io/) for reactive programming
- Uses [Caffeine](https://github.com/ben-manes/caffeine) for high-performance caching
