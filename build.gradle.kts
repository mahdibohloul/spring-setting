plugins {
  kotlin("jvm") version "1.9.23"
  kotlin("plugin.spring") version "1.9.23"
  id("org.springframework.boot") version "3.5.6"
  id("io.spring.dependency-management") version "1.1.7"

  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  id("io.gitlab.arturbosch.detekt") version "1.23.6"

  `java-library`
}

group = "io.github.mahdibohloul"
version = "0.0.1-SNAPSHOT"
description = "spring-setting"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("io.projectreactor:reactor-core")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("box.tapsi.libs:utilities-starter:0.9.3")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.4")

  testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.6")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.23")
  testImplementation("io.projectreactor:reactor-test:3.7.11")
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
    name.set("spring-setting")
    description.set("Spring Setting is a library that simplifies the management of application settings in Spring applications.")
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
          "ktlint_standard_max-line-length" to "120"
        )
      )
    trimTrailingWhitespace()
    leadingTabsToSpaces()
    endWithNewline()
  }
}

detekt {
  buildUponDefaultConfig = true
  allRules = true
  config.setFrom("$projectDir/detekt.yml")
  baseline = file("$projectDir/detekt-baseline.xml")
}
