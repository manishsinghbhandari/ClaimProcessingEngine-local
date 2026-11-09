package io.coredge.health.claims.models.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for user-county assignment operations.
 * Contains details about assigned counties for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCountyAssignmentResponse {
    
    private Long id;
    private String username;
    private Long countyId;
    private String countyName;
    private String countyCode;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
}
