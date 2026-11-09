package io.coredge.health.claims.service;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.ErpClaims;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import io.coredge.health.claims.utils.TokenGenerator;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Period;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static io.coredge.health.claims.constants.Constants.ClaimFundType.PMSF;
import static io.coredge.health.claims.constants.Constants.PaymentStatus.*;

import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.*;
import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.PAID;

@Slf4j
@Service
public class PaymentsService {

    @Value("${TERMINOLOGY_SERVER_BASE_URL}")
    private String terminologyServerBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final IParser fhirParser;
    private final ClaimPaymentHelper claimPaymentHelper;
    private final InsuranceClaimsService insuranceClaimsService;
    private final ErpClaimsService erpClaimsService;
    private final IGenericClient fhirClient;
    private final SendClaimForPaymentHandler sendClaimForPaymentHandler;

    public PaymentsService(IParser fhirParser, ClaimPaymentHelper claimPaymentHelper, InsuranceClaimsService insuranceClaimsService, ErpClaimsService erpClaimsService, IGenericClient fhirClient, SendClaimForPaymentHandler sendClaimForPaymentHandler) {
        this.fhirParser = fhirParser;
        this.claimPaymentHelper = claimPaymentHelper;
        this.insuranceClaimsService = insuranceClaimsService;
        this.erpClaimsService = erpClaimsService;
        this.fhirClient = fhirClient;
        this.sendClaimForPaymentHandler = sendClaimForPaymentHandler;
    }

    public String getPaymentsNotice(String claimId) {
        Bundle bundle = getPaymentNoticeCallBacks(claimId);
        return fhirParser.setPrettyPrint(true).encodeResourceToString(bundle);
    }

    private Bundle getPaymentNoticeCallBacks(String claimId) {
        // Fetch the claim Bundle
        log.info("Inside : getPaymentNoticeCallBacks");
        InsuranceClaim icObj;
        if (insuranceClaimsService.existsByExternalId(claimId)) {
            // Fetch the claim Details from the DB
            icObj = insuranceClaimsService.findByExternalID(claimId);
            if (icObj == null) {
                throw new BadRequestException(String.format("ClaimID : %s not found", claimId));
            }
            if (icObj.getUse().equalsIgnoreCase(PREAUTHORIZATION)) {
                throw new BadRequestException(String.format("Requested Id '%s' is a  PREAUTHORIZATION", claimId));
            }
        } else {
            throw new BadRequestException(String.format("Claim id '%s' does not exist", claimId));
        }
        // Claim claimObject = claimPaymentHelper.getClaimObject(claimId);
        ClaimResponse claimResponseObject;
        try {
            claimResponseObject = claimPaymentHelper.getClaimResponseObject(claimId);
        } catch (ResourceNotFoundException e) {
            throw new BadRequestException(e.getMessage());
        }

        ErpClaims erpDetails = erpClaimsService.findByClaimNo(claimId);
        if (erpDetails == null) {
            // Push to ERP claims If APPROVED/SENT_FOR_PAYMENT_PROCESSING,
            if (SENT_FOR_PAYMENT_PROCESSING.equals(icObj.getStatus()) || APPROVED.equals(icObj.getStatus())) {
                log.info("Payment service, Sending for payment [ERP] processing for claim id '{}'", claimId);
                sendClaimForPaymentHandler.handlePaymentErp(null, null, claimId);
                erpDetails = erpClaimsService.findByClaimNo(claimId);
            } else {
                log.info("Payment details not found for Claim ID:  '{}', Status : '{}'.", claimId, icObj.getStatus());
                throw new BadRequestException(String.format("Payment details not found for Claim ID '%s'. The claim may still be under processing.", claimId));
            }
        } else {
            erpDetails = erpClaimsService.findByClaimNo(claimId);
        }

        List<ErpClaims> childClaims = List.of();
        assert erpDetails != null;
        if (erpDetails.getHasChild()) {
            childClaims = erpClaimsService.findByParentClaimNo(erpDetails.getClaimNo());
        }

        // Only Payments partial/Completed or Rejected , Payment Reconciliation will be sent.
        if (SENT_FOR_PAYMENT_PROCESSING.name().equalsIgnoreCase(erpDetails.getPaymentStatus()) ||
                APPROVED.name().equalsIgnoreCase(erpDetails.getPaymentStatus())) {
            // Now All check for child payment status,
            if (!hasAnyChildCompleted(childClaims)) {
                throwError(claimId, icObj);
            }
        }

        // Determine the payment status.
        PaymentStatus paymentsStatus;
        if (erpDetails.getHasChild()) {
            paymentsStatus = determineAggregatePaymentStatus(erpDetails, childClaims);
            erpDetails.setAmount(paymentsStatus.paidAmount);

            // In the Erp claims table. if payment in partials in claims, then child or parent must be payment completed, Not updating in ERP partials
            String paymentStatus = paymentsStatus.code;
            if (PAYMENT_PARTIAL.name().equalsIgnoreCase(paymentStatus)) {
                paymentStatus = ExtensionStateCodeEnum.PAYMENT_COMPLETED.name();
            }
            erpDetails.setPaymentStatus(paymentStatus);
        } else {
            // Get the Payments status
            paymentsStatus = getPaymentsStatus(erpDetails, icObj.getStatus());
        }
        // Generate all the ID. Until not getting the These ID from ERP
        PaymentNoticeGetGenerateID payNotGenID = getPaymentNoticeGetGenerateID(erpDetails);

        Bundle bundle = new Bundle();
        bundle.setId(payNotGenID.bundleId());
        PaymentNotice paymentNotice = getPaymentNotice(claimResponseObject, erpDetails, icObj, paymentsStatus, payNotGenID);

        PaymentReconciliation paymentReconciliation = getPaymentReconciliation(claimResponseObject, icObj, erpDetails, claimId, paymentsStatus, payNotGenID);
        bundle.addEntry().setResource(paymentNotice);
        bundle.addEntry().setResource(paymentReconciliation);
        // Also update to ERP claims
        pushUpdateToErpClaims(erpDetails, payNotGenID);
        return bundle;
    }

    private static void throwError(String claimId, InsuranceClaim icObj) {
        log.info("Payment details not found for Claim ID:  '{}', Status : '{}'.", claimId, icObj.getStatus());
        throw new BadRequestException(String.format("Payment details not found for Claim ID '%s'. The claim current status : '%s'.", claimId, icObj.getStatus()));
    }

    private PaymentNotice getPaymentNotice(ClaimResponse claimResponse, ErpClaims erpDetails, InsuranceClaim icObj, PaymentStatus paymentsStatus, PaymentNoticeGetGenerateID payNotGenID) {

        PaymentNotice paymentNotice = new PaymentNotice();
        paymentNotice.setId(payNotGenID.paymentNoticeId());
        List<Identifier> identifiers = new ArrayList<>();
        Identifier identifier = new Identifier();
        identifier.setSystem(getConcatString(terminologyServerBaseUrl, "/PaymentNotice/" + payNotGenID.paymentNoticeId()));
        identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
        identifier.setValue(payNotGenID.paymentNoticeId());
        identifiers.add(identifier);
        paymentNotice.setIdentifier(identifiers);
        paymentNotice.setStatus(PaymentNotice.PaymentNoticeStatus.ACTIVE);
        paymentNotice.setCreated(Date.from(Instant.now()));

        paymentNotice.setProvider(new Reference(getConcatString(terminologyServerBaseUrl, "/" + getOrganization(icObj.getProvider())))
                .setDisplay(icObj.getProvider().getName()));

        paymentNotice.setPayment(new Reference(getConcatString(terminologyServerBaseUrl, "/PaymentReconciliation/" + payNotGenID.paymentReconciliationID())));
        paymentNotice.setPaymentDate(erpDetails.getDate());

        //TODO Need to Update the PAYEE Reference here from the ERP
        paymentNotice.setPayee(createReference("/Organization", "Social Health Authority"));

        paymentNotice.setRecipient(createReference("/" + getOrganization(icObj.getProvider()),
                StringUtils.hasText(icObj.getProvider().getName()) ? icObj.getProvider().getName().toUpperCase() : ""));

        Money amount = new Money();
        amount.setValue(paymentsStatus.totalAmount);
        amount.setCurrency(claimResponse.getTotal().getFirst().getAmount().getCurrency());
        paymentNotice.setAmount(amount);

        CodeableConcept paymentStatus = getCodeAbleConceptPaymentStatus(paymentsStatus);
        paymentNotice.setPaymentStatus(paymentStatus);

        boolean success = false;
        int retryCount = 0;
        while (!success && retryCount < 3) {
            try {
                // Attempt to update
                log.info("Saving Payment Notice, BundleId: {}", payNotGenID.bundleId());
                fhirClient.update().resource(paymentNotice).execute();
                success = true;
            } catch (ResourceVersionConflictException ex) {
                log.info("ResourceVersionConflict while saving the paymentNotice in FHIR server.  BundleId: {}, Retrying [{}]...", payNotGenID.bundleId(), retryCount);
                retryCount++;
            }
        }
        return paymentNotice;
    }

    private @NotNull CodeableConcept getCodeAbleConceptPaymentStatus(PaymentStatus paymentsStatus) {
        Coding paymentStatusCoding = new Coding();
        paymentStatusCoding.setSystem(getConcatString(terminologyServerBaseUrl, "CodeSystem/paymentstatus"));
        paymentStatusCoding.setCode(paymentsStatus.code);
        paymentStatusCoding.setDisplay(paymentsStatus.display.toUpperCase());
        CodeableConcept paymentStatus = new CodeableConcept();
        paymentStatus.addCoding(paymentStatusCoding);
        return paymentStatus;
    }

    private static @NotNull PaymentStatus getPaymentsStatus(ErpClaims erpDetails, ExtensionStateCodeEnum claimStatus) {
        String paymentsStatusInErp = erpDetails.getPaymentStatus();
        ExtensionStateCodeEnum finalStatus = UNKNOWN;
        if (StringUtils.hasText(paymentsStatusInErp) && (paymentsStatusInErp.equalsIgnoreCase(Constants.PaymentStatus.PAID) || paymentsStatusInErp.equalsIgnoreCase(ExtensionStateCodeEnum.PAYMENT_COMPLETED.name()))) {
            finalStatus = PAID;
        } else if (StringUtils.hasText(erpDetails.getRejection_reason()) ||
                (StringUtils.hasText(paymentsStatusInErp) && (paymentsStatusInErp.equalsIgnoreCase(UNPAID) || paymentsStatusInErp.equalsIgnoreCase(ExtensionStateCodeEnum.PAYMENT_DECLINED.name())))) {
            finalStatus = REJECTED;
        } else if (claimStatus.equals(PAYMENT_PARTIAL)) {
            finalStatus = PAYMENT_PARTIAL;
        }
        return new PaymentStatus(finalStatus.name().toUpperCase(), finalStatus.getDisplayName(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public boolean hasAnyChildCompleted(List<ErpClaims> childClaims) {
        return childClaims.stream()
                .anyMatch(claim -> ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equalsIgnoreCase(claim.getPaymentStatus()) || PAYMENT_PARTIAL.name().equalsIgnoreCase(claim.getPaymentStatus()) || ExtensionStateCodeEnum.PAYMENT_DECLINED.name().equalsIgnoreCase(claim.getPaymentStatus()));
    }

    private PaymentStatus determineAggregatePaymentStatus(ErpClaims parentClaim, List<ErpClaims> childClaims) {
        // Combine parent + children
        List<ErpClaims> allClaims = new ArrayList<>();
        allClaims.add(parentClaim);
        allClaims.addAll(childClaims);

        boolean allRejected = allClaims.stream()
                .allMatch(claim -> ExtensionStateCodeEnum.PAYMENT_DECLINED.name().equalsIgnoreCase(claim.getPaymentStatus()));

        boolean allPaid = allClaims.stream()
                .allMatch(claim -> ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equalsIgnoreCase(claim.getPaymentStatus())
                        || PAYMENT_PARTIAL.name().equalsIgnoreCase(claim.getPaymentStatus()));

        // Get only Paid amount
        BigDecimal paidAmount = allClaims.stream()
                .filter(claim -> ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equalsIgnoreCase(claim.getPaymentStatus())
                        || PAYMENT_PARTIAL.name().equalsIgnoreCase(claim.getPaymentStatus()))
                .map(ErpClaims::getClaimAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get Complete amount
        BigDecimal totalAmount = allClaims.stream()
                .map(ErpClaims::getClaimAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        // Get only Pmsf amount, if paid
        BigDecimal pmsfAmount = allClaims.stream()
                .filter(claim -> (ExtensionStateCodeEnum.PAYMENT_COMPLETED.name().equalsIgnoreCase(claim.getPaymentStatus())
                        || PAYMENT_PARTIAL.name().equalsIgnoreCase(claim.getPaymentStatus()))
                        && claim.getClaimNo().contains(PMSF.toUpperCase()))
                .map(ErpClaims::getClaimAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String aggregateStatus = allRejected ? REJECTED.name() :
                (allPaid ? PAID.name() : PAYMENT_PARTIAL.name());

        return new PaymentStatus(aggregateStatus, aggregateStatus.toUpperCase(), paidAmount, totalAmount, pmsfAmount);
    }


    private PaymentReconciliation getPaymentReconciliation(ClaimResponse claimResponse, InsuranceClaim icObj, ErpClaims erpDetails, String paymentReconciliationID, PaymentStatus paymentStatus, PaymentNoticeGetGenerateID payNotGenID) {

        // Calculate the all applicable amount
        BigDecimal uhcAmount, invoicedAmount, approvedAmount;
        BigDecimal pmsfAmount = BigDecimal.ZERO;
        if (erpDetails.getHasChild()) {
            approvedAmount = paymentStatus.paidAmount;
            invoicedAmount = paymentStatus.totalAmount;
            if (paymentStatus.pmsfAmount.compareTo(BigDecimal.ZERO) > 0) {
                pmsfAmount = paymentStatus.pmsfAmount;
                uhcAmount = approvedAmount.subtract(pmsfAmount);
            } else {
                uhcAmount = paymentStatus.paidAmount;
            }
        } else {
            invoicedAmount = erpDetails.getClaimAmount();
            uhcAmount = erpDetails.getClaimAmount();
            approvedAmount = erpDetails.getClaimAmount();
        }


        PaymentReconciliation paymentReconciliation = new PaymentReconciliation();
        paymentReconciliation.setId(paymentReconciliationID);

        // TODO Add identifier
        paymentReconciliation.addIdentifier(createIdentifier("/identifier/remittance", payNotGenID.remittanceID()));

        // Set basic properties
        paymentReconciliation.setStatus(PaymentReconciliation.PaymentReconciliationStatus.ACTIVE);
        paymentReconciliation.setCreated(Date.from(Instant.now()));
//        paymentReconciliation.setPeriod(createPeriod(icObj.getDateFrom(), icObj.getDateTo()));
        paymentReconciliation.setPeriod(createPeriod(adjustToOriginalClaimTimezone(icObj.getDateFrom()), adjustToOriginalClaimTimezone(icObj.getDateTo())));

        // TODO  PaymentIssuer, Requestor from ERP
        paymentReconciliation.setPaymentIssuer(createReference("/Organization", "Social Health Authority"));
        paymentReconciliation.setRequestor(createReference("/" + getOrganization(icObj.getProvider()),
                StringUtils.hasText(icObj.getProvider().getName()) ? icObj.getProvider().getName().toUpperCase() : ""));

        // TODO - For Now Hard coded Value
        paymentReconciliation.setDisposition("Settled via EFT transfer");
        paymentReconciliation.setPaymentDate(erpDetails.getDate());

        // Set payment amount
        String currency = claimResponse.getTotal().getFirst().getAmount().getCurrency();
        Money paymentAmount = new Money();
        paymentAmount.setValue(approvedAmount);
        paymentAmount.setCurrency(currency);
        paymentReconciliation.setPaymentAmount(paymentAmount);

        //TODO  Set payment identifier
        paymentReconciliation.setPaymentIdentifier(createIdentifier("/identifier/transfer", payNotGenID.PaymentIdentifier));

        String responseCallback = erpDetails.getResponseCallback();
        // TODO Implements other details. When get from the ERP
        if (responseCallback != null) {
            CreateClaimFundScheme request;
            try {
                request = objectMapper.readValue(responseCallback, CreateClaimFundScheme.class);
            } catch (JsonProcessingException e) {
                log.error("Error parsing CreateClaimFundScheme response", e);
            }
        }

        // Add details
        List<PaymentReconciliation.DetailsComponent> details = new ArrayList<>();
        details.add(createDetailComponent(
                payNotGenID.payerInvoice(),
                icObj.getExternalId(),
                erpDetails.getInvoiceNumber(),
                new DateRange(icObj.getDateFrom(), icObj.getDateTo()),
                new PaymentSummary(invoicedAmount, approvedAmount, BigDecimal.valueOf(0), BigDecimal.valueOf(0), BigDecimal.valueOf(0), BigDecimal.valueOf(0), uhcAmount, pmsfAmount),
                paymentStatus.code, erpDetails.getRejection_reason(), currency
        ));
        paymentReconciliation.setDetail(details);
        fhirClient.update().resource(paymentReconciliation).execute();
        return paymentReconciliation;
    }

    // Helper Method: Create Identifier
    private Identifier createIdentifier(String path, String value) {
        Identifier identifier = new Identifier();
        identifier.setSystem(getConcatString(terminologyServerBaseUrl, path));
        identifier.setValue(value);
        return identifier;
    }

    // Helper Method: Create Period
    private Period createPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        Period period = new Period();
        period.setStart(Date.from(startDate.atZone(ZoneId.systemDefault()).toInstant()));
        period.setEnd(Date.from(endDate.atZone(ZoneId.systemDefault()).toInstant()));
        return period;
    }

    // Helper Method: Create Reference
    private Reference createReference(String url, String display) {
        return new Reference(getConcatString(terminologyServerBaseUrl, url)).setDisplay(display);
    }

    // Helper Method: Create DetailsComponent
    private PaymentReconciliation.DetailsComponent createDetailComponent(
            String payerInvoiceId, String claimId, String display, DateRange dateRange,
            PaymentSummary summary, String paymentStatus, String rejectionReason, String currency) {
        PaymentReconciliation.DetailsComponent detail = new PaymentReconciliation.DetailsComponent();
        detail.setIdentifier(createIdentifier("/identifier/payer-invoice", payerInvoiceId));
        detail.setType(new CodeableConcept().addCoding(new Coding().setSystem(getConcatString(terminologyServerBaseUrl, "/CodeSystem/v3-ActCode")).setCode("PAYMENT").setDisplay("Payment")));
        detail.setRequest(createReference(getConcatString("/Claim/", claimId), display));

        // Add extensions
        String STRUCTURE_DEFINITION_PATH = "/StructureDefinition/";
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "billFrom", Date.from(dateRange.start().atZone(ZoneId.systemDefault()).toInstant())));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "billTo", Date.from(dateRange.end().atZone(ZoneId.systemDefault()).toInstant())));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "invoicedAmount", new Money().setValue(summary.invoicedAmount()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "approvedAmount", new Money().setValue(summary.approvedAmount()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "rejectedAmount", new Money().setValue(summary.rejectedAmount()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "discount", new Money().setValue(summary.discount()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "copay", new Money().setValue(summary.copay()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "withholdingTax", new Money().setValue(summary.withholdingTax()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "paymentStatus", new StringType(paymentStatus)));

        //
        if (rejectionReason != null) {
            detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "rejectionReason", new StringType(rejectionReason)));
        }

        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "uhc-contribution", new Money().setValue(summary.uhcContribution()).setCurrency(currency)));
        detail.addExtension(createExtension(STRUCTURE_DEFINITION_PATH + "pmsf-contribution", new Money().setValue(summary.pmsfContribution()).setCurrency(currency)));
        return detail;
    }

    // Helper Method: Create Extension
    private Extension createExtension(String path, Type value) {
        return new Extension(getConcatString(terminologyServerBaseUrl, path), value);
    }

    // For valueString
    private Extension createExtension(String path, String valueString) {
        return new Extension(getConcatString(terminologyServerBaseUrl, path), new StringType(valueString));
    }

    // For valueDate
    private Extension createExtension(String path, Date valueDate) {
        return new Extension(getConcatString(terminologyServerBaseUrl, path), new DateType(valueDate));
    }

    private LocalDateTime adjustToOriginalClaimTimezone(LocalDateTime dateFrom) {
        // The original claim is in +03:00 time zone
        ZoneOffset originalZoneOffset = ZoneOffset.of("+03:00");
        // Convert the LocalDateTime to ZonedDateTime with the original time zone
        ZonedDateTime zonedDateTime = dateFrom.atZone(ZoneOffset.UTC) // Assuming input is in UTC
                .withZoneSameInstant(originalZoneOffset);
        // Return the adjusted LocalDateTime
        return zonedDateTime.toLocalDateTime();
    }


    private PaymentNoticeGetGenerateID getPaymentNoticeGetGenerateID(ErpClaims erpDetails) {
        return new PaymentNoticeGetGenerateID(
                StringUtils.hasText(erpDetails.getPaymentBundleId()) ? erpDetails.getPaymentBundleId() : TokenGenerator.generateToken(),
                StringUtils.hasText(erpDetails.getPaymentNoticeId()) ? erpDetails.getPaymentNoticeId() : TokenGenerator.generateToken(),
                StringUtils.hasText(erpDetails.getPaymentReconciliationID()) ? erpDetails.getPaymentReconciliationID() : getConcatString("PAYRECON-", TokenGenerator.generateToken()),
                StringUtils.hasText(erpDetails.getRemittanceId()) ? erpDetails.getRemittanceId() : getConcatString("REM-", TokenGenerator.generateToken()),
                StringUtils.hasText(erpDetails.getPayerInvoiceId()) ? erpDetails.getPayerInvoiceId() : getConcatString("PAYER-INV-", TokenGenerator.generateToken()),
                StringUtils.hasText(erpDetails.getPaymentIdentifier()) ? erpDetails.getPaymentIdentifier() : getConcatString("EFT-", TokenGenerator.generateToken()));
    }

    private void pushUpdateToErpClaims(ErpClaims erpDetails, PaymentNoticeGetGenerateID paymentNoticeGetGenerateID) {
        if (!StringUtils.hasText(erpDetails.getPaymentNoticeId())) {
            erpDetails.setPaymentNoticeId(paymentNoticeGetGenerateID.paymentNoticeId());
            erpDetails.setPaymentReconciliationID(paymentNoticeGetGenerateID.paymentReconciliationID());
            erpDetails.setPaymentBundleId(paymentNoticeGetGenerateID.bundleId());
            erpDetails.setRemittanceId(paymentNoticeGetGenerateID.remittanceID());
            erpDetails.setPayerInvoiceId(paymentNoticeGetGenerateID.payerInvoice());
            erpDetails.setPaymentIdentifier(paymentNoticeGetGenerateID.PaymentIdentifier());
            // Also update to ERP claims
            log.info("Updating payment notice details Erp Claims");
            erpClaimsService.saveErpClaims(erpDetails);
        }
    }

    private String getOrganization(Provider provider) {
        if (StringUtils.hasText(provider.getMflCode())) {
            return provider.getMflCode();
        } else if (StringUtils.hasText(provider.getFidCode())) {
            return provider.getFidCode();
        } else if (StringUtils.hasText(provider.getIdentifier())) {
            return provider.getIdentifier();
        } else {
            return provider.getRegNo();
        }
    }

    private String getConcatString(String str1, String str2) {
        if (!StringUtils.hasText(str2)) {
            return StringUtils.hasText(str1) ? str1 : "";
        }
        return StringUtils.hasText(str1) ? str1 + str2 : str2;
    }

    // Supporting Class: DateRange, PaymentSummary
    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }

    private record PaymentStatus(String code, String display, BigDecimal paidAmount, BigDecimal totalAmount,
                                 BigDecimal pmsfAmount) {
    }

    private record PaymentSummary(BigDecimal invoicedAmount, BigDecimal approvedAmount, BigDecimal rejectedAmount,
                                  BigDecimal discount, BigDecimal copay, BigDecimal withholdingTax,
                                  BigDecimal uhcContribution, BigDecimal pmsfContribution) {
    }

    private record PaymentNoticeGetGenerateID(String bundleId, String paymentNoticeId, String paymentReconciliationID,
                                              String remittanceID, String payerInvoice, String PaymentIdentifier) {
    }

}