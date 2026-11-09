package io.coredge.health.claims.models.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary response DTO showing all counties assigned to a user.
 * Used for displaying user's county assignments in a consolidated view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCountySummaryResponse {
    
    private String username;
    private List<CountyInfo> assignedCounties;
    private Boolean hasUnrestrictedAccess; // true if no counties assigned (access to all)
    
    /**
     * Nested DTO representing county information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountyInfo {
        private Long countyId;
        private String countyName;
        private String countyCode;
    }
}
