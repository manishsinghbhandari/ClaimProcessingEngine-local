package io.coredge.health.claims.models.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for returning counties with identifiers for mapping assignments
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountyOptionResponse {
    private Long id;
    private String name;
    private String code;
}
