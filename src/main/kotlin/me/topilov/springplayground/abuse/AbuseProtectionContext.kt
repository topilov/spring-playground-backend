package me.topilov.springplayground.abuse

data class AbuseProtectionContext(
    val captchaToken: String,
    val remoteIp: String?,
    val identifier: String? = null,
)
