package me.topilov.springplayground.profile.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.UpdateProfileRequest
import me.topilov.springplayground.profile.service.ProfileService
import me.topilov.springplayground.shared.dto.ApiErrorResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profile")
@Tag(name = "Profile", description = "Authenticated profile endpoints.")
class ProfileController(
    private val profileService: ProfileService,
) {
    @Operation(
        summary = "Get current profile",
        description = "Returns the profile linked to the current authenticated session.",
        security = [SecurityRequirement(name = "sessionCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Current profile.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProfileResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid JSESSIONID session cookie.",
                content = [Content()],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Authenticated user has no linked profile.",
                content = [Content()],
            ),
        ],
    )
    @GetMapping("/me")
    fun currentProfile(@AuthenticationPrincipal principal: AppUserPrincipal): ProfileResponse =
        profileService.getCurrentProfile(principal.id)

    @Operation(
        summary = "Update current profile",
        description = "Updates the profile linked to the current authenticated session.",
        security = [SecurityRequirement(name = "sessionCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updated profile.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProfileResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation failed for the submitted profile payload.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiErrorResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "401",
                description = "Missing or invalid JSESSIONID session cookie.",
                content = [Content()],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Authenticated user has no linked profile.",
                content = [Content()],
            ),
        ],
    )
    @PutMapping("/me")
    fun updateCurrentProfile(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileResponse = profileService.updateCurrentProfile(principal.id, request)
}
