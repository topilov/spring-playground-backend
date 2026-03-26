package me.topilov.springplayground.telegram

import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramCodeSubmissionResult
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramConnectedAccount
import java.util.concurrent.ConcurrentHashMap

class FakeTelegramClientGateway : TelegramClientGateway {
    private val emojiStatuses = ConcurrentHashMap<Long, String?>()

    fun reset() {
        emojiStatuses.clear()
    }

    fun currentEmojiStatusDocumentIdForUser(userId: Long): String? = emojiStatuses[userId]

    fun setCurrentEmojiStatusDocumentIdForUser(userId: Long, emojiStatusDocumentId: String?) {
        emojiStatuses[userId] = emojiStatusDocumentId
    }

    override fun startAuthentication(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ) {
    }

    override fun submitCode(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        code: String,
    ): TelegramCodeSubmissionResult =
        if (code == "22222") {
            TelegramCodeSubmissionResult.PasswordRequired
        } else {
            TelegramCodeSubmissionResult.Connected(
                TelegramConnectedAccount(
                    telegramUserId = 900_000L + userId,
                    phoneNumber = phoneNumber,
                    username = "telegram-$userId",
                    displayName = "Telegram User $userId",
                    premium = true,
                    currentEmojiStatusDocumentId = null,
                ),
            )
        }

    override fun submitPassword(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        password: String,
    ): TelegramConnectedAccount = TelegramConnectedAccount(
        telegramUserId = 900_000L + userId,
        phoneNumber = phoneNumber,
        username = "telegram-$userId",
        displayName = "Telegram User $userId",
        premium = true,
        currentEmojiStatusDocumentId = null,
    )

    override fun updateEmojiStatus(
        userId: Long,
        phoneNumber: String,
        telegramUserId: Long,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        emojiStatusDocumentId: String?,
    ) {
        emojiStatuses[userId] = emojiStatusDocumentId
    }
}
