plugins {
  // Declared once here with apply false so the plugin is loaded exactly once into the
  // Gradle JVM and subprojects can apply it without repeating the version string.
  // This eliminates the "Kotlin Gradle plugin was loaded multiple times" warning.
  kotlin("jvm") version "2.2.21" apply false
  kotlin("plugin.spring") version "2.2.21" apply false
  // Declared here so its types are on the root buildscript classpath, allowing
  // configure<DependencyManagementHandler> in the subprojects block below.
  id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
  group = "io.github.mahdibohloul"
  version = "1.0.0"

  repositories {
    mavenCentral()
  }
}

subprojects {
  pluginManager.withPlugin("io.spring.dependency-management") {
    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementHandler> {
      imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
      }
    }
  }
}
