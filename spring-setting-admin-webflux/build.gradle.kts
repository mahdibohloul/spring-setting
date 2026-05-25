plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("io.spring.dependency-management")
  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  `java-library`
}

description = "spring-setting-admin-webflux"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

dependencies {
  api(project(":spring-setting-admin"))
  // TransactionalOperator is optional — only needed when a ReactiveTransactionManager is on the classpath.
  compileOnly("org.springframework:spring-tx")

  api("org.springframework.boot:spring-boot-starter-webflux")
  api("org.springframework.boot:spring-boot-starter-security")
  // oauth2 types appear in public API of KeycloakJwtConfiguration; compileOnly would break consumers
  compileOnly("org.springframework.security:spring-security-oauth2-resource-server")
  compileOnly("org.springframework.security:spring-security-oauth2-jose")

  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("io.projectreactor:reactor-core")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.4")
  implementation("tools.jackson.module:jackson-module-kotlin")
  implementation("tools.jackson.core:jackson-databind")

  compileOnly("org.slf4j:slf4j-api")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-webflux-test")
  testImplementation("org.springframework.security:spring-security-test")
  // spring-boot-starter-webflux and spring-boot-starter-security are now api deps —
  // they are visible to test sources automatically; no re-declaration needed.
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  pom {
    name.set("spring-setting-admin-webflux")
    description.set(
      "Spring WebFlux REST + Spring Security adapter for spring-setting-admin: exposes the admin " +
        "operations over HTTP with per-type per-operation role-based ACL and three pluggable auth " +
        "modes (Keycloak JWT, HTTP Basic, NOOP for tests).",
    )
    url.set("https://github.com/mahdibohloul/spring-setting")
    licenses {
      license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
        distribution.set("repo")
      }
    }
    developers {
      developer {
        id.set("mahdibohloul")
        name.set("Mahdi Bohloul")
        email.set("mahdiibohloul@gmail.com")
        url.set("https://github.com/mahdibohloul/")
      }
    }
    scm {
      url.set("https://github.com/mahdibohloul/spring-setting")
    }
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint()
      .editorConfigOverride(
        mapOf(
          "indent_size" to 2,
          "ktlint_standard_filename" to "disabled",
          "ktlint_standard_max-line-length" to "120",
        ),
      )
    trimTrailingWhitespace()
    leadingTabsToSpaces()
    endWithNewline()
  }
}

