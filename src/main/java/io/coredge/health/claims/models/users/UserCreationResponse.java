package io.coredge.health.claims.models.users;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserCreationResponse {
    private String message;
    private String username;
    private String userId;
    private boolean walletCreated;
    private String walletId;
    private String mnemonic; // Consider security implications - may want to exclude in production
    private List<String> addresses;
    private List<String> errors;
    private String status; // SUCCESS, PARTIAL_SUCCESS, FAILED
}