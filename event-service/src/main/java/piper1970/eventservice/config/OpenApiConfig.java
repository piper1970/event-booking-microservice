package piper1970.eventservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Config for Swagger/OpenApi for event-service, setting up OAuth2 security and general info
 */
@OpenAPIDefinition(
    servers = {
        @Server(url = "${api.gateway.url: http://localhost:8080}",
            description = "API Gateway")
    },
    info = @Info(
        title = "Event Service",
        version = "1.0",
        description = "Restful service for creating and accessing events"
    ),
    security = {@SecurityRequirement(name = "Authorization")}
)
@SecurityScheme(
    name = "Authorization",
    type = SecuritySchemeType.OAUTH2,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    flows = @OAuthFlows(authorizationCode = @OAuthFlow(
        authorizationUrl = "${open-api.oauth2.resourceserver.auth.url}",
        tokenUrl = "${open-api.oauth2.resourceserver.token.url}",
        scopes = {
            @OAuthScope(name = "openid", description = "openid scope")
        }
    ))
)
public class OpenApiConfig {

}
