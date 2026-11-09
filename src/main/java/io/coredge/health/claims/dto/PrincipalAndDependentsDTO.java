package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrincipalAndDependentsDTO {
    private SchemeMemberWithLimitsDTO principal;
    private List<SchemeMemberWithLimitsDTO> dependents;
}