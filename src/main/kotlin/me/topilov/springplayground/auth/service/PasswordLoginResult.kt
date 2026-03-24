package me.topilov.springplayground.auth.service

import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.TwoFactorLoginChallengeResponse

sealed interface PasswordLoginResult

data class SessionEstablishedLoginResult(val response: LoginResponse) : PasswordLoginResult

data class TwoFactorRequiredLoginResult(val response: TwoFactorLoginChallengeResponse) : PasswordLoginResult
