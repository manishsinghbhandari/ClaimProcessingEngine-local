package io.coredge.health.claims.component;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.models.eclaims.CppPaymentRequest;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class CppComponent {

    public CppPaymentRequest apply(CreateClaimFundScheme request, InsuranceClaim insurance) {
        // Convert fund schemes using helper method
        String userId = (insurance.getApprover1() == null || insurance.getApprover1() .isEmpty())
                ? insurance.getCreatedBy()
                : insurance.getApprover1();
        CppPaymentRequest response = mapFundSchemes(request.getFundScheme());
        String beneficiaryCrNumber = insurance.getPatient().getCrId();
        response.setClaimId(updateClaimNumber(response, request.getClaimNo()));
        response.setInvoiceNumber(request.getInvoiceNumber());
        response.setClaimReferenceLink(request.getClaimReferenceLink());
        response.setServiceCode(request.getInterventionCodes());
        response.setClaimAmount(insurance.getClaimedTotal());
        response.setCounty(insurance.getProvider().getCounty());
        response.setOwnershipType(insurance.getProvider().getFacilityAgent());
        response.setServiceDate(insurance.getDateFrom().toString());
        response.setClaimType(Constants.CppClaimType.SUBMISSION);
        response.setClaimStatus(insurance.getStatus().name());
        response.setClaimDate(insurance.getCreationDate().toString());
        response.setBeneficiaryCrNumber(beneficiaryCrNumber);
        response.setPrimaryContributorNumber(beneficiaryCrNumber);
        response.setBeneficiaryShaId(request.getBeneficiaryShaNo());
        response.setBeneficiaryName(request.getBeneficiaryName());
        response.setProviderName(request.getVendorName());
        response.setFid(request.getVendorCode());
        response.setClaimReferenceId(request.getClaimReferenceId());
        response.setVerifiedBy(userId);
        response.setProviderInvoiceDate(request.getInvoiceDate());
        response.setApprovedClaimAmount(insurance.getClaimedTotal());

        return response;
    }

    private CppPaymentRequest mapFundSchemes(List<CreateClaimFundScheme.FundScheme> funds) {
        if (funds == null || funds.isEmpty()) {
            return null;
        }
        return funds.stream()
                .filter(fund -> fund.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .map(fund -> {
                    CppPaymentRequest cppFund = new CppPaymentRequest();
                    cppFund.setFundSchemeType(fund.getFundSchemeType());
                    cppFund.setFundSchemeAmount(fund.getAmount());
                    return cppFund;
                })
                .orElse(null);
    }

    private String updateClaimNumber(CppPaymentRequest paymentRequest, String claimNumber) {

        List<String> CLAIM_SUFFIXES = List.of(
                Constants.ClaimFundType.PMSF,
                Constants.ClaimFundType.SHIF,
                Constants.ClaimFundType.ECCIF,
                Constants.ClaimFundType.PHC
        );

        boolean hasSuffix = CLAIM_SUFFIXES.stream()
                .anyMatch(suffix -> claimNumber.toLowerCase().endsWith(suffix.toLowerCase()));
        String childClaimNo = claimNumber; // default value
        if (!hasSuffix) {
            log.info("Claim ID without expected suffix: {}", claimNumber);
            childClaimNo = claimNumber + "_" + paymentRequest.getFundSchemeType();
            log.info("Updated Claim ID with suffix: {}", childClaimNo);
        }
        return childClaimNo;
    }

}
