package me.topilov.springplayground.auth.web

import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.exception.InvalidPasswordResetTokenException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(AuthUsernameAlreadyUsedException::class, AuthEmailAlreadyUsedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(exception: RuntimeException): ErrorResponse = ErrorResponse(error = exception.message ?: "Conflict")

    @ExceptionHandler(InvalidPasswordResetTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: InvalidPasswordResetTokenException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")
}

data class ErrorResponse(
    val error: String,
)
