package me.topilov.springplayground.auth.web

import io.swagger.v3.oas.annotations.Hidden
import me.topilov.springplayground.abuse.exception.CaptchaValidationFailedException
import me.topilov.springplayground.abuse.exception.RateLimitExceededException
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.exception.EmailNotVerifiedException
import me.topilov.springplayground.auth.exception.InvalidEmailVerificationTokenException
import me.topilov.springplayground.auth.exception.InvalidPasswordResetTokenException
import me.topilov.springplayground.auth.exception.InvalidTwoFactorCodeException
import me.topilov.springplayground.auth.exception.TwoFactorAlreadyEnabledException
import me.topilov.springplayground.auth.exception.TwoFactorAuthenticationFailedException
import me.topilov.springplayground.auth.exception.TwoFactorLoginChallengeNotFoundException
import me.topilov.springplayground.auth.exception.TwoFactorNotEnabledException
import me.topilov.springplayground.auth.exception.TwoFactorSetupNotStartedException
import me.topilov.springplayground.auth.passkey.exception.DuplicatePasskeyCredentialException
import me.topilov.springplayground.auth.passkey.exception.InvalidPasskeyCeremonyException
import me.topilov.springplayground.auth.passkey.exception.PasskeyAuthenticationFailedException
import me.topilov.springplayground.auth.passkey.exception.PasskeyNotFoundException
import me.topilov.springplayground.shared.dto.SimpleErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Hidden
@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(CaptchaValidationFailedException::class)
    fun handleCaptchaValidationFailed(exception: CaptchaValidationFailedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            ErrorResponse(
                error = exception.message ?: "Bad request",
                code = "CAPTCHA_VALIDATION_FAILED",
            ),
        )

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

    @ExceptionHandler(AuthUsernameAlreadyUsedException::class, AuthEmailAlreadyUsedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(exception: RuntimeException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Conflict")

    @ExceptionHandler(InvalidPasswordResetTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: InvalidPasswordResetTokenException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")

    @ExceptionHandler(InvalidEmailVerificationTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidEmailVerificationToken(exception: InvalidEmailVerificationTokenException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")

    @ExceptionHandler(InvalidPasskeyCeremonyException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidPasskeyCeremony(exception: InvalidPasskeyCeremonyException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")

    @ExceptionHandler(
        InvalidTwoFactorCodeException::class,
        TwoFactorSetupNotStartedException::class,
        TwoFactorNotEnabledException::class,
        TwoFactorLoginChallengeNotFoundException::class,
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleTwoFactorBadRequest(exception: RuntimeException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")

    @ExceptionHandler(DuplicatePasskeyCredentialException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleDuplicatePasskeyCredential(exception: DuplicatePasskeyCredentialException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Conflict")

    @ExceptionHandler(TwoFactorAlreadyEnabledException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTwoFactorConflict(exception: TwoFactorAlreadyEnabledException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Conflict")

    @ExceptionHandler(PasskeyNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handlePasskeyNotFound(exception: PasskeyNotFoundException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Not found")

    @ExceptionHandler(PasskeyAuthenticationFailedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handlePasskeyAuthenticationFailed(exception: PasskeyAuthenticationFailedException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Unauthorized")

    @ExceptionHandler(TwoFactorAuthenticationFailedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleTwoFactorAuthenticationFailed(exception: TwoFactorAuthenticationFailedException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Unauthorized")

    @ExceptionHandler(EmailNotVerifiedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleEmailNotVerified(exception: EmailNotVerifiedException): ErrorResponse =
        ErrorResponse(
            error = exception.message ?: "Unauthorized",
            code = "EMAIL_NOT_VERIFIED",
        )
}

data class ErrorResponse(
    val error: String,
    val code: String? = null,
    val retryAfterSeconds: Long? = null,
)
