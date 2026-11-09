package io.coredge.health.claims.rest;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.EvaluateDecisionResponse;
import io.camunda.zeebe.client.api.response.EvaluatedDecision;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionOutput;
import io.camunda.zeebe.client.api.response.MatchedDecisionRule;
import io.coredge.health.claims.audit.ClaimAuditReprocessService;
import io.coredge.health.claims.dto.ClaimStatusUpdateResult;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.models.ErpReprocessListClaimsRequest;
import io.coredge.health.claims.service.DMNService;
import io.coredge.health.claims.service.ErpCallBackService;
import io.coredge.health.claims.service.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.coredge.health.claims.constants.Constants.EndPoints.*;

@RestController
@RequestMapping(WF_PATH)
@Slf4j
public class WorkflowController {

    @Autowired
    private ZeebeClient zeebe;
    
    @Autowired
    private ErpCallBackService erpClaimsCallBack;

    @Autowired
    private DMNService dmnService;

    private record DecisionRecord(boolean isAutoApproved, Map<String, String> ruleMap) {
    }

    @GetMapping(WF_GET_CLAIM_RULES)
    public ResponseEntity<Map<String, List<Map<String, String>>>> getClaimRules() {
        return ResponseEntity.ok().body(dmnService.getClaimDecisionRules());
    }

    @GetMapping(WF_GET_PREAUTH_RULES)
    public ResponseEntity<Map<String, List<Map<String, String>>>> claimResponse() {
        return ResponseEntity.ok().body(dmnService.getPreAuthDecisionRules());
    }

    @PostMapping(WF_ERP_CALL_BACK_MAPPING)
    public ResponseEntity<String> claimResponse(@RequestBody String erpResponse) {
        erpClaimsCallBack.erpClaimsCallBack(erpResponse);
        return ResponseEntity.accepted().body("Callback received successfully");
    }

    @PostMapping("/paidStatus/upload")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        erpClaimsCallBack.processCsv(file);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/erp/reprocess")
    public ResponseEntity<String> erpBackReProcess(@RequestBody ErpReprocessListClaimsRequest request) {
        if(request.getClaimIDs().isEmpty()) {
            throw  new BadRequestException("Claim IDs is empty");
        }
        erpClaimsCallBack.reProcessErpRequestClaimIds(request);
        return ResponseEntity.ok("Accepted");
    }


    @PostMapping("/erpFallBack/reprocess")
    public ResponseEntity<String> erpFallBackReProcess(@RequestBody List<String> claimIDs) {
        erpClaimsCallBack.processErpClaimIds(claimIDs, true, true);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/erpFallBack/prepareOnly")
    public ResponseEntity<String> erpFallBackPrepareOnly(@RequestBody List<String> claimIDs) {
        log.info("Received Request For Batch update ERP requests , Size : {}", claimIDs.size());
        erpClaimsCallBack.processErpClaimIds(claimIDs, true, false);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/erpFallBack/batchPush")
    public ResponseEntity<String> erpPush(@RequestBody List<String> claimIDs) {
        log.info("Received Request For Batch Send Claims to ERP, Size : {}", claimIDs.size());
        erpClaimsCallBack.processErpClaimIds(claimIDs, false, true);
        return ResponseEntity.ok("Accepted");
    }

    @PostMapping("/evaluateInterventions")
    public List<Map<String, Object>> evaluateInterventions(@RequestBody Map<String, Object> variables) {
        String decisionDefinitionId = (String) variables.get("decisionDefinitionId");
        List<Map<String, Object>> interventions =
                (List<Map<String, Object>>) variables.get("variables");
        Map<String, Boolean> results = new HashMap<String, Boolean>();
        for (Map<String, Object> intervention : interventions) {
            String interventionCode = (String) intervention.get("code");
            DecisionRecord decisionRecord = validateDecision(intervention, decisionDefinitionId);
            intervention.put("Result", decisionRecord.isAutoApproved);
            intervention.put("Rule", decisionRecord.ruleMap);
            if (decisionRecord.ruleMap == null || decisionRecord.ruleMap.isEmpty()) {
                log.warn("SHA Code {} not found.", interventionCode);
                intervention.put("Rule", "SHA Code " + interventionCode + " not found");
            }
        }
        return interventions;
    }

    private DecisionRecord validateDecision(Map<String, Object> variables, String decisionId) {
        EvaluateDecisionResponse evalutionResponse;
        boolean isAutoApproved = false;
        Map<String, String> decisionRuleJson = new HashMap<String, String>();
        Claim.Use claimUse = decisionId.contains("auth") ? Claim.Use.PREAUTHORIZATION : Claim.Use.CLAIM;
        log.info("Evaluating Automation rule for {}", decisionId);
        log.info("With variables {}", variables);
        String interventionCode = (String) variables.get("code");
        evalutionResponse =
                zeebe
                        .newEvaluateDecisionCommand()
                        .decisionId(decisionId)
                        .variables(variables)
                        .send()
                        .join();

        Map<String, Object> filters = DMNService.filtersData(variables);
        for (EvaluatedDecision decision : evalutionResponse.getEvaluatedDecisions()) {
            for (MatchedDecisionRule rule : decision.getMatchedRules()) {
                for (EvaluatedDecisionOutput output : rule.getEvaluatedOutputs()) {
                    log.info("Decision Output {} = {} with RuleId {}", output.getOutputName(),
                            output.getOutputValue(), rule.getRuleIndex());
                    isAutoApproved = Boolean.parseBoolean(output.getOutputValue());
                    decisionRuleJson = dmnService.getRuleJsonById(rule.getRuleId(), claimUse, filters);
                }
            }
        }
        if (decisionRuleJson != null && decisionRuleJson.isEmpty()) {
            decisionRuleJson = dmnService.getRuleJsonByCode(interventionCode, claimUse, filters);
        }
        return new DecisionRecord(isAutoApproved, decisionRuleJson);
    }

    @PostMapping("/paidStatus/update")
    public ResponseEntity<ClaimStatusUpdateResult> updatePaidStatus(
            @RequestBody List<UUID> claimNumbers) {
        log.info("Received {} claim numbers for status update", claimNumbers.size());
        return ResponseEntity.ok(erpClaimsCallBack.updateStatusToSentForPaymentProcessing(claimNumbers));
    }
}
