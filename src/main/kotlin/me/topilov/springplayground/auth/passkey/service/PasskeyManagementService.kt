package me.topilov.springplayground.auth.passkey.service

import me.topilov.springplayground.auth.passkey.domain.PasskeyCredential
import me.topilov.springplayground.auth.passkey.dto.PasskeySummaryResponse
import me.topilov.springplayground.auth.passkey.dto.RenamePasskeyRequest
import me.topilov.springplayground.auth.passkey.exception.PasskeyNotFoundException
import me.topilov.springplayground.auth.passkey.repository.PasskeyCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PasskeyManagementService(
    private val passkeyCredentialRepository: PasskeyCredentialRepository,
) {
    @Transactional(readOnly = true)
    fun listForUser(userId: Long): List<PasskeySummaryResponse> =
        passkeyCredentialRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map(::toSummary)

    @Transactional
    fun rename(userId: Long, passkeyId: Long, request: RenamePasskeyRequest): PasskeySummaryResponse {
        val passkey = passkeyCredentialRepository.findByIdAndUserId(passkeyId, userId)
            .orElseThrow(::PasskeyNotFoundException)
        passkey.nickname = request.name.trim()
        return toSummary(passkey)
    }

    @Transactional
    fun delete(userId: Long, passkeyId: Long) {
        val passkey = passkeyCredentialRepository.findByIdAndUserId(passkeyId, userId)
            .orElseThrow(::PasskeyNotFoundException)
        passkeyCredentialRepository.delete(passkey)
    }

    fun toSummary(passkey: PasskeyCredential): PasskeySummaryResponse =
        PasskeySummaryResponse(
            id = requireNotNull(passkey.id) { "Persisted passkey id is missing" },
            name = passkey.nickname,
            createdAt = passkey.createdAt,
            lastUsedAt = passkey.lastUsedAt,
            deviceHint = passkey.authenticatorAttachment,
            transports = passkey.transports
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                .orEmpty(),
        )
}
