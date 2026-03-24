package me.topilov.springplayground.protection.infrastructure.config

import me.topilov.springplayground.protection.domain.ProtectionFlow
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.abuse")
data class ProtectionProperties(
    var flows: MutableMap<ProtectionFlow, ProtectionFlowPolicy> = defaultPolicies(),
) {
    fun policy(flow: ProtectionFlow): ProtectionFlowPolicy =
        flows[flow] ?: error("Missing protection policy for flow $flow")

    companion object {
        private fun defaultPolicies(): MutableMap<ProtectionFlow, ProtectionFlowPolicy> = mutableMapOf(
            ProtectionFlow.REGISTER to ProtectionFlowPolicy(
                requestLimit = 5,
                requestWindow = Duration.ofMinutes(5),
            ),
            ProtectionFlow.LOGIN to ProtectionFlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
                failureLimit = 5,
                failureWindow = Duration.ofMinutes(10),
            ),
            ProtectionFlow.FORGOT_PASSWORD to ProtectionFlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
                cooldown = Duration.ofMinutes(2),
            ),
            ProtectionFlow.RESEND_VERIFICATION_EMAIL to ProtectionFlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
                cooldown = Duration.ofMinutes(2),
            ),
            ProtectionFlow.RESET_PASSWORD to ProtectionFlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
            ),
            ProtectionFlow.TWO_FACTOR_LOGIN to ProtectionFlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
                failureLimit = 5,
                failureWindow = Duration.ofMinutes(10),
            ),
            ProtectionFlow.PASSKEY_LOGIN_OPTIONS to ProtectionFlowPolicy(
                requestLimit = 15,
                requestWindow = Duration.ofMinutes(5),
            ),
            ProtectionFlow.PASSKEY_LOGIN_VERIFY to ProtectionFlowPolicy(
                requestLimit = 20,
                requestWindow = Duration.ofMinutes(5),
            ),
            ProtectionFlow.VERIFY_EMAIL_CHANGE to ProtectionFlowPolicy(
                requestLimit = 10,
                requestWindow = Duration.ofMinutes(10),
            ),
        )
    }
}
