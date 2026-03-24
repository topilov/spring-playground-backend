package me.topilov.springplayground.auth.service

import me.topilov.springplayground.auth.dto.LoginResponse

data class SessionEstablishedLoginResult(val response: LoginResponse) : PasswordLoginResult
