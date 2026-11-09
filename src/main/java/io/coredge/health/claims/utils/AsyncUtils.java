package io.coredge.health.claims.utils;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.coredge.health.claims.context.HealthIdContextHolder;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

public class AsyncUtils {
    
    public static <T> T withCamundaContext(ActivatedJob job, Supplier<T> supplier) {
        try {
            Map<String, Object> params = job.getVariablesAsMap();
            String traceId = params.get(VAR_TRACE_ID) != null
                    ? (String) params.get(VAR_TRACE_ID)
                    : UUID.randomUUID().toString();
            String claimId = (String) params.get(VAR_CLAIM_ID);
            String claimType = params.get(VAR_CLAIM_TYPE) != null
                    ? (String) params.get(VAR_CLAIM_TYPE)
                    : null;

            if (traceId != null) {
                MDC.put(VAR_TRACE_ID, traceId);
            }
            if (claimId != null) {
                MDC.put(VAR_CLAIM_ID, claimId);
            }
            if (claimType != null) {
                MDC.put(VAR_CLAIM_TYPE, claimType);
            }
            HealthIdContextHolder.uniqueRequestId(traceId);

            return supplier.get();
        } finally {
            MDC.clear();
            HealthIdContextHolder.remove();
        }
    }

    public static void withCamundaContext(ActivatedJob job, Runnable runnable) {
        try {
            Map<String, Object> params = job.getVariablesAsMap();
            String traceId = params.get(VAR_TRACE_ID) != null
                    ? (String) params.get(VAR_TRACE_ID)
                    : UUID.randomUUID().toString();
            String claimId = (String) params.get(VAR_CLAIM_ID);
            String claimType = params.get(VAR_CLAIM_TYPE) != null
                    ? (String) params.get(VAR_CLAIM_TYPE)
                    : null;
            String userId = params.get(VAR_USER_ID) != null
                    ? (String) params.get(VAR_USER_ID)
                    : null;


            if (traceId != null) {
                MDC.put(VAR_TRACE_ID, traceId);
            }
            if (claimId != null) {
                MDC.put(VAR_CLAIM_ID, claimId);
            }
            if (claimType != null) {
                MDC.put(VAR_CLAIM_TYPE, claimType);
            }

            if (userId != null){
                MDC.put(VAR_USER_ID, HealthIdContextHolder.userId());
            }

            HealthIdContextHolder.uniqueRequestId(traceId);

            runnable.run();
        } finally {
            MDC.clear();
            HealthIdContextHolder.remove();
        }
    }

}