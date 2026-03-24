package me.topilov.springplayground.protection.exception

import me.topilov.springplayground.protection.domain.ProtectionFlow

class CaptchaValidationFailedException(
    val flow: ProtectionFlow,
    val errorCodes: List<String> = emptyList(),
    val remoteIp: String? = null,
    val identifier: String? = null,
    message: String = clientMessage(errorCodes),
) : RuntimeException(message) {
    companion object {
        fun clientMessage(errorCodes: List<String>): String = when {
            errorCodes.any { it == "timeout-or-duplicate" } ->
                "Captcha expired. Please try again."
            errorCodes.any { it == "internal-error" } ->
                "Captcha verification is temporarily unavailable. Please try again."
            else -> "Captcha verification failed. Please try again."
        }
    }
}
