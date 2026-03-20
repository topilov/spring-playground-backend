package me.topilov.springplayground.auth.web

import io.swagger.v3.oas.annotations.Hidden
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.exception.InvalidPasswordResetTokenException
import me.topilov.springplayground.shared.dto.SimpleErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Hidden
@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(AuthUsernameAlreadyUsedException::class, AuthEmailAlreadyUsedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(exception: RuntimeException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Conflict")

    @ExceptionHandler(InvalidPasswordResetTokenException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: InvalidPasswordResetTokenException): SimpleErrorResponse =
        SimpleErrorResponse(error = exception.message ?: "Bad request")
}
