package me.topilov.springplayground.auth.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import me.topilov.springplayground.auth.dto.DisableTwoFactorResponse
import me.topilov.springplayground.auth.dto.LoginResponse
import me.topilov.springplayground.auth.dto.RegenerateBackupCodesResponse
import me.topilov.springplayground.auth.dto.TwoFactorBackupCodeLoginVerifyRequest
import me.topilov.springplayground.auth.dto.TwoFactorLoginVerifyRequest
import me.topilov.springplayground.auth.dto.TwoFactorSetupConfirmRequest
import me.topilov.springplayground.auth.dto.TwoFactorSetupConfirmResponse
import me.topilov.springplayground.auth.dto.TwoFactorSetupStartResponse
import me.topilov.springplayground.auth.dto.TwoFactorStatusResponse
import me.topilov.springplayground.auth.application.TwoFactorLoginApplicationService
import me.topilov.springplayground.auth.security.AppUserPrincipal
import me.topilov.springplayground.auth.service.TwoFactorManagementService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/2fa")
class TwoFactorController(
    private val managementService: TwoFactorManagementService,
    private val loginApplicationService: TwoFactorLoginApplicationService,
) {
    @GetMapping("/status")
    fun status(@AuthenticationPrincipal principal: AppUserPrincipal): TwoFactorStatusResponse =
        managementService.status(principal.id)

    @PostMapping("/setup/start")
    fun startSetup(@AuthenticationPrincipal principal: AppUserPrincipal): TwoFactorSetupStartResponse =
        managementService.startSetup(principal.id)

    @PostMapping("/setup/confirm")
    fun confirmSetup(
        @AuthenticationPrincipal principal: AppUserPrincipal,
        @Valid @RequestBody request: TwoFactorSetupConfirmRequest,
    ): TwoFactorSetupConfirmResponse = managementService.confirmSetup(principal.id, request)

    @PostMapping("/backup-codes/regenerate")
    fun regenerateBackupCodes(@AuthenticationPrincipal principal: AppUserPrincipal): RegenerateBackupCodesResponse =
        managementService.regenerateBackupCodes(principal.id)

    @PostMapping("/disable")
    fun disable(@AuthenticationPrincipal principal: AppUserPrincipal): DisableTwoFactorResponse =
        managementService.disable(principal.id)

    @PostMapping("/login/verify")
    fun finishLoginWithTotp(
        @Valid @RequestBody request: TwoFactorLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = loginApplicationService.completeTotpLogin(request, servletRequest, servletResponse)

    @PostMapping("/login/verify-backup-code")
    fun finishLoginWithBackupCode(
        @Valid @RequestBody request: TwoFactorBackupCodeLoginVerifyRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): LoginResponse = loginApplicationService.completeBackupCodeLogin(request, servletRequest, servletResponse)
}
