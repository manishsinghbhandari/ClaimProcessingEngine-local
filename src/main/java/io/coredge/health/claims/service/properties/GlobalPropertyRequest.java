package io.coredge.health.claims.service.properties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GlobalPropertyRequest {
    private String key;
    private String value;
}
