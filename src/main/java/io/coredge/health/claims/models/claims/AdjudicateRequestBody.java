package io.coredge.health.claims.models.claims;

import io.coredge.health.claims.dto.InterventionDetails;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AdjudicateRequestBody {
    private String notes;
    private ExtensionStateCodeEnum action;
    private String code;
    private String description;
    private List<InterventionDetails> interventions;
}