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

  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  // TransactionalOperator is optional — only needed when a ReactiveTransactionManager is on the classpath.
  // Declared compileOnly so consumers that don't use transactions don't get spring-tx pulled in transitively.
  compileOnly("org.springframework:spring-tx")
  implementation("io.projectreactor:reactor-core")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.4")
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("org.slf4j:slf4j-api")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework:spring-tx")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

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
