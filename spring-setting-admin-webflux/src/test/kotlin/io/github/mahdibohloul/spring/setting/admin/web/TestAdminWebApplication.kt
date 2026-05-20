package io.github.mahdibohloul.spring.setting.admin.web

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Minimal Spring Boot root needed so that `@WebFluxTest` slices in this module can locate
 * a `@SpringBootConfiguration` and apply the correct auto-configuration set.
 *
 * This class is **test-only** — it is never shipped to consumers.
 */
@SpringBootApplication
internal class TestAdminWebApplication
