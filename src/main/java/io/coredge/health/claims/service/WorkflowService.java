package io.coredge.health.claims.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.*;

import java.util.Map;

import io.coredge.health.claims.exception.app.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@Service
@Slf4j
public class WorkflowService {

    @Value("${CLAIM_AUTOMATION_DECISION_ID:ShaClaimApprovalDecisionV2}")
    String CLAIM_AUTOMATION_DECISION_ID;

    @Value("${PREAUTH_AUTOMATION_DECISION_ID:ShaPreAuthApprovalDecisionV1}")
    String PREAUTH_AUTOMATION_DECISION_ID;

    @Value("${PREAUTH_AUTOMATION_DECISION_ID:ShaCivilPreAuthApprovalDecisionV1}")
    String CIVIL_PREAUTH_AUTOMATION_DECISION_ID;

    @Value("${CLAIM_PROCESS_ID:civilClaimProcess}")
    private String claimProcessId;

    @Value("${PRE_AUTH_PROCESS_ID:preAuthProcess}")
    private String preAuthProcessId;

    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private TaskListService taskService;

    @Autowired
    private DMNService dmnService;

    public record AdjudicationRecord(boolean isAutoApproved, Map<String, String> ruleMap) {
    }


    /**
     * AutoAdjudicate a given
     *
     * @param adjudicationInputs
     * @param claim
     * @return
     */
    public AdjudicationRecord autoAdjudicateIntervention(Map<String, Object> adjudicationInputs, Claim claim) {
        EvaluateDecisionResponse evaluationResponse;
        boolean isAutoApproved = false;
        AdjudicationRecord adjudicationRecord = null;
        log.info("AutoAdjudication:: Evaluating  rules for claimId: {}  and Intervention Code: {} and Type: {}"
                , claim.getIdPart(), adjudicationInputs.get("code"), claim.getUse().toCode());
        String businessDecisionRules = CLAIM_AUTOMATION_DECISION_ID;
        if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
            businessDecisionRules = PREAUTH_AUTOMATION_DECISION_ID;
        }

        log.info("Using DecisionId: {} to evaluate {} with variables {}", businessDecisionRules, claim.getIdPart(),
                adjudicationInputs);
        evaluationResponse =
                zeebe
                        .newEvaluateDecisionCommand()
                        .decisionId(businessDecisionRules)
                        .variables(adjudicationInputs)
                        .send()
                        .join();

        Map<String, Object> filters = DMNService.filtersData(adjudicationInputs);
        for (EvaluatedDecision decision : evaluationResponse.getEvaluatedDecisions()) {
            for (MatchedDecisionRule rule : decision.getMatchedRules()) {
                for (EvaluatedDecisionOutput output : rule.getEvaluatedOutputs()) {
                    log.info("Decision Output {} = {} from RuleId: {} for variables:{}",
                            output.getOutputName(), output.getOutputValue(),
                            rule.getRuleIndex(), adjudicationInputs);
                    isAutoApproved = Boolean.parseBoolean(output.getOutputValue());
                    Map<String, String> decisionRuleJson = dmnService.getRuleJsonById(rule.getRuleId(), claim.getUse(), filters);
                    adjudicationRecord = new AdjudicationRecord(isAutoApproved, decisionRuleJson);
                }
            }
        }
        if (evaluationResponse.getEvaluatedDecisions().isEmpty()) {
            log.warn("No matching Intervention code found in Decision Table {} for " +
                            "Intervention Code {} used in Claim:{} ",
                    businessDecisionRules, adjudicationInputs.get("code"), claim.getIdPart());
            adjudicationRecord = new AdjudicationRecord(isAutoApproved, null);
        } else if (evaluationResponse.getDecisionOutput() == null || evaluationResponse.getDecisionOutput().equals("null")) {
            // Note: There were rules for the same but didn't match all of it.
            Map<String, String> ruleJson = dmnService.getRuleJsonByCode((String) adjudicationInputs.get(VAR_SHA_CODE),
                    claim.getUse(), filters);
            adjudicationRecord = new AdjudicationRecord(isAutoApproved, ruleJson);
        }

        return adjudicationRecord;
    }

    /**
     * Regenerate new business process by cancelling existing process.
     *
     * @param existingProcessKey
     */
    public void regenBusinessProcess(Long existingProcessKey) {

        zeebe.newCancelInstanceCommand(existingProcessKey)
            .send()
            .whenComplete((response, throwable) -> {

                if (throwable != null) {

                    log.error("Failed to cancel process instance: {}",
                            existingProcessKey, throwable);
                    throw new BadRequestException("Failed to cancel process instance: "+
                            existingProcessKey ,throwable);
                }
                });
    }

    /**
     * Start a Claim Workflow Process.
     *
     * @param resourceType
     * @param variables
     * @return
     */
    @Async("virtualThreadExecutor")
    public CompletableFuture<Void> startBusinessProcess(Claim.Use resourceType, Map<String, Object> variables) {
        String businessProcessId = switch (resourceType) {
            case CLAIM -> "civilClaimProcess";
            case PREAUTHORIZATION -> "civilPreAuthProcess";
            default -> throw new RuntimeException("Invalid Resource Type to run Business process.");
        };
        log.info("Starting process `{}` with variables: {}", businessProcessId, variables);
        CompletableFuture<Void> future = new CompletableFuture<>();
        zeebe.newCreateInstanceCommand()
                .bpmnProcessId(businessProcessId)
                .latestVersion()
                .variables(variables)
                .send()
                .thenAccept(processInstance -> {
                    log.info("Zeebe process created: {} with variables: {}", processInstance, variables);
                    future.complete(null); // Mark future as completed successfully
                })
                .exceptionally(ex -> {
                    log.warn("Zeebe workflow creation failed: {} variables: {}", ex.getMessage(), variables, ex);
                    future.completeExceptionally(ex); // Mark future as completed exceptionally
                    return null;
                });

        return future;
    }

    /**
     * This method will save the Activated Job Variables Data in Db.
     *
     * @param processInstanceKey
     * @param claimId
     * @param claimResponseId
     */
    public void saveWorkFlowData(long processInstanceKey, String claimId, String claimResponseId) {
        taskService.saveNewWfData(processInstanceKey, claimId, claimResponseId);
    }

    public void updateWorkFlowData(long jobKey, String claimId, long processInstanceKey) {
      taskService.updateWfData(claimId, jobKey, processInstanceKey);
    }
}
