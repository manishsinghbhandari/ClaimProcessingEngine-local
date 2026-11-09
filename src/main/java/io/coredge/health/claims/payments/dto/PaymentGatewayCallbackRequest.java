package io.coredge.health.claims.payments.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayCallbackRequest {
    private String externalReference;
    private String traceId;
    private BigDecimal amount;
    private String purchaseOrderNumber;
    private String invoiceNumber;
    private String facilityIdentifier;
    private String facilityName;
    private String batchId;
    private String status;
    private String validationStatus;
    private String processingRemarks;
    private Integer totalFailedClaims;
    private java.util.List<FailedClaim> failedClaims;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedClaim {
        private String claimId;
        private String errorReason;
        private String errorDetails;
    }
}
