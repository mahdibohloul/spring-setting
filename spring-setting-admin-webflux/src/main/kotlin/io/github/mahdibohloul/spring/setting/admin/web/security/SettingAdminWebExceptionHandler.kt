package io.github.mahdibohloul.spring.setting.admin.web.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mahdibohloul.spring.setting.admin.InvalidHistoryCursorException
import io.github.mahdibohloul.spring.setting.admin.UnknownSettingTypeException
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

/**
 * JSON error renderer for the admin REST surface.
 *
 * Maps Spring-Security and library exceptions to consistent JSON bodies the SPA can act on:
 * - 401 for unauthenticated.
 * - 403 for authenticated-but-forbidden.
 * - 404 for unknown setting types.
 * - 400 for a history `before` cursor that the API did not return.
 * - 400 for malformed JSON / patch payloads (delegated to default Spring handling).
 * - 500 for everything else, with the message preserved for non-prod debugging.
 *
 * A 5xx is logged at ERROR with the stack trace, because the body has only the top-level message.
 */
class SettingAdminWebExceptionHandler(
  private val objectMapper: ObjectMapper,
) : WebExceptionHandler {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
    val (status, code) = when (ex) {
      is AuthenticationException -> HttpStatus.UNAUTHORIZED to "unauthenticated"
      is AccessDeniedException -> HttpStatus.FORBIDDEN to "forbidden"
      is UnknownSettingTypeException -> HttpStatus.NOT_FOUND to "unknown-setting-type"
      is InvalidHistoryCursorException -> HttpStatus.BAD_REQUEST to "invalid-cursor"
      is ResponseStatusException -> ex.statusCode.let {
        (HttpStatus.resolve(it.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR) to "error"
      }
      else -> HttpStatus.INTERNAL_SERVER_ERROR to "error"
    }
    if (status.is5xxServerError) {
      val request = exchange.request
      logger.error("Admin request {} {} failed with {}", request.method, request.path, status.value(), ex)
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
