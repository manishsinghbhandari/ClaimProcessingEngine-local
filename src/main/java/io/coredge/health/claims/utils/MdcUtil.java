package io.coredge.health.claims.utils;

import io.coredge.health.claims.context.HealthIdContextHolder;
import org.slf4j.MDC;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_CLAIM_ID;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_CLAIM_TYPE;

public class MdcUtil {

    public static void syncWithContextHolder() {
        put("userId", HealthIdContextHolder.userId());
        put(VAR_CLAIM_ID, HealthIdContextHolder.claimId());
        put(VAR_CLAIM_TYPE, HealthIdContextHolder.claimType());
        put("patientId", HealthIdContextHolder.patientId());
    }

    private static void put(String key, String value) {
        if (value != null && !value.isEmpty()) {
            MDC.put(key, value);
        }
    }
}
