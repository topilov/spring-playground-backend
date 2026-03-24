package me.topilov.springplayground.protection.captcha

import me.topilov.springplayground.protection.domain.ProtectionFlow

interface CaptchaVerificationService {
    fun verify(flow: ProtectionFlow, token: String, remoteIp: String?): CaptchaVerificationResult
}
