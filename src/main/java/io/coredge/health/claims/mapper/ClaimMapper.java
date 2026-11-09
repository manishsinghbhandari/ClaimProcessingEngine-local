package io.coredge.health.claims.mapper;

import com.google.gson.Gson;
import io.coredge.health.claims.db.entity.ClaimPaymentProjection;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.models.ClaimDTO;
import io.coredge.health.claims.models.ClaimPaymentResponse;
import io.coredge.health.claims.models.PaymentDetailResponse;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.Claim;

import java.util.List;

@UtilityClass
public class ClaimMapper {

  public String mapToCustomClaim(Claim claim) {

    ClaimDTO customClaim = ClaimDTO.builder().build();

    if (claim.hasType()) {
      customClaim.setCategory(claim.getType().getCoding().getFirst().getDisplay());
    }
    if (claim.hasSubType()) {
      customClaim.setServiceCategory(claim.getSubType().getCoding().getFirst().getDisplay());
    }
    customClaim.setSubCategory("Emergency");

    // InterventionRepository name and code
    customClaim.setInterventionName("Major trauma");
    customClaim.setInterventionCode("SHA-01-001");

    // Diagnosis code from ICD
    if (claim.hasDiagnosis()) {
      customClaim.setDiagnosisICDHeading(
          claim
              .getDiagnosis()
              .getFirst()
              .getDiagnosisCodeableConcept()
              .getCoding()
              .getFirst()
              .getCode());
    }

    customClaim.setBlockICDcode("");

    // Pre-Authorization done or not (assuming from an extension or custom logic)
    customClaim.setPreAuthDone(false); // Assuming static or dynamic value

    // Facility category
    customClaim.setFacilityCategory(claim.getProvider().getReference());

    // Claim amount
    if (claim.hasTotal()) {
      customClaim.setClaimAmount(claim.getTotal().getValue().doubleValue());
    } else {
      customClaim.setClaimAmount(1000); // Fallback default value
    }

    // Investigation and management tariffs (can be extended)
    customClaim.setInvestigationTariff(100); // Hardcoded or extracted from claim
    customClaim.setManagementTariff(200);

    // Needs surveillance claim
    customClaim.setNeedsSurveillanceClaim(true); // Assuming static/dynamic based on business logic

    // Additional tariffs and amounts
    customClaim.setInvestigationTerrif(5000);
    customClaim.setManagementAmount(5000);

    // Gender and age (assuming from the patient reference in the Claim)
    if (claim.hasPatient()) {
      customClaim.setGender("female"); // Extract gender from Patient resource if available
      customClaim.setAge(30); // Extract age from Patient resource or related data
    }
    // Times intervention used (assuming a static value or computed from context)
    customClaim.setTimesInterventionUsed(1);
    return new Gson().toJson(customClaim);
  }

  public static PaymentDetailResponse toPaymentDetailResponse(List<ClaimPaymentProjection> claimPayments, String claimId){
    if(claimPayments.isEmpty()){
      throw new DataNotFoundException("No Data found for payment detail Request for claimId :"+ claimId);
    }

    ClaimPaymentProjection projection = claimPayments.getFirst();
    return PaymentDetailResponse.builder()
            .claimedTotal(projection.getClaimedTotal())
            .approvedTotal(projection.getApprovedTotal())
            .claimPayableAmount(projection.getTotalPayableAmount())
            .systemUtilizationAmount(projection.getTotalUtilizationAmount())
            .claimPaymentResponses(toClaimPaymentResponse(claimPayments))
            .build();
  }

  public static List<ClaimPaymentResponse> toClaimPaymentResponse(List<ClaimPaymentProjection> claimPayments){
    return claimPayments.stream().map(ClaimMapper::toClaimPaymentResponse).toList();
  }

  public static ClaimPaymentResponse toClaimPaymentResponse(ClaimPaymentProjection claimPayment){
    return ClaimPaymentResponse.builder()
            .fundType(claimPayment.getFundType())
            .claimedAmount(claimPayment.getClaimedAmount())
            .facilityPayableAmount(claimPayment.getFacilityPayableAmount())
            .systemUtilizationAmount(claimPayment.getUtilizationAmount())
            .build();
  }
}
