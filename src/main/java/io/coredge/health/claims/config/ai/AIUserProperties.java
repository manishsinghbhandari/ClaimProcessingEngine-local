package io.coredge.health.claims.config.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "system.users.ai")
@Getter
@Setter
public class AIUserProperties {
    private String username;
}