package me.topilov.springplayground.profile.exception

class InvalidPendingEmailChangeTokenException : RuntimeException("Email change token is invalid or expired")
