package io.coredge.health.claims.livia;

import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.Data;
import org.hl7.fhir.r4.model.Claim;

import java.math.BigDecimal;
import java.util.*;

@Data
public class LiviaClaimRequest {
    private String memberNumber;
    private String principalMemberNumber;
    private String policyId;
    private String policyCode;

    private String invoiceReferenceNumber;
    private String invoiceDate;
    private String treatmentDate;
    private String invoiceStatus;
    private String comment;

    private Map<String, List<String>> icdCodes;
    private List<LineItem> lineItems;

    private String sickness;
    private String causeOfIllness;
    private String sicknessCondition;
    private String natureOfTreatment;

    private String doctorIdentifier;
    private String doctorIdentifierType;

    private String facilityIdentifier;
    private String facilityIdentifierType;

    private String admissionDate;
    private String dischargeDate;
    private String visit_code;

    private List<SupportedDocument> supportedDocument;


    @Data
    public static class SupportedDocument {
        private String documentName;
        private String url;
    }

    @Data
    public static class LineItem {
        private String status;
        private String type;

        private BigDecimal claimAmount = BigDecimal.ZERO;
        private BigDecimal settledAmount = BigDecimal.ZERO;
        private BigDecimal sha_SettledAmount = BigDecimal.ZERO;
        private BigDecimal eccif_settledAmount = BigDecimal.ZERO;
        private BigDecimal cs_settledAmount = BigDecimal.ZERO;
        private BigDecimal phc_settledAmount = BigDecimal.ZERO;

        private int quantity;
        private int price;

        private String details;
        private List<String> toothNos = new ArrayList<>();
        private String toothType;

        private String benefitType;
        private String benefitCode;
        private String code;

        private List<Drug> drugs = new ArrayList<>();

        private String preAuthReferenceNo;
        private String provisionalStartDate;
        private String provisionalEndDate;

    }

    @Data
    public static class Drug {
        private String drugName;
        private Integer drugId;
        private Integer price;
    }


    private static final String  PRIMARY = "PRIMARY";
    private static final String SECONDARY = "SECONDARY";
    private static final String CO_MORBID = "CO_MORBID";
    private static final String LOADED = "Loaded";
    private static final String PROCEDURE = "Procedure";

    public static LiviaClaimRequest fromInsuranceClaim(InsuranceClaim insuranceClaim, List<ClaimLineItem> lineItems, Claim claim){
        LiviaClaimRequest liviaClaimRequest = new LiviaClaimRequest();
        liviaClaimRequest.setMemberNumber(insuranceClaim.getPatient().getCrId());
        liviaClaimRequest.setTreatmentDate(FhirHelper.convertToStringFromDateTime(insuranceClaim.getDateFrom()));

        Map<String, List<String>> icdCodes = new HashMap<>();
        icdCodes.put(PRIMARY, claim.getDiagnosis().stream()
                .map(d -> d.getDiagnosisCodeableConcept()
                        .getCodingFirstRep()
                        .getCode())
                .toList());
        icdCodes.put(SECONDARY, new ArrayList<>());
        icdCodes.put(CO_MORBID, new ArrayList<>());
        liviaClaimRequest.setIcdCodes(icdCodes);

        List<LineItem> liviaItems = new ArrayList<>();
        for (ClaimLineItem lineItem : lineItems) {
            LineItem liviaLineItem = new LineItem();
            liviaLineItem.setStatus(LOADED);
            liviaLineItem.setType(PROCEDURE);
            liviaLineItem.setClaimAmount(lineItem.getNetValue());
            liviaLineItem.setSettledAmount(lineItem.getPmfAmount());
            liviaLineItem.setCs_settledAmount(lineItem.getPmfAmount());
            liviaLineItem.setQuantity(1);
            liviaLineItem.setDetails(lineItem.getInterventionName());
            liviaLineItem.setBenefitType(insuranceClaim.getAccessCode().equalsIgnoreCase("ip") ? "INPATIENT" : "OUTPATIENT");
            liviaLineItem.setBenefitCode(lineItem.getCode().substring(0, lineItem.getCode().lastIndexOf("-")));
            liviaLineItem.setCode(lineItem.getCode());
            liviaLineItem.setPreAuthReferenceNo(lineItem.getPreAuthId() != null ? lineItem.getPreAuthId().toString() : "");
            liviaLineItem.setProvisionalStartDate(FhirHelper.convertToStringFromDate(lineItem.getServicedPeriodStart()));
            liviaLineItem.setProvisionalEndDate(FhirHelper.convertToStringFromDate(lineItem.getServicedPeriodEnd()));
            liviaItems.add(liviaLineItem);
        }
        liviaClaimRequest.setLineItems(liviaItems);

        liviaClaimRequest.setFacilityIdentifier(insuranceClaim.getProvider().getFidCode());
        liviaClaimRequest.setFacilityIdentifierType("FID");
        liviaClaimRequest.setAdmissionDate(FhirHelper.convertToStringFromDateTime(insuranceClaim.getDateFrom()));
        liviaClaimRequest.setDischargeDate(FhirHelper.convertToStringFromDateTime(insuranceClaim.getDateTo()));

        liviaClaimRequest.setSupportedDocument(claim.getSupportingInfo().stream().map(supportingInfo -> {
            SupportedDocument doc = new SupportedDocument();
            if (supportingInfo.hasValueAttachment()) {
                doc.setDocumentName(supportingInfo.getValueAttachment().getTitle());
                doc.setUrl(supportingInfo.getValueAttachment().getUrl());
            }
            return doc;
        }).toList());

        return liviaClaimRequest;
    }
}
