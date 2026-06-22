package com.postwerk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 configuration for Swagger UI and API documentation.
 *
 * <p>Configures the API metadata (title, version, description) and JWT Bearer
 * authentication scheme so that authenticated endpoints can be tested directly
 * from the Swagger UI at {@code /swagger-ui.html}.</p>
 *
 * @since 1.0
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI postwerkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Postwerk API")
                        .version("1.0")
                        .description("REST API for the Postwerk email automation platform. "
                                + "Provides endpoints for authentication, email account management, "
                                + "email operations, category/filter/template CRUD, automation workflows, "
                                + "AI assistant chat, and audit logging.")
                        .contact(new Contact().name("Postwerk Team")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT access token here")));
    }
}
