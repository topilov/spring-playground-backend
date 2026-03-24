package me.topilov.springplayground.protection.infrastructure.config

import java.time.Duration

data class ProtectionFlowPolicy(
    var captchaRequired: Boolean = true,
    var requestLimit: Int = 10,
    var requestWindow: Duration = Duration.ofMinutes(1),
    var failureLimit: Int? = null,
    var failureWindow: Duration? = null,
    var cooldown: Duration? = null,
)
