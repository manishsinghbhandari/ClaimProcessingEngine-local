package io.coredge.health.claims.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.service.shr.ShrHelper;
import io.coredge.health.claims.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.exception.models.ErrorCode.REQUIRED_FIELD_NOT_EXIST;



@Service
@Slf4j
public class ClaimRequestProcessor {

    private final InsuranceClaimsRepository claimsRepository;

    private final ShrHelper shrHelper;

    public ClaimRequestProcessor(InsuranceClaimsRepository claimsRepository, ShrHelper shrHelper) {
        this.claimsRepository = claimsRepository;
        this.shrHelper = shrHelper;
    }

    @Value("${preauth.validation.enabled:false}")
    private boolean validationEnabled;


    public InsuranceClaim fromClaim(Claim claim, String claimId, String claimResponseId, String bundleId, Provider provider,
                                    Member member, ExtensionStateCodeEnum status) {
        InsuranceClaim iClaim = new InsuranceClaim();
        iClaim.setClaimId(claimId);
        iClaim.setClaimResponseId(claimResponseId);
        iClaim.setBundleId(bundleId);
        iClaim.setExternalId(claimId);
        iClaim.setProvider(provider);
        iClaim.setPatient(member);
        iClaim.setStatus(status);
        iClaim.setClaimCreatedAt(claim.getCreated().toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        // Fetch pre-auth Id from bundle
        validateAndSetPreAuthId(claim, iClaim);
        if (StringUtils.isNotEmpty(provider.getLevel())) {
            iClaim.setFacilityProviderLevel(provider.getLevel().toLowerCase());
        }
        iClaim.setIcdCodes(claim.getDiagnosis().stream()
                .map(diagnosisComponent -> diagnosisComponent.getDiagnosisCodeableConcept().getCoding().getFirst().getCode())
                .reduce((s, s2) -> s + " | " + s2).orElse(StringUtils.EMPTY));
        iClaim.setClaimCode(claim.getType().getText());
      //  iClaim.setClaimedTotal(claim.getTotal().getValue());
        iClaim.setClaimedTotal(getNetValue(claim));
        iClaim.setDateFrom(DateUtils.convertToKenyaLocalDateTime(claim.getBillablePeriod().getStart()));
        iClaim.setDateTo(DateUtils.convertToKenyaLocalDateTime(claim.getBillablePeriod().getEnd()));
        iClaim.setUse(claim.getUse().getDisplay().trim());
        if (claim.hasType()) {
            iClaim.setClaimType(claim.getType().getCoding().getFirst().getCode());
        }
        if (claim.hasSubType()) {
            iClaim.setAccessCode(claim.getSubType().getCoding().getFirst().getCode());
        }
        if(iClaim.getUse().equals(PREAUTHORIZATION)){
            iClaim.setExpiryDate(HealthIdContextHolder.preAuthExpiryDate());
        }
        // EPORTAL-397 -- If IPS exist in bundle then save into claims table
        isIpsExist(claim, iClaim);
        return iClaim;
    }



    private void validateAndSetPreAuthId(Claim claim, InsuranceClaim iClaim) {
        // Fetch pre-auth Id from bundle
        String preAuthId = fetchPreAuthIdFromBundle(claim);
        log.debug("Pre-Auth ID: {}", preAuthId);

        // Check if the pre-auth ID is present in the bundle and not blank
        if (StringUtils.isNotBlank(preAuthId)) {
            Optional<InsuranceClaim> insurancePreAuthClaimsOpt = claimsRepository.findByPreAuthIdAndUse(UUID.fromString(preAuthId), PREAUTHORIZATION);
            // Only set PreAuthId if it does NOT already exist in DB
            if (insurancePreAuthClaimsOpt.isEmpty()) {
                UUID preAuthUUID = UUID.fromString(preAuthId);
                iClaim.setPreAuthId(preAuthUUID);
            }
        }
    }

    /**
     * Extracts the Pre-Authorization ID from the given {@link Claim} bundle.
     * <p>
     * This method validates that the claim has related entries, and within those entries:
     * <ul>
     *     <li>Each related entry must contain a claim.</li>
     *     <li>The claim must have an identifier.</li>
     *     <li>The identifier must have a valid (non-null, non-empty) value.</li>
     * </ul>
     * <p>
     * If any of these conditions are not met, a {@link BusinessException} is thrown.
     * </p>
     *
     * @param claim The {@link Claim} object containing related claims.
     * @return The first valid Pre-Authorization ID found in the related claim entries,
     *         or an empty string if no related entries exist.
     * @throws BusinessException if:
     * <ul>
     *     <li>The claim has no related entries.</li>
     *     <li>A related claim is missing.</li>
     *     <li>A claim identifier is missing.</li>
     *     <li>The claim identifier has no value.</li>
     *     <li>No valid Pre-Auth ID is found.</li>
     * </ul>
     */


    public String fetchPreAuthIdFromBundle(Claim claim) {
        // If validation is disabled, return a default value or handle accordingly
        if (!validationEnabled || !claim.hasRelated()) {
            return StringUtils.EMPTY;
        }
            return claim.getRelated().stream()
                    .filter(relatedClaim -> Optional.ofNullable(relatedClaim.getRelationship())
                            .map(CodeableConcept::getCodingFirstRep)
                            .map(Coding::getCode)
                            .filter("pre-auth"::equalsIgnoreCase)
                            .isPresent())
                    .map(relatedClaim -> relatedClaim.getClaim().getIdentifier().getValue())
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(REQUIRED_FIELD_NOT_EXIST, "The Pre-Auth ID must not be blank in the bundle."));

        }


    /**
     * Calculates the total net value of all claim items.
     * Each item's net value is computed by multiplying its net by its quantity.
     *
     * @param claim The claim object containing a list of items.
     * @return The total net value of the claim as a BigDecimal.
     */
    public BigDecimal getNetValue(Claim claim) {
        return claim.getItem().stream()
                .map(item -> item.getNet().getValue()
                        .multiply(item.getQuantity().getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    public void isIpsExist(Claim claim, InsuranceClaim insuranceClaim) {
        String ipsClaim = isIpsClaim(claim);
        if (StringUtils.isNotBlank(ipsClaim)) {
            insuranceClaim.setIpsClaim(Boolean.TRUE);
        }
    }

    public String isIpsClaim(Claim claim) {
        return shrHelper.extractIdentifierContainingIps(claim);
    }
}