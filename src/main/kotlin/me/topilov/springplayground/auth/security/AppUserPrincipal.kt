package me.topilov.springplayground.auth.security

import me.topilov.springplayground.auth.domain.AuthRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class AppUserPrincipal(
    val id: Long,
    val usernameValue: String,
    val email: String,
    private val passwordHash: String,
    val role: AuthRole,
    private val enabledValue: Boolean,
    val emailVerified: Boolean,
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = usernameValue

    override fun isEnabled(): Boolean = enabledValue
}
