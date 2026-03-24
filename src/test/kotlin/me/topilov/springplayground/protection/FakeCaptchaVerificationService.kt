package me.topilov.springplayground.protection

import me.topilov.springplayground.protection.captcha.CaptchaVerificationResult
import me.topilov.springplayground.protection.captcha.CaptchaVerificationService
import me.topilov.springplayground.protection.domain.ProtectionFlow

class FakeCaptchaVerificationService : CaptchaVerificationService {
    override fun verify(
        flow: ProtectionFlow,
        token: String,
        remoteIp: String?,
    ): CaptchaVerificationResult = when (token.trim()) {
        "",
        "invalid-captcha-token",
        -> CaptchaVerificationResult(success = false, errorCodes = listOf("invalid-input-response"))

        "duplicate-captcha-token" -> CaptchaVerificationResult(
            success = false,
            errorCodes = listOf("timeout-or-duplicate"),
        )

        "internal-error-captcha-token" -> CaptchaVerificationResult(
            success = false,
            errorCodes = listOf("internal-error"),
        )

        else -> CaptchaVerificationResult(success = true)
    }
}
