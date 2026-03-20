package me.topilov.springplayground.auth.exception

class AuthUsernameAlreadyUsedException(username: String) :
    RuntimeException("Username '$username' is already in use")
