plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  id("io.spring.dependency-management")
  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  `java-library`
}

description = "spring-setting-admin-keycloak"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

dependencies {
  api(project(":spring-setting-admin-webflux"))

  // webflux already transitively provided by spring-setting-admin-webflux (api); keep compileOnly
  // to avoid duplicate declarations and potential version skew.
  compileOnly("org.springframework.boot:spring-boot-starter-webflux")
  api("org.springframework.security:spring-security-oauth2-resource-server")
  api("org.springframework.security:spring-security-oauth2-jose")

  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("io.projectreactor:reactor-core")

  compileOnly("org.slf4j:slf4j-api")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  // spring-security-oauth2-jose/resource-server are now api deps — visible to test sources automatically.
  // spring-boot-starter-webflux is api via spring-setting-admin-webflux transitive — also visible.
  testImplementation("org.springframework.boot:spring-boot-starter-webflux")
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
    name.set("spring-setting-admin-keycloak")
    description.set(
      "Keycloak adapter for spring-setting-admin-webflux: provides a ReactiveJwtDecoder against " +
        "a configurable Keycloak realm plus a JWT-claim-to-GrantedAuthority converter that reads realm_access.roles.",
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

