package piper1970.bookingservice.config;

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

@OpenAPIDefinition(
    servers = {
        @Server(url = "http://localhost:8080",
            description = "API Gateway")
    },
    info = @Info(
        title = "Booking Service",
        version = "1.0",
        description = "Restful service for booking requests to events"
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
        authorizationUrl = "${oauth2.provider.auth-uri}",
        tokenUrl = "${oauth2.provider.token-uri}",
        scopes = {
            @OAuthScope(name = "openid", description = "openid scope")
        }
    ))
)
public class OpenApiConfig {

}
