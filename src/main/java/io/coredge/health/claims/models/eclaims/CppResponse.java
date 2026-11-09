package io.coredge.health.claims.models.eclaims;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@ToString
public class CppResponse {
    private String claimId;
    private String claimReferenceId;
    private String status;
    private String timestamp;
    private String responseCode;
    private String responseMessage;
    private Object data;
    private List<String> errors;
}