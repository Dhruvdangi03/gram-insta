package com.instaclone.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /api/v1/swagger-ui.html. Register with POST /auth/register or /auth/login, then
 * click "Authorize" and paste the returned accessToken (no "Bearer " prefix needed) to exercise
 * every protected endpoint from the browser instead of curl.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI instaCloneOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Instagram Clone API")
                        .description("Phase 1 — core social graph: auth, profiles, posts, follows, feed, likes, comments.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
