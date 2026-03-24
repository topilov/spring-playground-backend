package me.topilov.springplayground.protection.application

data class ProtectionContext(
    val captchaToken: String,
    val remoteIp: String?,
    val identifier: String? = null,
)
