package me.topilov.springplayground.auth.exception

class InvalidEmailVerificationTokenException : RuntimeException("Email verification token is invalid or expired")
