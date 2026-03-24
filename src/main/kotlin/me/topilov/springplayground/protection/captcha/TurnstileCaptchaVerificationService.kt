package me.topilov.springplayground.protection.captcha

import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.protection.infrastructure.config.TurnstileProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TurnstileCaptchaVerificationService(
    private val turnstileProperties: TurnstileProperties,
    private val siteverifyClient: TurnstileSiteverifyClient,
    private val responseValidator: TurnstileResponseValidator,
) : CaptchaVerificationService {
    override fun verify(flow: ProtectionFlow, token: String, remoteIp: String?): CaptchaVerificationResult {
        if (!turnstileProperties.enabled) {
            return CaptchaVerificationResult(success = true)
        }

        val normalizedToken = token.trim()
        if (normalizedToken.isBlank() || normalizedToken.length > MAX_TOKEN_LENGTH) {
            return CaptchaVerificationResult(success = false, errorCodes = listOf("invalid-input-response"))
        }

        return try {
            val exchange = siteverifyClient.verify(normalizedToken, remoteIp)
            val decision = responseValidator.validate(flow, exchange.payload)
            if (!decision.accepted) {
                log.warn(
                    "Turnstile rejected captcha flow={} reason={} statusCode={} errorCodes={} expectedAction={} actualAction={} expectedHostname={} actualHostname={}",
                    flow,
                    decision.rejectionReason,
                    exchange.statusCode,
                    decision.errorCodes,
                    decision.expectedAction,
                    decision.actualAction,
                    decision.expectedHostname,
                    decision.actualHostname,
                )
            }
            decision.toResult()
        } catch (exception: Exception) {
            log.warn(
                "Turnstile verification failed flow={} reason=siteverify-request-failed exceptionClass={}",
                flow,
                exception.javaClass.name,
            )
            CaptchaVerificationResult(success = false, errorCodes = listOf("internal-error"))
        }
    }

    companion object {
        private const val MAX_TOKEN_LENGTH = 2048
        private val log = LoggerFactory.getLogger(TurnstileCaptchaVerificationService::class.java)
    }
}
