package io.coredge.health.claims.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

  @Value("${keycloak.auth-server-url}")
  private String authServerUrl;

  @Value("${keycloak.realm}")
  private String realm;

  @Value("${keycloak.client-id}")
  private String clientId;

  @Value("${keycloak.client-secret}")
  private String clientSecret;

  @Bean
  public Keycloak keycloak() {
    // Use client credentials flow (no need for username/password)
    return KeycloakBuilder.builder()
        .serverUrl(authServerUrl)
        .realm(realm)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .grantType(
            OAuth2Constants.CLIENT_CREDENTIALS) // Ensure you're using client credentials flow
        .build();
  }
}
