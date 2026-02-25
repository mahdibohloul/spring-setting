plugins {
  kotlin("jvm") version "1.9.23"
  kotlin("plugin.spring") version "1.9.23"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  `java-library`
}

description = "spring-setting-admin"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

dependencies {
  api(project(":spring-setting-core"))

  implementation("org.springframework:spring-context:6.2.10")
  implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.6")
  implementation("io.projectreactor:reactor-core:3.7.11")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.4")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
  implementation("org.slf4j:slf4j-api:2.0.12")

  testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.6")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.23")
  testImplementation("io.projectreactor:reactor-test:3.7.11")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
  testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

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
    name.set("spring-setting-admin")
    description.set(
      "Headless admin facade for spring-setting: list, get, patch (JSON Merge Patch), replace, and clear-all " +
        "operations over registered Setting types, with a pluggable authorization SPI.",
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

detekt {
  buildUponDefaultConfig = true
  allRules = true
  config.setFrom("$rootDir/detekt.yml")
  baseline = file("$rootDir/detekt-baseline.xml")
}
