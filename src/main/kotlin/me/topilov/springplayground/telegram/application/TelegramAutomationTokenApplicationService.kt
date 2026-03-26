package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAutomationToken
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.exception.TelegramAutomationTokenAlreadyExistsException
import me.topilov.springplayground.telegram.domain.exception.TelegramAutomationTokenInvalidException
import me.topilov.springplayground.telegram.domain.exception.TelegramNotConnectedException
import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAutomationTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.*

data class TelegramRawAutomationToken(
    val token: String,
    val tokenHint: String,
)

@Service
class TelegramAutomationTokenApplicationService(
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val automationTokenRepository: TelegramAutomationTokenRepository,
    private val telegramProperties: TelegramProperties,
    private val clock: Clock,
) {

    @Transactional
    fun createToken(userId: Long): TelegramRawAutomationToken {
        requireConnected(userId)
        if (automationTokenRepository.findByUserIdAndRevokedAtIsNull(userId) != null) {
            throw TelegramAutomationTokenAlreadyExistsException()
        }

        val generated = generateToken()
        val token = automationTokenRepository.findByUserId(userId)
            ?: TelegramAutomationToken(
                userId = userId,
                tokenHash = "",
                tokenHint = "",
            )
        token.tokenHash = hashToken(generated.token)
        token.tokenHint = generated.tokenHint
        token.createdAt = Instant.now(clock)
        token.lastUsedAt = null
        token.revokedAt = null
        automationTokenRepository.save(token)
        return generated
    }

    @Transactional
    fun regenerateToken(userId: Long): TelegramRawAutomationToken {
        requireConnected(userId)
        val now = Instant.now(clock)
        val generated = generateToken()
        val token = automationTokenRepository.findByUserId(userId)
            ?: TelegramAutomationToken(
                userId = userId,
                tokenHash = "",
                tokenHint = "",
            )
        token.tokenHash = hashToken(generated.token)
        token.tokenHint = generated.tokenHint
        token.createdAt = now
        token.lastUsedAt = null
        token.revokedAt = null
        automationTokenRepository.save(token)
        return generated
    }

    @Transactional
    fun revokeToken(userId: Long) {
        automationTokenRepository.findByUserIdAndRevokedAtIsNull(userId)?.let {
            it.revokedAt = Instant.now(clock)
            automationTokenRepository.save(it)
        }
    }

    fun findActiveToken(rawToken: String): TelegramAutomationToken {
        val normalized = rawToken.trim()
        return automationTokenRepository.findByTokenHashAndRevokedAtIsNull(hashToken(normalized))
            ?: throw TelegramAutomationTokenInvalidException()
    }

    @Transactional
    fun markUsed(token: TelegramAutomationToken): TelegramAutomationToken {
        token.lastUsedAt = Instant.now(clock)
        return automationTokenRepository.save(token)
    }

    private fun requireConnected(userId: Long) {
        val connection = accountConnectionRepository.findByUserId(userId).orElseThrow(::TelegramNotConnectedException)
        if (connection.connectionStatus != TelegramConnectionStatus.CONNECTED) {
            throw TelegramNotConnectedException()
        }
    }

    private fun generateToken(): TelegramRawAutomationToken {
        val bytes = ByteArray(telegramProperties.automationTokenBytes)
        secureRandom.nextBytes(bytes)
        val token = "tgf_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
        return TelegramRawAutomationToken(
            token = token,
            tokenHint = token.take(8) + "...",
        )
    }

    private fun hashToken(rawToken: String): String = MessageDigest.getInstance("SHA-256")
        .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
