package me.topilov.springplayground.auth.exception

class InvalidPasswordResetTokenException :
    RuntimeException("Password reset token is invalid or expired")
