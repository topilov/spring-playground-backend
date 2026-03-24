package me.topilov.springplayground.protection.captcha

data class TurnstileValidationDecision(
    val accepted: Boolean,
    val errorCodes: List<String> = emptyList(),
    val rejectionReason: String? = null,
    val expectedAction: String? = null,
    val actualAction: String? = null,
    val expectedHostname: String? = null,
    val actualHostname: String? = null,
) {
    fun toResult(): CaptchaVerificationResult =
        if (accepted) {
            CaptchaVerificationResult(success = true)
        } else {
            CaptchaVerificationResult(success = false, errorCodes = errorCodes)
        }
}
