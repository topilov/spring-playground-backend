package me.topilov.springplayground.auth.exception

import org.springframework.security.core.userdetails.UsernameNotFoundException

class AuthUserNotFoundException(usernameOrEmail: String) :
    UsernameNotFoundException("User '$usernameOrEmail' was not found")
