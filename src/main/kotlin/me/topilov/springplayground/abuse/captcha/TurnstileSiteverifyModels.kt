package me.topilov.springplayground.abuse.captcha

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TurnstileSiteverifyResponse(
    val success: Boolean = false,
    val hostname: String? = null,
    val action: String? = null,
    @param:JsonProperty("error-codes")
    val errorCodes: List<String> = emptyList(),
)
