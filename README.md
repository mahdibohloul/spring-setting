# Spring Setting

[![Build Status](https://github.com/mahdibohloul/spring-setting/workflows/CI/badge.svg)](https://github.com/mahdibohloul/spring-setting/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mahdibohloul/spring-setting-core.svg)](https://search.maven.org/artifact/io.github.mahdibohloul/spring-setting-core)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A Spring Boot library for managing application settings with multi-level storage support. Inspired by the Spring Data
pattern, it provides a unified interface for accessing settings from memory, Redis, and MongoDB with automatic fallback
capabilities.

## Features

- 🚀 **Multi-level Storage**: Memory (Caffeine) → Redis → MongoDB fallback chain
- ⚡ **Reactive Programming**: Built on Project Reactor for non-blocking operations
- 🔧 **Auto-configuration**: Spring Boot auto-configuration for all modules
- 🎯 **Composite Pattern**: Chain multiple repositories for hierarchical storage
- 📦 **Modular Design**: Use only the storage backends you need
- 🛡️ **Type Safety**: Kotlin-first with full type safety

## Quick Start

### 1. Add Dependencies

```kotlin
dependencies {
  // Core library (required)
  implementation("io.github.mahdibohloul:spring-setting-core:0.0.1-SNAPSHOT")

  // Choose your storage backends
  implementation("io.github.mahdibohloul:spring-setting-memory:0.0.1-SNAPSHOT")
  implementation("io.github.mahdibohloul:spring-setting-redis:0.0.1-SNAPSHOT")
  implementation("io.github.mahdibohloul:spring-setting-mongodb:0.0.1-SNAPSHOT")
}
```

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
  fun getDatabaseConfig(): Mono<DatabaseConfig> {
    return settingService.findByName("database.config", DatabaseConfig::class)
  }

  fun saveDatabaseConfig(config: DatabaseConfig): Mono<Void> {
    return settingService.save("database.config", config)
  }
}
```

## Architecture

### Multi-level Storage

The library implements a hierarchical storage pattern:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Memory Cache  │───▶│      Redis      │───▶│     MongoDB     │
│   (Caffeine)    │    │   (Fast Cache)  │    │  (Persistent)   │
│   Fastest       │    │   Persistent    │    │   Reliable      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

1. **Memory (Caffeine)**: Ultra-fast access with configurable TTL
2. **Redis**: Fast, persistent cache with network access
3. **MongoDB**: Reliable, persistent storage for long-term data

### Repository Pattern

```kotlin
interface SettingRepository {
  fun <T : Setting> findByName(key: String, type: KClass<T>): Mono<T>
  fun <T : Setting> deleteByName(key: String, type: KClass<T>): Mono<Void>
  fun <T : Setting> save(key: String, setting: T): Mono<Void>
}
```

## Modules

### Core Module (`spring-setting-core`)

Provides the base interfaces and composite pattern implementation.

**Dependencies:**

- Spring Context
- Spring Boot Auto-configuration
- Project Reactor
- Jackson for serialization

### Memory Module (`spring-setting-memory`)

Caffeine-based in-memory cache with configurable TTL.

**Features:**

- AsyncCache for reactive programming
- Configurable maximum size and TTL
- Automatic eviction policies

**Configuration:**

```yaml
spring-setting:
  memory:
    maximum-size: 1000
    expire-after-write: PT1H
```

### Redis Module (`spring-setting-redis`)

Reactive Redis implementation for distributed caching.

**Features:**

- Reactive Redis operations
- JSON serialization
- Network-accessible storage

**Configuration:**

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

### MongoDB Module (`spring-setting-mongodb`)

Reactive MongoDB implementation for persistent storage.

**Features:**

- Reactive MongoDB operations
- Document-based storage
- Full-text search capabilities

**Configuration:**

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/myapp
```

## Configuration

### Auto-configuration

The library automatically configures individual repository implementations based on available dependencies. The
auto-configuration follows a specific order to ensure proper dependency resolution:

#### Auto-configuration Order

1. **Core Auto-configuration** (`SettingAutoConfiguration`)
    - Provides basic `SettingRepository` fallback (`SimpleInMemorySettingRepository`)
    - Configures `SettingService`, `SettingReader`, `SettingWriter`
    - Runs first to establish the foundation

2. **Module Auto-configurations** (run after core)
    - **Memory** (`MemorySettingAutoConfiguration`) - runs after core
    - **Redis** (`RedisSettingAutoConfiguration`) - runs after core + Redis auto-configuration
    - **MongoDB** (`MongoSettingAutoConfiguration`) - runs after core + MongoDB auto-configuration

#### Configuration Flow

```
Spring Boot Auto-configurations
    ↓
SettingAutoConfiguration (core)
    ↓
MemorySettingAutoConfiguration (if Caffeine available)
RedisSettingAutoConfiguration (if Redis available)  
MongoSettingAutoConfiguration (if MongoDB available)
    ↓
User's Composite Configuration (explicit)
```

#### Auto-configuration Import Files

Each module includes a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file that
lists the auto-configuration classes:

- **spring-setting-core**: `SettingAutoConfiguration`
- **spring-setting-memory**: `MemorySettingAutoConfiguration`
- **spring-setting-redis**: `RedisSettingAutoConfiguration`
- **spring-setting-mongodb**: `MongoSettingAutoConfiguration`

These files follow
the [Spring Boot auto-configuration specification](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
and ensure that auto-configurations are properly discovered by Spring Boot.

**Why this ordering matters:**

- **Core first**: Establishes the basic `SettingRepository` fallback
- **Modules after core**: Can override the core's fallback with specific implementations
- **Dependencies respected**: Each module waits for its required Spring Boot auto-configurations
- **No conflicts**: Proper ordering prevents bean definition conflicts

```kotlin
@SpringBootApplication
class MyApplication {
  // Auto-configuration will detect available storage backends
  // and create individual repository beans (memory, redis, mongodb)
  // Composite repository configuration is left to the user
}
```

### Manual Configuration

The library provides auto-configuration for individual repository implementations, but **composite repository
configuration is left to the user** for full control and transparency.

```kotlin
@Configuration
class SettingConfiguration {

  /**
   * Explicitly configure the composite repository with your desired order.
   * This gives you full control over the repository chain and makes the behavior transparent.
   *
   * Repository order (left to right):
   * 1. Memory (Caffeine) - Fastest access, in-memory cache
   * 2. Redis - Fast, persistent cache with network access
   * 3. MongoDB - Persistent, reliable storage
   */
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

**Why explicit configuration?**

- **No Magic**: You know exactly what repositories are being used and in what order
- **Full Control**: You can customize the repository chain for your specific needs
- **Transparency**: The behavior is explicit and predictable
- **Flexibility**: Easy to add/remove repositories or change the order

### Configuration Examples

```kotlin
@Configuration
class SettingConfiguration {

  // Example 1: Memory + Redis (Fast cache with persistence)
  @Bean("memoryRedisComposite")
  fun memoryRedisComposite(
    memoryRepo: SettingRepository,
    redisRepo: SettingRepository,
  ): SettingRepository = CompositeSettingRepository(
    listOf(memoryRepo, redisRepo)
  )

  // Example 2: Memory + MongoDB (Fast cache with reliable storage)
  @Bean("memoryMongoComposite")
  fun memoryMongoComposite(
    memoryRepo: SettingRepository,
    mongoRepo: SettingRepository,
  ): SettingRepository = CompositeSettingRepository(
    listOf(memoryRepo, mongoRepo)
  )

  // Example 3: Full three-tier setup (Memory + Redis + MongoDB)
  @Bean
  @Primary
  fun fullComposite(
    memoryRepo: SettingRepository,
    redisRepo: SettingRepository,
    mongoRepo: SettingRepository,
  ): SettingRepository = CompositeSettingRepository(
    listOf(memoryRepo, redisRepo, mongoRepo)
  )
}
```

## Demo Application

Run the demo application to see the multi-level storage in action:

```bash
./gradlew :spring-setting-demo:bootRun
```

The demo showcases:

- Setting storage across all levels
- Automatic fallback behavior
- Performance characteristics

## Building from Source

```bash
git clone https://github.com/mahdibohloul/spring-setting.git
cd spring-setting
./gradlew build
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by [Spring Data](https://spring.io/projects/spring-data) patterns
- Built with [Project Reactor](https://projectreactor.io/) for reactive programming
- Uses [Caffeine](https://github.com/ben-manes/caffeine) for high-performance caching
