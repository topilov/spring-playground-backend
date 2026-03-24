package me.topilov.springplayground.auth.service

import com.warrenstrange.googleauth.GoogleAuthenticator
import org.springframework.stereotype.Component

@Component
class TotpCodeService {
    private val googleAuthenticator = GoogleAuthenticator()

    fun generateSecret(): String = googleAuthenticator.createCredentials().key

    fun verify(secret: String, code: String): Boolean {
        val normalizedCode = code.trim()
        if (!normalizedCode.matches(Regex("^\\d{6}$"))) {
            return false
        }
        return googleAuthenticator.authorize(secret, normalizedCode.toInt())
    }
}
