package io.coredge.health.claims.service.properties;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GlobalPropertyResponse {
    private Long id;
    private String key;
    private String value;
    private boolean status;
}
