package me.topilov.springplayground.telegram.application

data class TelegramPendingAuth(
    val pendingAuthId: String,
    val userId: Long,
    val phoneNumber: String,
    val nextStep: TelegramPendingAuthStep,
    val sessionDirectoryKey: String,
    val sessionDatabaseKeyCiphertext: String,
)

enum class TelegramPendingAuthStep {
    CODE,
    PASSWORD,
}

interface TelegramPendingAuthStore {
    fun save(pendingAuth: TelegramPendingAuth)

    fun findById(pendingAuthId: String): TelegramPendingAuth?

    fun delete(pendingAuthId: String)
}
