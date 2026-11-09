package io.coredge.health.claims.mapper.intervention;

import io.coredge.health.claims.enums.intervention.RuleType;

import static io.coredge.health.claims.constants.intervention.InterventionConstants.*;
import static io.coredge.health.claims.constants.intervention.InterventionConstants.EYE_CARE;

public class RuleMapping {

    private RuleMapping () {}

    public static RuleType mapToRuleType(String key) {
        if (SURGICAL_CATEGORIES.contains(key)) {
            return RuleType.SURGICAL;
        }
        return switch (key) {
            case RENAL_CARE -> RuleType.RENAL_CARE;
            case PER_DIEM -> RuleType.PER_DIEM;
            case INPATIENT -> RuleType.INPATIENT;
            case PERITONEAL_DIALYSIS -> RuleType.PERITONEAL_DIALYSIS;
            case ACUTE_DIALYSIS -> RuleType.ACUTE_DIALYSIS;
            case REHAB -> RuleType.REHAB;
            case EYE_CARE -> RuleType.EYE_CARE;
            default -> null;
        };
    }
}
