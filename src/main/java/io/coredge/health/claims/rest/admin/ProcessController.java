package io.coredge.health.claims.rest.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.Gson;
import io.camunda.operate.model.ProcessInstance;
import io.camunda.operate.model.SearchResult;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.dto.operate.OperateProcessInstanceResponse;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.models.claims.AdjudicateRequestBody;
import io.coredge.health.claims.models.claims.ProviderPreAuthCancelRequestBody;
import io.coredge.health.claims.service.*;
import io.coredge.health.claims.service.operate.OperateProcess;
import io.coredge.health.claims.worker.ClaimProcessService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.PROCESS_API_PATH;
import static io.coredge.health.claims.constants.Constants.AdminEndPoints.PROCESS_LIST_API_PATH;
import static io.coredge.health.claims.constants.Constants.EndPoints.SAVE_SUPPORTING_INFO_API_PATH;

@RestController
@RequestMapping(PROCESS_API_PATH)
@Slf4j
public class ProcessController {

  @Autowired private ZeebeClient zeebe;

  @Autowired private WorkflowService wfService;

  @Autowired private ClaimProcessService claimProcessService;

  @Autowired private BpmnTaskService bpmnService;

  @Autowired private DocumentsService documentsService;

  @Autowired private ClaimBundleService claimBundleService;

  @Autowired private ClaimResponseService claimResponseService;

  @Autowired
  private OperateProcess operateProcess;

  @PostMapping("/find")
  public void startProcessInstance(@RequestBody Map<String, Object> variables) {
    String taskId = bpmnService.findTaskByVariable("claimId", (String) variables.get("claimId"));
    log.info("ProcessId: " + taskId);
  }

  @GetMapping("/start-claim")
  public void startSchedulerTest() {
    claimProcessService.processClaims();
  }

  @GetMapping(PROCESS_LIST_API_PATH)
  public List<ProcessInstance> startProcessInstance() {
    return bpmnService.getProcessList();
  }

  @GetMapping("/instanceResultList")
  public SearchResult<ProcessInstance> startProcessResultInstance() {
    return bpmnService.getProcessResultList();
  }

  @PostMapping("/restartProcess/{claimId}")
  public ResponseEntity<String> startProcessResultInstance(@PathVariable String claimId) {
     bpmnService.renewProcessForClaim(claimId);
     return ResponseEntity.accepted().build();
  }

  @PostMapping(SAVE_SUPPORTING_INFO_API_PATH)
  public ResponseEntity<?> postDocumentsByClaimObject(@RequestBody String claimObject)
          throws JsonProcessingException {
    return documentsService.saveDocuments(claimObject);
  }

  @PostMapping("/startProcess")
  public ResponseEntity<String> startNewProcessResultInstance(@RequestBody List<String> claimIds) {
    bpmnService.newProcessesForClaims(claimIds);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/clearClaimResponses")
  public ResponseEntity<String> clearClaimResponses() {
    claimBundleService.clearClaimResponses();
    return ResponseEntity.accepted().build();
  }

//  @RequiresPermission(resource = ResourceEnum.PREAUTHORIZATIONS, action = {ActionEnum.VIEW, ActionEnum.EDIT, ActionEnum.RECALL})
  @PostMapping("/recallPreAuth/{claimId}")
  public ResponseEntity<?> recallPreAuth(@PathVariable String claimId, @RequestBody AdjudicateRequestBody requestBody) {
    return getResponseEntity(claimResponseService.recallPreAuth(claimId, requestBody), requestBody);
  }

  @PostMapping(value = "provider/recallPreAuth", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> recallPreAuthByProvider(@Validated @RequestBody ProviderPreAuthCancelRequestBody requestBody) {
    return getResponseEntity(claimResponseService.recallPreAuthByProvider(requestBody), requestBody);
  }

  @NotNull
  private ResponseEntity<?> getResponseEntity(List<ClaimResponse.NoteComponent> noteComponents, Object requestBody) {
    try {
        return ResponseEntity.ok(new Gson().toJson(noteComponents));
    } catch (DataBadRequestException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getCode().description());
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getMessage());
    }
  }

  @PostMapping("/cancel-process-wf")
  public ResponseEntity<String> cancellingWorkFlows(@RequestBody List<String> claimIds) {
    bpmnService.cancelProcessInstance(claimIds);
    return ResponseEntity.accepted().build();
  }

    @GetMapping("/active/{claimId}")
    public ResponseEntity<OperateProcessInstanceResponse> checkActiveInstance(@PathVariable String claimId) {
        OperateProcessInstanceResponse operateProcessInstanceResponse = operateProcess.getActiveInstances(claimId);
        return ResponseEntity.ok(operateProcessInstanceResponse);
    }

}
