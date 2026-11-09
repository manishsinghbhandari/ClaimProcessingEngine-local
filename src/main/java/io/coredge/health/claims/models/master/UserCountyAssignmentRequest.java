package io.coredge.health.claims.models.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for assigning counties to a user.
 * Supports batch assignment of multiple counties to a single user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCountyAssignmentRequest {
    
    @NotBlank(message = "Username is required")
    private String username;
    
    private List<Long> countyIds;
}
