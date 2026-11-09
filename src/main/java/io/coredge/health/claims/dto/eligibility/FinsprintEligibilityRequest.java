package io.coredge.health.claims.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Finsprint eligibility validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinsprintEligibilityRequest {
    
    @JsonProperty("requestIdType")
    private String requestIdType;
    
    @JsonProperty("requestIdNumber")
    private String requestIdNumber;
}
