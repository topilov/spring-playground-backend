package me.topilov.springplayground.profile.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val displayName: String,
    @field:Size(max = 600)
    val bio: String = "",
)
