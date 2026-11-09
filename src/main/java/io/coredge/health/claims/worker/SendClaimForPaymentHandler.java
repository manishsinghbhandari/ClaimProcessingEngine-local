package io.coredge.health.claims.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.coredge.health.claims.client.IFSClient;
import io.coredge.health.claims.component.ClientsComponent;
import io.coredge.health.claims.component.IfsClientComponent;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.ClaimPaymentRepository;
import io.coredge.health.claims.db.repositories.WorkFlowRepository;
import io.coredge.health.claims.dto.ClaimStatusUpdateResult;
import io.coredge.health.claims.enums.BenefitMasterSearchType;
import io.coredge.health.claims.enums.ErpErrorCode;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.enums.FundType;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.app.WorkFlowException;
import io.coredge.health.claims.exception.base.ExternalServiceException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.helpers.CreateClaimFundSchemeValidator;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.eclaims.*;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.service.*;
import io.coredge.health.claims.utils.AsyncUtils;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.utils.ClaimsValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.coredge.health.claims.constants.Constants.ClaimFundType.*;
import static io.coredge.health.claims.constants.Constants.HandlerMethod.SEND_CLAIM_FOR_PAYMENT;
import static io.coredge.health.claims.constants.Constants.PaymentStatus.PAID;
import static io.coredge.health.claims.constants.Constants.PaymentType.COMPLETE;
import static io.coredge.health.claims.constants.Constants.PaymentType.PARTIAL;
import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.utils.FhirHelper.convertDateStringToTargetFormat;
import static io.coredge.health.claims.utils.FhirHelper.convertToStringFromDateTime;

@SuppressWarnings("DuplicatedCode")
@Slf4j
@Service
public class SendClaimForPaymentHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DHA_CLAIM_ID_CONSTANT = "DHA";

    @Value("${erp.claim.reference.link}")
    private String erpClaimReferenceLinkBase;

    @Value("${erp.claim.bifurcation.enable:false}")
    private Boolean erpClaimBifurcationEnable;

    @Value("${system.platform.fee.enable:false}")
    private Boolean systemPlatformFeeEnable;

    @Value("${erp.claim.traffic.enable:false}")
    private Boolean erpClaimTrafficEnable;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private ClientsComponent clientsComponent;

    @Autowired
    private IFSClient ifsClient;
    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private ErpClaimsService erpClaimsService;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private StatusUpdateService statusUpdateService;

    @Autowired
    WorkFlowRepository workFlowRepository;

    @Autowired
    private SystemUtilizationService systemUtilizationService;

    @Autowired
    private ClaimPaymentRepository claimPaymentRepository;


    @Autowired
    private IfsClientComponent ifsClientComponet;


    @Autowired
    private ErpSubmissionService erpSubmissionService;

    @Autowired
    private ClaimsValidator claimsValidator;

    @JobWorker(type = SEND_CLAIM_FOR_PAYMENT)
    public void handle(JobClient client, ActivatedJob job) {
        AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable("claimId");
            handlePaymentErp(client, job, claimId);
        });
    }

    public void handlePaymentErp(JobClient client, ActivatedJob job, String claimID) {
        handlePaymentErp(client, job, claimID, false, false);
    }

    public void handlePaymentErp(JobClient client, ActivatedJob job, String claimId, Boolean reProcess, Boolean force) {
        handlePaymentErp(client, job, claimId, reProcess, force, false);
    }

    public void handlePaymentErp(JobClient client, ActivatedJob job, String claimId, Boolean reProcess, Boolean force, boolean erpRepush) {

        if (!force) {
            if (!erpClaimTrafficEnable) {
                log.warn("Traffic towards ERP is disabled , Skipping the handle payment job");
                throwErrorToCamunda(client, job, "Traffic towards ERP is disabled", "Traffic towards ERP is disabled", new RuntimeException("Traffic towards ERP is disabled"));
                return;
            }
        }

        log.warn("Traffic towards ERP is enable, Force flag {}, Skipping the handle payment job", force);

        boolean executableInsertOperationOnly;
        //  Find Claim Object from FHIR server
        if (job != null) {
            claimId = (String) job.getVariable("claimId");
        }


        if (StringUtils.isEmpty(claimId)) {
            log.warn("Claim ID is null, cannot handle payment job");
            throw new BadRequestException(String.format("Requested Id '%s' is a  PRE-AUTHORIZATION", claimId));
        }

        InsuranceClaim claimDetailsDB = insuranceClaimsService.getByExternalID(claimId);
        if (claimDetailsDB.getUse().equalsIgnoreCase(PREAUTHORIZATION)) {
            throw new BadRequestException(String.format("Requested Id '%s' is a  PRE-AUTHORIZATION", claimId));
        }

        // If status 'SENT_FOR_PAYMENT_PROCESSING' Then recheck in erp tables
        if (ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.equals(claimDetailsDB.getStatus())) {
            if (erpRepush) {
                log.info("ERP Repush: bypassing duplicate guard for claimId={}", claimId);
                executableInsertOperationOnly = false;
            } else if (erpClaimsService.isExistsByClaimNoHttpCallStatus(claimDetailsDB.getExternalId(), List.of("201", "409"))) {
                log.warn("Can't re-process claim for payment request, Already sent for payment, ClaimID: {}, Status: {}", claimId, claimDetailsDB.getStatus());
                return;
            } else {
                executableInsertOperationOnly = true;
            }
        } else if (ExtensionStateCodeEnum.APPROVED.equals(claimDetailsDB.getStatus())
                || ExtensionStateCodeEnum.MANUAL_REVIEW.equals(claimDetailsDB.getStatus())) {
            executableInsertOperationOnly = false;
        } else {
            log.warn("Can't process claim for payment request, Invalid Status, ClaimID: {}, Status: {}", claimId, claimDetailsDB.getStatus());
            return;
        }

        Claim claim = claimPaymentHelper.getClaimObject(claimId);
        // Claim response
        ClaimResponse claimResponse = getClaimResponse(claimDetailsDB);
        try {
            ResultClaimRequest resultClaimRequest = getResult(claimId);

            //Calculate the final intervention approved amount
            Map<String, Map<String, BigDecimal>> interventionAmountMapFinal = processClaimResponse(resultClaimRequest, claimId);

            Map<String, List<InterventionCodeRequest>> transformInterventionAmounts = transformInterventionAmounts(interventionAmountMapFinal);
            // Add approved amount from claim for each intervention and do total by each type (phc,shif,eccif,pmsf)
            ResultClaimFund fundSchemesResult = getFundSchemes(resultClaimRequest, interventionAmountMapFinal, claimId);

            // Total net amount approved
            final BigDecimal[] totalClaimAmountApproved = {resultClaimRequest.requestedClaimAmountFromClaim()[0]};
            if ((totalClaimAmountApproved[0].compareTo(BigDecimal.ZERO) <= 0)) {
                // Fallback method for the where claimResponse have zero total for 100 of claims in approved state
                if (claimDetailsDB.getClaimedTotal().compareTo(BigDecimal.ZERO) > 0) {
                    totalClaimAmountApproved[0] = fundSchemesResult.totalClaimAmountFromClaim();
                } else {
                    throw new DataValidationException("Total Claim amount can't be Zero");
                }
            }

            List<CreateClaimFundScheme> claimFundSchemeList;
            // Create the multiple Request of fund claim.
            if (systemPlatformFeeEnable) {
                //  Including the Platform Fee
                log.info("ERP payment, System platform fee enabled");
                claimFundSchemeList = getCreateClaimFundSchemeListPlatformFee(claim,
                        totalClaimAmountApproved, resultClaimRequest, fundSchemesResult.fundSchemes, claimDetailsDB, transformInterventionAmounts);
            } else {
                // Current Ongoing Flow
                log.info("ERP payment, System platform fee disabled");
                claimFundSchemeList = getCreateClaimFundSchemeList(claim,
                        totalClaimAmountApproved, resultClaimRequest, fundSchemesResult.fundSchemes, claimDetailsDB, transformInterventionAmounts);
            }
            insuranceClaimsService.save(claimDetailsDB);
            log.info("Create ClaimFundScheme list: {}", new Gson().toJson(claimFundSchemeList));
            if (!claimFundSchemeList.isEmpty()) {
                for (int i = 0; i < claimFundSchemeList.size(); i++) {
                    CreateClaimFundScheme claimFundScheme = claimFundSchemeList.get(i);
                    boolean isLastProcess = (i == claimFundSchemeList.size() - 1);

                    if (claimFundScheme.getHasChild() == false) {
                        log.info("Push to Erp, ClaimId: {}", claimFundScheme.getClaimNo());
                        log.info("ERP Request: {}", new Gson().toJson(claimFundScheme));

                        PushToErp(client, job, claimFundScheme, claim, claimResponse,
                                executableInsertOperationOnly, isLastProcess, erpRepush);

                        // Manually move the camunda work flow
                        if (reProcess && isLastProcess) {
                            if (StringUtils.isEmpty(claimFundScheme.getParentClaimNo())) {
                                moveProcess(claimFundScheme.getClaimNo());
                            }
                        }
                    }
                }

            } else {
                log.info("No claim fund scheme found for ClaimID: {}", claimId);
            }

        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception e) {
            log.info("!!! sendClaimForPaymentToERP Exception: {}", e.getLocalizedMessage());
            throwErrorToCamunda(client, job, e.getLocalizedMessage(), e.getLocalizedMessage(), new RuntimeException(e));
        }
    }

    public Map<String, List<InterventionCodeRequest>> transformInterventionAmounts(
            Map<String, Map<String, BigDecimal>> interventionAmountMapFinal) {

        Map<String, List<InterventionCodeRequest>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, BigDecimal>> entry : interventionAmountMapFinal.entrySet()) {

            String serviceCode = entry.getKey();
            Map<String, BigDecimal> schemeAmounts = entry.getValue();

            for (Map.Entry<String, BigDecimal> schemeEntry : schemeAmounts.entrySet()) {

                String scheme = schemeEntry.getKey();
                BigDecimal amount = schemeEntry.getValue();

                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                InterventionCodeRequest request = new InterventionCodeRequest();
                request.setServiceCodeType(serviceCode);
                request.setAmount(amount);

                result.computeIfAbsent(scheme, k -> new ArrayList<>())
                        .add(request);
            }
        }

        log.info("Intervention amounts transformed: {}", new Gson().toJson(result));
        return result;
    }
    public void PushToErp(JobClient client, ActivatedJob job, CreateClaimFundScheme createClaimFundScheme, Claim claim, ClaimResponse claimResponse, Boolean executableInsertOperationOnly, boolean isLastProcess, boolean erpRepush) {
        String claimID = claim.getIdPart();
        ErpErrorCode errorCode = null;
        String errorMessage = null;

        try {
            if (job != null) {
                log.info("Handling payment request for process instance {}", job.getProcessInstanceKey());
            }

            // Check for duplicate submission
            if (!erpRepush && erpClaimsService.isExistsByClaimNoAndPaymentStatus(createClaimFundScheme.getClaimNo(), ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name())) {
                log.warn("Duplicate Request , Claim Number is already sent to ERP for the Payment. {}", createClaimFundScheme.getClaimNo());
                // check if in claims table status is also 'SENT_FOR_PAYMENT_PROCESSING'
                triggerCallbacksAfterSuccess(claimID, claim, claimResponse);
                checkAndUpdateClaimsStatus(claimID);
                return;
            }

            ERPResponseModel claimErpResponse;
            String claimErpResp = "";

            // Send claim to ERP/IFS
            if (!executableInsertOperationOnly) {
                log.info("Pushing to ERP for ClaimID : {}", createClaimFundScheme.getClaimNo());
                claimErpResp = clientsComponent.processERPClaim(createClaimFundScheme);
            }
            List<String> errorMarkers = List.of("Error:", "ERROR");
            boolean hasError = errorMarkers.stream().anyMatch(claimErpResp::contains);

            if (!hasError) {
                if (executableInsertOperationOnly) {
                    log.info("Skipping ERP request..., ExecutableInsertOperationOnly to ERP tbl for ClaimID: {}", createClaimFundScheme.getClaimNo());
                    claimErpResponse = createERPResponseStoreOnly(createClaimFundScheme.getClaimNo());
                } else {
                    claimErpResponse = parseErpResponse(claimErpResp, createClaimFundScheme.getClaimNo());
                }
                log.info("---------has ERROR---FALSE --CURRENT STATUS {}", claimErpResp.contains("Status=500"));
                if (claimErpResponse.getStatusCode().contains(Constants.HTTP_STATUS.CODE_201)) {
                    log.info("Claim submitted successfully to ERP: {}", claim.getId());
                    claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.QUEUED);
                    claim.setStatus(Claim.ClaimStatus.ACTIVE);

                    // Transactional DB update
                    try {
                        log.info("Updating status in the Claims Tbl, ClaimId: {}", createClaimFundScheme.getClaimNo());
                        erpSubmissionService.updateDatabaseAfterErpSuccess(createClaimFundScheme, claimErpResponse, claimResponse.getIdPart(), null, claimID , isLastProcess);
                    } catch (Exception e) {
                        log.error("Database update failed after ERP success for ClaimID: {}, Error: {}", createClaimFundScheme.getClaimNo(), e.getMessage(), e);
                        errorCode = ErpErrorCode.DB_UPDATE_FAILED;
                        errorMessage = "Database update failed: " + e.getMessage();
                        throw e;
                    }

                } else {
                    if (claimErpResponse.getStatusCode().contains(Constants.HTTP_STATUS.CODE_409)) {
                        log.warn("ErpResponse Conflict 409 : {}", claimErpResp);
                    }
                    if (!erpClaimsService.existByClaimNo(createClaimFundScheme.getClaimNo())) {
                        saveToErpClaims(false, createClaimFundScheme, claimErpResponse, claimResponse.getIdPart()); // Push to DB
                    }
                    // check if in claims table status is also 'SENT_FOR_PAYMENT_PROCESSING'
                    checkAndUpdateClaimsStatus(claimID);
                }
                // Trigger callbacks only after successful DB commit
                if (StringUtils.isEmpty(createClaimFundScheme.getParentClaimNo())) {
                    triggerCallbacksAfterSuccess(claimID, claim, claimResponse);
                }
            } else {
                log.info("---------has ERROR---TRUE --CURRENT STATUS {}", claimErpResp.contains("Status=500"));
                if (claimErpResp.contains(Constants.HTTP_STATUS.CODE_409_ERROR)) {
                    log.warn("ErpResponse with Conflict 409 : {}", claimErpResp);
                    // Parse String to Object claimErpResponse
                    claimErpResp = claimErpResp.replace("Error: ", "");
                    claimErpResponse = parseErpResponse(claimErpResp, claimID);
                    if (!erpClaimsService.existByClaimNo(createClaimFundScheme.getClaimNo())) {
                        saveToErpClaims(false, createClaimFundScheme, claimErpResponse, claimResponse.getIdPart()); // Push to DB
                    }
                    // check if in claims table status is also 'SENT_FOR_PAYMENT_PROCESSING'
                    checkAndUpdateClaimsStatus(claimID);
                }

                if(claimErpResp.contains(Constants.HTTP_STATUS.CODE_400_ERROR)){
                    log.warn("ErpResponse with status 400 : {}", claimErpResp);

                    // Parse String to Object claimErpResponse
                    claimErpResp = claimErpResp.replace("Error: ", "");
                    claimErpResponse = parseErpResponse(claimErpResp, claimID);
                    if (!erpClaimsService.existByClaimNo(createClaimFundScheme.getClaimNo())) {
                        saveToErpClaims(false, createClaimFundScheme, claimErpResponse, claimResponse.getIdPart()); // Push to DB
                    }
                    // check if in claims table status is also 'SENT_FOR_PAYMENT_PROCESSING'
                    checkAndUpdateClaimsStatus(claimID);
                }



                // Trigger callbacks only after successful DB commit
                if (StringUtils.isEmpty(createClaimFundScheme.getParentClaimNo())) {
                    triggerCallbacksAfterSuccess(claimID, claim, claimResponse);
                }

                // For ERP repush: treat 409 (Claim already exists) as success
                if (erpRepush && claimErpResp.contains(Constants.HTTP_STATUS.CODE_409_ERROR)) {
                    log.info("ERP Repush: treating 409 as success for ClaimID: {}", createClaimFundScheme.getClaimNo());
                    return;
                }

                log.warn("ERP request failed with error markers for ClaimID: {}, Response: {}", createClaimFundScheme.getClaimNo(), claimErpResp);
                errorCode = ErpErrorCode.ERP_VALIDATION_ERROR;
                errorMessage = "ERP validation error: " + claimErpResp;
                throw new RuntimeException(errorMessage);
            }
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("PushToErp failed for ClaimID: {}, ErrorCode: {}, Message: {}", createClaimFundScheme.getClaimNo(),
                    errorCode != null ? errorCode.getCode() : "UNKNOWN", e.getMessage(), e);

            // Set claim status to ERROR
            if (errorCode != null) {
                erpSubmissionService.setClaimStatusToError(claimID, errorCode, errorMessage != null ? errorMessage : e.getMessage());
            }

            // Throw error to Camunda
            throwErrorToCamunda(client, job, errorMessage != null ? errorMessage : e.getMessage(),
                    errorMessage != null ? errorMessage : e.getMessage(), new RuntimeException(e));
        }
    }

    private void checkAndUpdateClaimsStatus(String claimID) {
        InsuranceClaim claimDB = insuranceClaimsService.getByExternalID(claimID);
        if (claimDB != null && !ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.equals(claimDB.getStatus())) {
            claimsValidator.validateClaimStatusBeforeUpdate(claimDB.getStatus(), ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING , claimDB.getUse() , claimID);
            claimDB.setStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
            claimDB.setLastModifiedDate(LocalDateTime.now());
            insuranceClaimsService.save(claimDB);
        }
    }


    private ERPResponseModel parseErpResponse(String claimErpResp, String claimId) {
        try {
            ERPResponseModel response = objectMapper.readValue(claimErpResp, ERPResponseModel.class);
            log.info("Parsed ERP response for ClaimID: {}, StatusCode: {}", claimId, response.getStatusCode());
            return response;
        } catch (JsonProcessingException e) {
            log.error("Failed to parse ERP response for ClaimID: {}, Response: {}",
                    claimId, claimErpResp, e);
            String errorMessage = "Failed to parse ERP response: " + e.getMessage();
            // You can customize the exception class if required
            throw new RuntimeException(errorMessage, e);
        }
    }


    /**
     * Triggers callbacks after successful database commit.
     */
    private void triggerCallbacksAfterSuccess(String claimID, Claim claim, ClaimResponse claimResponse) {
        try {
            log.info("Triggering callbacks after successful commit for ClaimID: {}", claimID);

            // Update FHIR status
            UpdateStatusFHIR(claimID, claim, claimResponse, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);

            // Trigger webhooks
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimID, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);

            // Trigger status update callbacks
            statusUpdateService.triggerCallBackStatusUpdate(claimID, true, true, false);

            log.info("Callbacks triggered successfully for ClaimID: {}", claimID);
        } catch (Exception e) {
            log.error("Failed to trigger callbacks for ClaimID: {}, Error: {}", claimID, e.getMessage(), e);
            // Don't throw here as the main transaction succeeded
        }
    }

    protected void moveProcess(String claimId) {
        // Move the workflow further by 'Wait for Payment Confirmation from ERP' message to engine.
        try {
            log.info("Workflow message publishing for claimId: {}", claimId);
            // Get the Process ID from the DB.
            Optional<List<WorkFlow>> workFlows = workFlowRepository.findAllByClaimIdAndActiveIsTrue(claimId);
            if(workFlows.isEmpty() || workFlows.get().isEmpty()) {
                throw new IllegalArgumentException("Workflow not found for claimId: " + claimId);
            }

            // Move to next step
            // Note : TaskID will be get from the BPMN.
            workFlows.get().forEach(workFlow -> moveToNextStep(workFlow.getProcessInstanceKey(), "Event_0jx1upo"));

            log.info("Workflow message published successfully for claimId: {}", claimId);
        } catch (Exception e) {
            log.warn("Failed to publish message for claimId: {}", claimId, e);
            throw new WorkFlowException("Failed to publish message to workflow engine", e);
        }
    }

    @Async("virtualThreadExecutor")
    public void moveToNextStep(long processInstanceKey, String nextTaskId) {
        try {
            zeebe.newModifyProcessInstanceCommand(processInstanceKey)
                    .activateElement(nextTaskId)
                    .send()
                    .get(3, TimeUnit.SECONDS);  // Timeout after 10s
        } catch (Exception e) {
            log.warn("Failed to publish message to workflow engine ,{}", e.getMessage());
        }
    }


    private static void throwErrorToCamunda(JobClient client, ActivatedJob job, String e, String e1, RuntimeException e2) {
        if (client != null && job != null) {
            client.newThrowErrorCommand(job).errorCode("ErpSendPaymentError").errorMessage(e).variables(Map.of("errorMessage", e1)).send();
        }
        throw e2;
    }

    private void UpdateStatusFHIR(String claimID, Claim claim, ClaimResponse claimResponse, ExtensionStateCodeEnum status) {
        try {
            // Mark Status as SENT_FOR_PAYMENT_PROCESSING
            log.info("Updating FHIR claim {} , Status: '{}'", claimID, status);
            claimsHandlerUtility.updateClaimAndResponseStatus(claimID, claim, claimResponse, status);
            log.info("Marking Claim '{}', Status: '{}'  successfully", claimID, status);

            //Push call back status providers
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimID, status);
            statusUpdateService.triggerCallBackStatusUpdate(claimID, true, false, false);

            log.info("Updated Claim '{}', Status: '{}'  successfully", claimID, status);
        } catch (Exception e) {
            log.warn("Failed updating FHIR status, Claim: '{}', Status: '{}'.", claimID, status);
        }
    }

    private ClaimResponse getClaimResponse(InsuranceClaim iClaim) {
        // Claim Response
        ClaimResponse claimResponse = null;
        if (StringUtils.isNotEmpty(iClaim.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(iClaim.getClaimResponseId());
        }
        // Temp Fix for the Multiple Claim response object in FHIR
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(iClaim.getExternalId());
            if (claimResponse == null) {
                log.info("ClaimResponse is not found against External-ID '{}'", iClaim.getExternalId());
                throw new RuntimeException("Error occurred, ClaimResponse is not found against claim ID :" + iClaim.getExternalId());
            }
        }
        return claimResponse;
    }

    public void saveToErpClaims(Boolean isCallback, CreateClaimFundScheme createClaimFundScheme, ERPResponseModel claimErpResponse, String claimResponseID) {
        String bank_file_reference = null;
        if (StringUtils.isNotEmpty(createClaimFundScheme.getBank_file_reference())) {
            bank_file_reference = createClaimFundScheme.getBank_file_reference();
        }
        saveToErpClaims(isCallback, createClaimFundScheme, claimErpResponse, claimResponseID, bank_file_reference);
    }

    public void saveToErpClaims(Boolean isCallback, CreateClaimFundScheme createClaimFundScheme, ERPResponseModel claimErpResponse, String claimResponseID, String bankReferenceNumber) {
        try {
            log.info("Saving Claim ID to ERP : {}", createClaimFundScheme.getClaimNo());
            ErpClaims erpClaims = Objects.requireNonNullElseGet(erpClaimsService.findByClaimNo(createClaimFundScheme.getClaimNo()), ErpClaims::new);
            if (isCallback) {
                if (StringUtils.isEmpty(erpClaims.getClaimNo()))
                    BeanUtils.copyProperties(createClaimFundScheme, erpClaims, "fundDistribution");
                // Extract amounts from fundDistribution for database storage
                if (createClaimFundScheme.getFundDistribution() != null) {
                    if (createClaimFundScheme.getFundDistribution().getProviderShare() != null) {
                        erpClaims.setProviderAmount(createClaimFundScheme.getFundDistribution().getProviderShare().getAmount());
                    }
                    if (createClaimFundScheme.getFundDistribution().getSystemUtilization() != null) {
                        erpClaims.setUtilizationAmount(createClaimFundScheme.getFundDistribution().getSystemUtilization().getAmount());
                    }
                }
                erpClaims.setCallBackStatus(PAID.equalsIgnoreCase(createClaimFundScheme.getPaymentStatus()) ? "202" : "422");

                log.info("Callback, ClaimID : {}, Payment status : {}", erpClaims.getClaimNo(), erpClaims.getPaymentStatus());
                if (StringUtils.isNotEmpty((createClaimFundScheme.getRejection_reason()))) {
                    erpClaims.setRejection_reason(createClaimFundScheme.getRejection_reason());
                }
                if (createClaimFundScheme.getAdjustmentAmount() != null)
                    erpClaims.setType(createClaimFundScheme.getAdjustmentAmount().compareTo(BigDecimal.ZERO) > 0 ? PARTIAL : COMPLETE);

                erpClaims.setDate(new Date());
                erpClaims.setLastModifiedDate(LocalDateTime.now());
                erpClaims.setAmount(createClaimFundScheme.getClaimAmount() != null ? createClaimFundScheme.getClaimAmount() : BigDecimal.ZERO);
                erpClaims.setPaymentStatus(createClaimFundScheme.getClaimAmount().compareTo(erpClaims.getClaimAmount()) > 0 ?  ExtensionStateCodeEnum.PAYMENT_PARTIAL.name() :  ExtensionStateCodeEnum.PAYMENT_COMPLETED.name());
            } else {
                BeanUtils.copyProperties(createClaimFundScheme, erpClaims, "fundDistribution");
                // Extract amounts from fundDistribution for database storage
                if (createClaimFundScheme.getFundDistribution() != null) {
                    if (createClaimFundScheme.getFundDistribution().getProviderShare() != null) {
                        erpClaims.setProviderAmount(createClaimFundScheme.getFundDistribution().getProviderShare().getAmount());
                    }
                    if (createClaimFundScheme.getFundDistribution().getSystemUtilization() != null) {
                        erpClaims.setUtilizationAmount(createClaimFundScheme.getFundDistribution().getSystemUtilization().getAmount());
                    }
                }
                if (StringUtils.isNotEmpty(erpClaims.getParentClaimNo())) {
                    erpClaims.setParentClaimNo(createClaimFundScheme.getParentClaimNo());
                }
                erpClaims.setHttpCallStatus(claimErpResponse.getStatusCode());
                erpClaims.setHttpCallMsg(claimErpResponse.getResponseBody().getResult().getMessage());
                erpClaims.setErpId(claimErpResponse.getResponseBody().getResult().getId());
                erpClaims.setClaimResponseId(claimResponseID);
                erpClaims.setServiceCode(String.valueOf(createClaimFundScheme.getServiceCode()));
                erpClaims.setPaymentStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());

                // added for reprocess
                erpClaims.setCreationDate(LocalDateTime.now());
                erpClaims.setLastModifiedDate(LocalDateTime.now());
            }
            if (StringUtils.isNotEmpty(bankReferenceNumber)) {
                erpClaims.setBankReference(bankReferenceNumber);
            }

            // Save the Request JSON
            String jsonStr = objectMapper.writeValueAsString(createClaimFundScheme);
            if (isCallback) {
                erpClaims.setResponseCallback(jsonStr);
            } else {
                erpClaims.setRequest(jsonStr);
            }
            log.info("saving to DB [isCallback : {}] for Claim-ID - {}", isCallback, createClaimFundScheme.getClaimNo());
            erpClaimsService.saveErpClaims(erpClaims);
        } catch (JsonProcessingException e) {
            log.warn("JsonProcessingException: {}", e.getMessage());
        }
    }


    private @NotNull SendClaimForPaymentHandler.ResultClaimRequest getResult(String claimID) {
        Map<String, ClaimProcessInterventionData.InterventionCodeDetail> interventionDetails = new HashMap<>();
        Map<String, Map<String, BigDecimal>> interventionAmountMap = new HashMap<>();
        final BigDecimal[] requestedClaimAmountFromClaim = {BigDecimal.ZERO};
        List<String> interventionList = new ArrayList<>();
        List<InterventionCodeRequest> interventionCodeRequests = new ArrayList<>();
        insuranceClaimsService.fetchClaimItems(claimID)
                .stream()
                .filter(chDTO -> chDTO.getLineItemStatus().equals(Constants.ClaimLineItemStatus.PASSED))
                .forEach(chDTO -> {
                    String interventionCode = chDTO.getInterventionCode();
                    List<BenefitMasterResponse> interventionData = benefitMasterService.fetchBenefitsMasterData(BenefitMasterSearchType.INTERVENTION.getValue(), interventionCode);
                    var firstInterventionData = interventionData.getFirst();
                    Map<String, BigDecimal> percentageShareFund = Map.of(ECCIF, Helper.getPercentageValue(firstInterventionData.getEccif()), PHC, Helper.getPercentageValue(firstInterventionData.getPhc()),
                            SHIF, Helper.getPercentageValue(firstInterventionData.getShif()), PMSF, Helper.getPercentageValue(firstInterventionData.getPmsf()));
                    interventionList.add(interventionCode);
                    interventionAmountMap.put(interventionCode, percentageShareFund);
                    BigDecimal approvedAmountFromClaim = getApprovedAmountFromClaim(chDTO);
                    interventionDetails.put(interventionCode, ClaimProcessInterventionData.InterventionCodeDetail.builder().interventionCode(interventionCode).approvedAmount(approvedAmountFromClaim).sequence(chDTO.getSequence())
                            .interventionAmountPercentageShare(percentageShareFund).build());
                    log.info("ClaimId: {}, approvedAmountFromClaim: {}", approvedAmountFromClaim, claimID);
                    requestedClaimAmountFromClaim[0] = requestedClaimAmountFromClaim[0].add(approvedAmountFromClaim);
                    InterventionCodeRequest request = new InterventionCodeRequest();
                    request.setAmount(chDTO.getApprovedPrice());
                    request.setServiceCodeType(chDTO.getInterventionCode());
                    interventionCodeRequests.add(request);
                });

        // Logging
        log.info("claimID: {}, approvedAmountFromClaim: {} | interventionAmountMap: {}", claimID, requestedClaimAmountFromClaim[0], interventionAmountMap);
        if (interventionAmountMap.isEmpty()) {
            log.warn("No claimLineItems/intervention amount found for Claim ID {}", claimID);
            throw new DataValidationException(String.format("No claimLineItems/intervention amount found for ClaimID: %s", claimID));
        }
        return new ResultClaimRequest(ClaimProcessInterventionData.builder().interventionCodeDetailsStr(interventionDetails).build(), interventionList, requestedClaimAmountFromClaim, interventionCodeRequests);
    }

    /**
     * Calculates the approved amount from claim history.
     * Prefers net value * quantity if available and > 0,
     * otherwise falls back to approved price * approved quantity if < 0.
     *
     * @param chDTO the claim history data
     * @return approved amount or BigDecimal.ZERO if insufficient data
     */
    private BigDecimal getApprovedAmountFromClaim(ClaimHistoryDTO chDTO) {
        BigDecimal price = chDTO.getApprovedPrice() != null ? chDTO.getApprovedPrice() : BigDecimal.ZERO;
        BigDecimal approvedQty = chDTO.getApprovedQuantity() != null ? BigDecimal.valueOf(chDTO.getApprovedQuantity()) : BigDecimal.ONE;

        if (price.compareTo(BigDecimal.ZERO) > 0) {
            return price.multiply(approvedQty);
        }

        BigDecimal net = chDTO.getNetValue() != null ? chDTO.getNetValue() : BigDecimal.ZERO;
        BigDecimal qty = chDTO.getQuantity() != null ? BigDecimal.valueOf(chDTO.getQuantity()) : BigDecimal.ONE;

        if (net.compareTo(BigDecimal.ZERO) > 0) {
            return net.multiply(qty);
        }

        return BigDecimal.ZERO;
    }

    private Map<String, Map<String, BigDecimal>> processClaimResponse(ResultClaimRequest resultClaimRequest, String claimId) {
        if (Objects.isNull(resultClaimRequest)) {
            log.warn("ClaimResponse has no item, claimID: {}", claimId);
            throw new RuntimeException("Claim response has no item");
        }
        //Final Claim approved amount map
        Map<String, Map<String, BigDecimal>> interventionAmountMapFinal = new HashMap<>();
        BigDecimal amount = resultClaimRequest.requestedClaimAmountFromClaim[0];
        ClaimProcessInterventionData claimProcessInterventionData = resultClaimRequest.claimProcessInterventionData;
        Map<String, ClaimProcessInterventionData.InterventionCodeDetail> interventionDetails = claimProcessInterventionData.getInterventionCodeDetailsStr();
        interventionDetails.forEach((interCode, v) -> {
            ClaimProcessInterventionData.InterventionCodeDetail detail = interventionDetails.get(interCode);
            Map<String, BigDecimal> interventionAmountMap = detail.getInterventionAmountPercentageShare();
            log.info("InterventionDetail [{}] for : {}", detail.getInterventionCode(), interventionAmountMap);
            // Extract PHC, SHIF, ECCIF from interventionAmountMap
            ResultShaPmsf resultShaPmsf = calculateCombinedShaPmsfAmount(detail.getInterventionCode(), interventionAmountMap, v.getApprovedAmount(), claimId);
            Map<String, BigDecimal> amountFund = Map.of(
                    ECCIF, resultShaPmsf.eccif,
                    PHC, resultShaPmsf.phc,
                    SHIF, resultShaPmsf.shif,
                    PMSF, resultShaPmsf.pmsf);

            // If intervention code already present, sum the values
            interventionAmountMapFinal.merge(detail.getInterventionCode(), amountFund, (oldMap, newMap) -> {
                Map<String, BigDecimal> summedMap = new HashMap<>(oldMap);
                newMap.forEach((key, value) ->
                        summedMap.merge(key, value, BigDecimal::add)
                );
                return summedMap;
            });
        });
        log.info("ClaimID: {}, resultClaimRequestAmount : {}, interventionAmountMapF:: {}", claimId, resultClaimRequest.requestedClaimAmountFromClaim[0], interventionAmountMapFinal);
        return interventionAmountMapFinal;
    }

    /**
     * Calculates the combined SHA fund utilization (ECCIF, PHC, SHIF, PMSF) based on benefit configuration
     * and claim details for a specific intervention code and claim.
     *
     * @param interventionCode      the intervention code for which funds are being calculated
     * @param interventionAmountMap the fund-wise percentage allocation map
     * @param amount                the total amount eligible for deduction
     * @param claimId               the claim ID to fetch associated details
     * @return ResultShaPmsf object containing calculated ECCIF, PHC, SHIF, and PMSF fund usage
     */
    private ResultShaPmsf calculateCombinedShaPmsfAmount(String interventionCode, Map<String, BigDecimal> interventionAmountMap, BigDecimal amount, String claimId) {

        var benefit = benefitMasterService.fetchBenefitsMasterData(BenefitMasterSearchType.INTERVENTION.getValue(), interventionCode)
                .getFirst();

        var claim = insuranceClaimsService.fetchClaimItems(claimId)
                .stream()
                .filter(c -> c.getLineItemStatus().equals(Constants.ClaimLineItemStatus.PASSED)
                        && interventionCode.equals(c.getInterventionCode()))
                .findFirst().orElseThrow();


        boolean eccifApplicable = benefit.getEccif().contains("100");
        boolean pmsfApplicable = benefit.getPmsf().contains("100");
        boolean shifApplicable = benefit.getShif().contains("100");

        BigDecimal eccif = BigDecimal.ZERO, pmsf = BigDecimal.ZERO, shif = BigDecimal.ZERO;
        BigDecimal phc = Helper.calculateTheAmount(amount, interventionAmountMap.getOrDefault(PHC, BigDecimal.ZERO));
        BigDecimal remainingAmount = amount;

        BigDecimal eccifBase = Objects.requireNonNullElse(claim.getEccifAmount(), BigDecimal.ZERO);
        BigDecimal pmfAmount = claim.isPmfItem() ? Objects.requireNonNullElse(claim.getPmfAmount(), BigDecimal.ZERO) : BigDecimal.ZERO;

        // All three funds applicable: PMSF → ECCIF → SHIF
        if (eccifApplicable && pmsfApplicable && shifApplicable) {
            if (claim.isPmfItem()) {
                pmsf = Helper.calculateTheAmount(pmfAmount, interventionAmountMap.getOrDefault(PMSF, BigDecimal.ZERO));
                remainingAmount = remainingAmount.subtract(pmsf);
            }
            eccif = Helper.calculateTheAmount(eccifBase, interventionAmountMap.getOrDefault(ECCIF, BigDecimal.ZERO));
            remainingAmount = remainingAmount.subtract(eccif);
            shif = Helper.calculateTheAmount(remainingAmount, interventionAmountMap.getOrDefault(SHIF, BigDecimal.ZERO));

            // Only ECCIF and PMSF applicable: PMSF → ECCIF
        } else if (eccifApplicable && pmsfApplicable) {
            if (claim.isPmfItem()) {
                pmsf = Helper.calculateTheAmount(pmfAmount, interventionAmountMap.getOrDefault(PMSF, BigDecimal.ZERO));
                remainingAmount = remainingAmount.subtract(pmfAmount);
            }
            eccif = Helper.calculateTheAmount(
                    eccifBase.compareTo(BigDecimal.ZERO) > 0 ? eccifBase : remainingAmount,
                    interventionAmountMap.getOrDefault(ECCIF, BigDecimal.ZERO)
            );

            // Only ECCIF and SHIF applicable: ECCIF → SHIF
        } else if (eccifApplicable && shifApplicable) {
            eccif = Helper.calculateTheAmount(
                    eccifBase.compareTo(BigDecimal.ZERO) > 0 ? eccifBase : remainingAmount,
                    interventionAmountMap.getOrDefault(ECCIF, BigDecimal.ZERO)
            );
            remainingAmount = remainingAmount.subtract(eccif);
            shif = Helper.calculateTheAmount(remainingAmount, interventionAmountMap.getOrDefault(SHIF, BigDecimal.ZERO));

            // Only PMSF and SHIF applicable: PMSF → SHIF
        } else if (pmsfApplicable && shifApplicable) {
            if (claim.isPmfItem()) {
                pmsf = Helper.calculateTheAmount(pmfAmount, interventionAmountMap.getOrDefault(PMSF, BigDecimal.ZERO));
                remainingAmount = remainingAmount.subtract(pmfAmount);
            }
            shif = Helper.calculateTheAmount(remainingAmount, interventionAmountMap.getOrDefault(SHIF, BigDecimal.ZERO));

            // Only ECCIF applicable
        } else if (eccifApplicable) {
            eccif = Helper.calculateTheAmount(
                    eccifBase.compareTo(BigDecimal.ZERO) > 0 ? eccifBase : remainingAmount,
                    interventionAmountMap.getOrDefault(ECCIF, BigDecimal.ZERO)
            );
            // Only PMSF applicable and claim is PMF item
        } else if (pmsfApplicable && claim.isPmfItem()) {
            pmsf = Helper.calculateTheAmount(pmfAmount, interventionAmountMap.getOrDefault(PMSF, BigDecimal.ZERO));
            // Only SHIF applicable
        } else if (shifApplicable) {
            shif = Helper.calculateTheAmount(remainingAmount, interventionAmountMap.getOrDefault(SHIF, BigDecimal.ZERO));
        }
        return new ResultShaPmsf(eccif, phc, shif, pmsf);
    }


    private List<CreateClaimFundScheme> getCreateClaimFundSchemeList(Claim claim, BigDecimal[] totalClaimAmountApproved,
                                                                     ResultClaimRequest resultClaimRequest, List<CreateClaimFundScheme.FundScheme> fundScheme, InsuranceClaim claimDetailsDB,  Map<String, List<InterventionCodeRequest>> transformInterventionAmounts) {
        List<CreateClaimFundScheme> resultResp = new ArrayList<>();
        List<ResultSegregateFundSchemes> listsFundScheme = segregateFundSchemes(fundScheme);
        CreateClaimFundScheme parentClaimFundScheme = getCreateClaimFundScheme(
                claim,
                totalClaimAmountApproved[0],
                resultClaimRequest,
                fundScheme,
                claimDetailsDB,
                processVendorCode(claimDetailsDB),
                claimDetailsDB.getProvider().getName(),
                claim.getIdPart(), claimDetailsDB.getLastModifiedBy());

        parentClaimFundScheme.setHasChild(true);
        resultResp.add(parentClaimFundScheme);

        for (ResultSegregateFundSchemes result : listsFundScheme) {
            CreateClaimFundScheme schemeCopy = cloneClaimFundScheme(parentClaimFundScheme); // deep copy
            // Child claims - modified claim numbers (invoice same as parent)
            schemeCopy.setParentClaimNo(parentClaimFundScheme.getClaimNo()); // set parent
            String childClaimNo = parentClaimFundScheme.getClaimNo().concat("_").concat(result.fundSchemeType());
            String childInvoiceNumber = parentClaimFundScheme.getInvoiceNumber().concat("/").concat(result.fundSchemeType());

            schemeCopy.setClaimNo(childClaimNo);
            schemeCopy.setInvoiceNumber(childInvoiceNumber);
            schemeCopy.setClaimAmount(result.amount);
            schemeCopy.setFundScheme(result.modifiedList);
            // Set fund type
            if (result.fundSchemeType().equalsIgnoreCase(ECCIF)) {
                List<InterventionCodeRequest> interventionCodeRequest =  transformInterventionAmounts.get(ECCIF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            } else if (result.fundSchemeType().equalsIgnoreCase(PMSF)) {
                List<InterventionCodeRequest> interventionCodeRequest = transformInterventionAmounts.get(PMSF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            } else if (result.fundSchemeType().equalsIgnoreCase(SHIF)) {
                List<InterventionCodeRequest> interventionCodeRequest = transformInterventionAmounts.get(SHIF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            }

            // Logic to fix the claim amount and service-code amount mismatch.
            serviceCodeAmountCalculation(schemeCopy);
            schemeCopy.setHasChild(false);
            resultResp.add(schemeCopy);
        }
        return resultResp;
    }

    private List<CreateClaimFundScheme> getCreateClaimFundSchemeListPlatformFee(Claim claim, BigDecimal[] totalClaimAmountApproved,
                                                                                ResultClaimRequest resultClaimRequest, List<CreateClaimFundScheme.FundScheme> fundScheme, InsuranceClaim claimDetailsDB, Map<String, List<InterventionCodeRequest>> transformInterventionAmounts) {
        // Get utilization charge configuration based on claim creation date
        Optional<SystemUtilizationChargeView> systemUtilizationCharge = systemUtilizationService.findSystemUtilizationCharge(claimDetailsDB.getCreationDate());

        // Initialize tracking amounts
        claimDetailsDB.setClaimPayableAmount(BigDecimal.ZERO);
        claimDetailsDB.setSystemUtilizationAmount(BigDecimal.ZERO);

        List<CreateClaimFundScheme> resultResp = new ArrayList<>();
        List<ResultSegregateFundSchemes> listsFundScheme = segregateFundSchemes(fundScheme);

        // Calculate total utilization charge (2% capped at max) for proportional distribution
        BigDecimal totalUtilizationCharge = BigDecimal.ZERO;
        if (systemUtilizationCharge.isPresent()) {
            totalUtilizationCharge = calculateSystemUtilizationCharge(totalClaimAmountApproved[0], systemUtilizationCharge.get());
        }

        // Create parent claim (same as getCreateClaimFundSchemeList)
        CreateClaimFundScheme parentClaimFundScheme = getCreateClaimFundScheme(
                claim,
                totalClaimAmountApproved[0],
                resultClaimRequest,
                fundScheme,
                claimDetailsDB,
                processVendorCode(claimDetailsDB),
                claimDetailsDB.getProvider().getName(),
                claim.getIdPart(), claimDetailsDB.getLastModifiedBy());

        parentClaimFundScheme.setHasChild(true);
        resultResp.add(parentClaimFundScheme);

        // Process each fund scheme type
        for (ResultSegregateFundSchemes result : listsFundScheme) {
            CreateClaimFundScheme schemeCopy = cloneClaimFundScheme(parentClaimFundScheme);

            // Child claims - modified claim numbers (same as getCreateClaimFundSchemeList)
            schemeCopy.setParentClaimNo(parentClaimFundScheme.getClaimNo());
            String childClaimNo = parentClaimFundScheme.getClaimNo().concat("_").concat(result.fundSchemeType());
            String childInvoiceNumber = parentClaimFundScheme.getInvoiceNumber().concat("/").concat(result.fundSchemeType());

            schemeCopy.setClaimNo(childClaimNo);
            schemeCopy.setInvoiceNumber(childInvoiceNumber);
            schemeCopy.setClaimAmount(result.amount());
            schemeCopy.setFundScheme(result.modifiedList());
            schemeCopy.setHasChild(false);

            // Set fund type
            if (result.fundSchemeType().equalsIgnoreCase(ECCIF)) {
                List<InterventionCodeRequest> interventionCodeRequest = transformInterventionAmounts.get(ECCIF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            } else if (result.fundSchemeType().equalsIgnoreCase(PMSF)) {
                List<InterventionCodeRequest> interventionCodeRequest = transformInterventionAmounts.get(PMSF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            } else if (result.fundSchemeType().equalsIgnoreCase(SHIF)) {
                List<InterventionCodeRequest> interventionCodeRequest = transformInterventionAmounts.get(SHIF);
                schemeCopy.setInterventionCodes(interventionCodeRequest);
            }

            // Logic to fix the claim amount and service-code amount mismatch.
            serviceCodeAmountCalculation(schemeCopy);

            // Calculate utilization charge proportionally for this fund
            BigDecimal utilizationAmount = BigDecimal.ZERO;
            BigDecimal providerAmount = result.amount();

            if (systemUtilizationCharge.isPresent() && totalUtilizationCharge.compareTo(BigDecimal.ZERO) > 0) {
                utilizationAmount = calculateContributionAmount(result.amount(), totalClaimAmountApproved[0], totalUtilizationCharge);
                providerAmount = result.amount().subtract(utilizationAmount);
            }

            // Set new payload fields for ERP (nested fundDistribution object)
            schemeCopy.setFundDistribution(FundDistribution.of(providerAmount, utilizationAmount));

            // Track totals for InsuranceClaim
            claimDetailsDB.addClaimPayableAmount(providerAmount);
            claimDetailsDB.addSystemUtilizationAmount(utilizationAmount);

            // Save ClaimPayment record
            ClaimPayment claimPayment = ClaimPayment.builder()
                    .claim(claimDetailsDB)
                    .claimedAmount(result.amount())
                    .facilityPayableAmount(providerAmount)
                    .systemUtilizationAmount(utilizationAmount)
                    .build();
            // Set fund type
            if (result.fundSchemeType().equalsIgnoreCase(ECCIF)) {
                claimPayment.setFundType(FundType.ECCIF);
            } else if (result.fundSchemeType().equalsIgnoreCase(PMSF)) {
                claimPayment.setFundType(FundType.PMSF);
            } else if (result.fundSchemeType().equalsIgnoreCase(SHIF)) {
                claimPayment.setFundType(FundType.SHIF);
            }

            claimPaymentRepository.saveOrUpdate(claimPayment);
            resultResp.add(schemeCopy);
        }

        return resultResp;
    }
    //

    private  void serviceCodeAmountCalculation(CreateClaimFundScheme request) {
        BigDecimal claimCalculatedAmount = BigDecimal.ZERO;
        for (InterventionCodeRequest interventionCodeRequest: request.getInterventionCodes()) {
            claimCalculatedAmount = claimCalculatedAmount.add(interventionCodeRequest.getAmount());
        }
        request.setClaimAmount(claimCalculatedAmount);
    }

    private CreateClaimFundScheme cloneClaimFundScheme(CreateClaimFundScheme original) {
        CreateClaimFundScheme copy = new CreateClaimFundScheme();
        BeanUtils.copyProperties(original, copy);

        // Deep copy intervention codes
        if (original.getInterventionCodes() != null) {
            List<InterventionCodeRequest> newCodes = new ArrayList<>();

            for (InterventionCodeRequest code : original.getInterventionCodes()) {
                InterventionCodeRequest newCode = new InterventionCodeRequest();
                BeanUtils.copyProperties(code, newCode);
                newCodes.add(newCode);
            }

            copy.setInterventionCodes(newCodes);
        }
        return copy;
    }


    private static List<ResultSegregateFundSchemes> segregateFundSchemes(List<CreateClaimFundScheme.FundScheme> fundSchemeList) {
        List<ResultSegregateFundSchemes> result = new ArrayList<>();

        // Identify funds where amount > 0
        List<CreateClaimFundScheme.FundScheme> nonZeroFunds = fundSchemeList.stream()
                .filter(fs -> fs.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        // Generate segregated lists
        for (CreateClaimFundScheme.FundScheme nonZeroFund : nonZeroFunds) {
            List<CreateClaimFundScheme.FundScheme> modifiedList = new ArrayList<>();
            String type = null;
            BigDecimal amount = BigDecimal.ZERO;
            for (CreateClaimFundScheme.FundScheme fs : fundSchemeList) {
                if (fs.equals(nonZeroFund)) {
                    // Keep original amount for the selected scheme
                    amount = fs.getAmount();
                    type = fs.getFundSchemeType();
                    modifiedList.add(new CreateClaimFundScheme.FundScheme(fs.getFundSchemeType(), fs.getAmount()));
                } else {
                    // Set amount to zero for others
                    modifiedList.add(new CreateClaimFundScheme.FundScheme(fs.getFundSchemeType(), BigDecimal.ZERO));
                }
            }
            // Add type, amount
            result.add(new ResultSegregateFundSchemes(modifiedList, type, amount));
        }

        return result;
    }

    private CreateClaimFundScheme getCreateClaimFundScheme(Claim claim, BigDecimal totalClaimAmountApproved, ResultClaimRequest resultClaimRequest, List<CreateClaimFundScheme.FundScheme> fundScheme, InsuranceClaim claimDetailsDB, String vendorCode, String vendorName, String claimNo, String verifiedBy) {
        ClaimPaymentHelper.InvoiceDetails invoiceDetails = claimPaymentHelper.getInvoiceDetails(claim);

        //Use provider invoice number if available; otherwise, fallback to first 8 characters of claim number
        String invoiceNumber = Optional.ofNullable(invoiceDetails.invoiceNumber())
                .filter(inv -> !inv.isBlank())
                .orElse(claimNo.substring(0, Math.min(8, claimNo.length())));

        // Invoice date to be sent to CPP (formatted as required)
        String invoiceDate = convertDateStringToTargetFormat(
                // Use invoice date from provider if available
                invoiceDetails.invoiceDate() != null
                        ? invoiceDetails.invoiceDate()
                        // Otherwise fallback to claim billable period end date
                        : claim.getBillablePeriod().getEnd()
        );

        String erpClaimReferenceLink = erpClaimReferenceLinkBase.concat(claim.getIdPart());
        String shaNumber = claimDetailsDB.getPatient().getShaNumber();
        if (shaNumber.isEmpty()) {
            shaNumber = claimDetailsDB.getPatient().getCrId();
        }
        // Vendor Code
        if (StringUtils.isBlank(vendorCode)) {
            vendorCode = processVendorCode(claimDetailsDB);
        }
        if (StringUtils.isBlank(vendorName)) {
            vendorName = claimDetailsDB.getProvider().getName();
        }
        CreateClaimFundScheme createClaimFundScheme = CreateClaimFundScheme.builder()
                .claimReferenceLink(erpClaimReferenceLink)
                .vendorCode(vendorCode)
                //.vendorName(claimDetailsDB.getProvider().getName())
                .vendorName(vendorName)
                .beneficiaryName(claimDetailsDB.getPatient().getName())   // Patient Name
                .beneficiaryShaNo(shaNumber) // sha number
                .claimAmount(Helper.formatToTwoDecimalPlaces(totalClaimAmountApproved)) // Claim Approved amount
                //.claimNo(claim.getIdPart()) // Claim Number
                .claimNo(claimNo) // Claim Number
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .serviceCode(resultClaimRequest.interventionList)
                .interventionCodes(resultClaimRequest.interventionCodeRequests)// Service/Intervention List
                .fundScheme(fundScheme)
                .hasChild(false)
                .verifiedBy(verifiedBy)
                .creationDate(convertToStringFromDateTime(claimDetailsDB.getCreationDate()))
                .serviceEndDate(Helper.getServiceDate(claimDetailsDB.getDateFrom())).build();
        log.info("createClaimFundScheme: {}", createClaimFundScheme);
        List<String> validationResults = CreateClaimFundSchemeValidator.validateCreateClaimFundScheme(createClaimFundScheme);
        if (!validationResults.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", validationResults));
        }
        return createClaimFundScheme;
    }

    private ERPResponseModel createERPResponseStoreOnly(String claimId) {
        ERPResponseModel.ResponseBody.Result result = ERPResponseModel.ResponseBody.Result.builder()
                .statusCode("201")
                .message("Claim processed successfully.")
                .claimId(claimId)
                .id("")
                .build();
        ERPResponseModel.ResponseBody responseBody = ERPResponseModel.ResponseBody.builder()
                .result(result)
                .build();
        return ERPResponseModel.builder()
                .statusCode("201")
                .responseStatus("SUCCESS")
                .responseBody(responseBody)
                .build();
    }

    public static String processVendorCode(InsuranceClaim claimDetailsDB) {
//        String vendorCode = claimDetailsDB.getProvider().getMflCode();
//        if (StringUtils.isEmpty(vendorCode)) {
//            return claimDetailsDB.getProvider().getFidCode(); // Fallback to fidCode if vendorCode is empty or null
//        }
//        return vendorCode.startsWith("MFL-") ? vendorCode.replace("MFL-", "") : vendorCode;
        return claimDetailsDB.getProvider().getFidCode();
    }

    @SuppressWarnings("DuplicatedCode")
    private static @NotNull ResultClaimFund getFundSchemes(ResultClaimRequest resultClaimRequest, Map<String, Map<String, BigDecimal>> interventionAmountMapFinal, String claimID) {
        final BigDecimal[] totalEccif = {BigDecimal.ZERO}, totalPhc = {BigDecimal.ZERO}, totalShif = {BigDecimal.ZERO}, totalPmsf = {BigDecimal.ZERO};
        interventionAmountMapFinal.forEach((interventionCode, amounts) -> {
            if (amounts.isEmpty()) {
                log.warn("No amount found for claimID :{}, ", claimID);
                throw new RuntimeException("Invalid amount for : " + interventionCode + " : " + amounts);
            }
            // Accumulate the amounts for eccif, phc, shif, pmsf
            totalEccif[0] = totalEccif[0].add(amounts.getOrDefault(ECCIF, BigDecimal.ZERO));
            totalPhc[0] = totalPhc[0].add(amounts.getOrDefault(PHC, BigDecimal.ZERO));
            totalShif[0] = totalShif[0].add(amounts.getOrDefault(SHIF, BigDecimal.ZERO));
            totalPmsf[0] = totalPmsf[0].add(amounts.getOrDefault(PMSF, BigDecimal.ZERO));

            log.debug("ClaimID: {} , InterventionCode: {} | ECCIF: {} | PHC: {} | SHIF: {} | PMSF {}", claimID, interventionCode, amounts.getOrDefault(ECCIF, BigDecimal.ZERO), amounts.getOrDefault(PHC, BigDecimal.ZERO), amounts.getOrDefault(SHIF, BigDecimal.ZERO), amounts.getOrDefault(PMSF, BigDecimal.ZERO));
        });

        log.info("ClaimID: {}, requestedClaimAmountFromClaim : {}", claimID, resultClaimRequest.requestedClaimAmountFromClaim()[0]);
        BigDecimal totalAmountFinal = getTotalsApproveAmountAndComparison(totalEccif[0], totalPhc[0], totalShif[0], totalPmsf[0], resultClaimRequest.requestedClaimAmountFromClaim()[0], claimID);

        // Create the fund scheme list directly from the totals
        return new ResultClaimFund(List.of(
                CreateClaimFundScheme.FundScheme.builder().fundSchemeType(ECCIF.toUpperCase()).amount(totalEccif[0]).build(),
                CreateClaimFundScheme.FundScheme.builder().fundSchemeType(PHC.toUpperCase()).amount(totalPhc[0]).build(),
                CreateClaimFundScheme.FundScheme.builder().fundSchemeType(SHIF.toUpperCase()).amount(totalShif[0]).build(),
                CreateClaimFundScheme.FundScheme.builder().fundSchemeType(PMSF.toUpperCase()).amount(totalPmsf[0]).build())
                , totalAmountFinal);
    }

    private static BigDecimal getTotalsApproveAmountAndComparison(BigDecimal totalEccif, BigDecimal totalPhc, BigDecimal totalShif, BigDecimal totalPmsf, BigDecimal requestedClaimAmount, String claimID) {
        log.info("CLaimID : {}, Total ECCIF: {} | Total PHC: {} | Total SHIF: {} | Total PMSF {}", claimID, totalEccif, totalPhc, totalShif, totalPmsf);
        BigDecimal totalFundedAmount = totalEccif.add(totalPhc).add(totalShif).add(totalPmsf);
        log.info("Total Calculated Claim Amount: {},Requested {}, ClaimId: {}", totalFundedAmount, requestedClaimAmount, claimID);
        if (totalFundedAmount.compareTo(requestedClaimAmount) > 0) {
            log.warn("Total funded amount exceeds requested claim amount by {}, claimId: {}", totalFundedAmount.subtract(requestedClaimAmount), claimID);
        } else if (totalFundedAmount.compareTo(requestedClaimAmount) < 0) {
            log.warn("Total funded amount is less than requested claim amount by {}, ClaimID: {}", requestedClaimAmount.subtract(totalFundedAmount), claimID);
        } else {
            log.info("Total funded amount matches the requested claim amount exactly. , ClaimID: {}", claimID);
        }
        return totalFundedAmount;
    }


    public ClaimStatusUpdateResult updateStatusToSentForPaymentProcessing(List<UUID> claimNumbers) {

        log.info("Starting status update for {} claim(s) to SENT_FOR_PAYMENT_PROCESSING", claimNumbers.size());

        List<String> processedClaimIds = new ArrayList<>();
        List<String> failedClaimIds = new ArrayList<>();

        Optional<List<InsuranceClaim>> insuranceClaims =
                insuranceClaimsService.getApproveClaims(claimNumbers);

        if (insuranceClaims.isEmpty() || insuranceClaims.get().isEmpty()) {
            log.warn("No InsuranceClaim records found for claim IDs: {}", claimNumbers);
            return new ClaimStatusUpdateResult(processedClaimIds, failedClaimIds);
        }

        insuranceClaims.get().forEach(claims -> {

            String claimId = claims.getClaimId().toString();

            try {
                log.info("Processing claimId: {}", claimId);
                if (erpClaimsService.isExistsByClaimNoAndPaymentStatus(claimId, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name())) {
                    log.warn("Duplicate Request , Claim Number is already send to ERP for the Payment. {}", claimId);
                    Claim claim = claimPaymentHelper.getClaimObject(claimId);
                    log.info("FHIR Claim object fetched successfully for claimId: {}", claimId);

                    ClaimResponse claimResponse = getClaimResponse(claims);
                    log.debug("ClaimResponse object retrieved for claimId: {}", claimId);

                    UpdateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
                    log.info("Successfully updated status to SENT_FOR_PAYMENT_PROCESSING for claimId: {}", claimId);
                    processedClaimIds.add(claimId);
                }

            } catch (Exception ex) {
                log.error("Error updating status for claimId: {}", claimId, ex);
                failedClaimIds.add(claimId);
            }
        });
        log.info("Completed status update. Success: {}, Failed: {}", processedClaimIds.size(), failedClaimIds.size());
        return new ClaimStatusUpdateResult(processedClaimIds, failedClaimIds);
    }





    private record ResultClaimRequest(ClaimProcessInterventionData claimProcessInterventionData,
                                      List<String> interventionList, BigDecimal[] requestedClaimAmountFromClaim, List<InterventionCodeRequest> interventionCodeRequests ) {
    }

    private record ResultClaimFund(List<CreateClaimFundScheme.FundScheme> fundSchemes,
                                   BigDecimal totalClaimAmountFromClaim) {
    }

    private record ResultShaPmsf(BigDecimal eccif, BigDecimal phc, BigDecimal shif, BigDecimal pmsf) {
    }

    private record ResultSegregateFundSchemes(List<CreateClaimFundScheme.FundScheme> modifiedList,
                                              String fundSchemeType, BigDecimal amount) {
    }

    private static BigDecimal calculateSystemUtilizationCharge(BigDecimal amount, SystemUtilizationChargeView systemUtilizationCharge) {
        BigDecimal twoPercent = amount.multiply(systemUtilizationCharge.getRate()).divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP);
        return twoPercent.min(systemUtilizationCharge.getMaximumValue());
    }

    private static BigDecimal calculateContributionAmount(BigDecimal contributionAmount, BigDecimal totalAmount, BigDecimal utilizationAmount) {
        return contributionAmount.multiply(utilizationAmount).divide(totalAmount, 2, RoundingMode.HALF_UP);
    }
}