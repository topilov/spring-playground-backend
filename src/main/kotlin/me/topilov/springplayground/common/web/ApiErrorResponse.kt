package me.topilov.springplayground.common.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Framework-generated error response body.")
data class ApiErrorResponse(
    @field:Schema(example = "2026-03-20T06:22:13.782Z")
    val timestamp: String,
    @field:Schema(example = "400")
    val status: Int,
    @field:Schema(example = "Bad Request")
    val error: String,
    @field:Schema(example = "/api/profile/me")
    val path: String,
)
