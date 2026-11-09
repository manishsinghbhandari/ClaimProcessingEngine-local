package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcProviderDto {
    private Long id;
    private String name;
    private String level;
}
