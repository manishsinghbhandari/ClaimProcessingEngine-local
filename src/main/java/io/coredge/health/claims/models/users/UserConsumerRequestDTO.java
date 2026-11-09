package io.coredge.health.claims.models.users;

import io.coredge.health.claims.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class UserConsumerRequestDTO {
    @NotBlank(message = "Consumer cannot be blank")
    private String consumer;

    @NotBlank(message = "Username cannot be blank")
    private String username;
    private String password;

    @NotNull(message = "AuthType cannot be null")
    private AuthType authType;

    private String url;
    private boolean callback;  // Mark this true for Call back user entry
}
