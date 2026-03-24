package me.topilov.springplayground.auth.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Password-login step-up response returned when a second factor is required.")
data class TwoFactorLoginChallengeResponse(
    val requiresTwoFactor: Boolean = true,
    val loginChallengeId: String,
    val methods: List<String>,
    val expiresAt: Instant,
)
