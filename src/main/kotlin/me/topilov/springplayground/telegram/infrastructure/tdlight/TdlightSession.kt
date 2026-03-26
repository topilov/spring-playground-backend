package me.topilov.springplayground.telegram.infrastructure.tdlight

sealed interface TdlightCodeSubmissionResult {
    data object PasswordRequired : TdlightCodeSubmissionResult

    data class Connected(
        val account: TelegramConnectedAccount,
    ) : TdlightCodeSubmissionResult
}

interface TdlightSession : AutoCloseable {
    fun startAuthentication()

    fun awaitCodeRequest()

    fun submitCode(code: String): TdlightCodeSubmissionResult

    fun submitPassword(password: String): TelegramConnectedAccount

    fun updateEmojiStatus(emojiStatusDocumentId: String?)
}

interface TdlightSessionFactory {
    fun create(
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ): TdlightSession
}
