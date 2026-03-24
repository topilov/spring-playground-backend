package me.topilov.springplayground.abuse

import me.topilov.springplayground.abuse.captcha.CaptchaVerificationResult
import me.topilov.springplayground.abuse.captcha.CaptchaVerificationService
import me.topilov.springplayground.abuse.config.AbuseProtectionProperties
import me.topilov.springplayground.abuse.exception.CaptchaValidationFailedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AbuseProtectionServiceTest {
    @Test
    fun `protect includes captcha failure details in exception`() {
        val service = AbuseProtectionService(
            abuseProtectionProperties = AbuseProtectionProperties(),
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
                AbuseProtectionFlow.LOGIN,
                AbuseProtectionContext(
                    captchaToken = "bad-token",
                    remoteIp = "203.0.113.10",
                    identifier = "demo",
                ),
            )
        }

        assertThat(exception.flow).isEqualTo(AbuseProtectionFlow.LOGIN)
        assertThat(exception.errorCodes).containsExactly("invalid-input-response", "timeout-or-duplicate")
        assertThat(exception.remoteIp).isEqualTo("203.0.113.10")
        assertThat(exception.identifier).isEqualTo("demo")
    }

    private class FixedCaptchaVerificationService(
        private val result: CaptchaVerificationResult,
    ) : CaptchaVerificationService {
        override fun verify(
            flow: AbuseProtectionFlow,
            token: String,
            remoteIp: String?,
        ): CaptchaVerificationResult = result
    }
}
