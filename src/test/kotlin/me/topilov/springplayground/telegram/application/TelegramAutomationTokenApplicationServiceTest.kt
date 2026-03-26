package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramAutomationToken
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAutomationTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class TelegramAutomationTokenApplicationServiceTest {
    private val accountConnectionRepository = mock(TelegramAccountConnectionRepository::class.java)
    private val automationTokenRepository = mock(TelegramAutomationTokenRepository::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-03-26T10:15:30Z"), ZoneOffset.UTC)

    private val service = TelegramAutomationTokenApplicationService(
        accountConnectionRepository = accountConnectionRepository,
        automationTokenRepository = automationTokenRepository,
        clock = clock,
    )

    @Test
    fun `create token stores only hash and returns raw token once`() {
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(
            Optional.of(
                TelegramAccountConnection(
                    userId = 1L,
                    connectionStatus = TelegramConnectionStatus.CONNECTED,
                    premium = true,
                    sessionDirectoryKey = "session-key",
                    sessionDatabaseKeyCiphertext = "ciphertext",
                ),
            ),
        )
        `when`(automationTokenRepository.findByUserId(1L)).thenReturn(null)

        val created = service.createToken(1L)

        assertThat(created.token).startsWith("tgf_")
        assertThat(created.tokenHint).startsWith("tgf_")
        verify(automationTokenRepository).save(
            org.mockito.ArgumentMatchers.argThat<TelegramAutomationToken> { token ->
                token.userId == 1L &&
                    token.tokenHash != created.token &&
                    token.tokenHash.length == 64 &&
                    token.revokedAt == null
            },
        )
    }

    @Test
    fun `regenerate token revokes existing active token and persists replacement hash`() {
        val existing = TelegramAutomationToken(
            userId = 1L,
            tokenHash = "a".repeat(64),
            tokenHint = "tgf_old...",
            createdAt = Instant.parse("2026-03-26T09:00:00Z"),
        )

        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(
            Optional.of(
                TelegramAccountConnection(
                    userId = 1L,
                    connectionStatus = TelegramConnectionStatus.CONNECTED,
                    premium = true,
                    sessionDirectoryKey = "session-key",
                    sessionDatabaseKeyCiphertext = "ciphertext",
                ),
            ),
        )
        `when`(automationTokenRepository.findByUserId(1L)).thenReturn(existing)
        `when`(automationTokenRepository.findByUserIdAndRevokedAtIsNull(1L)).thenReturn(existing)

        val regenerated = service.regenerateToken(1L)

        assertThat(regenerated.token).startsWith("tgf_")
        assertThat(existing.revokedAt).isNull()
        assertThat(existing.tokenHash).isNotEqualTo("a".repeat(64))
        verify(automationTokenRepository, times(1)).save(existing)
    }
}
