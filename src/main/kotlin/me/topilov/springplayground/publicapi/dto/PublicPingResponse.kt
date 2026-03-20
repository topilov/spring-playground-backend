package me.topilov.springplayground.publicapi.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Simple public reachability response.")
data class PublicPingResponse(
    @field:Schema(example = "ok")
    val status: String,
)
