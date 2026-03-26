package me.topilov.springplayground.telegram.infrastructure.tdlight

import it.tdlight.client.APIToken
import it.tdlight.client.AuthenticationSupplier
import it.tdlight.client.ClientInteraction
import it.tdlight.client.InputParameter
import it.tdlight.client.ParameterInfo
import it.tdlight.client.SimpleTelegramClient
import it.tdlight.client.SimpleTelegramClientFactory
import it.tdlight.client.TDLibSettings
import it.tdlight.jni.TdApi
import me.topilov.springplayground.telegram.infrastructure.config.TelegramProperties
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class DefaultTdlightSessionFactory(
    private val properties: TelegramProperties,
) : TdlightSessionFactory {
    override fun create(
        phoneNumber: String,
        sessionDirectoryKey: String,
        sessionDatabaseKey: String,
    ): TdlightSession = SimpleClientTdlightSession(
        clientAndInteraction = buildClient(phoneNumber, sessionDirectoryKey),
    )

    private fun buildClient(
        phoneNumber: String,
        sessionDirectoryKey: String,
    ): ClientAndInteraction {
        val factory = SimpleTelegramClientFactory()
        val settings = TDLibSettings.create(APIToken(properties.apiId, properties.apiHash)).apply {
            val sessionDirectory = sessionDirectory(sessionDirectoryKey)
            setDatabaseDirectoryPath(sessionDirectory)
            setDownloadedFilesDirectoryPath(sessionDirectory.resolve("downloads"))
            setFileDatabaseEnabled(true)
            setChatInfoDatabaseEnabled(true)
            setMessageDatabaseEnabled(true)
            setUseTestDatacenter(false)
            setSystemLanguageCode("en")
            setDeviceModel("spring-playground")
            setSystemVersion("server")
            setApplicationVersion("spring-playground")
            setEnableStorageOptimizer(true)
            setIgnoreFileNames(false)
        }
        val interaction = TdlightPromptInteraction()
        val client = factory.builder(settings).apply {
            setClientInteraction(interaction)
        }.build(AuthenticationSupplier.user(phoneNumber))
        return ClientAndInteraction(client = client, interaction = interaction)
    }

    private fun sessionDirectory(sessionDirectoryKey: String): Path {
        val root = Paths.get(properties.sessionRoot).toAbsolutePath().normalize()
        val sessionDirectory = root.resolve(sessionDirectoryKey)
        Files.createDirectories(sessionDirectory)
        Files.createDirectories(sessionDirectory.resolve("downloads"))
        return sessionDirectory
    }
}

private class SimpleClientTdlightSession(
    clientAndInteraction: ClientAndInteraction,
) : TdlightSession {
    private val client = clientAndInteraction.client
    private val interaction = clientAndInteraction.interaction
    private val connectedAccountFuture = client.getMeAsync().thenApply(::toConnectedAccount)

    override fun startAuthentication() {
        // SimpleTelegramClient starts authorization when it is constructed.
    }

    override fun awaitCodeRequest() {
        interaction.awaitCodeRequest(timeout)
    }

    override fun submitCode(code: String): TdlightCodeSubmissionResult {
        interaction.completeCode(code, timeout)
        val outcome = awaitAny(
            connectedAccountFuture.thenApply<TdlightCodeSubmissionResult> { TdlightCodeSubmissionResult.Connected(it) },
            interaction.nextPasswordRequestFuture().thenApply<TdlightCodeSubmissionResult> { TdlightCodeSubmissionResult.PasswordRequired },
            interaction.nextCodeRequestFuture().thenApply<TdlightCodeSubmissionResult> {
                throw IllegalStateException("Telegram requested another authentication code.")
            },
        )
        return outcome
    }

    override fun submitPassword(password: String): TelegramConnectedAccount {
        interaction.completePassword(password, timeout)
        return awaitAny(
            connectedAccountFuture,
            interaction.nextPasswordRequestFuture().thenApply {
                throw IllegalStateException("Telegram requested another password.")
            },
        )
    }

    override fun updateEmojiStatus(emojiStatusDocumentId: String?) {
        connectedAccountFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        client.send(TdApi.SetEmojiStatus(toEmojiStatus(emojiStatusDocumentId))).get(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun close() {
        try {
            client.closeAndWait()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun toEmojiStatus(emojiStatusDocumentId: String?): TdApi.EmojiStatus? =
        emojiStatusDocumentId?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { TdApi.EmojiStatus(TdApi.EmojiStatusTypeCustomEmoji(it.toLong()), 0) }

    private fun toConnectedAccount(user: TdApi.User): TelegramConnectedAccount {
        val displayName = listOf(user.firstName, user.lastName)
            .map { it.orEmpty().trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { user.phoneNumber }
        val username = user.usernames?.activeUsernames?.firstOrNull()
            ?: user.usernames?.editableUsername?.takeIf { !it.isNullOrBlank() }
        val currentEmojiStatusDocumentId =
            (user.emojiStatus?.type as? TdApi.EmojiStatusTypeCustomEmoji)?.customEmojiId?.toString()
        return TelegramConnectedAccount(
            telegramUserId = user.id,
            phoneNumber = user.phoneNumber,
            username = username,
            displayName = displayName,
            premium = user.isPremium,
            currentEmojiStatusDocumentId = currentEmojiStatusDocumentId,
        )
    }

    private fun <T> awaitAny(vararg futures: CompletableFuture<out T>): T {
        val combined = CompletableFuture.anyOf(*futures)
        @Suppress("UNCHECKED_CAST")
        return combined.get(timeout.toMillis(), TimeUnit.MILLISECONDS) as T
    }

    companion object {
        private val timeout: Duration = Duration.ofSeconds(30)
    }
}

private class TdlightPromptInteraction : ClientInteraction {
    private val nextCodeRequest = AtomicReference(CompletableFuture<PromptRequest>())
    private val nextPasswordRequest = AtomicReference(CompletableFuture<PromptRequest>())
    private val currentCodeRequest = AtomicReference<PromptRequest?>(null)
    private val currentPasswordRequest = AtomicReference<PromptRequest?>(null)

    override fun onParameterRequest(
        inputParameter: InputParameter,
        parameterInfo: ParameterInfo,
    ): CompletableFuture<String> = when (inputParameter) {
        InputParameter.ASK_CODE -> registerRequest(currentCodeRequest, nextCodeRequest)
        InputParameter.ASK_PASSWORD -> registerRequest(currentPasswordRequest, nextPasswordRequest)
        else -> CompletableFuture.failedFuture(
            IllegalStateException("Unsupported Telegram authentication prompt: $inputParameter"),
        )
    }

    fun awaitCodeRequest(timeout: Duration): PromptRequest =
        currentCodeRequest.get()
            ?: nextCodeRequest.get().get(timeout.toMillis(), TimeUnit.MILLISECONDS)

    fun completeCode(code: String, timeout: Duration) {
        val request = awaitCodeRequest(timeout)
        currentCodeRequest.compareAndSet(request, null)
        request.response.complete(code)
    }

    fun completePassword(password: String, timeout: Duration) {
        val request = currentPasswordRequest.get()
            ?: nextPasswordRequest.get().get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        currentPasswordRequest.compareAndSet(request, null)
        request.response.complete(password)
    }

    fun nextCodeRequestFuture(): CompletableFuture<PromptRequest> =
        currentCodeRequest.get()?.let { CompletableFuture.completedFuture(it) } ?: nextCodeRequest.get()

    fun nextPasswordRequestFuture(): CompletableFuture<PromptRequest> =
        currentPasswordRequest.get()?.let { CompletableFuture.completedFuture(it) } ?: nextPasswordRequest.get()

    private fun registerRequest(
        slot: AtomicReference<PromptRequest?>,
        signal: AtomicReference<CompletableFuture<PromptRequest>>,
    ): CompletableFuture<String> {
        val request = PromptRequest(CompletableFuture())
        slot.set(request)
        signal.getAndSet(CompletableFuture()).complete(request)
        return request.response
    }
}

private data class PromptRequest(
    val response: CompletableFuture<String>,
)

private data class ClientAndInteraction(
    val client: SimpleTelegramClient,
    val interaction: TdlightPromptInteraction,
)
