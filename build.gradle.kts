plugins {
  // Declared here with apply false so its types are on the root buildscript classpath,
  // allowing configure<DependencyManagementHandler> in the subprojects block below.
  id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
  group = "io.github.mahdibohloul"
  version = "0.10.0"

  repositories {
    mavenCentral()
  }
}

subprojects {
  if (name != "spring-setting-demo") {
    // Keep Kotlin pinned at 1.9.23 for Phase A (SB3 final release).
    // The Spring Boot BOM would otherwise pull in 1.9.25, which breaks detekt 1.23.6.
    // Phase B will upgrade both Kotlin and detekt together as part of the SB4 migration.
    extra["kotlin.version"] = "1.9.23"

    // Apply the Spring Boot BOM to every library module in one place.
    // pluginManager.withPlugin fires lazily once the plugin is applied in each subproject,
    // so there is no ordering problem with the subproject's own plugins {} block.
    pluginManager.withPlugin("io.spring.dependency-management") {
      configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementHandler> {
        imports {
          mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
        }
      }
    }
  }
}
