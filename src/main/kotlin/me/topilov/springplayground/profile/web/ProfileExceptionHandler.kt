package me.topilov.springplayground.profile.web

import io.swagger.v3.oas.annotations.Hidden
import me.topilov.springplayground.auth.web.ErrorResponse
import me.topilov.springplayground.profile.exception.InvalidCurrentPasswordException
import me.topilov.springplayground.profile.exception.InvalidPendingEmailChangeTokenException
import me.topilov.springplayground.profile.exception.NewEmailMatchesCurrentEmailException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@Hidden
@RestControllerAdvice
class ProfileExceptionHandler {
    @ExceptionHandler(
        InvalidCurrentPasswordException::class,
        InvalidPendingEmailChangeTokenException::class,
        NewEmailMatchesCurrentEmailException::class,
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(exception: RuntimeException): ErrorResponse =
        ErrorResponse(error = exception.message ?: "Bad request")
}
