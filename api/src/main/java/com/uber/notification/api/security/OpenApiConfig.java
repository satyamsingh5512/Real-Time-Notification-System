package com.uber.notification.api.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Makes JWT usable directly from Swagger UI: Authorize → paste
 * {@code Bearer <token from POST /api/v1/auth/login>}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Real-Time Notification System API",
                version = "v1",
                description = "Multi-channel notification platform: JWT + RBAC, per-user inbox, "
                        + "preferences, admin broadcast, internal scheduling, WebSocket live push."),
        servers = {@Server(url = "/", description = "Same origin (via nginx) or backend :8080")},
        security = {@SecurityRequirement(name = "bearerAuth")})
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
