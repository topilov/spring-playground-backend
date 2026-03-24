package me.topilov.springplayground.protection

import me.topilov.springplayground.protection.application.ProtectionContext
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.captcha.CaptchaVerificationResult
import me.topilov.springplayground.protection.captcha.CaptchaVerificationService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.protection.exception.CaptchaValidationFailedException
import me.topilov.springplayground.protection.infrastructure.config.ProtectionProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtectionServiceTest {
    @Test
    fun `protect includes captcha failure details in exception`() {
        val service = ProtectionService(
            protectionProperties = ProtectionProperties(),
            captchaVerificationService = FixedCaptchaVerificationService(
                CaptchaVerificationResult(
                    success = false,
                    errorCodes = listOf("invalid-input-response", "timeout-or-duplicate"),
                ),
            ),
            rateLimitStore = InMemoryRateLimitStore(),
            cooldownStore = InMemoryCooldownStore(),
        )

        val exception = assertThrows<CaptchaValidationFailedException> {
            service.protect(
                ProtectionFlow.LOGIN,
                ProtectionContext(
                    captchaToken = "bad-token",
                    remoteIp = "203.0.113.10",
                    identifier = "demo",
                ),
            )
        }

        assertThat(exception.flow).isEqualTo(ProtectionFlow.LOGIN)
        assertThat(exception.errorCodes).containsExactly("invalid-input-response", "timeout-or-duplicate")
        assertThat(exception.remoteIp).isEqualTo("203.0.113.10")
        assertThat(exception.identifier).isEqualTo("demo")
    }

    private class FixedCaptchaVerificationService(
        private val result: CaptchaVerificationResult,
    ) : CaptchaVerificationService {
        override fun verify(
            flow: ProtectionFlow,
            token: String,
            remoteIp: String?,
        ): CaptchaVerificationResult = result
    }
}
