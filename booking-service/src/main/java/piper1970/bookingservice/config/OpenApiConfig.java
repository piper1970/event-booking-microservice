package piper1970.bookingservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityScheme.In;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String schemaName = "security_oauth2";

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Booking Service")
            .version("1.0")
            .description("Restful service for booking requests to events")
        ).addSecurityItem(new SecurityRequirement()
            .addList(schemaName))
        .components(new Components()
            .addSecuritySchemes(
                schemaName, new SecurityScheme()
                    .name(schemaName)
                    .type(SecurityScheme.Type.HTTP)
                    .bearerFormat("JWT")
                    .in(In.COOKIE)
                    .scheme("bearer")
            )
        );
  }
}
