package io.coredge.health.claims.models.AI;

import io.coredge.health.claims.enums.Decision;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DecisionDetail {
    private Decision decision;
    private List<String> remarks;
}
