allprojects {
  group = "io.github.mahdibohloul"
  version = "0.11.10"

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
  }
}
