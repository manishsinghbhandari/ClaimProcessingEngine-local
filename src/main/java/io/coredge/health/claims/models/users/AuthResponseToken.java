package io.coredge.health.claims.models.users;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponseToken {

    @JsonProperty("access_token")
    protected String token;

    @JsonProperty("expires_in")
    protected long expiresIn;

    @JsonProperty("refresh_expires_in")
    protected long refreshExpiresIn;

    @JsonProperty("token_type")
    protected String tokenType;

}
