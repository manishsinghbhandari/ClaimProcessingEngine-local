package io.coredge.health.claims.models.assignments;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReassignTask {

    private String claimId;
    private String reason;
    private String userId;
}
