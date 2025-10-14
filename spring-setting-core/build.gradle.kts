plugins {
  kotlin("jvm") version "1.9.23"
  kotlin("plugin.spring") version "1.9.23"
  id("io.spring.dependency-management") version "1.1.7"
  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  `java-library`
}

description = "spring-setting-core"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

dependencies {
  implementation("org.springframework:spring-context:6.2.7")
  implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.6")
  implementation("io.projectreactor:reactor-core:3.7.11")
  implementation("org.springframework:spring-aop:6.1.5")
  implementation("org.aspectj:aspectjweaver:1.9.21")
  implementation("org.slf4j:slf4j-api:2.0.12")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
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
    name.set("spring-setting-core")
    description.set("Core Spring Setting library with repository interfaces and composite pattern.")
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

