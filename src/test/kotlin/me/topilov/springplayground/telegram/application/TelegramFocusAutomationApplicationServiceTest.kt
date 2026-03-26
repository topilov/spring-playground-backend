package me.topilov.springplayground.telegram.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.protection.application.ProtectionContext
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramAutomationToken
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramEmojiMappingResolver
import me.topilov.springplayground.telegram.domain.TelegramFocusMode
import me.topilov.springplayground.telegram.domain.TelegramFocusPriorityResolver
import me.topilov.springplayground.telegram.domain.TelegramFocusState
import me.topilov.springplayground.telegram.domain.exception.TelegramSyncFailedException
import me.topilov.springplayground.telegram.infrastructure.crypto.TelegramSessionSecretCrypto
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramFocusMappingRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramFocusStateRepository
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class TelegramFocusAutomationApplicationServiceTest {
    private val automationTokenService = mock(TelegramAutomationTokenApplicationService::class.java)
    private val accountConnectionRepository = mock(TelegramAccountConnectionRepository::class.java)
    private val focusStateRepository = mock(TelegramFocusStateRepository::class.java)
    private val focusMappingRepository = mock(TelegramFocusMappingRepository::class.java)
    private val telegramClientGateway = mock(TelegramClientGateway::class.java)
    private val sessionSecretCrypto = mock(TelegramSessionSecretCrypto::class.java)
    private val protectionService = mock(ProtectionService::class.java)
    private val servletRequest = mock(HttpServletRequest::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-03-26T12:00:00Z"), ZoneOffset.UTC)

    private val service = TelegramFocusAutomationApplicationService(
        automationTokenService = automationTokenService,
        accountConnectionRepository = accountConnectionRepository,
        focusStateRepository = focusStateRepository,
        focusMappingRepository = focusMappingRepository,
        priorityResolver = TelegramFocusPriorityResolver(),
        mappingResolver = TelegramEmojiMappingResolver(
            defaults = mapOf(TelegramFocusMode.SLEEP to "1005"),
        ),
        telegramClientGateway = telegramClientGateway,
        sessionSecretCrypto = sessionSecretCrypto,
        protectionService = protectionService,
        clock = clock,
    )

    @Test
    fun `successful focus sync marks token used only after telegram update succeeds`() {
        val token = TelegramAutomationToken(userId = 1L, tokenHash = "a".repeat(64), tokenHint = "tgf_123...")
        val connection = TelegramAccountConnection(
            userId = 1L,
            telegramUserId = 900001L,
            telegramPhoneNumber = "+15551234567",
            connectionStatus = TelegramConnectionStatus.CONNECTED,
            premium = true,
            sessionDirectoryKey = "session-key",
            sessionDatabaseKeyCiphertext = "ciphertext",
            defaultEmojiStatusDocumentId = "7000",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")
        val sleepState = TelegramFocusState(
            userId = 1L,
            focusMode = TelegramFocusMode.SLEEP,
            active = true,
            lastActivatedAt = Instant.parse("2026-03-26T12:00:00Z"),
        )

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(focusStateRepository.findByUserIdAndFocusMode(1L, TelegramFocusMode.SLEEP)).thenReturn(null)
        `when`(focusStateRepository.findAllByUserId(1L)).thenReturn(listOf(sleepState))
        `when`(focusMappingRepository.findAllByUserId(1L)).thenReturn(emptyList())
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")

        val result = service.applyFocusUpdate("token", TelegramFocusMode.SLEEP, true, servletRequest)

        assertThat(result.effectiveFocusMode).isEqualTo(TelegramFocusMode.SLEEP)
        assertThat(result.appliedEmojiStatusDocumentId).isEqualTo("1005")
        verify(automationTokenService).markUsed(token)
        assertThat(connection.lastSyncErrorCode).isNull()
    }

    @Test
    fun `failed telegram sync stores stable error metadata and does not mark token used`() {
        val token = TelegramAutomationToken(userId = 1L, tokenHash = "a".repeat(64), tokenHint = "tgf_123...")
        val connection = TelegramAccountConnection(
            userId = 1L,
            telegramUserId = 900001L,
            telegramPhoneNumber = "+15551234567",
            connectionStatus = TelegramConnectionStatus.CONNECTED,
            premium = true,
            sessionDirectoryKey = "session-key",
            sessionDatabaseKeyCiphertext = "ciphertext",
            defaultEmojiStatusDocumentId = "7000",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")
        val sleepState = TelegramFocusState(
            userId = 1L,
            focusMode = TelegramFocusMode.SLEEP,
            active = true,
            lastActivatedAt = Instant.parse("2026-03-26T12:00:00Z"),
        )

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(focusStateRepository.findByUserIdAndFocusMode(1L, TelegramFocusMode.SLEEP)).thenReturn(null)
        `when`(focusStateRepository.findAllByUserId(1L)).thenReturn(listOf(sleepState))
        `when`(focusMappingRepository.findAllByUserId(1L)).thenReturn(emptyList())
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")
        doThrow(IllegalStateException("tdlight offline")).`when`(telegramClientGateway).updateEmojiStatus(
            1L,
            "+15551234567",
            900001L,
            "session-key",
            "db-key",
            "1005",
        )

        assertThatThrownBy {
            service.applyFocusUpdate("token", TelegramFocusMode.SLEEP, true, servletRequest)
        }.isInstanceOf(TelegramSyncFailedException::class.java)

        assertThat(connection.lastSyncErrorCode).isEqualTo("TELEGRAM_SYNC_FAILED")
        assertThat(connection.lastSyncErrorMessage).isEqualTo("tdlight offline")
        verify(automationTokenService, never()).markUsed(token)
    }
}
