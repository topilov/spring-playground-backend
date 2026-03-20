package me.topilov.springplayground.publicapi

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import me.topilov.springplayground.publicapi.dto.PublicPingResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Unauthenticated public endpoints.")
class PublicController {
    @Operation(
        summary = "Public ping",
        description = "Returns a simple public status response so callers can verify that the backend is reachable.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Public ping response.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = PublicPingResponse::class),
                    ),
                ],
            ),
        ],
    )
    @GetMapping("/ping")
    fun ping(): PublicPingResponse = PublicPingResponse(status = "ok")
}
