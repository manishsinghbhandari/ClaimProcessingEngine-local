package io.coredge.health.claims.utils;

import io.coredge.health.claims.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.stereotype.Service;
import java.util.Map;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@Slf4j
@Service
public class ReactiveClaimAdjudicationService {
    
    private final RedisIdempotencyService idempotencyService;
    private final WorkflowService wfService;
    
    public ReactiveClaimAdjudicationService(RedisIdempotencyService idempotencyService, WorkflowService wfService) {
        this.idempotencyService = idempotencyService;
        this.wfService = wfService;
    }

    /**
     * Trigger an auto-adjudication process for a claim and added redis-based idempotency check.
     * @param resourceType
     * @param workflowParams
     */
    public void triggerAutoAdjudication(Claim.Use resourceType, Map<String, Object> workflowParams) {
        String claimId = (String) workflowParams.get(VAR_CLAIM_ID);
        String idempotencyKey = "auto_adj:" + claimId;
        log.info("🎯 Starting auto-adjudication for claim: {}", claimId);
        // Simple SET NX check - returns true only for first request.
        if (!idempotencyService.shouldProcessRequest(idempotencyKey)) {
            log.warn("🚫 DUPLICATE auto-adjudication prevented - Claim ID: {}", claimId);
            return;
        }
        // Proceed with business logic - this is the first and only request
        wfService.startBusinessProcess(resourceType, workflowParams);
        log.info("✅ Auto-adjudication triggered successfully - Claim ID: {}", claimId);
    }

}