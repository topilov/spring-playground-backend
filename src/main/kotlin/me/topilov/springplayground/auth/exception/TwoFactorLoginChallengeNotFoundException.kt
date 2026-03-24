package me.topilov.springplayground.auth.exception

class TwoFactorLoginChallengeNotFoundException : RuntimeException("Two-factor login challenge is invalid or expired")
