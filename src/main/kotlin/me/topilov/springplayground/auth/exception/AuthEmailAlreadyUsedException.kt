package me.topilov.springplayground.auth.exception

class AuthEmailAlreadyUsedException(email: String) :
    RuntimeException("Email '$email' is already in use")
