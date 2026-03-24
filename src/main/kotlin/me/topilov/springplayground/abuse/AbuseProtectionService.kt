package me.topilov.springplayground.abuse

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.abuse.captcha.CaptchaVerificationService
import me.topilov.springplayground.abuse.config.AbuseProtectionProperties
import me.topilov.springplayground.abuse.exception.CaptchaValidationFailedException
import me.topilov.springplayground.abuse.exception.RateLimitExceededException
import me.topilov.springplayground.abuse.store.CooldownStore
import me.topilov.springplayground.abuse.store.RateLimitStore
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration

@Service
class AbuseProtectionService(
    private val abuseProtectionProperties: AbuseProtectionProperties,
    private val captchaVerificationService: CaptchaVerificationService,
    private val rateLimitStore: RateLimitStore,
    private val cooldownStore: CooldownStore,
) {
    fun protect(
        flow: AbuseProtectionFlow,
        context: AbuseProtectionContext,
    ) {
        val policy = abuseProtectionProperties.policy(flow)
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

    fun enforceCooldown(
        flow: AbuseProtectionFlow,
        identifier: String,
    ) {
        val cooldown = abuseProtectionProperties.policy(flow).cooldown ?: return
        val remaining = cooldownStore.activateIfAbsent(cooldownKey(flow, identifier), cooldown)
        if (remaining != null) {
            throw buildRateLimitException(flow, remaining)
        }
    }

    fun checkFailureThrottle(
        flow: AbuseProtectionFlow,
        request: HttpServletRequest,
        identifier: String,
    ) {
        val policy = abuseProtectionProperties.policy(flow)
        val failureLimit = policy.failureLimit ?: return
        val current = rateLimitStore.get(failureKey(flow, extractClientIp(request), identifier)) ?: return
        if (current.count >= failureLimit) {
            throw buildRateLimitException(flow, current.expiresIn)
        }
    }

    fun recordFailure(
        flow: AbuseProtectionFlow,
        request: HttpServletRequest,
        identifier: String,
    ) {
        val policy = abuseProtectionProperties.policy(flow)
        val failureWindow = policy.failureWindow ?: return
        rateLimitStore.increment(failureKey(flow, extractClientIp(request), identifier), failureWindow)
    }

    fun clearFailures(
        flow: AbuseProtectionFlow,
        request: HttpServletRequest,
        identifier: String,
    ) {
        rateLimitStore.reset(failureKey(flow, extractClientIp(request), identifier))
    }

    fun buildContext(
        captchaToken: String?,
        request: HttpServletRequest,
        identifier: String? = null,
    ): AbuseProtectionContext = AbuseProtectionContext(
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
        flow: AbuseProtectionFlow,
        context: AbuseProtectionContext,
        limit: Int,
        window: Duration,
    ) {
        val key = requestKey(flow, context.remoteIp, context.identifier)
        val state = rateLimitStore.increment(key, window)
        if (state.count > limit) {
            throw buildRateLimitException(flow, state.expiresIn)
        }
    }

    private fun requestKey(flow: AbuseProtectionFlow, remoteIp: String?, identifier: String?): String =
        "abuse:request:${flow.name.lowercase()}:${hash(remoteIp ?: "unknown")}:${hash(identifier ?: "global")}"

    private fun failureKey(flow: AbuseProtectionFlow, remoteIp: String?, identifier: String): String =
        "abuse:failure:${flow.name.lowercase()}:${hash(remoteIp ?: "unknown")}:${hash(identifier)}"

    private fun cooldownKey(flow: AbuseProtectionFlow, identifier: String): String =
        "abuse:cooldown:${flow.name.lowercase()}:${hash(identifier)}"

    private fun buildRateLimitException(flow: AbuseProtectionFlow, remaining: Duration): RateLimitExceededException {
        val retryAfterSeconds = remaining.seconds.coerceAtLeast(1)
        return when (flow) {
            AbuseProtectionFlow.LOGIN -> RateLimitExceededException(
                code = "LOGIN_THROTTLED",
                retryAfterSeconds = retryAfterSeconds,
                message = "Too many login attempts. Please try again later.",
            )
            AbuseProtectionFlow.TWO_FACTOR_LOGIN -> RateLimitExceededException(
                code = "TWO_FACTOR_THROTTLED",
                retryAfterSeconds = retryAfterSeconds,
                message = "Too many two-factor attempts. Please try again later.",
            )
            AbuseProtectionFlow.FORGOT_PASSWORD,
            AbuseProtectionFlow.RESEND_VERIFICATION_EMAIL,
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
