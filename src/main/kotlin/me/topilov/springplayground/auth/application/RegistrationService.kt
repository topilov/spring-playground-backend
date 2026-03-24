package me.topilov.springplayground.auth.application

import jakarta.servlet.http.HttpServletRequest
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.dto.RegisterRequest
import me.topilov.springplayground.auth.dto.RegisterResponse
import me.topilov.springplayground.auth.exception.AuthEmailAlreadyUsedException
import me.topilov.springplayground.auth.exception.AuthUsernameAlreadyUsedException
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.repository.UserProfileRepository
import me.topilov.springplayground.protection.application.ProtectionService
import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class RegistrationService(
    private val authUserRepository: AuthUserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailVerificationService: EmailVerificationService,
    private val protectionService: ProtectionService,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun register(request: RegisterRequest, servletRequest: HttpServletRequest): RegisterResponse {
        protectionService.protect(
            ProtectionFlow.REGISTER,
            protectionService.buildContext(request.captchaToken, servletRequest),
        )
        val username = request.username.trim()
        val email = request.email.trim().lowercase()

        val registration = try {
            requireNotNull(transactionTemplate.execute<RegisteredUser> {
                if (authUserRepository.existsByUsernameIgnoreCase(username)) {
                    throw AuthUsernameAlreadyUsedException(username)
                }

                if (authUserRepository.existsByEmailIgnoreCase(email)) {
                    throw AuthEmailAlreadyUsedException(email)
                }

                val user = authUserRepository.save(
                    AuthUser(
                        username = username,
                        email = email,
                        passwordHash = requireNotNull(passwordEncoder.encode(request.password)) {
                            "Encoded password is missing"
                        },
                        emailVerified = false,
                    ),
                )

                userProfileRepository.save(
                    UserProfile(
                        user = user,
                        displayName = username,
                        bio = "",
                    ),
                )

                RegisteredUser(
                    userId = requireNotNull(user.id) { "Persisted user id is missing" },
                    username = user.username,
                    email = user.email,
                )
            })
        } catch (exception: DataIntegrityViolationException) {
            when {
                authUserRepository.existsByUsernameIgnoreCase(username) -> throw AuthUsernameAlreadyUsedException(username)
                authUserRepository.existsByEmailIgnoreCase(email) -> throw AuthEmailAlreadyUsedException(email)
                else -> throw exception
            }
        }

        try {
            emailVerificationService.sendRegistrationVerificationEmail(
                userId = registration.userId,
                username = registration.username,
                email = registration.email,
            )
        } catch (exception: RuntimeException) {
            log.warn("Failed to send verification email to {}", registration.email, exception)
        }

        return RegisterResponse(
            userId = registration.userId,
            username = registration.username,
            email = registration.email,
        )
    }

    private data class RegisteredUser(
        val userId: Long,
        val username: String,
        val email: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(RegistrationService::class.java)
    }
}
