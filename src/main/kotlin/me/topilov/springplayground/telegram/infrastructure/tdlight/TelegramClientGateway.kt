package me.topilov.springplayground.telegram.infrastructure.tdlight

data class TelegramConnectedAccount(
    val telegramUserId: Long,
    val phoneNumber: String,
    val username: String?,
    val displayName: String,
    val premium: Boolean,
    val currentEmojiStatusDocumentId: String?,
)

sealed interface TelegramCodeSubmissionResult {
    data object PasswordRequired : TelegramCodeSubmissionResult

    data class Connected(
        val account: TelegramConnectedAccount,
    ) : TelegramCodeSubmissionResult
}

interface TelegramClientGateway {
    fun startAuthentication(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    )

    fun submitCode(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        code: String,
    ): TelegramCodeSubmissionResult

    fun submitPassword(
        userId: Long,
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        password: String,
    ): TelegramConnectedAccount

    fun updateEmojiStatus(
        userId: Long,
        phoneNumber: String,
        telegramUserId: Long,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
        emojiStatusDocumentId: String?,
    )
}
