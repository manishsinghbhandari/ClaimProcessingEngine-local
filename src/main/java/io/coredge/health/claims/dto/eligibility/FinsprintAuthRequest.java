package io.coredge.health.claims.dto.eligibility;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Finsprint authentication
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinsprintAuthRequest {
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("password")
    private String password;
}
