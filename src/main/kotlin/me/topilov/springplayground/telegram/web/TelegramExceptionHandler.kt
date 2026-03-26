package me.topilov.springplayground.telegram.web

import io.swagger.v3.oas.annotations.Hidden
import me.topilov.springplayground.common.web.ErrorResponse
import me.topilov.springplayground.common.web.SimpleErrorResponse
import me.topilov.springplayground.telegram.domain.exception.TelegramAutomationTokenAlreadyExistsException
import me.topilov.springplayground.telegram.domain.exception.TelegramAutomationTokenInvalidException
import me.topilov.springplayground.telegram.domain.exception.TelegramInvalidFocusModeException
import me.topilov.springplayground.telegram.domain.exception.TelegramInvalidAuthStepException
import me.topilov.springplayground.telegram.domain.exception.TelegramNotConnectedException
import me.topilov.springplayground.telegram.domain.exception.TelegramPendingAuthNotFoundException
import me.topilov.springplayground.telegram.domain.exception.TelegramPremiumRequiredException
import me.topilov.springplayground.telegram.domain.exception.TelegramSyncFailedException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Hidden
@RestControllerAdvice
class TelegramExceptionHandler {
    @ExceptionHandler(TelegramPendingAuthNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlePendingAuthNotFound(exception: TelegramPendingAuthNotFoundException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Not found", code = "TELEGRAM_PENDING_AUTH_NOT_FOUND")

    @ExceptionHandler(TelegramInvalidAuthStepException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidAuthStep(exception: TelegramInvalidAuthStepException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request", code = "TELEGRAM_INVALID_AUTH_STEP")

    @ExceptionHandler(TelegramInvalidFocusModeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidFocusMode(exception: TelegramInvalidFocusModeException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request", code = "TELEGRAM_INVALID_FOCUS_MODE")

    @ExceptionHandler(TelegramAutomationTokenInvalidException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleInvalidAutomationToken(exception: TelegramAutomationTokenInvalidException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Unauthorized", code = "TELEGRAM_AUTOMATION_TOKEN_INVALID")

    @ExceptionHandler(TelegramNotConnectedException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotConnected(exception: TelegramNotConnectedException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Not found", code = "TELEGRAM_NOT_CONNECTED")

    @ExceptionHandler(TelegramPremiumRequiredException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handlePremiumRequired(exception: TelegramPremiumRequiredException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Conflict", code = "TELEGRAM_PREMIUM_REQUIRED")

    @ExceptionHandler(TelegramSyncFailedException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleSyncFailed(exception: TelegramSyncFailedException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad gateway", code = "TELEGRAM_SYNC_FAILED")

    @ExceptionHandler(TelegramAutomationTokenAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTokenAlreadyExists(exception: TelegramAutomationTokenAlreadyExistsException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Conflict")
}
