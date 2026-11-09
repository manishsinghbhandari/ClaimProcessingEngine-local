package io.coredge.health.claims.models.users;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserConsumerResponse {
    private String consumer;
    private String username;
    private String token;
    private String message;
    private boolean callback;
}
