package io.coredge.health.claims.livia;

import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.Data;
import org.hl7.fhir.r4.model.Claim;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class LiviaPreAuthRequest {
    private String memberNumber;
    private String principalMemberNumber;
    private String policyId;
    private String policyCode;
    private BigDecimal totalAmount;

    private Map<String, List<String>> icdCodes;
    private List<PreAuthLineItem> lineItems;   // MUST match JSON key "lineItems"

    private String preAuthType;
    private String doctorIdentifier;
    private String doctorIdentifierType;

    private String facilityIdentifier;
    private String facilityIdentifierType;

    private String preAuthReferenceNo;
    private String admissionDate;
    private String dischargeDate;

    private String visit_code;
    private String sickness;
    private String causeOfIllness;
    private String sicknessCondition;
    private String natureOfTreatment;
    private String reason;

    private List<SupportedDocument> supportedDocument;

    @Data
    public static class SupportedDocument {
        private String documentName;
        private String url;
    }

    @Data
    public static class PreAuthLineItem {
        private BigDecimal requestedAmount;
        private BigDecimal sha_requestedAmount;  //TODO: confirm is Present
        private BigDecimal phc_requestedAmount;    //TODO: confirm is Present
        private BigDecimal eccif_requestedAmount;
        private BigDecimal cs_requestedAmount;   //TODO: confirm is Present

        private String type;
        private List<Drug> drugs = new ArrayList<>();
        private String code;
        private String notes;
        private String details;
        private int quantity;
        private String provisionalStartDate;
        private String provisionalEndDate;
        private Integer noOfTreatmentDays;
        private String toothType;
        private List<String> toothNos = new ArrayList<>();
        private String benefitCode;
        private String benefitType;
    }

    @Data
    public static class Drug {
        private String drugName;
        private Integer drugId;
        private Integer price;
    }

    private static final String PRIMARY = "PRIMARY";
    private static final String SECONDARY = "SECONDARY";
    private static final String CO_MORBID = "CO_MORBID";
    private static final String PROCEDURE = "Procedure";


    public static LiviaPreAuthRequest preAuthFromInsuranceClaim(InsuranceClaim claim, List<ClaimLineItem> lineItems,
                                                              Claim fhirClaim)
    {

        LiviaPreAuthRequest req = new LiviaPreAuthRequest();
        req.setMemberNumber(claim.getPatient().getCrId());
        req.setTotalAmount(claim.getClaimedTotal());

        Map<String, List<String>> icdCodes = new HashMap<>();
        icdCodes.put(PRIMARY, fhirClaim.getDiagnosis().stream()
                .map(d -> d.getDiagnosisCodeableConcept()
                        .getCodingFirstRep()
                        .getCode())
                .toList());
        icdCodes.put(SECONDARY, new ArrayList<>());
        icdCodes.put(CO_MORBID, new ArrayList<>());
        req.setIcdCodes(icdCodes);

        // Convert lineItems
        List<LiviaPreAuthRequest.PreAuthLineItem> items = new ArrayList<>();
        for (ClaimLineItem claimLineItem : lineItems) {
            LiviaPreAuthRequest.PreAuthLineItem liviaLineItems = new LiviaPreAuthRequest.PreAuthLineItem();
            liviaLineItems.setRequestedAmount(claimLineItem.getNetValue());
            liviaLineItems.setCs_requestedAmount(claimLineItem.getPmfAmount());
            liviaLineItems.setType(PROCEDURE);
            liviaLineItems.setCode(claimLineItem.getCode());
            liviaLineItems.setDetails(claimLineItem.getInterventionName());
            liviaLineItems.setQuantity(1);
            liviaLineItems.setProvisionalStartDate(FhirHelper.convertToStringFromDate(claimLineItem.getServicedPeriodStart()));
            liviaLineItems.setProvisionalEndDate(FhirHelper.convertToStringFromDate(claimLineItem.getServicedPeriodEnd()));
            liviaLineItems.setNoOfTreatmentDays(FhirHelper.calculateLoS(claimLineItem.getServicedPeriodStart(), claimLineItem.getServicedPeriodEnd()));
            liviaLineItems.setBenefitCode(claimLineItem.getCode().substring(0, claimLineItem.getCode().lastIndexOf("-")));
            liviaLineItems.setBenefitType(claim.getAccessCode().equalsIgnoreCase("ip") ? "INPATIENT" : "OUTPATIENT");
            items.add(liviaLineItems);
        }
        req.setLineItems(items);

        req.setPreAuthType(claim.getAccessCode().equalsIgnoreCase("ip") ? "INPATIENT" : "OUTPATIENT");
        req.setFacilityIdentifier(claim.getProvider().getFidCode());
        req.setFacilityIdentifierType("FID");
        req.setPreAuthReferenceNo(claim.getClaimId().toString());
        req.setAdmissionDate(FhirHelper.convertToStringFromDateTime(claim.getDateFrom()));
        req.setDischargeDate(FhirHelper.convertToStringFromDateTime(claim.getDateTo()));

        req.setSupportedDocument(fhirClaim.getSupportingInfo().stream().map(supportingInfo -> {
            LiviaPreAuthRequest.SupportedDocument doc = new LiviaPreAuthRequest.SupportedDocument();
            if (supportingInfo.hasValueAttachment()) {
                doc.setDocumentName(supportingInfo.getValueAttachment().getTitle());
                doc.setUrl(supportingInfo.getValueAttachment().getUrl());
            }
            return doc;
        }).toList());

        return req;
    }
}
