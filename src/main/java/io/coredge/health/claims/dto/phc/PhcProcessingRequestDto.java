package io.coredge.health.claims.dto.phc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic request DTO for PHC processing actions
 * Used for CALCULATION, APPROVAL, and SEND_TO_ERP actions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcProcessingRequestDto {
    
    @NotBlank(message = "monthName is required")
    @Pattern(regexp = "^\\d{6}$|^[A-Za-z]{3}\\s\\d{4}$", 
             message = "monthName must be in YYYYMM format (e.g., 202511) or Mon YYYY format (e.g., Nov 2025)")
    private String monthName;
}
