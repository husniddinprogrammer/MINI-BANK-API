package com.banking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Springdoc OpenAPI 2.x for Swagger UI with Bearer token authentication.
 *
 * <p>The {@code BearerAuth} security scheme registered here enables the Swagger UI
 * "Authorize" button, allowing developers to supply a JWT and test protected endpoints
 * directly in the browser without external tooling.
 *
 * @author Mini Banking API
 * @version 1.0
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "BearerAuth";

    /**
     * Defines the OpenAPI specification including security scheme and API metadata.
     *
     * @return configured {@link OpenAPI} bean
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH, bearerSecurityScheme())
            );
    }

    private Info apiInfo() {
        return new Info()
            .title("Mini Banking API")
            .description("Production-grade REST API for a mini banking system. " +
                "Demonstrates Spring Security 6, JWT, transactional integrity, " +
                "and banking business rules.")
            .version("1.0.0")
            .contact(new Contact()
                .name("Banking API Team")
                .email("api@banking.com")
            )
            .license(new License().name("MIT"));
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Provide a valid JWT access token. Obtain one via POST /api/v1/auth/login");
    }
}
