package me.topilov.springplayground.protection.domain

enum class ProtectionFlow {
    REGISTER,
    LOGIN,
    FORGOT_PASSWORD,
    RESEND_VERIFICATION_EMAIL,
    RESET_PASSWORD,
    TWO_FACTOR_LOGIN,
    PASSKEY_LOGIN_OPTIONS,
    PASSKEY_LOGIN_VERIFY,
    VERIFY_EMAIL_CHANGE,
}
