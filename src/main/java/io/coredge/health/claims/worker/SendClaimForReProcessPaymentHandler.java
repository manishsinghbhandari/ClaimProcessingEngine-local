package io.coredge.health.claims.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.component.ClientsComponent;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.enums.BenefitMasterSearchType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.helpers.CreateClaimFundSchemeValidator;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.eclaims.ClaimProcessInterventionData;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.models.eclaims.ERPResponseModel;
import io.coredge.health.claims.models.eclaims.InterventionCodeRequest;
import io.coredge.health.claims.models.master.BenefitMasterResponse;
import io.coredge.health.claims.service.BenefitMasterService;
import io.coredge.health.claims.service.ErpClaimsService;
import io.coredge.health.claims.service.InsuranceClaimsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.coredge.health.claims.constants.Constants.ClaimFundType.*;
import static io.coredge.health.claims.utils.FhirHelper.convertDateStringToTargetFormat;


@Slf4j
@Service
public class SendClaimForReProcessPaymentHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.claim.reference.link}")
    private String erpClaimReferenceLinkBase;

    @Value("${erp.claim.bifurcation.enable:false}")
    private Boolean erpClaimBifurcationEnable;

    @Autowired
    private BenefitMasterService benefitMasterService;

    @Autowired
    private ClientsComponent clientsComponent;

    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private ErpClaimsService erpClaimsService;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;


    public void handleReProcessPaymentErp(List<String> claimIDs, boolean store, boolean push) {
        if (claimIDs == null || claimIDs.isEmpty()) {
            log.warn("Error processing Batch list.: Nothing to process in Batch");
        }
        List<CreateClaimFundScheme> erpRequestBodyList = new ArrayList<>();
        assert claimIDs != null;
        for (String claimID : claimIDs) {
            try {
                log.info("ReProcessing for Erp payment request Claim ID {}", claimID);
                CreateClaimFundScheme erpRequestBody = createErpRequestBodyNew(claimID);
                // Store into DB is it's not already processed.
                if (Boolean.TRUE.equals(store) && erpRequestBody != null) {
                    saveToErpClaims(erpRequestBody, null, null);
                } else {
                    if (erpRequestBody == null) {
                        log.warn("ReProcessing for Erp request is null,  Claim ID {}", claimID);
                    } else {
                        log.warn("Skipping store to DB");
                    }
                }
                if (erpRequestBody != null) {
                    erpRequestBodyList.add(erpRequestBody);
                }
            } catch (Exception e) {
                // Log or handle exception properly
                log.warn("Error processing claimID: {} - {}", claimID, e.getMessage());
            }
        }
        if (erpRequestBodyList.isEmpty()) {
            log.warn("Error processing Batch list.: Nothing to process in Batch");
            return;
        }
        // push to erp
        if (Boolean.TRUE.equals(push)) {
            try {
                // Only push which are marked as processed True.
                callErpMultiAPI(erpRequestBodyList);
                log.info("Successfully processed Batch list, More Info see trail");
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.warn("Skipping push to ERP, Push Flag {}", push);
        }

    }

    private CreateClaimFundScheme createErpRequestBody(String claimId) {
        try {
            //
            ErpClaims byClaimNo = erpClaimsService.findByClaimNo(claimId);
            // only Process which are marked as isProcess false.
            if (byClaimNo != null) {
                // Added new column in Table. By default mark all the false or null
                if (Boolean.TRUE.equals(byClaimNo.getIsProcessed())) {
                    log.warn("Already Re-processed for Erp payment {}", claimId);
                    return null;
                }
            } else {
                log.warn("Claim ID not found {}", claimId);
                return null;
            }
            ResultClaimRequest resultClaimRequest = getResult(claimId);
            //Calculate the final intervention approved amount
            Map<String, Map<String, BigDecimal>> interventionAmountMapFinal = processClaimResponse(resultClaimRequest, claimId);
            // Add approved amount from claim for each intervention and do total by each type (phc,shif,eccif,pmsf)
            ResultClaimFund fundSchemesResult = getFundSchemes(resultClaimRequest, interventionAmountMapFinal, claimId);
            // Total net amount approved
            InsuranceClaim claimDetailsDB = insuranceClaimsService.getByExternalID(claimId);
            if (!EnumSet.of(ExtensionStateCodeEnum.APPROVED, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING).contains(claimDetailsDB.getStatus())) {
                log.warn("Can't Re-process [Reprocess Multi Flow] claim for payment request, ClaimID: {}, Status: {}", claimId, claimDetailsDB.getStatus());
                return null;
            }
            final BigDecimal[] totalClaimAmountApproved = {fundSchemesResult.totalClaimAmountFromClaim()};
            if ((totalClaimAmountApproved[0].compareTo(BigDecimal.ZERO) <= 0)) {
                // Fallback method for the where claimResponse have zero total for 100 of claims in approved state
                if (claimDetailsDB.getClaimedTotal().compareTo(BigDecimal.ZERO) > 0) {
                    totalClaimAmountApproved[0] = claimDetailsDB.getClaimedTotal();
                } else {
                    throw new DataValidationException("Total Claim amount can't be Zero");
                }
            }

            if (StringUtils.isNotEmpty(byClaimNo.getStatus()) && byClaimNo.getStatus().equals("READY_FOR_PAYMENT") && byClaimNo.getRequest() != null) {
                return objectMapper.readValue(byClaimNo.getRequest(), CreateClaimFundScheme.class);
            } else {
                return getCreateClaimFundScheme(claimId,
                        totalClaimAmountApproved, resultClaimRequest, fundSchemesResult.fundSchemes, claimDetailsDB, byClaimNo);
            }
        } catch (Exception e) {
            log.info("!!!SendClaimForPaymentToERP[Reprocess Multi Flow] Exception: {}", e.getLocalizedMessage());
        }
        return null;
    }


    private CreateClaimFundScheme createErpRequestBodyNew(String claimId) {
        try {
            //
            InsuranceClaim byExternalID = insuranceClaimsService.findByExternalID(claimId);
            // only Process which are marked as isProcess false.
            if (byExternalID != null) {
                // Only Pick that status ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING
                if (byExternalID.getStatus() == null || byExternalID.getStatus() == ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING) {
                    log.warn("Claims processed for Erp claims {}, status : {}", claimId, byExternalID.getStatus());
                }
            } else {
                log.warn("Claim ID not found {}", claimId);
                return null;
            }
            ResultClaimRequest resultClaimRequest = getResult(claimId);
            //Calculate the final intervention approved amount
            Map<String, Map<String, BigDecimal>> interventionAmountMapFinal = processClaimResponse(resultClaimRequest, claimId);
            // Add approved amount from claim for each intervention and do total by each type (phc,shif,eccif,pmsf)
            ResultClaimFund fundSchemesResult = getFundSchemes(resultClaimRequest, interventionAmountMapFinal, claimId);
            // Total net amount approved
            InsuranceClaim claimDetailsDB = insuranceClaimsService.getByExternalID(claimId);
            if (!EnumSet.of(ExtensionStateCodeEnum.APPROVED, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING).contains(claimDetailsDB.getStatus())) {
                log.warn("Can't Re-process [Reprocess Multi Flow] claim for payment request, ClaimID: {}, Status: {}", claimId, claimDetailsDB.getStatus());
                return null;
            }
            final BigDecimal[] totalClaimAmountApproved = {fundSchemesResult.totalClaimAmountFromClaim()};
            if ((totalClaimAmountApproved[0].compareTo(BigDecimal.ZERO) <= 0)) {
                // Fallback method for the where claimResponse have zero total for 100 of claims in approved state
                if (claimDetailsDB.getClaimedTotal().compareTo(BigDecimal.ZERO) > 0) {
                    totalClaimAmountApproved[0] = claimDetailsDB.getClaimedTotal();
                } else {
                    throw new DataValidationException("Total Claim amount can't be Zero");
                }
            }

            return getCreateClaimFundScheme(claimId,
                    totalClaimAmountApproved, resultClaimRequest, fundSchemesResult.fundSchemes, claimDetailsDB, null);

        } catch (Exception e) {
            log.info("!!!SendClaimForPaymentToERP[Reprocess Multi Flow] Exception: {}", e.getLocalizedMessage());
        }
        return null;
    }

    private void callErpMultiAPI(List<CreateClaimFundScheme> claimFundSchemeList) throws JsonProcessingException {
        ERPResponseModel claimErpResponse;
        //Hit The ERP, Request for Payments
        log.info("Starting Claim submission to ERP for ClaimID in Batch ");
        // Send to Claim To ERP for Payments.
        String claimErpResp = clientsComponent.processERPClaimMulti(claimFundSchemeList);
        List<String> errorMarkers = List.of("Error:", "ERROR");
        boolean hasError = errorMarkers.stream().anyMatch(claimErpResp::contains);
        if (!hasError) {
            claimErpResponse = objectMapper.readValue(claimErpResp, ERPResponseModel.class);
            if (claimErpResp.contains("201")) {
                log.info("Claim submitted Success to the ERP in Batch");
                AtomicInteger seq = new AtomicInteger(0);
                claimFundSchemeList.forEach(createClaimFundScheme -> {
                    ERPResponseModel claimErpResponseNew = new ERPResponseModel();
                    BeanUtils.copyProperties(claimErpResponse, claimErpResponseNew);
                    try {
                        String getIdForParticularClaimID = claimErpResponse.getResponseBody().getResult().getIds().get(seq.get());
                        claimErpResponse.getResponseBody().getResult().setId(getIdForParticularClaimID);
                    } catch (Exception e) {
                        log.warn("ref not found, claim ID: {}", createClaimFundScheme.getClaimNo());
                    }

                    claimErpResponseNew.getResponseBody().getResult().setClaimId(createClaimFundScheme.getClaimNo());
                    saveToErpClaims(createClaimFundScheme, claimErpResponseNew, null); // Push to DB
                    seq.getAndIncrement();
                });
            } else {
                if (claimErpResp.contains("409")) {
                    log.warn("ErpResponse Conflict 409 for the Batch File :Response : {}", claimErpResp);
                }

            }
        } else {
            log.warn("Error while sending payment request for batch claim ID");
        }
    }


    public void saveToErpClaims(CreateClaimFundScheme createClaimFundScheme, ERPResponseModel claimErpResponse, String claimResponseID) {
        saveToErpClaims(createClaimFundScheme, claimErpResponse, claimResponseID, null);
    }

    public void saveToErpClaims(CreateClaimFundScheme createClaimFundScheme, ERPResponseModel claimErpResponse, String claimResponseID, String refNumber) {
        try {
            log.info("Saving [Reprocess] Claim ID to ERP : {}", createClaimFundScheme.getClaimNo());
            ErpClaims erpClaims = Objects.requireNonNullElseGet(erpClaimsService.findByClaimNo(createClaimFundScheme.getClaimNo()), ErpClaims::new);

            BeanUtils.copyProperties(createClaimFundScheme, erpClaims);
            erpClaims.setServiceCode(String.valueOf(createClaimFundScheme.getServiceCode()));
            // Case only storing to DB. Not sending ERP
            erpClaims.setStatus("READY_FOR_PAYMENT");
            erpClaims.setPaymentStatus(ExtensionStateCodeEnum.APPROVED.name());
            if (claimErpResponse != null) {
                erpClaims.setPaymentStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());
                erpClaims.setHttpCallStatus(claimErpResponse.getStatusCode());
                erpClaims.setHttpCallMsg(claimErpResponse.getResponseBody().getResult().getMessage());
                if (claimErpResponse.getResponseBody().getResult().getId() != null) {
                    erpClaims.setErpId(claimErpResponse.getResponseBody().getResult().getId());
                }
                // Marked process true and also add current date
                erpClaims.setIsProcessed(true);
                erpClaims.setProcessDate(LocalDateTime.now());
                erpClaims.setStatus("SENT_FOR_PAYMENT");
            }
            if (StringUtils.isNotEmpty((claimResponseID))) {
                erpClaims.setClaimResponseId(claimResponseID);
            }
            // added for reprocess
            erpClaims.setCreationDate(LocalDateTime.now());
            erpClaims.setLastModifiedDate(LocalDateTime.now());

            if (StringUtils.isNotEmpty(refNumber)) {
                erpClaims.setErpId(refNumber);
            }

            // Save the Request JSON
            String jsonStr = objectMapper.writeValueAsString(createClaimFundScheme);
            if (StringUtils.isNotEmpty(jsonStr)) {
                erpClaims.setRequest(jsonStr);
            }

            log.info("saving to DB [Reprocess multi Flow] for Claim-ID - {}", createClaimFundScheme.getClaimNo());
            erpClaimsService.saveErpClaims(erpClaims);
        } catch (JsonProcessingException e) {
            log.warn("JsonProcessingException: {}", e.getMessage());
        }
    }

    private @NotNull SendClaimForReProcessPaymentHandler.ResultClaimRequest getResult(String claimID) {
        Map<String, ClaimProcessInterventionData.InterventionCodeDetail> interventionDetails = new HashMap<>();
        Map<String, Map<String, BigDecimal>> interventionAmountMap = new HashMap<>();
        final BigDecimal[] requestedClaimAmountFromClaim = {BigDecimal.ZERO};
        List<String> interventionList = new ArrayList<>();
        List<InterventionCodeRequest> interventionCodeRequests= new ArrayList<>();
        insuranceClaimsService.fetchClaimItems(claimID).forEach(chDTO -> {
            String interventionCode = chDTO.getInterventionCode();
            List<BenefitMasterResponse> interventionData = benefitMasterService.fetchBenefitsMasterData(BenefitMasterSearchType.INTERVENTION.getValue(), interventionCode);
            var firstInterventionData = interventionData.getFirst();
            Map<String, BigDecimal> amountFund = Map.of(ECCIF, Helper.getPercentageValue(firstInterventionData.getEccif()), PHC, Helper.getPercentageValue(firstInterventionData.getPhc()),
                    SHIF, Helper.getPercentageValue(firstInterventionData.getShif()), PMSF, Helper.getPercentageValue(firstInterventionData.getPmsf()));
            interventionList.add(interventionCode);
            interventionAmountMap.put(interventionCode, amountFund);
            interventionDetails.put(interventionCode, ClaimProcessInterventionData.InterventionCodeDetail.builder().interventionCode(interventionCode).interventionAmountPercentageShare(amountFund).build());
            requestedClaimAmountFromClaim[0] = requestedClaimAmountFromClaim[0].add(chDTO.getApprovedPrice());
            InterventionCodeRequest interventionCodeRequest= new InterventionCodeRequest();
            interventionCodeRequest.setAmount(chDTO.getApprovedPrice());
            interventionCodeRequest.setServiceCodeType(chDTO.getInterventionCode());
            interventionCodeRequests.add(interventionCodeRequest);
        });

        // Logging
        log.info("claimID: {}, approvedAmountFromClaim: {} | interventionAmountMap: {}", claimID, requestedClaimAmountFromClaim[0], interventionAmountMap);
        if (interventionAmountMap.isEmpty()) {
            log.warn("No claimLineItems/intervention amount found for Claim ID {}", claimID);
            return null;
        }
        return new ResultClaimRequest(ClaimProcessInterventionData.builder().interventionCodeDetailsStr(interventionDetails).build(), interventionList, requestedClaimAmountFromClaim, interventionCodeRequests);
    }

    private Map<String, Map<String, BigDecimal>> processClaimResponse(ResultClaimRequest resultClaimRequest, String claimId) {
        if (resultClaimRequest == null) {
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
     * Calculates and returns SHA and PMSF fund allocations for a claim.
     *
     * @param interventionCode      The intervention code.
     * @param interventionAmountMap Map containing intervention amounts.
     * @param amount                The total claim amount.
     * @param claimId               The claim ID.
     * @return ResultShaPmsf object containing calculated amounts.
     */
    protected ResultShaPmsf calculateCombinedShaPmsfAmount(String interventionCode, Map<String, BigDecimal> interventionAmountMap, BigDecimal amount, String claimId) {

        var benefit = benefitMasterService.fetchBenefitsMasterData(BenefitMasterSearchType.INTERVENTION.getValue(), interventionCode).getFirst();
        var claim = insuranceClaimsService.fetchClaimItems(claimId).stream()
                .filter(c -> interventionCode.equals(c.getInterventionCode()))
                .findFirst().orElseThrow();

        BigDecimal eccif = BigDecimal.ZERO;
        BigDecimal phc = Helper.calculateTheAmount(amount, interventionAmountMap.get(PHC));
        BigDecimal shif, pmsf, eccifAmount = BigDecimal.ZERO;
        boolean eccifApplicable = benefit.getEccif().contains("100");
        boolean PmsfApplicable = benefit.getPmsf().contains("100");

        if (eccifApplicable) {
            eccifAmount = Objects.requireNonNullElse(claim.getEccifAmount(), BigDecimal.ZERO);
            eccif = Helper.calculateTheAmount(eccifAmount.compareTo(BigDecimal.ZERO) > 0 ? eccifAmount : amount, interventionAmountMap.get(ECCIF));
        }
        if (PmsfApplicable) {
            if (erpClaimBifurcationEnable) { // Only For PMSF
                var pmfAmount = Objects.requireNonNullElse(claim.getPmfAmount(), BigDecimal.ZERO);
                pmsf = Helper.calculateTheAmount(pmfAmount, interventionAmountMap.get(PMSF));
                shif = Helper.calculateTheAmount(amount.subtract(eccifAmount).subtract(pmfAmount), interventionAmountMap.get(SHIF));
            } else {
                boolean isPmf = claim.isPmfItem();
                pmsf = Helper.calculateTheAmount(amount, isPmf ? interventionAmountMap.get(PMSF) : BigDecimal.ZERO);
                shif = Helper.calculateTheAmount(amount, isPmf ? BigDecimal.ZERO : interventionAmountMap.get(SHIF));
            }
        } else {
            shif = Helper.calculateTheAmount(amount, interventionAmountMap.get(SHIF));
            pmsf = Helper.calculateTheAmount(amount.subtract(eccifAmount), interventionAmountMap.get(PMSF));
        }

        return new ResultShaPmsf(eccif, phc, shif, pmsf);
    }

    private CreateClaimFundScheme getCreateClaimFundScheme(String claimID, BigDecimal[] totalClaimAmountApproved, ResultClaimRequest resultClaimRequest, List<CreateClaimFundScheme.FundScheme> fundScheme, InsuranceClaim claimDetailsDB, ErpClaims byClaimNo) {
        String inVoiceNumber;
        ClaimPaymentHelper.InvoiceDetails invoiceDetails = null;
        Claim claim = null;
        if (byClaimNo == null) {
            claim = claimPaymentHelper.getClaimObject(claimID);
            invoiceDetails = claimPaymentHelper.getInvoiceDetails(claim);
            inVoiceNumber = claimPaymentHelper.getInvoiceNumber(claim);

        } else {
            inVoiceNumber = byClaimNo.getInvoiceNumber();
        }

        // Use provider invoice number if available; otherwise fallback to first 8 chars of claim number
        inVoiceNumber = Optional.ofNullable(inVoiceNumber)
                .filter(inv -> !inv.isBlank())
                .orElse(claimID.substring(0, Math.min(8, claimID.length())));

        // Invoice date to be sent to CPP (formatted as required)
        String invoiceDate = convertDateStringToTargetFormat(
                // Use invoice date from provider if available
                invoiceDetails.invoiceDate() != null
                        ? invoiceDetails.invoiceDate()
                        // Otherwise fallback to claim billable period end date
                        : claim.getBillablePeriod().getEnd()
        );

        String erpClaimReferenceLink = erpClaimReferenceLinkBase.concat(claimID);
        String shaNumber = claimDetailsDB.getPatient().getShaNumber();
        if (shaNumber.isEmpty()) {
            shaNumber = claimDetailsDB.getPatient().getCrId();
        }
        // Vender Code
        String vendorCode = processVendorCode(claimDetailsDB);
        assert invoiceDetails != null;
        CreateClaimFundScheme createClaimFundScheme = CreateClaimFundScheme.builder()
                .claimReferenceLink(erpClaimReferenceLink)
                .vendorCode(vendorCode)
                .vendorName(claimDetailsDB.getProvider().getName())
                .beneficiaryName(claimDetailsDB.getPatient().getName())   // Patient Name
                .beneficiaryShaNo(shaNumber) // sha number
                .claimAmount(totalClaimAmountApproved[0]) // Claim Approved amount
                .claimNo(claimID) // Claim Number
                .claimAmount(Helper.formatToTwoDecimalPlaces(totalClaimAmountApproved[0]))
                .invoiceNumber(inVoiceNumber)
                .serviceCode(resultClaimRequest.interventionList) // Service/Intervention List
                .interventionCodes(resultClaimRequest.interventionCodeRequests)
                .fundScheme(fundScheme).claimAmount(totalClaimAmountApproved[0])
                .serviceEndDate(Helper.getServiceDate(claimDetailsDB.getDateFrom()))
                .invoiceDate(invoiceDate)
                .build();
        log.info("createClaimFundScheme: {}", createClaimFundScheme);
        List<String> validationResults = CreateClaimFundSchemeValidator.validateCreateClaimFundScheme(createClaimFundScheme);
        if (!validationResults.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", validationResults));
        }
        return createClaimFundScheme;
    }


    public static String processVendorCode(InsuranceClaim claimDetailsDB) {
        String vendorCode = claimDetailsDB.getProvider().getMflCode();
        if (StringUtils.isEmpty(vendorCode)) {
            return claimDetailsDB.getProvider().getFidCode(); // Fallback to fidCode if vendorCode is empty or null
        }
        return vendorCode.startsWith("MFL-") ? vendorCode.replace("MFL-", "") : vendorCode;
    }

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

    private record ResultClaimRequest(ClaimProcessInterventionData claimProcessInterventionData, List<String> interventionList, BigDecimal[] requestedClaimAmountFromClaim, List<InterventionCodeRequest> interventionCodeRequests) {
    }

    private record ResultClaimFund(List<CreateClaimFundScheme.FundScheme> fundSchemes, BigDecimal totalClaimAmountFromClaim) {
    }

    protected record ResultShaPmsf(BigDecimal eccif, BigDecimal phc, BigDecimal shif, BigDecimal pmsf) {
    }

}