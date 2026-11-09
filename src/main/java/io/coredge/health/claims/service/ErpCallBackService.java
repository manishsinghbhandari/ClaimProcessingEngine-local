
package io.coredge.health.claims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.dto.ClaimStatusUpdateResult;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.app.WorkFlowException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.ErpReprocessListClaimsRequest;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import io.coredge.health.claims.worker.SendClaimForReProcessPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.coredge.health.claims.constants.Constants.PaymentStatus.PAID;
import static io.coredge.health.claims.constants.Constants.PaymentStatus.PAYMENT_COMPLETED;
import static io.coredge.health.claims.constants.Constants.PaymentType.PARTIAL;


@Slf4j
@Service
public class ErpCallBackService {


    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.claim.back.enable:true}")
    private Boolean erpClaimBackApiEnable;

    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private ErpClaimsService erpClaimsService;

    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private PaymentsService paymentsService;

    @Autowired
    private SendClaimForReProcessPaymentHandler sendClaimForRePaymentHandler;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;


    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private StatusUpdateService statusUpdateService;

    private static final int BATCH_SIZE = 300;

    @Async("virtualThreadExecutor")
    public void processErpClaimIds(List<String> claimIDs, boolean store, Boolean push) {
        log.info("Received Request For Batch Reprocess , Size : {}, Store: {}, Push: {}", claimIDs.size(), store, push);
        sendClaimForRePaymentHandler.handleReProcessPaymentErp(claimIDs, store, push);
    }


    @Async("virtualThreadExecutor")
    public void reProcessErpRequestClaimIds(ErpReprocessListClaimsRequest request) {
        List<String> claimIds = request.getClaimIDs();
        log.info("Received Request For Reprocess approved Status claims, Size : {}, ", claimIds.size());
        // Create a virtual thread per batch
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int totalClaims = claimIds.size();
            for (int i = 0; i < totalClaims; i += BATCH_SIZE) {
                int finalI = i;
                executor.submit(() -> processClaimsIdsBatch(claimIds.subList(finalI, Math.min(finalI + BATCH_SIZE, totalClaims)), request.getForce()));
            }

        }
    }

    private void processClaimsIdsBatch(List<String> batch, Boolean force) {
        // Add your processing logic here
        for (String claimId : batch) {
            reProcessErpRequest(claimId, force);
        }
    }

    public void reProcessErpRequest(String claimID, Boolean force) {
        try {
            log.info("ReProcessing for Erp payment request Claim ID {}", claimID);
            sendClaimForPaymentHandler.handlePaymentErp(null, null, claimID, true, force);
        } catch (Exception e) {
            // Log or handle exception properly
            log.warn("Error processing claimID: {} - {}", claimID, e.getMessage());
        }
    }

    @Async("virtualThreadExecutor")
    public void processCsv(MultipartFile file) {
        if (file.isEmpty()) {
            log.info("File is empty!");
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(10); // 4 parallel threads

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            List<Map<String, String>> batch = new ArrayList<>();

            for (CSVRecord record : csvParser) {
                Map<String, String> row = new HashMap<>();
                row.put("number", record.get("number"));
                row.put("claim_no", record.get("claim_no"));
                row.put("reference", record.get("reference"));
                batch.add(row);

                if (batch.size() >= BATCH_SIZE) {
                    List<Map<String, String>> batchCopy = new ArrayList<>(batch);
                    executor.submit(() -> processBatch(batchCopy));
                    batch.clear(); // Reset for next batch
                }
            }

            // Process remaining records
            if (!batch.isEmpty()) {
                executor.submit(() -> processBatch(batch));
            }

        } catch (Exception e) {
            log.warn("Error processing CSV: {}", e.getMessage(), e);
        } finally {
            executor.shutdown(); // Stop accepting new tasks, but complete existing ones
            try {
                executor.awaitTermination(120L, TimeUnit.SECONDS); // Wait 2min
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve the interrupt status
            }
        }
    }

    private void processBatch(List<Map<String, String>> batch) {
        for (Map<String, String> row : batch) {
            applyBusinessLogic(row);
        }
    }

    private void applyBusinessLogic(Map<String, String> row) {
        String logIdentifier = "AsyncPaid Update warn, Warn : {} , ClaimID : {}";
        int number = Integer.parseInt(row.get("number"));
        String claimId = row.get("claim_no");
        String refNum = row.get("reference");
        CreateClaimFundScheme request = null;
        if (StringUtils.isEmpty(claimId)) {
            log.warn(logIdentifier, "Claim ID is null!", claimId);
        }

        log.info("Processing AsyncPaid number {}, claimId: {}", number, claimId);
        ErpClaims byClaimNo = erpClaimsService.findByClaimNo(claimId);
        if (byClaimNo == null) {
            log.info("byClaimNo is null,  number {}, claimId: {}", number, claimId);
            // Handle the ID that with status 'SENT_FOR_PAYMENT_PROCESSING' but not found in the erp_claims.
            InsuranceClaim byExternalID = insuranceClaimsService.findByExternalID(claimId);
            if (byExternalID == null) {
                log.warn("AsyncPaid Update ClaimId '{}' is not found,", claimId);
                return;
            }
            //check if claim exist in claims table with status "SENT_FOR_PAYMENT_PROCESSING", "APPROVED
            List<ExtensionStateCodeEnum> states = List.of(ExtensionStateCodeEnum.APPROVED, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
            if (states.contains(byExternalID.getStatus())) {
                // Reprocess the claim Request.
                log.info(logIdentifier, "Reprocessing the ClaimID", claimId);
                reProcessErpRequest(claimId, false);
                byClaimNo = erpClaimsService.findByClaimNo(claimId);
            } else {
                log.warn("AsyncPaid Update ClaimId '{}' is not found, Or already status : {}", claimId, byExternalID.getStatus());
                return;
            }
        }
        if (StringUtils.isNotBlank(byClaimNo.getPaymentStatus()) && (byClaimNo.getPaymentStatus().equals(PAID) || byClaimNo.getPaymentStatus().equals(PAYMENT_COMPLETED))) {
            log.warn(logIdentifier, "IPayment status is already Paid ", claimId);
            return;
        }
        String erpReqBody = byClaimNo.getRequest();
        if (StringUtils.isNotBlank(erpReqBody)) {
            try {
                request = objectMapper.readValue(erpReqBody, CreateClaimFundScheme.class);
                request.setPaymentStatus(PAID);
                request.setClaimAmount(byClaimNo.getClaimAmount());
            } catch (JsonProcessingException e) {
                log.warn(logIdentifier, e.getMessage(), claimId);
            }
        } else {
            log.info("IPayment creating payment body: {}", claimId);
            request = CreateClaimFundScheme.builder()
                    .claimNo(claimId)
                    .paymentStatus(PAID)
                    .beneficiaryName(byClaimNo.getBeneficiaryName())
                    .beneficiaryShaNo(byClaimNo.getBeneficiaryShaNo())
                    .build();
        }


        Claim claim = null;
        try {
            claim = claimPaymentHelper.getClaimObject(claimId);

        } catch (Exception e) {
            log.warn(logIdentifier, claimId, e.getMessage());
            return;
        }
        if (claim == null) {
            log.warn(logIdentifier, "Claim not found for ID", claimId);
            return;
        }


        ClaimResponse claimResponse = null;
        // Temp Fix for the multiple claim response issue
        if (StringUtils.isNotEmpty(byClaimNo.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(byClaimNo.getClaimResponseId());
        }
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(claimId);
            if (claimResponse == null) {
                log.warn(logIdentifier, "Claim response not found against claim ID", claimId);
                return;
            }
        }
        erpClaimsPaidStatus(new Validate(request, claimId, byClaimNo, claim, claimResponse), refNum);

    }

    public void erpClaimsPaidStatus(Validate result, String ref) {
        log.info("AsyncPaid Callback request received from ERP SYSTEM");
        log.info("AsyncPaid Validation - true ,  payment response from ERP for claimId {}", result.claimId());

        UpdatePaymentStatus paymentStatus = getUpdatePaymentStatus(result);
        statusUpdateService.triggerCallBackStatusUpdate(result.claimId(), true, true, false);

        log.info("AsyncPaid ERP Payment {} for claim-No - {}", paymentStatus.isPaid() ? "completed" : "declined", result.claimId());

        // Push to DB
        sendClaimForPaymentHandler.saveToErpClaims(true, result.request(), null, null, ref);

        boolean requireInvestigation = false;
        // Move the workflow further by sending message to engine.
        // TODO: requireInvestigation needs to be handled correctly.
        // requireInvestigation = determineInvestigationNeeded(claimResponse); // Implement logic here
        try {
            zeebe.newPublishMessageCommand().messageName("paymentStatusConfirmation")
                    .correlationKey(result.claimId())
                    .variable("requireInvestigation", requireInvestigation)
                    .send().join();

            log.info("AsyncPaid Workflow message published successfully for claimId: {}", result.claimId());
        } catch (Exception e) {
            log.warn("AsyncPaid Failed to publish message for claimId: {}", result.claimId(), e);
            throw new WorkFlowException("AsyncPaid Failed to publish message to workflow engine", e);
        }
        // Force fully call back status
        try {
            String paymentsAdviseBundle = paymentsService.getPaymentsNotice(result.claimId());
            log.info("AsyncPaid Finally callPaymentWebhooks, ClaimId:  {}", result.claimId());
            claimsHandlerUtility.callPaymentWebhooks(paymentsAdviseBundle, result.claimId, paymentStatus.claimResponse().getIdPart(), ExtensionStateCodeEnum.PAID);
        } catch (Exception e) {
            log.warn("AsyncPaid Error (Erp call back Flow) while calling claim payment to callback: {}", e.getMessage());
        }

        log.info("AsyncPaid Update Successfully, ClaimID : {}", result.claimId());
    }

    private @NotNull UpdatePaymentStatus getUpdatePaymentStatus(Validate result) {
        ClaimResponse claimResponse;
        claimResponse = result.claimResponse;
        // update the extension

        log.info("getUpdatePaymentStatus : claimID: {}", result.claimId);
        boolean isPaid = PAID.equalsIgnoreCase(result.request().getPaymentStatus());
        ClaimPaymentHelper.ClaimResponseStatus claimResponseStatus = claimPaymentHelper.addPaymentComponentToClaimResponse(result.claim(), claimResponse, result.request());
        claimResponse = claimResponseStatus.claimResponse();

        // Payments details
        ClaimPaymentHelper.PaymentStatus paymentStatus = claimResponseStatus.paymentStatus();
        result.claim().setStatus(Claim.ClaimStatus.ACTIVE);
        claimResponse.setOutcome(isPaid ? (paymentStatus.aggregateStatus().equals(PARTIAL) ? ClaimResponse.RemittanceOutcome.PARTIAL : ClaimResponse.RemittanceOutcome.COMPLETE) : ClaimResponse.RemittanceOutcome.ERROR);
        ExtensionStateCodeEnum state = isPaid ? (paymentStatus.aggregateStatus().equals(PARTIAL) ? ExtensionStateCodeEnum.PAYMENT_PARTIAL : ExtensionStateCodeEnum.PAYMENT_COMPLETED) :
                paymentStatus.isAllDeclined() ? ExtensionStateCodeEnum.PAYMENT_DECLINED : ExtensionStateCodeEnum.PAYMENT_PARTIAL;
        claimsHandlerUtility.updateClaimAndResponseStatus(result.claimId(), result.claim(), claimResponse, state);
        return new UpdatePaymentStatus(claimResponse, isPaid);
    }

    private record UpdatePaymentStatus(ClaimResponse claimResponse, boolean isPaid) {
    }

    /**
     * Method to process the response back from ERP.
     *
     * @param erpResponse Call back request
     */
    public void erpClaimsCallBack(String erpResponse) {
        // Disable the Callback Request : Mail by anurag to handle 5L wrong mapping shif data
        if (!erpClaimBackApiEnable) {
            log.info("Erp callback Api is disabled, Skipping request");
            return;
        }
        log.info("Callback request received from ERP SYSTEM");
        // Validate Request and update status
        claimsStatusUpdate(validateRequest(erpResponse));
    }


    @Async("virtualThreadExecutor")
    public void claimsStatusUpdate(Validate result) {
        log.info("Validation - true ,  payment response from ERP for claimId {}", result.request.getClaimNo());
        UpdatePaymentStatus paymentStatus = getUpdatePaymentStatus(result);
        log.info("ERP Payment {} for claim-No - {}", paymentStatus.isPaid ? "completed" : "declined", result.request.getClaimNo());

        // Push to DB
        sendClaimForPaymentHandler.saveToErpClaims(true, result.request(), null, null);
        log.info("Sending status update for claim-No - {}", result.request.getClaimNo());
        statusUpdateService.triggerCallBackStatusUpdate(result.claimId(),true,false, false);

        boolean requireInvestigation = false;
        // Move the workflow further by sending message to engine.
        // TODO: requireInvestigation needs to be handled correctly.
        // requireInvestigation = determineInvestigationNeeded(claimResponse); // Implement logic here
        try {
            zeebe.newPublishMessageCommand().messageName("paymentStatusConfirmation")
                    .correlationKey(result.claimId())
                    .variable("requireInvestigation", requireInvestigation)
                    .send().join();

            log.info("Workflow message published successfully for claimId: {}", result.claimId());
        } catch (Exception e) {
            log.warn("Failed to publish message for claimId: {}", result.claimId(), e);
            throw new WorkFlowException("Failed to publish message to workflow engine", e);
        }
        // Force fully call back status
        try {
            String paymentsAdviseBundle = paymentsService.getPaymentsNotice(result.claimId());
            log.info("Finally callPaymentWebhooks, ClaimId:  {}", result.claimId());
            claimsHandlerUtility.callPaymentWebhooks(paymentsAdviseBundle, result.claimId, paymentStatus.claimResponse.getIdPart(), ExtensionStateCodeEnum.PAID);
        } catch (Exception e) {
            log.warn("Error (Erp call back Flow) while calling claim payment to callback: {}", e.getMessage());
        }
    }

    private @NotNull ErpCallBackService.Validate validateRequest(String erpResponse) {
        CreateClaimFundScheme request;
        try {
            request = objectMapper.readValue(erpResponse, CreateClaimFundScheme.class);
            log.info("ClaimNumber: {}, paymentStatus: {}", request.getClaimNo(), request.getPaymentStatus());
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ERP response: {}", e.getMessage(), e);
            throw new DataValidationException("Unable to process ERP callback", e);
        }

        if (request.getClaimNo() == null || request.getPaymentStatus() == null) {
            log.info("Invalid ERP response: Missing mandatory fields.");
            throw new BadRequestException("Invalid ERP callback payload.");
        }

        if (!request.getPaymentStatus().equalsIgnoreCase(PAID) && StringUtils.isEmpty(request.getRejection_reason())) {
            log.info("Invalid ERP response: Missing mandatory fields. for rejection reason");
            throw new BadRequestException("Invalid ERP callback payload. rejection_reason missing");
        }

        String claimId = request.getClaimNo();
        ErpClaims byClaimNo = erpClaimsService.findByClaimNo(claimId);
        if (byClaimNo == null) {
            log.info("Invalid request. Claim number not found in DB");
            throw new BadRequestException(String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
        }

        // Validate process request for correct invoice number
        String reqInvoiceNumber = claimPaymentHelper.getPartAfterColon(request.getInvoiceNumber());
        if (!byClaimNo.getInvoiceNumber().contains(reqInvoiceNumber)) {
            log.info("Invalid request. Claim number not found in DB");
            throw new BadRequestException(String.format("Invalid callback request. Invoice number [%s] not found", reqInvoiceNumber));
        }
        String sanitizedClaimNo = claimId.contains("_") ? claimId.split("_")[0] : claimId;
        Claim claim = claimPaymentHelper.getClaimObject(sanitizedClaimNo);
        if (claim == null) {
            log.warn("Claim not found for ID: {}", claimId);
            throw new BadRequestException(String.format("Invalid callback request. Claim-Id [%s] not found in system.", claimId));
        }

        ClaimResponse claimResponse = null;
        // Temp Fix for the multiple claim response issue
        if (StringUtils.isNotEmpty(byClaimNo.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(byClaimNo.getClaimResponseId());
        }
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(sanitizedClaimNo);
            if (claimResponse == null) {
                log.warn("Claim response not found against claim ID: {}", claimId);
                throw new BadRequestException("Invalid Claim ID, Claim response not found");
            }
        }
        return new Validate(request, sanitizedClaimNo, byClaimNo, claim, claimResponse);
    }

    public record Validate(CreateClaimFundScheme request, String claimId, ErpClaims byClaimNo, Claim claim,
                           ClaimResponse claimResponse) {
    }


    public ClaimStatusUpdateResult updateStatusToSentForPaymentProcessing(List<UUID> cliamNuber){
       return sendClaimForPaymentHandler.updateStatusToSentForPaymentProcessing(cliamNuber);
    }

}
