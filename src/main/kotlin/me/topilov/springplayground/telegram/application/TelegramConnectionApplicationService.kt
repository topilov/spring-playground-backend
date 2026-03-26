package me.topilov.springplayground.telegram.application

import me.topilov.springplayground.telegram.domain.TelegramAccountConnection
import me.topilov.springplayground.telegram.domain.TelegramConnectionStatus
import me.topilov.springplayground.telegram.domain.exception.TelegramInvalidAuthStepException
import me.topilov.springplayground.telegram.domain.exception.TelegramPendingAuthNotFoundException
import me.topilov.springplayground.telegram.infrastructure.crypto.TelegramSessionSecretCrypto
import me.topilov.springplayground.telegram.infrastructure.repository.TelegramAccountConnectionRepository
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramClientGateway
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramCodeSubmissionResult
import me.topilov.springplayground.telegram.infrastructure.tdlight.TelegramConnectedAccount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64

data class TelegramConnectionStepResult(
    val pendingAuthId: String? = null,
    val nextStep: TelegramPendingAuthStep? = null,
    val connected: Boolean,
    val connectionStatus: TelegramConnectionStatus? = null,
    val connection: TelegramAccountConnection? = null,
)

@Service
class TelegramConnectionApplicationService(
    private val pendingAuthStore: TelegramPendingAuthStore,
    private val accountConnectionRepository: TelegramAccountConnectionRepository,
    private val telegramClientGateway: TelegramClientGateway,
    private val sessionSecretCrypto: TelegramSessionSecretCrypto,
) {
    @Transactional
    fun startConnection(userId: Long, phoneNumber: String): TelegramConnectionStepResult {
        val normalizedPhoneNumber = phoneNumber.trim()
        val sessionDirectoryKey = randomToken()
        val sessionDatabaseKey = randomToken()
        val pendingAuth = TelegramPendingAuth(
            pendingAuthId = "telegram-auth-${randomToken()}",
            userId = userId,
            phoneNumber = normalizedPhoneNumber,
            nextStep = TelegramPendingAuthStep.CODE,
            sessionDirectoryKey = sessionDirectoryKey,
            sessionDatabaseKeyCiphertext = sessionSecretCrypto.encrypt(sessionDatabaseKey),
        )
        telegramClientGateway.startAuthentication(userId, normalizedPhoneNumber, sessionDirectoryKey, sessionDatabaseKey)
        pendingAuthStore.save(pendingAuth)
        return TelegramConnectionStepResult(
            pendingAuthId = pendingAuth.pendingAuthId,
            nextStep = TelegramPendingAuthStep.CODE,
            connected = false,
        )
    }

    @Transactional
    fun confirmCode(userId: Long, pendingAuthId: String, code: String): TelegramConnectionStepResult {
        val pendingAuth = requirePendingAuth(userId, pendingAuthId)
        if (pendingAuth.nextStep != TelegramPendingAuthStep.CODE) {
            throw TelegramInvalidAuthStepException()
        }
        val databaseKey = sessionSecretCrypto.decrypt(pendingAuth.sessionDatabaseKeyCiphertext)
        return when (
            val result = telegramClientGateway.submitCode(
                userId = userId,
                phoneNumber = pendingAuth.phoneNumber,
                sessionDirectoryKey = pendingAuth.sessionDirectoryKey,
                sessionDatabaseKey = databaseKey,
                code = code.trim(),
            )
        ) {
            TelegramCodeSubmissionResult.PasswordRequired -> {
                pendingAuthStore.save(pendingAuth.copy(nextStep = TelegramPendingAuthStep.PASSWORD))
                TelegramConnectionStepResult(
                    pendingAuthId = pendingAuth.pendingAuthId,
                    nextStep = TelegramPendingAuthStep.PASSWORD,
                    connected = false,
                )
            }
            is TelegramCodeSubmissionResult.Connected -> {
                pendingAuthStore.delete(pendingAuth.pendingAuthId)
                val connection = saveConnectedAccount(userId, pendingAuth, result.account)
                TelegramConnectionStepResult(
                    connected = true,
                    connectionStatus = connection.connectionStatus,
                    connection = connection,
                )
            }
        }
    }

    @Transactional
    fun confirmPassword(userId: Long, pendingAuthId: String, password: String): TelegramConnectionStepResult {
        val pendingAuth = requirePendingAuth(userId, pendingAuthId)
        if (pendingAuth.nextStep != TelegramPendingAuthStep.PASSWORD) {
            throw TelegramInvalidAuthStepException()
        }
        val databaseKey = sessionSecretCrypto.decrypt(pendingAuth.sessionDatabaseKeyCiphertext)
        val connected = telegramClientGateway.submitPassword(
            userId = userId,
            phoneNumber = pendingAuth.phoneNumber,
            sessionDirectoryKey = pendingAuth.sessionDirectoryKey,
            sessionDatabaseKey = databaseKey,
            password = password,
        )
        pendingAuthStore.delete(pendingAuth.pendingAuthId)
        val connection = saveConnectedAccount(userId, pendingAuth, connected)
        return TelegramConnectionStepResult(
            connected = true,
            connectionStatus = connection.connectionStatus,
            connection = connection,
        )
    }

    @Transactional
    fun disconnect(userId: Long) {
        val connection = accountConnectionRepository.findByUserId(userId).orElse(null) ?: return
        connection.connectionStatus = TelegramConnectionStatus.DISCONNECTED
        connection.telegramUserId = null
        connection.telegramPhoneNumber = null
        connection.telegramUsername = null
        connection.telegramDisplayName = null
        connection.premium = false
        connection.sessionDirectoryKey = null
        connection.sessionDatabaseKeyCiphertext = null
        accountConnectionRepository.save(connection)
    }

    private fun requirePendingAuth(userId: Long, pendingAuthId: String): TelegramPendingAuth {
        val pendingAuth = pendingAuthStore.findById(pendingAuthId) ?: throw TelegramPendingAuthNotFoundException()
        if (pendingAuth.userId != userId) {
            throw TelegramPendingAuthNotFoundException()
        }
        return pendingAuth
    }

    private fun saveConnectedAccount(
        userId: Long,
        pendingAuth: TelegramPendingAuth,
        connectedAccount: TelegramConnectedAccount,
    ): TelegramAccountConnection {
        val connection = accountConnectionRepository.findByUserId(userId)
            .orElseGet { TelegramAccountConnection(userId = userId) }
        connection.telegramUserId = connectedAccount.telegramUserId
        connection.telegramPhoneNumber = connectedAccount.phoneNumber
        connection.telegramUsername = connectedAccount.username
        connection.telegramDisplayName = connectedAccount.displayName
        connection.connectionStatus = TelegramConnectionStatus.CONNECTED
        connection.premium = connectedAccount.premium
        connection.sessionDirectoryKey = pendingAuth.sessionDirectoryKey
        connection.sessionDatabaseKeyCiphertext = pendingAuth.sessionDatabaseKeyCiphertext
        connection.defaultEmojiStatusDocumentId = connectedAccount.currentEmojiStatusDocumentId
        return accountConnectionRepository.save(connection)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val secureRandom = SecureRandom()
    }
}
