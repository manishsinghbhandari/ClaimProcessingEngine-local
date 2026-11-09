package io.coredge.health.claims.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.coredge.health.claims.client.CppClient;
import io.coredge.health.claims.client.ErpClient;
import io.coredge.health.claims.client.IFSClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AfyanguClaimsAudit;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.AfyanguAuditRepository;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.ExternalServiceException;
import io.coredge.health.claims.models.ErpResponseRecord;
import io.coredge.health.claims.models.benefits.FundProjection;
import io.coredge.health.claims.models.eclaims.*;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.repository.PaymentProcessingRepository;
import io.coredge.health.claims.payments.service.PaymentAuditService;
import io.coredge.health.claims.payments.transform.PaymentProcessingTransformer;
import io.coredge.health.claims.service.BenefitMasterService;
import io.coredge.health.claims.service.InsuranceClaimsService;
import io.coredge.health.claims.service.StatusUpdateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.exception.models.ErrorCode.MISSING_CLAIM_IN_BUNDLE;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.PaymentStatus.ERP_PAYMENT_NEEDS_REPARATION;
import static io.coredge.health.claims.payments.enums.PaymentsEnum.PaymentStatus.SENT_TO_ERP;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientsComponent {

    private final ErpClient erpClient;
    private final IfsClientComponent ifsClientComponet;
    private final ObjectMapper objectMapper;
    private final IFSClient ifsClient;
    private final CppClient cppClient;
    private final CppComponent cppComponent;
    private final AfyanguAuditRepository afyanguAuditRepository;
    private final InsuranceClaimsService insuranceClaimsService;
    private final BenefitMasterService benefitMasterService;
    private final StatusUpdateService statusUpdateService;
    private final PaymentProcessingTransformer paymentProcessingTransformer;
    private final PaymentProcessingRepository paymentProcessingRepository;
    private final PaymentAuditService auditService;

    @Value("${cpp.payment.enable:false}")
    private boolean cppPayment;

    @Value("${ifs.integration.enable:false}")
    private boolean ifsPayment;

    @Value("${cutoff.date}")
    private String cutoffDateStr;

    @Value("${system.platform.fee.enable:false}")
    private boolean systemPlatformFee;

    private LocalDate cutoffDate;

    private static final String DHA_CLAIM_ID_CONSTANT = "DHA";

    @Value("${enable.ifs.claim.push:true}")
    private boolean enableIFSClaimPush;

    @PostConstruct
    public void init() {
        this.cutoffDate = LocalDate.parse(cutoffDateStr); // e.g., 2025-10-31
    }


    public String processERPClaim(CreateClaimFundScheme claimRequest) {
        ErpResponseRecord result = ErpResponseRecord.builder().build();
        String requestJson = "";
        PaymentsEnum.ErpType erpType = PaymentsEnum.ErpType.IFS; // default is IFS Now
        try {
            LocalDateTime payerCreatedDate = resolvePayerCreatedDate(claimRequest);

            requestJson = new ObjectMapper().writeValueAsString(claimRequest);
            log.info("Request Body ERP: {}", requestJson);

            erpType = resolveErpType(payerCreatedDate);

            log.info("Routing claim to ERP. erpType={}, claimNo={}, payerCreatedDate={}",
                    erpType, claimRequest.getClaimNo(), payerCreatedDate);

            result = processByErpType(erpType, claimRequest, requestJson);

            savePaymentProcessing(claimRequest, erpType.name(), SENT_TO_ERP.name());

            auditService.logEvent( PaymentsEnum.AuditEventType.CLAIM,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.valueOf(erpType.name()),
                    SENT_TO_ERP,
                    claimRequest.getClaimNo(),
                    null,
                    result.getRequest(),
                    result.getResponse(),
                    null,
                    PaymentsEnum.AuditStatus.PASSED.name(),
                    null
            );

            return result.getResponse();

        } catch (ExternalServiceException ex) {
            log.error("Error callback0 processing - ErpType : [{}] ERP claim", erpType ,ex);
            auditService.logEvent(
                    PaymentsEnum.AuditEventType.CLAIM,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.valueOf(erpType.name()),
                    ERP_PAYMENT_NEEDS_REPARATION,
                    claimRequest.getClaimNo(),
                    null,
                    result.getRequest(),
                    result.getResponse(),
                    null,
                    PaymentsEnum.AuditStatus.FAILED.name(),
                    ex.getMessage()
            );
            throw ex;

        } catch (Exception e) {
            log.error("Error callback1 processing - ErpType : [{}] ERP claim", erpType ,e);
            auditService.logEvent(
                    PaymentsEnum.AuditEventType.CLAIM,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.ERP,
                    ERP_PAYMENT_NEEDS_REPARATION,
                    claimRequest.getClaimNo(),
                    null,
                    result.getRequest(),
                    result.getResponse(),
                    null,
                    PaymentsEnum.AuditStatus.FAILED.name(),
                    e.getMessage()
            );
            log.error("ERP callback API Error: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private LocalDateTime resolvePayerCreatedDate(CreateClaimFundScheme claimRequest) {
        boolean isPhcClaim = "SHA".equals(claimRequest.getBeneficiaryName())
                && "SHA".equals(claimRequest.getBeneficiaryShaNo());

        if (isPhcClaim) {
            LocalDate serviceDate = LocalDate.parse(claimRequest.getServiceEndDate());
            LocalDateTime payerCreatedDate = serviceDate.atStartOfDay();
            log.info("Payer Created DateTime for PHC: {}", payerCreatedDate);
            return payerCreatedDate;
        }

        String payerCreatedDateStr = claimRequest.getCreationDate();
        if (payerCreatedDateStr != null && !payerCreatedDateStr.isEmpty()) {
            LocalDateTime payerCreatedDate = OffsetDateTime
                    .parse(payerCreatedDateStr)
                    .toLocalDateTime();
            log.info("Payer Created DateTime: {}", payerCreatedDate);
            return payerCreatedDate;
        }

        log.warn(
                "Payer Created DateTime is null for claim: {}",
                claimRequest.getClaimNo()
        );
        return null;
    }

    private ErpResponseRecord processByErpType(
            PaymentsEnum.ErpType erpType,
            CreateClaimFundScheme claimRequest,
            String requestJson) {

        switch (erpType) {
            case IFS -> {
                log.debug("Invoking IFS ERP for claimNo={}", claimRequest.getClaimNo());
                // Map to CppPaymentRequest
                IfsRequest paymentRequest = ifsClientComponet.apply(claimRequest);
                String reqIfs="";
                try {
                  reqIfs  = objectMapper.writeValueAsString(paymentRequest);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return new ErpResponseRecord(processClaimWithIfs(paymentRequest), reqIfs);
            }
            case CPP -> {
                log.debug("Invoking CPP ERP for claimNo={}", claimRequest.getClaimNo());
                return new ErpResponseRecord(processClaimWithCpp(claimRequest), requestJson);
            }
            case ODOO -> {
                log.debug("Invoking ODOO ERP for claimNo={}", claimRequest.getClaimNo());
                return new ErpResponseRecord(erpClient.callOdooErp(requestJson), requestJson);
            }
            default -> throw new IllegalStateException("Unsupported ERP type: " + erpType);
        }
    }


    /**
     * Resolves the target ERP system for a claim based on payer creation date
     * and runtime configuration flags.
     *
     * <p>Routing rules:
     * <ul>
     *   <li>If CPP payments are enabled, and the payer created date is after the configured cutoff date:
     *     <ul>
     *       <li>Route to <b>IFS</b> ERP when IFS claim push is enabled</li>
     *       <li>Otherwise route to <b>CPP</b> ERP</li>
     *     </ul>
     *   </li>
     *   <li>All other cases are routed to <b>ODOO</b> ERP (default/fallback)</li>
     * </ul>
     *
     * <p>This method performs only ERP resolution and does not execute
     * any external ERP calls.
     *
     * @param payerCreatedDate the payer-side claim creation date used to
     *                         determine ERP routing; may be {@code null}
     * @return the resolved {@link PaymentsEnum.ErpType} indicating which
     * ERP system the claim should be routed to
     */
    private PaymentsEnum.ErpType resolveErpType(LocalDateTime payerCreatedDate) {
        if (cppPayment
                && payerCreatedDate != null
                && payerCreatedDate.toLocalDate().isAfter(cutoffDate)) {

            return enableIFSClaimPush
                    ? PaymentsEnum.ErpType.IFS
                    : PaymentsEnum.ErpType.CPP;
        }
        return PaymentsEnum.ErpType.ODOO;
    }


    /**
     * Saves payment processing data to the payment_processing table.
     * Uses the transformer to convert CreateClaimFundScheme to PaymentProcessing
     * entity.
     *
     * @param createClaimFundScheme the claim fund scheme from ERP processing
     * @param erpType               the type of ERP system
     * @param paymentStatus         the current payment status
     */
    private void savePaymentProcessing(CreateClaimFundScheme createClaimFundScheme, String erpType,
            String paymentStatus) {
        try {
            log.info("Transforming and saving to payment_processing table for ClaimID: {}",
                    createClaimFundScheme.getClaimNo());
            PaymentProcessing paymentProcessing = paymentProcessingTransformer.transformToPaymentProcessing(
                    createClaimFundScheme,
                    erpType,
                    paymentStatus);
            paymentProcessingRepository.save(paymentProcessing);
            log.info("Successfully saved to payment_processing table for ClaimID: {}",
                    createClaimFundScheme.getClaimNo());
        } catch (Exception e) {
            log.error("Failed to save to payment_processing table for ClaimID: {}, Error: {}",
                    createClaimFundScheme.getClaimNo(), e.getMessage(), e);
        }
    }

    private String processClaimWithIfs(IfsRequest paymentRequest) {
        // Fetch audit reference number
        log.info("Started processClaimWithIfs against claim no {}", paymentRequest.getInvoices().getClaimId());
        String ifsResponse = ifsClient.processIFSClaim(paymentRequest);
        log.info("Response Body ifs: {}", ifsResponse);
        return ifsResponse;
    }

    public String processERPClaimMulti(List<CreateClaimFundScheme> claimRequestList) {
        return erpClient.callErpMulti(claimRequestList);
    }

    private String processClaimWithCpp(CreateClaimFundScheme claimRequest) {
        // Fetch audit reference number
        log.info("Started processClaimWithCpp against claim no {}", claimRequest.getClaimNo());
        String claimId = cleanClaimNo(claimRequest.getClaimNo());
        Optional<AfyanguClaimsAudit> afyanguClaims = afyanguAuditRepository.findByClaimIdAndIdentifierType(claimId,
                Constants.WEBHOOK_STATUS.CPP);
        if (afyanguClaims.isPresent()) {
            String referenceNo = afyanguClaims.get().getReferenceNo();
            log.info("ReferenceNo in first call: {}", referenceNo);
            claimRequest.setClaimReferenceId(referenceNo);
        } else {

            // First call failed → trigger callback to populate data
            log.info("Callback for in cliam call: {}", claimId);
            statusUpdateService.triggerCallBackStatusUpdate(claimId, false, true, true);
            // Second lookup after callback
            Optional<AfyanguClaimsAudit> afyanguClaimSecondCall = afyanguAuditRepository.findByClaimIdAndIdentifierType(
                    claimId, Constants.WEBHOOK_STATUS.CPP);

            if (afyanguClaimSecondCall.isPresent()) {
                String referenceNo = afyanguClaimSecondCall.get().getReferenceNo();
                log.info("ReferenceNo in second call: {}", referenceNo);
                claimRequest.setClaimReferenceId(referenceNo);
            } else {
                throw new DataValidationException(
                        MISSING_CLAIM_IN_BUNDLE,
                        String.format(
                                "ReferenceNo is not found in Afyangu audit against Claim no %s.",
                                claimRequest.getClaimNo()));
            }
        }

        // Fetch InsuranceClaim
        InsuranceClaim insuranceClaim = insuranceClaimsService.findByExternalID(claimId);
        if (Objects.isNull(insuranceClaim)) {
            throw new DataValidationException(MISSING_CLAIM_IN_BUNDLE,
                    String.format("Claim %s not found.", claimRequest.getClaimNo()));
        }

        // Map to CppPaymentRequest
        CppPaymentRequest paymentRequest = cppComponent.apply(claimRequest, insuranceClaim);
        List<String> interventionCode = paymentRequest.getServiceCode().stream().filter(Objects::nonNull)
                .map(InterventionCodeRequest::getServiceCodeType).toList();
        Optional<List<ClaimLineItem>> claimLineItems = insuranceClaimsService.getLineItem(claimId);

        List<InterventionCodeRequest> serviceCodes = new ArrayList<>();
        if (claimLineItems.isPresent()) {
            List<ClaimLineItem> items = claimLineItems.get();
            if (Constants.ClaimFundType.PMSF.equalsIgnoreCase(paymentRequest.getFundSchemeType())) {
                serviceCodes = items.stream().map(this::buildPFMServiceCode).toList();
            } else if (Constants.ClaimFundType.ECCIF.equalsIgnoreCase(paymentRequest.getFundSchemeType())) {
                serviceCodes = items.stream().map(this::buildECCIFServiceCode).toList();
            } else {
                // it is only for shif
                serviceCodes = items.stream().map(this::buildShifServiceCode).toList();
            }
        }

        paymentRequest.setServiceCode(serviceCodes);

        if (interventionCode.size() > 1) {
            log.info("Claim {} has multiple intervention codes", claimId);
            List<FundProjection> fundProjections = benefitMasterService.getFundsByIntervention(interventionCode);

            Map<String, List<String>> fundMap = fundProjections.stream()
                    .collect(Collectors.toMap(
                            FundProjection::getInterventionCode,
                            data -> Map.of(
                                    Constants.ClaimFundType.PHC, data.getPhc(),
                                    Constants.ClaimFundType.SHIF, data.getShif(),
                                    Constants.ClaimFundType.ECCIF, data.getEccif(),
                                    Constants.ClaimFundType.PMSF, data.getPmsf()).entrySet().stream()
                                    .filter(e -> e.getValue() != null && !"0".equalsIgnoreCase(e.getValue().trim()))
                                    .map(e -> e.getKey().toUpperCase())
                                    .toList(),
                            (existing, duplicate) -> {
                                // merge lists so no exception occurs
                                List<String> merged = new ArrayList<>(existing);
                                merged.addAll(duplicate);
                                return merged.stream().distinct().toList();
                            }));

            filterServiceCodesByFund(paymentRequest, fundMap);

            BigDecimal fundSchemeAmount = paymentRequest.getFundSchemeAmount();
            BigDecimal approvedAmount = paymentRequest.getApprovedClaimAmount();
            log.info("Initial values → fundSchemeAmount: {}, approvedAmount: {}, claimId: {}", fundSchemeAmount,
                    approvedAmount, claimId);
            if (fundSchemeAmount != null && approvedAmount != null && fundSchemeAmount.compareTo(approvedAmount) < 0) {
                BigDecimal totalAmount = paymentRequest.getServiceCode().stream()
                        .map(InterventionCodeRequest::getAmount) // extract Amount
                        .filter(Objects::nonNull) // avoid null pointer
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (approvedAmount.compareTo(totalAmount) == 0) {
                    log.info("ApprovedAmount ({}) equals totalAmount ({}). Updating fundSchemeAmount for claimId: {}",
                            approvedAmount, totalAmount, claimId);
                    paymentRequest.setFundSchemeAmount(totalAmount);
                }
            }
            if (approvedAmount != null && fundSchemeAmount != null && approvedAmount.compareTo(fundSchemeAmount) != 0) {
                log.info("Updating approvedAmount from {} to {} for claimId: {}", approvedAmount, fundSchemeAmount,
                        claimId);
                paymentRequest.setApprovedClaimAmount(fundSchemeAmount);
            }
        }

        paymentRequest.setClaimId(trimAfterDha(paymentRequest.getClaimId()));
        if (paymentRequest.getClaimId().contains(DHA_CLAIM_ID_CONSTANT)) {
            paymentRequest.setCounty(null);
        }
        log.info("Before applyFundSchemeCap request to CPP for claim [{}], Request: {}",
                paymentRequest.getClaimId(), new Gson().toJson(paymentRequest));

        if (systemPlatformFee) {
            applyFundSchemeCap(paymentRequest);
        }
        // Call CPP Client (blocking)
        CppResponse cppResponse = cppClient.paymentProcessByCpp(paymentRequest).block();
        log.info("Response Body CPP: {}", cppResponse);
        return buildErpResponseAsString(cppResponse);
    }

    public String cleanClaimNo(String claimNo) {
        int underscoreIndex = claimNo.indexOf("_");
        return underscoreIndex != -1 ? claimNo.substring(0, underscoreIndex) : claimNo;
    }

    /**
     * Process PHC claim directly to CPP without transformation
     */
    public String processPhcClaim(CppPaymentRequest phcRequest) {
        try {
            log.info("Processing PHC claim directly to CPP, claimId: {}", phcRequest.getClaimId());
            LocalDateTime payerCreatedDate = LocalDateTime.parse(phcRequest.getServiceDate());
            if (cppPayment && payerCreatedDate.toLocalDate().isAfter(cutoffDate)) {
                CppResponse cppResponse = cppClient.processPhcClaim(phcRequest).block();
                log.info("CPP Response for PHC: {}", cppResponse);
                return buildErpResponseAsString(cppResponse);
            } else {
                String requestJson = new ObjectMapper().writeValueAsString(phcRequest);
                log.info("CPP disabled, falling back to legacy Odoo ERP for PHC claim: {}", phcRequest.getClaimId());
                return erpClient.callOdooErp(requestJson);
            }
        } catch (Exception e) {
            log.error("PHC CPP API Error: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Build ERPResponseModel from CppResponse and return JSON string
     */
    private String buildErpResponseAsString(CppResponse cppResponse) {
        String statusCode = "500";
        if ("SUCCESS".equals(cppResponse.getStatus())) {
            statusCode = "201";
        }

        ERPResponseModel erpResponse = ERPResponseModel.builder()
                .statusCode(statusCode)
                .responseStatus(cppResponse.getStatus())
                .responseBody(
                        ERPResponseModel.ResponseBody.builder()
                                .result(
                                        ERPResponseModel.ResponseBody.Result.builder()
                                                .statusCode(statusCode)
                                                .status(cppResponse.getStatus())
                                                .message(cppResponse.getResponseMessage())
                                                .claimId(cppResponse.getClaimId())
                                                .id(cppResponse.getClaimReferenceId())
                                                .missingFields(cppResponse.getErrors())
                                                .build())
                                .build())
                .build();

        try {
            return objectMapper.writeValueAsString(erpResponse);
        } catch (JsonProcessingException e) {
            log.error("Error serializing ERPResponseModel: {}", e.getMessage(), e);
            return "{\"statusCode\":\"500\",\"responseStatus\":\"ERROR\",\"responseBody\":null}";
        }
    }

    public void filterServiceCodesByFund(CppPaymentRequest request, Map<String, List<String>> fundMap) {
        if (request == null || request.getServiceCode() == null || request.getFundSchemeType() == null) {
            return;
        }
        // Filter service codes where the intervention supports this fund type
        List<InterventionCodeRequest> filteredCodes = request.getServiceCode().stream()
                .filter(service -> {
                    String interventionCode = service.getServiceCodeType();
                    List<String> fundedTypes = fundMap.get(interventionCode);
                    return fundedTypes != null && fundedTypes.contains(request.getFundSchemeType());
                })
                .toList();

        // Update the request with filtered service codes
        request.setServiceCode(filteredCodes);

        log.info("Filtered service codes for fund type {} → {}",
                request.getFundSchemeType(),
                filteredCodes.stream().map(InterventionCodeRequest::getServiceCodeType).toList());
    }

    private InterventionCodeRequest buildPFMServiceCode(ClaimLineItem lineItem) {
        InterventionCodeRequest request = new InterventionCodeRequest();
        request.setAmount(
                lineItem.getPmfAmount() != null ? lineItem.getPmfAmount() : lineItem.getApprovedPrice());
        request.setServiceCodeType(lineItem.getCode());
        return request;
    }

    private InterventionCodeRequest buildECCIFServiceCode(ClaimLineItem lineItem) {
        InterventionCodeRequest request = new InterventionCodeRequest();
        request.setAmount(
                lineItem.getEccifAmount() != null ? lineItem.getEccifAmount() : lineItem.getApprovedPrice());
        request.setServiceCodeType(lineItem.getCode());
        return request;
    }

    private InterventionCodeRequest buildShifServiceCode(ClaimLineItem lineItem) {
        BigDecimal approvedPrice = lineItem.getApprovedPrice() != null ? lineItem.getApprovedPrice() : BigDecimal.ZERO;
        BigDecimal pmfAmount = lineItem.getPmfAmount() != null ? lineItem.getPmfAmount() : BigDecimal.ZERO;
        BigDecimal eccifAmount = lineItem.getEccifAmount() != null ? lineItem.getEccifAmount() : BigDecimal.ZERO;
        BigDecimal shifAmount = approvedPrice.subtract(pmfAmount).subtract(eccifAmount);
        InterventionCodeRequest interventionCodeRequest = new InterventionCodeRequest();
        interventionCodeRequest.setAmount(shifAmount);
        interventionCodeRequest.setServiceCodeType(lineItem.getCode());
        return interventionCodeRequest;
    }

    private String trimAfterDha(String claimId) {
        int index = claimId.indexOf("DHA");
        return index != -1
                ? claimId.substring(0, index + 3)
                : claimId;
    }

    private void applyFundSchemeCap(CppPaymentRequest paymentRequest) {

        List<InterventionCodeRequest> serviceCodes = paymentRequest.getServiceCode();
        BigDecimal fundSchemeAmount = paymentRequest.getFundSchemeAmount();

        // 1. Sum of service code amounts
        BigDecimal totalServiceAmount = serviceCodes.stream()
                .map(InterventionCodeRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Apply cap only when required
        if (totalServiceAmount.compareTo(fundSchemeAmount) > 0) {

            BigDecimal distributedTotal = BigDecimal.ZERO;

            for (int i = 0; i < serviceCodes.size(); i++) {
                InterventionCodeRequest code = serviceCodes.get(i);

                BigDecimal newAmount;

                // Last element adjustment (rounding safe)
                if (i == serviceCodes.size() - 1) {
                    newAmount = fundSchemeAmount.subtract(distributedTotal);
                } else {
                    newAmount = code.getAmount()
                            .multiply(fundSchemeAmount)
                            .divide(totalServiceAmount, 2, RoundingMode.HALF_UP);
                    distributedTotal = distributedTotal.add(newAmount);
                }
                // Update serviceCode amount
                code.setAmount(newAmount);
            }
            // Update approved claim amount
            paymentRequest.setApprovedClaimAmount(fundSchemeAmount);
        }
    }

}
