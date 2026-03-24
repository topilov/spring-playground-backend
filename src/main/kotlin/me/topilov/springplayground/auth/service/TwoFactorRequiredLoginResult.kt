package me.topilov.springplayground.auth.service

import me.topilov.springplayground.auth.dto.TwoFactorLoginChallengeResponse

data class TwoFactorRequiredLoginResult(val response: TwoFactorLoginChallengeResponse) : PasswordLoginResult
