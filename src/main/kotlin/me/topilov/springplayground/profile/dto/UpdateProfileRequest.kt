package me.topilov.springplayground.profile.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Payload used to update the current authenticated user's profile.")
data class UpdateProfileRequest(
    @field:NotBlank
    @field:Size(max = 120)
    @field:Schema(
        description = "User-facing display name.",
        example = "Updated Demo",
        maxLength = 120,
    )
    val displayName: String,
    @field:Size(max = 600)
    @field:Schema(
        description = "Short profile biography. Defaults to an empty string when omitted.",
        example = "Updated from frontend",
        maxLength = 600,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    )
    val bio: String = "",
)
