package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.AuthType;
import io.swagger.v3.oas.annotations.Webhook;
import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import java.util.List;

@Configuration
@Data
@Builder
public class ClaimResponseWebhooks {

    private List<Webhook> webhook;
    @Data
    @Builder
    public static class Webhook {
        private String consumer;
        private String url;
        private String apiKey;
        private String secret;
        private AuthType authType;
        private String callbackType;
    }
}