package io.coredge.health.claims.service;

import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import io.camunda.operate.model.ProcessInstance;
import io.camunda.operate.model.SearchResult;
import io.camunda.operate.search.*;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.WorkFlow;
import io.coredge.health.claims.db.entity.projection.ClaimStatusProjection;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.WorkFlowRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_CLAIM_ID;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.VAR_CLAIM_RESPONSE_ID;

@Slf4j
@Service
public class BpmnTaskService {

  @Value("${camunda.client.tasklist.base-url:http://localhost:8082}")
  private String CAMUNDA_TASKLIST_BASE_URL;

  @Value("${CAMUNDA_API_KEY:}")
  private String CAMUNDA_API_KEY;

  @Autowired
  private ZeebeClient zeebe;

  @Autowired
  private CamundaOperateClient operateClient;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  InsuranceClaimsRepository insuranceClaimsRepository;

  @Autowired
  WorkFlowRepository workFlowRepository;

  @Autowired
  FhirService fhirService;

  @Autowired
  WorkflowService wfService;

  @Autowired
  CamundaService camundaService;

  @Autowired
  @Qualifier("virtualThreadExecutor")
  private Executor virtualThreadExecutor;


  // Find tasks by variable
  public String findTaskByVariable(String variableName, String variableValue) {
    String url =
        UriComponentsBuilder.fromHttpUrl(CAMUNDA_TASKLIST_BASE_URL + "/v1/tasks/search")
            .queryParam("variables", variableName + "=" + variableValue)
            .toUriString();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + CAMUNDA_API_KEY);
    headers.set("Content-Type", "application/json");

    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    return parseTaskIdFromResponse(response.getBody());
  }

  // Complete the task (approve it)
  public void completeTask(String taskId, boolean isApproved, String apiKey) {
    String url = CAMUNDA_TASKLIST_BASE_URL + "/" + taskId + "/complete";

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + apiKey);
    headers.set("Content-Type", "application/json");

    String completeBody = "{ \"variables\": { \"approved\": { \"value\": " + isApproved + " } } }";
    HttpEntity<String> entity = new HttpEntity<>(completeBody, headers);

    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
  }

  // Helper method to parse task ID from the response (Assuming JSON response)
  private String parseTaskIdFromResponse(String responseBody) {
    // Use a JSON parser like Jackson or Gson to extract taskId
    // Assuming the response contains a list of tasks [{...,"id":"task-id",...}]
    String taskId = null;
    // Parse taskId from JSON response
    return taskId;
  }

  public List<ProcessInstance> getProcessList(){
      try {
          SearchQuery searchAll = new SearchQuery.Builder().build();
          return operateClient.searchProcessInstances(searchAll);
      } catch (OperateException e) {
          throw new RuntimeException(e);
      }
  }

  public SearchResult<ProcessInstance> getProcessResultList(){
    try {
      ProcessInstanceFilter instanceFilter = ProcessInstanceFilter.builder()
              .bpmnProcessId("claimProcess")
              .build();
      SearchQuery instanceQuery = new SearchQuery.Builder()
              .filter(instanceFilter)
              .size(20)
              .sort(new Sort("state", SortOrder.ASC)).build();

      return operateClient.searchProcessInstanceResults(instanceQuery);
    } catch (OperateException e) {
      throw new RuntimeException(e);
    }
  }

  public ProcessInstance getProcessByVariable(String varName, String value){
    try {
      //ProcessInstanceFilter instanceFilter = ProcessInstanceFilter.builder().bpmnProcessId("claimProcess").
      SearchQuery searchAll = new SearchQuery.Builder().build();
      return operateClient.searchProcessInstances(searchAll).getFirst();
    } catch (OperateException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Regenerate new process and cancel existing process for claimId.
   * @param claimId
   */
  public void renewProcessForClaim(String claimId){
    try {
        Optional<List<InsuranceClaim>> iClaims = insuranceClaimsRepository.findByExternalId(claimId);
        if(iClaims.isEmpty() || iClaims.get().isEmpty()){
            throw new BadRequestException(String.format("Claim id : %s  not found", claimId));
        }
        InsuranceClaim iClaim = iClaims.get().getFirst();
        if(Constants.RESTRICT_REPROCESS_CLAIM_STATUS.contains(iClaim.getStatus())) {
            throw new BadRequestException(String.format("Claim with claimId : %s has been moved to restricted Status : %s ", claimId , iClaim.getStatus()));
        }
        Optional<List<WorkFlow>> workflows = workFlowRepository.findAllByClaimIdAndActiveIsTrue(claimId);
        if (workflows.isPresent() && !workflows.get().isEmpty()) {

            workflows.get().forEach(workFlow -> {
                log.info("Deleting old process with InstanceKey: {} for claimId: {}", workFlow.getProcessInstanceKey(), claimId);
                wfService.regenBusinessProcess(workFlow.getProcessInstanceKey());
                workFlow.setActive(false);
                workFlowRepository.save(workFlow);
            });
        }

      ClaimResponse claimResponse = fhirService.fetchClaimResponseByClaimId(claimId);

      Map<String, Object> workflowParams = new HashMap<String, Object>();
      workflowParams.put(VAR_CLAIM_ID, claimId);
      workflowParams.put(VAR_CLAIM_RESPONSE_ID, claimResponse.getIdPart());

      wfService.startBusinessProcess(Claim.Use.fromCode(iClaim.getUse().toLowerCase()), workflowParams);

    } catch (Exception e) {
      log.warn("Error while renewProcessForClaim for claimId {} {} {}", claimId, e , e.getMessage());
    }
  }

  private final BlockingQueue<InsuranceClaim> zeebeQueue = new LinkedBlockingQueue<>(64); // Match maxJobsActive

  /**
   * Starting new processes for claims asynchronously in parallel.
   * @param claimIds List of claim IDs to process.
   */
  public void newProcessesForClaims(List<String> claimIds) {
    log.info("Starting newProcessesForClaims for {} claims", claimIds.size());

    // 1. Batch fetch all claims first
    Map<String, InsuranceClaim> claimsById = insuranceClaimsRepository
            .findAllByExternalIdIn(claimIds)
            .stream()
            .collect(Collectors.toMap(InsuranceClaim::getExternalId, Function.identity()));

    // 2. Start workers (use virtualThreadExecutor instead of fixed pool)
    int zeebeConcurrency = 32; // still cap number of workers
    IntStream.range(0, zeebeConcurrency).forEach(i ->
            ((ExecutorService) virtualThreadExecutor).submit(() -> {
              while (!Thread.currentThread().isInterrupted()) {
                try {
                  InsuranceClaim claim = zeebeQueue.poll(100, TimeUnit.MILLISECONDS);
                  if (claim == null) break; // Exit when queue empties
                  camundaService.processClaim(claim);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
            })
    );

    // 3. Feed claims to queue (also with virtualThreadExecutor)
    claimIds.forEach(claimId ->
            ((ExecutorService) virtualThreadExecutor).submit(() -> {
              InsuranceClaim claim = claimsById.get(claimId);
              if (claim != null) {
                zeebeQueue.add(claim); // Will block if queue is full
              }
            })
    );
  }

  /**
   * Cancel process workflows on camunda for given claim IDs in batches asynchronously.
   * @param claimIds List of claim IDs to cancel processes for.
   */
  @Async("virtualThreadExecutor")
  public void cancelProcessInstance(List<String> claimIds) {
    final int BATCH_SIZE = 1000;
    try {
      for (int start = 0; start < claimIds.size(); start += BATCH_SIZE) {
        int end = Math.min(start + BATCH_SIZE, claimIds.size());
        List<String> batchIds = claimIds.subList(start, end);
        log.info("Processing batch from {} to {} ({} claims)", start, end, batchIds.size());

        List<ClaimStatusProjection> currentStatuses = insuranceClaimsRepository.getClaimStatuses(claimIds);
        Map<String , ExtensionStateCodeEnum> stateCodeEnumMap = currentStatuses.stream().collect(Collectors.toMap(claimStatusProjection -> claimStatusProjection.getClaimId().toString() , ClaimStatusProjection::getStatus));

        Optional<List<WorkFlow>> optional = workFlowRepository.findAllByClaimIdInAndActiveIsTrue(batchIds);

        if (optional.isEmpty()) {
          log.warn("No workflows found for batch: {}", batchIds);
          throw new DataNotFoundException("No workflows found for given claim IDs.");
        }

        List<WorkFlow> workFlows = optional.get();

        List<Long> deletedWorkflows = new ArrayList<>();
        for (WorkFlow workflow : workFlows) {

            if(stateCodeEnumMap.get(workflow.getClaimId()) != null && Constants.RESTRICT_REPROCESS_CLAIM_STATUS.contains(stateCodeEnumMap.get(workflow.getClaimId()))) {
                log.error("Claim with claimId : {} has been moved to restricted Status : {} ", workflow.getClaimId() , stateCodeEnumMap.get(workflow.getClaimId()));
                continue;
            }
          log.info("Cancelling process instance for claimId: {} and processInstanceKey: {}", workflow.getClaimId(), workflow.getProcessInstanceKey());
          zeebe.newCancelInstanceCommand(workflow.getProcessInstanceKey()).send().join();
          deletedWorkflows.add(workflow.getId());
        }

        if (!deletedWorkflows.isEmpty()) {
          workFlowRepository.deleteAllByIdsNative(deletedWorkflows);
        }

        log.info("Successfully cancelled {} process instances and deleted entries in work_flow table", deletedWorkflows.size());
      }

      log.info("All cancelProcessInstance claims batches processed successfully.");

    } catch (Exception e) {
      log.warn("Error while cancelProcessInstance for claimIds size {}: {}", claimIds.size(), e.getMessage());
      throw new BadRequestException("Batch processing stopped due to failure.", e);
    }
  }

}
