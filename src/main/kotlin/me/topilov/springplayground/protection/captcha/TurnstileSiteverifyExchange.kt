package me.topilov.springplayground.protection.captcha

data class TurnstileSiteverifyExchange(
    val statusCode: Int,
    val payload: TurnstileSiteverifyResponse,
)
