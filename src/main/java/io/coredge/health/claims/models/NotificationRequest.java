package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private String claimId;
    private String audience;
    private String provider;
    private String action;
    private String payload;
    private String dedupKey;
    private int retryCount;

}
