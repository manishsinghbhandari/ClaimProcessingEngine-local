package io.coredge.health.claims.service;

import brave.Tracer;
import io.camunda.zeebe.client.ZeebeClient;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.WorkFlowRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.utils.ReactiveClaimAdjudicationService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@Slf4j
@Service
public class CamundaService {

  @Autowired
  private ZeebeClient zeebe;

  @Autowired
  private WorkFlowRepository workFlowRepository;

  @Autowired
  private WorkflowService wfService;

  @Autowired
  private InsuranceClaimsRepository insuranceClaimsRepository;

  private final Tracer tracer;

    public CamundaService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void startCamundaProcess(Object claimData , String claimId) {
        Optional<ExtensionStateCodeEnum> currentStatus = insuranceClaimsRepository.getClaimStatus(UUID.fromString(claimId));
        if(currentStatus.isPresent() && Constants.RESTRICT_REPROCESS_CLAIM_STATUS.contains(currentStatus.get())) {
            throw new BadRequestException(String.format("Claim with claimId : %s has been moved to restricted Status : %s ", claimId , currentStatus.get()));
        }
    zeebe
        .newCreateInstanceCommand()
        .bpmnProcessId("claimProcess")
        .latestVersion()
        .variable("claim", claimData)
        .send();
  }

  /**
   * This method retrieves all claims that are not yet processed and starts a new process for each claim.
   * @param iClaim
   */
  @Transactional
  public void processClaim(InsuranceClaim iClaim) {
    String claimId = iClaim.getExternalId();
    String claimResponseId = iClaim.getClaimResponseId();
    String bundleId = iClaim.getBundleId();
    log.info("Started new process for claimId: {}", claimId);

    try {
      String traceId = tracer.currentSpan() != null ? tracer.currentSpan().context().traceIdString() : UUID.randomUUID().toString();
      Map<String, Object> workflowParams = Map.of(
              VAR_CLAIM_ID, claimId,
              VAR_CLAIM_RESPONSE_ID, claimResponseId,
              VAR_CLAIM_TYPE, Claim.Use.fromCode(iClaim.getUse().toLowerCase().trim()),
              VAR_TRACE_ID, traceId,
              VAR_BUNDLE_ID, bundleId,
              VAR_USER_ID, HealthIdContextHolder.userId()
      );

      if (workFlowRepository.existsByClaimId(claimId).orElse(false)) {
          Optional<ExtensionStateCodeEnum> currentStatus = insuranceClaimsRepository.getClaimStatus(UUID.fromString(claimId));
          if(currentStatus.isPresent() && Constants.RESTRICT_REPROCESS_CLAIM_STATUS.contains(currentStatus.get())) {
              throw new BadRequestException(String.format("Claim with claimId : %s has been moved to restricted Status : %s ", claimId , currentStatus.get()));
          }
        var workFlowsOpt = workFlowRepository.findAllByClaimIdAndActiveIsTrue(claimId);
        if (workFlowsOpt.isPresent() && !workFlowsOpt.get().isEmpty()) {
          var workFlows = workFlowsOpt.get();
         workFlows.forEach(workFlow -> {
              log.info("Deleting old process with InstanceKey: {} for claimId: {}", workFlow.getProcessInstanceKey(), claimId);
              wfService.regenBusinessProcess(workFlow.getProcessInstanceKey());
              workFlow.setActive(false);
              workFlowRepository.save(workFlow);
          });
        }
      } else {
        wfService.startBusinessProcess(Claim.Use.fromCode(iClaim.getUse().toLowerCase().trim()), workflowParams);
      }
    } catch (Exception e) {
      log.warn("Error while starting new process for claimId {}: {}", claimId, e.getMessage(), e);
    }
  }

}
