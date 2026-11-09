package io.coredge.health.claims.models;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class PreAuthCancelAuditLog {
    private String claimId;
    private String requester;
    private String requestBodyJson;
    private String response;
    private String errorMessage;
    private String status;
}
