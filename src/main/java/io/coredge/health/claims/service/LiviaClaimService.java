package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.coredge.health.claims.client.LiviaClient;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.ClaimPractitionerMappingRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.livia.LiviaClaimRequest;
import io.coredge.health.claims.livia.LiviaPreAuthCancelRequest;
import io.coredge.health.claims.livia.LiviaPreAuthRequest;
import io.coredge.health.claims.service.BMS.SchemeMemberLimitService;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;

@Slf4j
@Service
public class LiviaClaimService {

    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private IGenericClient fhirClient;

    @Value("${TERMINOLOGY_SERVER_BASE_URL}")
    private String terminologyBaseUrl;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private LiviaClient liviaClient;

    @Autowired
    private CivilServantAuditService civilServantAuditService;

    @Autowired
    private SchemeMemberLimitService schemeMemberLimitService;

    @Autowired
    private ClaimPractitionerMappingRepository claimPractitionerMappingRepository;

    private static String NATIONAL_ID_TYPE = "National ID";

    private static final String LOADED_STATUS = "Loaded";

    public static final String SCHEME_CATEGORY_CODE_URL = "/StructureDefinition/schemeCategoryCode";

    private final Gson gson = new Gson();

    public boolean submitLiviaClaim(Claim claim, List<ClaimLineItem> lineItems) {
        log.info("Submitting claim to Livia:");
        boolean isApproved = false;
        String claimId = claim.getIdPart();
        List<Claim.InsuranceComponent> insurances = claim.getInsurance();
        String url = "";
        for (Claim.InsuranceComponent insurance : insurances) {
            if (insurance.hasCoverage() && insurance.getCoverage().getReference().contains("pmf")) {
                url = insurance.getCoverage().getReference();
            }
        }
        String coverageId = url.substring(url.lastIndexOf("/") + 1);
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).get().getFirst();
        LiviaClaimRequest liviaClaimRequest = LiviaClaimRequest.fromInsuranceClaim(iClaim, lineItems, claim);
        String doctorIdentifier = FhirHelper.extractDoctorIdentifier(claim, fhirClient);
        log.info("Doctor Identifier extracted  : {} claimId : {}", doctorIdentifier, claimId);

        liviaClaimRequest.setDoctorIdentifier(doctorIdentifier);
        liviaClaimRequest.setDoctorIdentifierType(NATIONAL_ID_TYPE);

         //SEND TO BMS
//        schemeMemberLimitService.processClaim(iClaim.getClaimId(), iClaim.getAccessCode().toUpperCase(), liviaClaimRequest.getTotalAmount().doubleValue(),"PMSF");
        Coverage coverage = fhirClient.read().resource(Coverage.class).withId(coverageId).execute();
        coverage.getExtension().stream().forEach(extension -> {
            if (extension.getUrl().equals(terminologyBaseUrl.concat(SCHEME_CATEGORY_CODE_URL))) {
                Extension extension1 = (Extension) extension;
                String value = extension1.getValue().toString();
                liviaClaimRequest.setPolicyCode(value);
                log.info("Policy Code: {}", value);
            }
        });

        liviaClaimRequest.setInvoiceDate(FhirHelper.convertDateStringToTargetFormat(claimPaymentHelper.getInvoiceDate(claim)));
        liviaClaimRequest.setInvoiceReferenceNumber(claimId);
        liviaClaimRequest.setInvoiceStatus(LOADED_STATUS);

        Gson gson = new Gson();
        String liviaClaimRequestJson = gson.toJson(liviaClaimRequest);
        log.info("ClaimId : {} Livia body json: {}", claimId, liviaClaimRequestJson);
        CivilServantClaimsAudit audit = civilServantAuditService.save(liviaClaimRequest, liviaClaimRequestJson, claimId);
        ResponseEntity<String> response = liviaClient.submitClaim(liviaClaimRequest, claimId);

        if (response.getStatusCode().is2xxSuccessful()) {
            isApproved = true;
            log.info("Livia Claim Approved for claimId : {} ", claimId);
            try {
                civilServantAuditService.update(audit, response.getBody(), claimId,"APPROVED");
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                String invoiceId = jsonNode.get("invoiceId").asText();
                if (iClaim.getCivilServantInvId() == null) {
                    log.info("Livia: Inserting new Invoice ID: {} for Claim ID: {}", invoiceId, claimId);
                    iClaim.setCivilServantInvId(invoiceId);
                } else {
                    String existingInvoiceId = iClaim.getCivilServantInvId();
                    if (existingInvoiceId.equals(invoiceId)) {
                        log.info("Livia: Invoice ID unchanged for Claim ID: {}. Existing ID: {}, New ID: {}", claimId, existingInvoiceId, invoiceId);
                    } else {
                        log.info("Livia: Updating Invoice ID for Claim ID: {}. Old ID: {}, New ID: {}", claimId, existingInvoiceId, invoiceId);
                        iClaim.setCivilServantInvId(invoiceId);
                    }
                }
                log.info("Livia Claim invoiceId : {} for claimId : {} ", invoiceId, claimId);
                claimsRepository.save(iClaim);
            } catch (Exception e) {
                log.error("Failed parsing and saving invoiceId in database claimId : {} error : {}", claimId, e.getMessage());
            }
        } else {
            log.error("Livia claim submission failed claimId : {} error : {}", claimId, response.getBody());
            civilServantAuditService.update(audit, response.getBody(), claimId, "REJECTED");
            isApproved = false;
        }
        return isApproved;
    }

    public boolean verifyLiviaClaim(Claim claim, List<ClaimLineItem> lineItems) {
        log.info("Verify preAuth from Livia:");
        boolean isApproved = false;
        String claimId = claim.getIdPart();
        List<Claim.InsuranceComponent> insurances = claim.getInsurance();
        String url = "";
        for (Claim.InsuranceComponent insurance : insurances) {
            if (insurance.hasCoverage() && insurance.getCoverage().getReference().contains("pmf")) {
                url = insurance.getCoverage().getReference();
            }
        }
        String coverageId = url.substring(url.lastIndexOf("/") + 1);
        InsuranceClaim iClaim = claimsRepository.findByExternalId(claimId).get().getFirst();
        LiviaPreAuthRequest liviaClaimRequest = LiviaPreAuthRequest.preAuthFromInsuranceClaim(iClaim, lineItems, claim);
        String doctorIdentifier = FhirHelper.extractDoctorIdentifier(claim, fhirClient);
        log.info("Doctor Identifier extracted  : {} claimId : {}", doctorIdentifier, claimId);

        liviaClaimRequest.setDoctorIdentifier(doctorIdentifier);
        liviaClaimRequest.setDoctorIdentifierType(NATIONAL_ID_TYPE);

         //SEND TO BMS
//        schemeMemberLimitService.processClaim(iClaim.getClaimId(), iClaim.getAccessCode().toUpperCase(), liviaClaimRequest.getTotalAmount().doubleValue(),"PMSF");
        Coverage coverage = fhirClient.read().resource(Coverage.class).withId(coverageId).execute();
        coverage.getExtension().forEach(extension -> {
            if (extension.getUrl().equals(terminologyBaseUrl.concat(SCHEME_CATEGORY_CODE_URL))) {
                String value = extension.getValue().toString();
                liviaClaimRequest.setPolicyCode(value);
                log.info("Policy Code: {}", value);
            }
        });

        Gson gson = new Gson();
        String liviaClaimRequestJson = gson.toJson(liviaClaimRequest);
        log.info("ClaimId : {} Livia body json: {}", claimId, liviaClaimRequestJson);
        CivilServantClaimsAudit audit = civilServantAuditService.save(liviaClaimRequest, liviaClaimRequestJson, claimId);
        ResponseEntity<String> response = liviaClient.verifyClaim(liviaClaimRequest, claimId);

        if (response.getStatusCode().is2xxSuccessful()) {
            isApproved = true;
            civilServantAuditService.update(audit, response.getBody(), claimId, "APPROVED");
            log.info("ClaimId : {} Livia Pre Auth Approved ", claimId);
        } else {
            civilServantAuditService.update(audit, response.getBody(), claimId, "REJECTED");
            log.error("Livia claim submission failed claimId : {} error : {}", claimId, response.getBody());
        }
        return isApproved;
    }

    /**
     * Cancels a pre-authorization in Livia system for civil servant claims.
     */
    public String cancelLiviaPreAuth(String claimId, String preAuthReferenceNo, String cancellationReason) {

        log.info("Cancelling preauth in Livia for claimId: {}, preAuthReferenceNo: {}",
                claimId, preAuthReferenceNo);

        LiviaPreAuthCancelRequest cancelRequest = LiviaPreAuthCancelRequest.builder()
                .preAuthReferenceNo(preAuthReferenceNo)
                .reason(cancellationReason)
                .build();

        log.info("Livia preauth cancellation request for claimId: {} - {}",
                claimId, gson.toJson(cancelRequest));

        try {
            ResponseEntity<String> response = liviaClient.cancelPreAuth(cancelRequest, claimId);
            log.info("Livia preauth cancellation successful for claimId: {}, status: {}, response: {}",
                    claimId, response.getStatusCode(), response.getBody());

            return response.getBody();

        } catch (HttpStatusCodeException ex) {
            String responseBody = ex.getResponseBodyAsString();
            log.error("Livia preauth cancellation failed for claimId: {}, status: {}, response: {}",
                    claimId, ex.getStatusCode(), responseBody);

            throw new BadRequestException(extractLiviaMessage(responseBody));

        } catch (Exception ex) {
            log.error("Unexpected error while cancelling preauth in Livia for claimId: {}",
                    claimId, ex);

            throw new BadRequestException("Unable to cancel preauth in Livia at this time");
        }
    }

    private String extractLiviaMessage(String responseBody) {
        try {
            if (responseBody != null) {
                JsonObject jsonObject = gson.fromJson(responseBody, JsonObject.class);
                if (jsonObject != null && jsonObject.has("message")) {
                    return jsonObject.get("message").getAsString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Livia error response: {}", responseBody);
        }
        return "Preauth cancellation failed at Livia";
    }
}
