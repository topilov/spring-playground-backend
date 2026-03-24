package me.topilov.springplayground.protection.captcha

data class CaptchaVerificationResult(
    val success: Boolean,
    val errorCodes: List<String> = emptyList(),
)
