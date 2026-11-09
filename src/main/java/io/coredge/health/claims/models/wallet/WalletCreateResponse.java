package io.coredge.health.claims.models.wallet;

import lombok.Data;
import java.util.List;

@Data
public class WalletCreateResponse {
    private String message;
    private String userId;
    private String mnemonic;
    private List<String> addresses;
}