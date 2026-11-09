package io.coredge.health.claims.models.users;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WalletUpdateRequest {
    private String username;
    private String walletType; // Optional: specify wallet type
}