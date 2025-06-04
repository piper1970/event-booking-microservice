package piper1970.eventservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.OAuthScope;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
    servers = {
        @Server(url = "http://localhost:8081")
    },
    info = @Info(
        title = "Event Service",
        version = "1.0",
        description = "Restful service for creating and accessing events"
    )
)
@SecurityScheme(
    name = "security_oauth2",
    type = SecuritySchemeType.OAUTH2,
    flows = @OAuthFlows(authorizationCode = @OAuthFlow(
        authorizationUrl = "${oauth2.provider.auth-uri}",
        tokenUrl = "${oauth2.provider.token-uri}",
        scopes = {
            @OAuthScope(name = "openid", description = "openid scope")
        }
    ))
)
public class OpenApiConfig {}
