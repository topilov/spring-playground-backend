package me.topilov.springplayground.protection.captcha

import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.protection.infrastructure.config.TurnstileProperties
import org.springframework.stereotype.Component

@Component
class TurnstileResponseValidator(
    private val turnstileProperties: TurnstileProperties,
) {
    fun validate(flow: ProtectionFlow, response: TurnstileSiteverifyResponse): TurnstileValidationDecision {
        val expectedAction = turnstileProperties.actionFor(flow)
        val expectedHostname = turnstileProperties.expectedHostname?.trim().takeUnless { it.isNullOrEmpty() }
        val actualAction = response.action?.trim().takeUnless { it.isNullOrEmpty() }
        val actualHostname = response.hostname?.trim().takeUnless { it.isNullOrEmpty() }

        if (!response.success) {
            return TurnstileValidationDecision(
                accepted = false,
                errorCodes = response.errorCodes,
                rejectionReason = "siteverify-rejected",
                actualAction = actualAction,
                actualHostname = actualHostname,
            )
        }

        if (expectedAction != null && actualAction != null && actualAction != expectedAction) {
            return TurnstileValidationDecision(
                accepted = false,
                errorCodes = listOf("invalid-action"),
                rejectionReason = "invalid-action",
                expectedAction = expectedAction,
                actualAction = actualAction,
                actualHostname = actualHostname,
            )
        }

        if (expectedHostname != null && actualHostname != null && actualHostname != expectedHostname) {
            return TurnstileValidationDecision(
                accepted = false,
                errorCodes = listOf("invalid-hostname"),
                rejectionReason = "invalid-hostname",
                expectedHostname = expectedHostname,
                actualHostname = actualHostname,
                actualAction = actualAction,
            )
        }

        return TurnstileValidationDecision(accepted = true)
    }
}
