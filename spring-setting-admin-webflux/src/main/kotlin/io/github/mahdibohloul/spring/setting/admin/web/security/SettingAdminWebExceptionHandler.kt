package io.github.mahdibohloul.spring.setting.admin.web.security

import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

/**
 * JSON error renderer for the admin REST surface.
 *
 * Maps Spring-Security and library exceptions to consistent JSON bodies the SPA can act on:
 * - 401 for unauthenticated.
 * - 403 for authenticated-but-forbidden.
 * - 404 for unknown setting types.
 * - 400 for malformed JSON / patch payloads (delegated to default Spring handling).
 * - 500 for everything else, with the message preserved for non-prod debugging.
 */
class SettingAdminWebExceptionHandler(
  private val objectMapper: ObjectMapper,
) : WebExceptionHandler {

  override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
    val (status, code) = when (ex) {
      is AuthenticationException -> HttpStatus.UNAUTHORIZED to "unauthenticated"
      is AccessDeniedException -> HttpStatus.FORBIDDEN to "forbidden"
      is UnknownSettingTypeException -> HttpStatus.NOT_FOUND to "unknown-setting-type"
      is ResponseStatusException -> ex.statusCode.let {
        (HttpStatus.resolve(it.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR) to "error"
      }
      else -> HttpStatus.INTERNAL_SERVER_ERROR to "error"
    }

    val response = exchange.response
    response.statusCode = status
    response.headers.contentType = MediaType.APPLICATION_JSON

    val body = mapOf(
      "code" to code,
      "message" to (ex.message ?: code),
    )
    val payload = objectMapper.writeValueAsBytes(body)
    val buffer = response.bufferFactory().wrap(payload)
    return response.writeWith(Mono.just(buffer))
      .doOnError { DataBufferUtils.release(buffer) }
  }
}
