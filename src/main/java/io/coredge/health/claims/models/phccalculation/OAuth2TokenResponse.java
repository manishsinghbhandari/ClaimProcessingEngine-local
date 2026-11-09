package io.coredge.health.claims.models.phccalculation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuth2TokenResponse(@JsonProperty("access_token") String accessToken,
                                  @JsonProperty("expires_in") String expiresIn) {
}