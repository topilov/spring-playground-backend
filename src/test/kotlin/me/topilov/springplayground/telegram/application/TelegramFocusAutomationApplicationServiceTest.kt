package me.topilov.springplayground.telegram.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.protection.application.ProtectionContext
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramAutomationToken
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.TelegramMode
import me.topilov.springplayground.telegram.domain.exception.TelegramSyncFailedException
import me.topilov.springplayground.telegram.infrastructure.crypto.TelegramSessionSecretCrypto
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramModeRepository
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
    private val modeRepository = mock(TelegramModeRepository::class.java)
    private val telegramClientGateway = mock(TelegramClientGateway::class.java)
    private val sessionSecretCrypto = mock(TelegramSessionSecretCrypto::class.java)
    private val protectionService = mock(ProtectionService::class.java)
    private val servletRequest = mock(HttpServletRequest::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-03-26T12:00:00Z"), ZoneOffset.UTC)

    private val service = TelegramFocusAutomationApplicationService(
        automationTokenService = automationTokenService,
        accountConnectionRepository = accountConnectionRepository,
        modeRepository = modeRepository,
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
        val mode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(mode)
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")

        val result = service.applyFocusUpdate("token", "work", true, servletRequest)

        assertThat(result.activeFocusMode).isEqualTo("work")
        assertThat(result.appliedEmojiStatusDocumentId).isEqualTo("1001")
        assertThat(connection.activeModeId).isEqualTo(10L)
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
        val mode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(mode)
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")
        doThrow(IllegalStateException("tdlight offline")).`when`(telegramClientGateway).updateEmojiStatus(
            1L,
            "+15551234567",
            900001L,
            "session-key",
            "db-key",
            "1001",
        )

        assertThatThrownBy {
            service.applyFocusUpdate("token", "work", true, servletRequest)
        }.isInstanceOf(TelegramSyncFailedException::class.java)

        assertThat(connection.lastSyncErrorCode).isEqualTo("TELEGRAM_SYNC_FAILED")
        assertThat(connection.lastSyncErrorMessage).isEqualTo("tdlight offline")
        verify(automationTokenService, never()).markUsed(token)
    }

    @Test
    fun `deactivating the active mode restores the default emoji status`() {
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
            activeModeId = 10L,
        )
        val mode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(mode)
        `when`(modeRepository.findById(10L)).thenReturn(Optional.of(mode))
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")

        val result = service.applyFocusUpdate("token", "work", false, servletRequest)

        assertThat(result.activeFocusMode).isNull()
        assertThat(result.appliedEmojiStatusDocumentId).isEqualTo("7000")
        assertThat(connection.activeModeId).isNull()
    }

    @Test
    fun `activating a user-defined mode returns active focus mode string`() {
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
        val mode = TelegramMode(
            id = 10L,
            userId = 1L,
            modeKey = "work",
            emojiStatusDocumentId = "1001",
        )
        val protectionContext = ProtectionContext(captchaToken = "", remoteIp = "127.0.0.1", identifier = "1")

        `when`(automationTokenService.findActiveToken("token")).thenReturn(token)
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(connection))
        `when`(modeRepository.findByUserIdAndModeKey(1L, "work")).thenReturn(mode)
        `when`(protectionService.buildContext("", servletRequest, "1")).thenReturn(protectionContext)
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")

        val result = service.applyFocusUpdate("token", "work", true, servletRequest)

        assertThat(result.activeFocusMode).isEqualTo("work")
        assertThat(result.appliedEmojiStatusDocumentId).isEqualTo("1001")
    }
}
