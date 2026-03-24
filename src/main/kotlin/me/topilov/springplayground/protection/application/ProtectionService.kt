package me.topilov.springplayground.protection.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.protection.captcha.CaptchaVerificationService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.protection.exception.CaptchaValidationFailedException
import me.topilov.springplayground.protection.exception.RateLimitExceededException
import me.topilov.springplayground.protection.infrastructure.config.ProtectionProperties
import me.topilov.springplayground.protection.infrastructure.store.CooldownStore
import me.topilov.springplayground.protection.infrastructure.store.RateLimitStore
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Service
class ProtectionService(
    private val protectionProperties: ProtectionProperties,
    private val captchaVerificationService: CaptchaVerificationService,
    private val rateLimitStore: RateLimitStore,
    private val cooldownStore: CooldownStore,
) {
    fun protect(flow: ProtectionFlow, context: ProtectionContext) {
        val policy = protectionProperties.policy(flow)
        enforceRequestLimit(flow, context, policy.requestLimit, policy.requestWindow)

        if (policy.captchaRequired) {
            val captchaResult = captchaVerificationService.verify(flow, context.captchaToken, context.remoteIp)
            if (!captchaResult.success) {
                throw CaptchaValidationFailedException(
                    flow = flow,
                    errorCodes = captchaResult.errorCodes,
                    remoteIp = context.remoteIp,
                    identifier = context.identifier,
                )
            }
        }
    }

    fun enforceCooldown(flow: ProtectionFlow, identifier: String) {
        val cooldown = protectionProperties.policy(flow).cooldown ?: return
        val remaining = cooldownStore.activateIfAbsent(cooldownKey(flow, identifier), cooldown)
        if (remaining != null) {
            throw buildRateLimitException(flow, remaining)
        }
    }

    fun checkFailureThrottle(flow: ProtectionFlow, request: HttpServletRequest, identifier: String) {
        val policy = protectionProperties.policy(flow)
        val failureLimit = policy.failureLimit ?: return
        val current = rateLimitStore.get(failureKey(flow, extractClientIp(request), identifier)) ?: return
        if (current.count >= failureLimit) {
            throw buildRateLimitException(flow, current.expiresIn)
        }
    }

    fun recordFailure(flow: ProtectionFlow, request: HttpServletRequest, identifier: String) {
        val policy = protectionProperties.policy(flow)
        val failureWindow = policy.failureWindow ?: return
        rateLimitStore.increment(failureKey(flow, extractClientIp(request), identifier), failureWindow)
    }

    fun clearFailures(flow: ProtectionFlow, request: HttpServletRequest, identifier: String) {
        rateLimitStore.reset(failureKey(flow, extractClientIp(request), identifier))
    }

    fun buildContext(
        captchaToken: String?,
        request: HttpServletRequest,
        identifier: String? = null,
    ): ProtectionContext = ProtectionContext(
        captchaToken = captchaToken.orEmpty(),
        remoteIp = extractClientIp(request),
        identifier = identifier,
    )

    fun extractClientIp(request: HttpServletRequest): String? {
        val cfConnectingIp = request.getHeader("CF-Connecting-IP")?.trim()
        if (!cfConnectingIp.isNullOrBlank()) {
            return cfConnectingIp
        }

        val forwarded = request.getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
        if (!forwarded.isNullOrBlank()) {
            return forwarded
        }

        return request.remoteAddr?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun enforceRequestLimit(
        flow: ProtectionFlow,
        context: ProtectionContext,
        limit: Int,
        window: Duration,
    ) {
        val key = requestKey(flow, context.remoteIp, context.identifier)
        val state = rateLimitStore.increment(key, window)
        if (state.count > limit) {
            throw buildRateLimitException(flow, state.expiresIn)
        }
    }

    private fun requestKey(flow: ProtectionFlow, remoteIp: String?, identifier: String?): String =
        "protection:request:${flow.name.lowercase()}:${hash(remoteIp ?: "unknown")}:${hash(identifier ?: "global")}"

    private fun failureKey(flow: ProtectionFlow, remoteIp: String?, identifier: String): String =
        "protection:failure:${flow.name.lowercase()}:${hash(remoteIp ?: "unknown")}:${hash(identifier)}"

    private fun cooldownKey(flow: ProtectionFlow, identifier: String): String =
        "protection:cooldown:${flow.name.lowercase()}:${hash(identifier)}"

    private fun buildRateLimitException(flow: ProtectionFlow, remaining: Duration): RateLimitExceededException {
        val retryAfterSeconds = remaining.seconds.coerceAtLeast(1)
        return when (flow) {
            ProtectionFlow.LOGIN -> RateLimitExceededException(
                code = "LOGIN_THROTTLED",
                retryAfterSeconds = retryAfterSeconds,
                message = "Too many login attempts. Please try again later.",
            )
            ProtectionFlow.TWO_FACTOR_LOGIN -> RateLimitExceededException(
                code = "TWO_FACTOR_THROTTLED",
                retryAfterSeconds = retryAfterSeconds,
                message = "Too many two-factor attempts. Please try again later.",
            )
            ProtectionFlow.FORGOT_PASSWORD,
            ProtectionFlow.RESEND_VERIFICATION_EMAIL,
            -> RateLimitExceededException(
                code = "COOLDOWN_ACTIVE",
                retryAfterSeconds = retryAfterSeconds,
                message = "Please wait before requesting another security email.",
            )
            else -> RateLimitExceededException(
                code = "RATE_LIMITED",
                retryAfterSeconds = retryAfterSeconds,
                message = "Too many requests. Please try again later.",
            )
        }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
}
