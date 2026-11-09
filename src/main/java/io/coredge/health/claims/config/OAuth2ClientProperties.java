package io.coredge.health.claims.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuth2ClientProperties {
    private String baseUrl;
    private String authEndpoint;
    private String clientId;
    private String clientSecret;
}