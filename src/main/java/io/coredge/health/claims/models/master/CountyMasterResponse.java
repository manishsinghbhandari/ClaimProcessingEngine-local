package io.coredge.health.claims.models.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning county information from Provider entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountyMasterResponse {
    private String name;
}
