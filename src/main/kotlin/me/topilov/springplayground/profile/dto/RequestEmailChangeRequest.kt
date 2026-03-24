package me.topilov.springplayground.profile.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RequestEmailChangeRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val newEmail: String,
)
