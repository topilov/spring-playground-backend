package me.topilov.springplayground.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityScheme(
    name = "sessionCookie",
    type = SecuritySchemeType.APIKEY,
    `in` = SecuritySchemeIn.COOKIE,
    paramName = "JSESSIONID",
    description = "Session cookie returned by POST /api/auth/login and sent on protected requests.",
)
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .components(Components())
        .info(
            Info()
                .title("Spring Playground Backend API")
                .version("0.0.1-SNAPSHOT")
                .description(
                    "Session-based auth/profile/public HTTP API. " +
                        "This OpenAPI schema is the machine-readable contract for frontend integration. " +
                        "Browser clients should send credentialed requests so the JSESSIONID session cookie is stored and resent.",
                ),
        )
}
