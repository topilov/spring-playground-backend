package me.topilov.springplayground.profile.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.profile.dto.ChangePasswordRequest
import me.topilov.springplayground.profile.dto.ChangePasswordResponse
import me.topilov.springplayground.profile.dto.ProfileResponse
import me.topilov.springplayground.profile.dto.RequestEmailChangeRequest
import me.topilov.springplayground.profile.dto.RequestEmailChangeResponse
import me.topilov.springplayground.profile.dto.UpdateProfileRequest
import me.topilov.springplayground.profile.dto.UpdateUsernameRequest
import me.topilov.springplayground.profile.dto.VerifyEmailChangeRequest
import me.topilov.springplayground.profile.service.ProfileService
import me.topilov.springplayground.shared.dto.ApiErrorResponse
import me.topilov.springplayground.shared.dto.SimpleErrorResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
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

    @Operation(
        summary = "Update current username",
        description = "Updates the username of the current authenticated user.",
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
                description = "Validation failed for the submitted username payload.",
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
            ApiResponse(
                responseCode = "409",
                description = "Username is already in use.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = SimpleErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/me/username")
    fun updateUsername(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: UpdateUsernameRequest,
    ): ProfileResponse = profileService.updateUsername(principal.id, request)

    @Operation(
        summary = "Change current password",
        description = "Changes the password of the current authenticated user after verifying the current password.",
        security = [SecurityRequirement(name = "sessionCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Password changed.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ChangePasswordResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation failed or the current password is incorrect.",
                content = [Content()],
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
    @PostMapping("/me/password")
    fun changePassword(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: ChangePasswordRequest,
    ): ChangePasswordResponse = profileService.changePassword(principal.id, request)

    @Operation(
        summary = "Request current email change",
        description = "Starts a new email change flow for the current authenticated user and sends a verification link to the new email address.",
        security = [SecurityRequirement(name = "sessionCookie")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Email change request accepted.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = RequestEmailChangeResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation failed or the new email matches the current email.",
                content = [Content()],
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
            ApiResponse(
                responseCode = "409",
                description = "Email is already in use.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = SimpleErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/me/email/change-request")
    fun requestEmailChange(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: RequestEmailChangeRequest,
    ): RequestEmailChangeResponse = profileService.requestEmailChange(principal.id, request)

    @Operation(
        summary = "Verify current email change",
        description = "Confirms an email change using a one-time token that was sent to the new email address.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Email change completed.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ProfileResponse::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation failed or the email change token is invalid or expired.",
                content = [Content()],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Authenticated user has no linked profile.",
                content = [Content()],
            ),
            ApiResponse(
                responseCode = "409",
                description = "Email is already in use.",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = SimpleErrorResponse::class),
                    ),
                ],
            ),
        ],
    )
    @PostMapping("/me/email/verify")
    fun verifyEmailChange(
        @Valid @RequestBody request: VerifyEmailChangeRequest,
        servletRequest: HttpServletRequest,
    ): ProfileResponse = profileService.verifyEmailChange(request, servletRequest)
}
