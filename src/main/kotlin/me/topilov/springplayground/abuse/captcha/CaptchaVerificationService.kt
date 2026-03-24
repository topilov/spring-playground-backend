package me.topilov.springplayground.abuse.captcha

import me.topilov.springplayground.abuse.AbuseProtectionFlow

interface CaptchaVerificationService {
    fun verify(flow: AbuseProtectionFlow, token: String, remoteIp: String?): CaptchaVerificationResult
}

data class CaptchaVerificationResult(
    val success: Boolean,
    val errorCodes: List<String> = emptyList(),
)
