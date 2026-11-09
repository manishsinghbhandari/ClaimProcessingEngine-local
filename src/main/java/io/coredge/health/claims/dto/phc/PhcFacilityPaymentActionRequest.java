package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcFacilityPaymentActionRequest {
    
    @Valid
    private List<FacilityPaymentItem> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacilityPaymentItem {
        @NotNull(message = "Facility payment ID is required")
        private Long id;
        
        private BigDecimal approvedAmount;
        
        @NotNull(message = "Action is required")
        private FacilityPaymentAction action;
        
        private String remarks;
    }
    
    public enum FacilityPaymentAction {
        APPROVE,
        REJECT,
        PENDING
    }
}
