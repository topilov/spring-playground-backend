package me.topilov.springplayground.auth.passkey.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Passkey rename request.")
data class RenamePasskeyRequest(
    @field:NotBlank
    @field:Size(max = 100)
    @field:Schema(example = "Work Laptop")
    val name: String,
)
