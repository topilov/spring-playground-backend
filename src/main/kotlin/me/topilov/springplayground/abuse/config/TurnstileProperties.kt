package me.topilov.springplayground.abuse.config

import me.topilov.springplayground.abuse.AbuseProtectionFlow
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.turnstile")
data class TurnstileProperties(
    var enabled: Boolean = true,
    var secretKey: String = "",
    var siteverifyUrl: String = "https://challenges.cloudflare.com/turnstile/v0/siteverify",
    var expectedHostname: String? = null,
    var timeout: Duration = Duration.ofSeconds(3),
    var actions: MutableMap<AbuseProtectionFlow, String> = defaultActions(),
) {
    fun actionFor(flow: AbuseProtectionFlow): String? = actions[flow]

    companion object {
        private fun defaultActions(): MutableMap<AbuseProtectionFlow, String> = mutableMapOf(
            AbuseProtectionFlow.REGISTER to "register",
            AbuseProtectionFlow.LOGIN to "login",
            AbuseProtectionFlow.FORGOT_PASSWORD to "forgot-password",
            AbuseProtectionFlow.RESEND_VERIFICATION_EMAIL to "resend-verification-email",
            AbuseProtectionFlow.RESET_PASSWORD to "reset-password",
            AbuseProtectionFlow.TWO_FACTOR_LOGIN to "two-factor-login",
            AbuseProtectionFlow.PASSKEY_LOGIN_OPTIONS to "passkey-login-options",
            AbuseProtectionFlow.PASSKEY_LOGIN_VERIFY to "passkey-login-verify",
            AbuseProtectionFlow.VERIFY_EMAIL_CHANGE to "verify-email-change",
        )
    }
}
