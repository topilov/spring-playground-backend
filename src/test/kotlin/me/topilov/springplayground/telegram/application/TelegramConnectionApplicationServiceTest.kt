package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.infrastructure.crypto.TelegramSessionSecretCrypto
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramCodeSubmissionResult
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramConnectedAccount
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class TelegramConnectionApplicationServiceTest {
    private val pendingAuthStore = mock(TelegramPendingAuthStore::class.java)
    private val accountConnectionRepository = mock(TelegramAccountConnectionRepository::class.java)
    private val telegramClientGateway = mock(TelegramClientGateway::class.java)
    private val sessionSecretCrypto = mock(TelegramSessionSecretCrypto::class.java)

    private val service = TelegramConnectionApplicationService(
        pendingAuthStore = pendingAuthStore,
        accountConnectionRepository = accountConnectionRepository,
        telegramClientGateway = telegramClientGateway,
        sessionSecretCrypto = sessionSecretCrypto,
    )

    @Test
    fun `confirm code replaces stale default emoji status on reconnect`() {
        val pendingAuth = TelegramPendingAuth(
            pendingAuthId = "pending-1",
            userId = 1L,
            phoneNumber = "+15551234567",
            nextStep = TelegramPendingAuthStep.CODE,
            sessionDirectoryKey = "session-1",
            sessionDatabaseKeyCiphertext = "ciphertext",
        )
        val existingConnection = TelegramAccountConnection(
            userId = 1L,
            connectionStatus = TelegramConnectionStatus.DISCONNECTED,
            defaultEmojiStatusDocumentId = "7000",
        )

        `when`(pendingAuthStore.findById("pending-1")).thenReturn(pendingAuth)
        `when`(sessionSecretCrypto.decrypt("ciphertext")).thenReturn("db-key")
        `when`(telegramClientGateway.submitCode(
            userId = 1L,
            phoneNumber = "+15551234567",
            sessionDirectoryKey = "session-1",
            sessionDatabaseKey = "db-key",
            code = "11111",
        )).thenReturn(
            TelegramCodeSubmissionResult.Connected(
                TelegramConnectedAccount(
                    telegramUserId = 900001L,
                    phoneNumber = "+15551234567",
                    username = "telegram-1",
                    displayName = "Telegram User 1",
                    premium = true,
                    currentEmojiStatusDocumentId = "8111",
                ),
            ),
        )
        `when`(accountConnectionRepository.findByUserId(1L)).thenReturn(Optional.of(existingConnection))
        `when`(accountConnectionRepository.save(existingConnection)).thenAnswer { it.arguments[0] }

        val result = service.confirmCode(userId = 1L, pendingAuthId = "pending-1", code = "11111")

        assertThat(result.connected).isTrue()
        assertThat(result.connection?.defaultEmojiStatusDocumentId).isEqualTo("8111")
    }
}
