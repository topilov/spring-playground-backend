package me.topilov.springplayground.auth.application

import me.topilov.springplayground.auth.domain.AuthRole
import me.topilov.springplayground.auth.domain.AuthUser
import me.topilov.springplayground.auth.repository.AuthUserRepository
import me.topilov.springplayground.profile.domain.UserProfile
import me.topilov.springplayground.profile.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
@Profile("local")
class LocalDemoUserInitializer(
    private val properties: LocalDemoUserProperties,
    private val authUserRepository: AuthUserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    transactionManager: PlatformTransactionManager? = null,
) : ApplicationRunner {
    private val transactionTemplate = transactionManager?.let(::TransactionTemplate)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) {
            log.info("Local demo user bootstrap is disabled")
            return
        }

        val transactionTemplate = requireNotNull(transactionTemplate) {
            "Transaction manager is required when running the local demo user initializer"
        }
        transactionTemplate.executeWithoutResult {
            ensureDemoUser()
        }
    }

    fun ensureDemoUser() {
        if (!properties.enabled) {
            return
        }

        val existingUser = authUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
            properties.username,
            properties.email,
        ).orElse(null)

        val demoUser = existingUser ?: authUserRepository.save(
            AuthUser(
                username = properties.username,
                email = properties.email,
                passwordHash = encodePassword(),
                role = AuthRole.USER,
                enabled = true,
                emailVerified = true,
            ),
        )

        if (existingUser != null) {
            demoUser.username = properties.username
            demoUser.email = properties.email
            demoUser.passwordHash = encodedPasswordFor(demoUser)
            demoUser.role = AuthRole.USER
            demoUser.enabled = true
            demoUser.emailVerified = true
            authUserRepository.save(demoUser)
        }

        val userId = requireNotNull(demoUser.id) { "Persisted demo user id is missing" }
        val profile = userProfileRepository.findByUserId(userId).orElseGet {
            UserProfile(user = demoUser)
        }
        profile.user = demoUser
        profile.displayName = properties.displayName
        profile.bio = properties.bio
        userProfileRepository.save(profile)

        log.info("Local demo user is ready for username '{}' and email '{}'", demoUser.username, demoUser.email)
    }

    private fun encodedPasswordFor(user: AuthUser): String =
        if (user.passwordHash.isNotBlank() && passwordEncoder.matches(properties.password, user.passwordHash)) {
            user.passwordHash
        } else {
            encodePassword()
        }

    private fun encodePassword(): String = requireNotNull(passwordEncoder.encode(properties.password)) {
        "Encoded demo password is missing"
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalDemoUserInitializer::class.java)
    }
}

@ConfigurationProperties("app.local.demo-user")
data class LocalDemoUserProperties(
    var enabled: Boolean = true,
    var username: String = "demo",
    var email: String = "demo@example.com",
    var password: String = "demo-password",
    var displayName: String = "Demo User",
    var bio: String = "Session-backed example profile",
)
