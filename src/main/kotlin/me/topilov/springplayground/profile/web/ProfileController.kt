package me.topilov.springplayground.profile.web

import jakarta.validation.Valid
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.profile.service.ProfileService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profile")
class ProfileController(
    private val profileService: ProfileService,
) {
    @GetMapping("/me")
    fun currentProfile(@AuthenticationPrincipal principal: AppUserPrincipal): ProfileService.ProfileResponse =
        profileService.getCurrentProfile(principal.id)

    @PutMapping("/me")
    fun updateCurrentProfile(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: ProfileService.UpdateProfileRequest,
    ): ProfileService.ProfileResponse = profileService.updateCurrentProfile(principal.id, request)
}
