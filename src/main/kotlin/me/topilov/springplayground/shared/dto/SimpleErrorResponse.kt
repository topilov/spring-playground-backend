package me.topilov.springplayground.shared.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Simple application-defined error response body.")
data class SimpleErrorResponse(
    @field:Schema(example = "Username 'demo' is already in use")
    val error: String,
)
