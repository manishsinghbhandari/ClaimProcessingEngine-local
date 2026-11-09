package io.coredge.health.claims.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Finsprint authentication
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinsprintAuthResponse {
    
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("refreshToken")
    private String refreshToken;
}
