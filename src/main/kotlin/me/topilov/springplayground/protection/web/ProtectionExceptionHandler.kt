package me.topilov.springplayground.protection.web

import io.swagger.v3.oas.annotations.Hidden
import me.topilov.springplayground.common.web.ErrorResponse
import me.topilov.springplayground.protection.exception.CaptchaValidationFailedException
import me.topilov.springplayground.protection.exception.RateLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Hidden
@RestControllerAdvice
class ProtectionExceptionHandler {
    @ExceptionHandler(CaptchaValidationFailedException::class)
    fun handleCaptchaValidationFailed(exception: CaptchaValidationFailedException): ResponseEntity<ErrorResponse> {
        log.warn(
            "Captcha validation failed flow={} errorCodes={} identifier={}",
            exception.flow,
            exception.errorCodes,
            exception.identifier ?: "none",
        )

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                error = exception.message ?: "Bad request",
                code = "CAPTCHA_VALIDATION_FAILED",
            ),
        )
    }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(exception: RateLimitExceededException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", exception.retryAfterSeconds.toString())
            .body(
                ErrorResponse(
                    error = exception.message ?: "Too many requests",
                    code = exception.code,
                    retryAfterSeconds = exception.retryAfterSeconds,
                ),
            )

    companion object {
        private val log = LoggerFactory.getLogger(ProtectionExceptionHandler::class.java)
    }
}
