package io.coredge.health.claims.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenAPIConfig {

  @Value("${openapi.url}")
  private String url;

  @Value("${server.servlet.context-path:/api/v1}")
  private String  servletContext;

  @Bean
  public OpenAPI myOpenAPI() {

    String urlFinal = url.concat(servletContext);
    Server devServer = new Server();
    devServer.setUrl(urlFinal);
    devServer.setDescription("Server URL");

    Contact contact = new Contact();
    contact.setEmail("rajeshk@coredge.io");
    contact.setName("DevelopmentTeams");
    contact.setUrl("https://www.apeiro-digital.com");

    License mitLicense = new License().name("Apeiro-digital License").url("https://www.apeiro-digital.com");

    Info info = new Info()
        .title("Claim Engine API")
        .version("1.0")
        .contact(contact)
        .description("Claim Processing API.").termsOfService("https://www.apeiro-digital.com")
        .license(mitLicense);


      SecuritySchame result = getSecuritySchame();

      return new OpenAPI()
              .components(new Components().addSecuritySchemes("BearerAuth", result.securityScheme()))
              .addSecurityItem(result.securityRequirement())
              .info(info)
              .servers(List.of(devServer));
  }

    private static @NotNull SecuritySchame getSecuritySchame() {
        // Define Security Scheme for Authorization Header
        SecurityScheme securityScheme = new SecurityScheme()
                .name("Authorization")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        // Create Security Requirement
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("BearerAuth");
        return new SecuritySchame(securityScheme, securityRequirement);
    }

    private record SecuritySchame(SecurityScheme securityScheme, SecurityRequirement securityRequirement) {
    }
}