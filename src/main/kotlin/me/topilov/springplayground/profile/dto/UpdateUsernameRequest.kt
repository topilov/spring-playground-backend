package me.topilov.springplayground.profile.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateUsernameRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val username: String,
)
