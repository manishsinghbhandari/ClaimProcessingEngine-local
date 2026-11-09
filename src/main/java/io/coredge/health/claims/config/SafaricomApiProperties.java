package io.coredge.health.claims.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "safaricom.api")
public class SafaricomApiProperties {
    private String baseUrl;
    private String authEndpoint;
    private String statsEndpoint;
    private String clientId;
    private String clientSecret;
}