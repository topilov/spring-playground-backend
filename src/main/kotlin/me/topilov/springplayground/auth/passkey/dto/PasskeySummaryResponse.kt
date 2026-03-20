package me.topilov.springplayground.auth.passkey.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User-facing passkey summary.")
data class PasskeySummaryResponse(
    @field:Schema(example = "10")
    val id: Long,
    @field:Schema(example = "Work Laptop")
    val name: String,
    @field:Schema(example = "2026-03-21T10:15:30Z")
    val createdAt: Instant,
    @field:Schema(example = "2026-03-21T12:05:00Z")
    val lastUsedAt: Instant? = null,
    @field:Schema(example = "platform")
    val deviceHint: String? = null,
    val transports: List<String> = emptyList(),
)
