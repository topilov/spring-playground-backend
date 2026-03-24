package me.topilov.springplayground.protection.infrastructure.config

import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.turnstile")
data class TurnstileProperties(
    var enabled: Boolean = true,
    var secretKey: String = "",
    var siteverifyUrl: String = "https://challenges.cloudflare.com/turnstile/v0/siteverify",
    var expectedHostname: String? = null,
    var timeout: Duration = Duration.ofSeconds(3),
    var actions: MutableMap<ProtectionFlow, String> = defaultActions(),
) {
    fun actionFor(flow: ProtectionFlow): String? = actions[flow]

    companion object {
        private fun defaultActions(): MutableMap<ProtectionFlow, String> = mutableMapOf(
            ProtectionFlow.REGISTER to "register",
            ProtectionFlow.LOGIN to "login",
            ProtectionFlow.FORGOT_PASSWORD to "forgot-password",
            ProtectionFlow.RESEND_VERIFICATION_EMAIL to "resend-verification-email",
            ProtectionFlow.RESET_PASSWORD to "reset-password",
            ProtectionFlow.TWO_FACTOR_LOGIN to "two-factor-login",
            ProtectionFlow.PASSKEY_LOGIN_OPTIONS to "passkey-login-options",
            ProtectionFlow.PASSKEY_LOGIN_VERIFY to "passkey-login-verify",
            ProtectionFlow.VERIFY_EMAIL_CHANGE to "verify-email-change",
        )
    }
}
