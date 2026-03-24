package me.topilov.springplayground.abuse.config

import me.topilov.springplayground.abuse.AbuseProtectionFlow
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.abuse")
data class AbuseProtectionProperties(
    var flows: MutableMap<AbuseProtectionFlow, FlowPolicy> = defaultPolicies(),
) {
    fun policy(flow: AbuseProtectionFlow): FlowPolicy =
        flows[flow] ?: error("Missing abuse-protection policy for flow $flow")

    companion object {
        private fun defaultPolicies(): MutableMap<AbuseProtectionFlow, FlowPolicy> = mutableMapOf(
            AbuseProtectionFlow.REGISTER to FlowPolicy(
                requestLimit = 5,
                requestWindow = Duration.ofMinutes(5),
            ),
            AbuseProtectionFlow.LOGIN to FlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
                failureLimit = 5,
                failureWindow = Duration.ofMinutes(10),
            ),
            AbuseProtectionFlow.FORGOT_PASSWORD to FlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
                cooldown = Duration.ofMinutes(2),
            ),
            AbuseProtectionFlow.RESEND_VERIFICATION_EMAIL to FlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
                cooldown = Duration.ofMinutes(2),
            ),
            AbuseProtectionFlow.RESET_PASSWORD to FlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
            ),
            AbuseProtectionFlow.TWO_FACTOR_LOGIN to FlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
                failureLimit = 5,
                failureWindow = Duration.ofMinutes(10),
            ),
            AbuseProtectionFlow.PASSKEY_LOGIN_OPTIONS to FlowPolicy(
                requestLimit = 15,
                requestWindow = Duration.ofMinutes(5),
            ),
            AbuseProtectionFlow.PASSKEY_LOGIN_VERIFY to FlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
            ),
            AbuseProtectionFlow.VERIFY_EMAIL_CHANGE to FlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
            ),
        )
    }
}

data class FlowPolicy(
    var captchaRequired: Boolean = true,
    var requestLimit: Int = 10,
    var requestWindow: Duration = Duration.ofMinutes(1),
    var failureLimit: Int? = null,
    var failureWindow: Duration? = null,
    var cooldown: Duration? = null,
)
