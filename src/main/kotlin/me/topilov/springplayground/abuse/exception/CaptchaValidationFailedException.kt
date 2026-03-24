package me.topilov.springplayground.abuse.exception

class CaptchaValidationFailedException(
    message: String = "Captcha validation failed",
) : RuntimeException(message)
