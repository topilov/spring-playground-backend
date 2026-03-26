package me.topilov.springplayground.telegram.infrastructure.tdlight

import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TdlightTelegramClientGatewayTest {
    private val properties = TelegramProperties(
        enabled = true,
        sessionRoot = "build/tdlight-test-sessions",
        apiId = 12345,
        apiHash = "hash",
    )
    private val sessionFactory = FakeTdlightSessionFactory()
    private val gateway = TdlightTelegramClientGateway(properties, sessionFactory)

    @Test
    fun `start authentication initializes and waits for code request`() {
        gateway.startAuthentication(
            userId = 1L,
            phoneNumber = "+15551234567",
            sessionDirectoryKey = "session-1",
            sessionDatabaseKey = "db-key",
        )

        val session = sessionFactory.requireSession("session-1")
        assertThat(session.startAuthenticationCalls).isEqualTo(1)
        assertThat(session.awaitCodeRequestCalls).isEqualTo(1)
    }

    @Test
    fun `submit code returns password required from tdlight session`() {
        val session = sessionFactory.session("session-1")
        session.codeResult = TdlightCodeSubmissionResult.PasswordRequired

        val result = gateway.submitCode(
            userId = 1L,
            phoneNumber = "+15551234567",
            sessionDirectoryKey = "session-1",
            sessionDatabaseKey = "db-key",
            code = "11111",
        )

        assertThat(result).isEqualTo(TelegramCodeSubmissionResult.PasswordRequired)
        assertThat(session.submittedCodes).containsExactly("11111")
    }

    @Test
    fun `update emoji status opens session on demand and applies emoji`() {
        gateway.updateEmojiStatus(
            userId = 1L,
            phoneNumber = "+15551234567",
            telegramUserId = 900001L,
            sessionDirectoryKey = "session-2",
            sessionDatabaseKey = "db-key",
            emojiStatusDocumentId = "1005",
        )

        val session = sessionFactory.requireSession("session-2")
        assertThat(session.appliedEmojiStatuses).containsExactly("1005")
    }
}

private class FakeTdlightSessionFactory : TdlightSessionFactory {
    private val sessions = linkedMapOf<String, FakeTdlightSession>()

    override fun create(
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ): TdlightSession = sessions.getOrPut(sessionDirectoryKey) { FakeTdlightSession(phoneNumber) }

    fun session(sessionDirectoryKey: String): FakeTdlightSession =
        sessions.getOrPut(sessionDirectoryKey) { FakeTdlightSession("+15551234567") }

    fun requireSession(sessionDirectoryKey: String): FakeTdlightSession = checkNotNull(sessions[sessionDirectoryKey])
}

private class FakeTdlightSession(
    private val phoneNumber: String,
) : TdlightSession {
    var startAuthenticationCalls: Int = 0
    var awaitCodeRequestCalls: Int = 0
    var codeResult: TdlightCodeSubmissionResult = TdlightCodeSubmissionResult.Connected(
        TelegramConnectedAccount(
            telegramUserId = 900001L,
            phoneNumber = phoneNumber,
            username = "telegram-1",
            displayName = "Telegram User 1",
            premium = true,
            currentEmojiStatusDocumentId = null,
        ),
    )
    val submittedCodes = mutableListOf<String>()
    val appliedEmojiStatuses = mutableListOf<String?>()

    override fun startAuthentication() {
        startAuthenticationCalls += 1
    }

    override fun awaitCodeRequest() {
        awaitCodeRequestCalls += 1
    }

    override fun submitCode(code: String): TdlightCodeSubmissionResult {
        submittedCodes += code
        return codeResult
    }

    override fun submitPassword(password: String): TelegramConnectedAccount =
        TelegramConnectedAccount(
            telegramUserId = 900001L,
            phoneNumber = phoneNumber,
            username = "telegram-1",
            displayName = "Telegram User 1",
            premium = true,
            currentEmojiStatusDocumentId = null,
        )

    override fun updateEmojiStatus(emojiStatusDocumentId: String?) {
        appliedEmojiStatuses += emojiStatusDocumentId
    }

    override fun close() {
    }
}
