package me.topilov.springplayground.auth.passkey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.topilov.springplayground.auth.passkey.dto.PasskeyAuthenticationPublicKeyOptionsDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyCredentialDescriptorDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyPubKeyCredParamDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyRegistrationPublicKeyOptionsDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyRelyingPartyDto
import me.topilov.springplayground.auth.passkey.dto.PasskeyUserIdentityDto
import org.springframework.stereotype.Component

@Component
class PasskeyOptionsMapper {
    private val objectMapper = jacksonObjectMapper()

    fun registrationOptionsFromCredentialsCreateJson(json: String): PasskeyRegistrationPublicKeyOptionsDto {
        val node = objectMapper.readTree(json).get("publicKey")
            ?: error("Missing publicKey in WebAuthn registration options")

        return PasskeyRegistrationPublicKeyOptionsDto(
            challenge = node.requiredText("challenge"),
            rp = PasskeyRelyingPartyDto(
                name = node.required("rp").requiredText("name"),
                id = node.required("rp").textOrNull("id"),
            ),
            user = PasskeyUserIdentityDto(
                id = node.required("user").requiredText("id"),
                name = node.required("user").requiredText("name"),
                displayName = node.required("user").requiredText("displayName"),
            ),
            pubKeyCredParams = node.required("pubKeyCredParams").map { param ->
                PasskeyPubKeyCredParamDto(
                    type = param.requiredText("type"),
                    alg = param.required("alg").asLong(),
                )
            },
            timeout = node.textOrLong("timeout"),
            excludeCredentials = node.listOrEmpty("excludeCredentials").map(::descriptorFrom),
            authenticatorSelection = node.objectOrNull("authenticatorSelection"),
            attestation = node.textOrNull("attestation"),
            hints = node.listOfStrings("hints"),
            extensions = node.objectOrEmpty("extensions"),
        )
    }

    fun authenticationOptionsFromCredentialsGetJson(json: String): PasskeyAuthenticationPublicKeyOptionsDto {
        val node = objectMapper.readTree(json).get("publicKey")
            ?: error("Missing publicKey in WebAuthn authentication options")

        return PasskeyAuthenticationPublicKeyOptionsDto(
            challenge = node.requiredText("challenge"),
            timeout = node.textOrLong("timeout"),
            rpId = node.textOrNull("rpId"),
            allowCredentials = node.listOrEmpty("allowCredentials").map(::descriptorFrom),
            userVerification = node.textOrNull("userVerification"),
            hints = node.listOfStrings("hints"),
            extensions = node.objectOrEmpty("extensions"),
        )
    }

    private fun descriptorFrom(node: com.fasterxml.jackson.databind.JsonNode): PasskeyCredentialDescriptorDto =
        PasskeyCredentialDescriptorDto(
            type = node.requiredText("type"),
            id = node.requiredText("id"),
            transports = node.listOfStrings("transports"),
        )

    private fun com.fasterxml.jackson.databind.JsonNode.requiredText(name: String): String =
        required(name).asText()

    private fun com.fasterxml.jackson.databind.JsonNode.textOrNull(name: String): String? =
        get(name)?.takeUnless { it.isNull }?.asText()

    private fun com.fasterxml.jackson.databind.JsonNode.textOrLong(name: String): Long? =
        get(name)?.takeUnless { it.isNull }?.asLong()

    private fun com.fasterxml.jackson.databind.JsonNode.listOrEmpty(name: String): List<com.fasterxml.jackson.databind.JsonNode> =
        get(name)?.takeIf { it.isArray }?.toList().orEmpty()

    private fun com.fasterxml.jackson.databind.JsonNode.listOfStrings(name: String): List<String> =
        listOrEmpty(name).map { it.asText() }

    private fun com.fasterxml.jackson.databind.JsonNode.objectOrNull(name: String): Map<String, Any?>? =
        get(name)?.takeIf { it.isObject }?.let { objectMapper.convertValue(it, Map::class.java) as Map<String, Any?> }

    private fun com.fasterxml.jackson.databind.JsonNode.objectOrEmpty(name: String): Map<String, Any?> =
        objectOrNull(name).orEmpty()
}
