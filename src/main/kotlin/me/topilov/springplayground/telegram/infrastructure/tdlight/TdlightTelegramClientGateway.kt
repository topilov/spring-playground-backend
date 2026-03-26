package me.topilov.springplayground.telegram.infrastructure.tdlight

import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import java.util.concurrent.ConcurrentHashMap

class TdlightTelegramClientGateway(
    private val properties: TelegramProperties,
    private val sessionFactory: TdlightSessionFactory = DefaultTdlightSessionFactory(properties),
) : TelegramClientGateway {
    private val sessions = ConcurrentHashMap<String, CachedTdlightSession>()

    override fun startAuthentication(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ) {
        requireEnabled()
        val session = sessionFor(phoneNumber, sessionDirectoryKey, sessionDatabaseKey)
        session.startAuthentication()
        session.awaitCodeRequest()
    }

    override fun submitCode(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        code: String,
    ): TelegramCodeSubmissionResult {
        requireEnabled()
        return when (val result = sessionFor(phoneNumber, sessionDirectoryKey, sessionDatabaseKey).submitCode(code)) {
            TdlightCodeSubmissionResult.PasswordRequired -> TelegramCodeSubmissionResult.PasswordRequired
            is TdlightCodeSubmissionResult.Connected -> TelegramCodeSubmissionResult.Connected(result.account)
        }
    }

    override fun submitPassword(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        password: String,
    ): TelegramConnectedAccount {
        requireEnabled()
        return sessionFor(phoneNumber, sessionDirectoryKey, sessionDatabaseKey).submitPassword(password)
    }

    override fun updateEmojiStatus(
        userId: Long,
        phoneNumber: String,
        telegramUserId: Long,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        emojiStatusDocumentId: String?,
    ) {
        requireEnabled()
        sessionFor(phoneNumber, sessionDirectoryKey, sessionDatabaseKey).updateEmojiStatus(emojiStatusDocumentId)
    }

    private fun sessionFor(
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ): TdlightSession = sessions.compute(sessionDirectoryKey) { _, existing ->
        if (existing == null || existing.phoneNumber != phoneNumber || existing.sessionDatabaseKey != sessionDatabaseKey) {
            existing?.session?.close()
            CachedTdlightSession(
                phoneNumber = phoneNumber,
                sessionDatabaseKey = sessionDatabaseKey,
                session = sessionFactory.create(phoneNumber, sessionDirectoryKey, sessionDatabaseKey),
            )
        } else {
            existing
        }
    }!!.session

    private fun requireEnabled() {
        require(properties.enabled) { "TDLight Telegram integration is not enabled in this environment." }
        require(properties.apiId > 0 && properties.apiHash.isNotBlank()) {
            "TDLight Telegram integration is missing apiId/apiHash configuration."
        }
    }

    private data class CachedTdlightSession(
        val phoneNumber: String,
        val sessionDatabaseKey: String,
        val session: TdlightSession,
    )
}
