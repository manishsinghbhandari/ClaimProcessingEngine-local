package io.coredge.health.claims.models.users;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WalletUpdateResponse {
    private String message;
    private String username;
    private String userId;
    private boolean walletUpdated;
    private String walletId;
    private String mnemonic; // Consider security implications - may want to exclude in production
    private List<String> addresses;
    private List<String> errors;
    private String status; // SUCCESS, FAILED, ALREADY_EXISTS
    private long processingTimeMs; // For batch operations
}