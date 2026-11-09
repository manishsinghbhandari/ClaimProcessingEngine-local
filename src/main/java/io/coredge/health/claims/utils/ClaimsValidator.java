package io.coredge.health.claims.utils;

import io.coredge.health.claims.component.InterventionValidationComponent;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.*;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;

import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.helpers.PatientHelper;
import io.coredge.health.claims.models.Predetermination;
import io.coredge.health.claims.service.ClaimRequestProcessor;
import io.coredge.health.claims.service.PostOfficeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.*;
import static io.coredge.health.claims.constants.Constants.Use.PREAUTHORIZATION;
import static io.coredge.health.claims.exception.models.ErrorCode.*;

/*
 Story reference: EPORTAL-181
 Story description: Implement validations on Pre-auth attaching to Claims in the Claim Processing engine
 */

@Slf4j
@Component
public class ClaimsValidator {

    @Value("${validateStateTransition}")
    private Boolean stateTransitionFlag;

    SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

    private final InsuranceClaimsRepository claimsRepository;

    private final LineItemRepository lineItemRepository;

    private final ClaimRequestProcessor claimRequestProcessor;

    private final PatientHelper patientHelper;

    private final PostOfficeService postOfficeService;

    private final InterventionCodeRepository interventionCodeRepository;

    private final InterventionValidationComponent validationComponent;

    private final InterventionDurationRepository interventionDurationRepository;

    private final ClaimStatusTransitionDefinitionRepository claimStatusTransitionDefinitionRepository;


    public ClaimsValidator(InsuranceClaimsRepository claimsRepository,
                           LineItemRepository lineItemRepository, ClaimRequestProcessor claimRequestProcessor,
                           PatientHelper patientHelper, PostOfficeService postOfficeService,
                           InterventionCodeRepository interventionCodeRepository,
                           InterventionValidationComponent validationComponent, InterventionDurationRepository interventionDurationRepository, ClaimStatusTransitionDefinitionRepository claimStatusTransitionDefinitionRepository) {
        this.claimsRepository = claimsRepository;
        this.lineItemRepository = lineItemRepository;
        this.claimRequestProcessor = claimRequestProcessor;
        this.patientHelper = patientHelper;
        this.postOfficeService = postOfficeService;
        this.interventionCodeRepository = interventionCodeRepository;
        this.validationComponent = validationComponent;
        this.interventionDurationRepository = interventionDurationRepository;
        this.claimStatusTransitionDefinitionRepository = claimStatusTransitionDefinitionRepository;
    }

    /**
     * final Constants
     */
    private static final String MFL = "MFL-";
    private static final String FID = "FID-";


    public void validatePreAuthClaims(Claim claim, Bundle inputBundle) {

        if (claim.getUse().equals(Claim.Use.CLAIM)) {
            String preAuthId = claimRequestProcessor.fetchPreAuthIdFromBundle(claim);
            if (!preAuthId.isBlank()) {
                validatePreAuthApproved(preAuthId, claim, inputBundle);
            } else {
                // If pre-authorization not required then there is no required validation
                log.info("Skipping the validation....");
            }
        }
    }

    /**
     * Validate related pre-authorization claims against the main claim.
     *
     * @param claim
     * @param bundle
     * @param preAuthShaCodeMap
     */
    public void checkRelatedPreAuth(Claim claim, Bundle bundle, Map<String, String> preAuthShaCodeMap) {
        Map<String, BigDecimal> codes =
                claim.getItem().stream()
                        .collect(Collectors.toMap(
                                item -> item.getProductOrService()
                                        .getCodingFirstRep()
                                        .getCode(),
                                item -> item.getNet().getValue(),
                                BigDecimal::add
                        ));
        LocalDate claimStartDate = FhirHelper.convertToLocalDate(claim.getBillablePeriod().getStart());
        if (claim.getUse().equals(Claim.Use.CLAIM) && claim.hasRelated()) {
            List<Claim.RelatedClaimComponent> relatedClaims = claim.getRelated();

            // Condition: Number of related pre-auth claims should not exceed the number of claim codes
            if (relatedClaims.size() > claim.getItem().size()) {
                throw new BusinessException(INVALID_DATA, "Number of Related Pre-Auth claims cannot be greater than number of Claim items.");
            }

            Set<String> matchedCodes = new HashSet<>();
            Set<String> preAuthIdSet = new HashSet<>();

            for (Claim.RelatedClaimComponent relatedClaim : relatedClaims) {
                Reference claimReference = relatedClaim.getClaim();
                String preAuthId = Optional.ofNullable(claimReference.getId())
                        .filter(id -> !id.isBlank())
                        .orElseGet(() -> claimReference.getIdentifier() != null
                                ? claimReference.getIdentifier().getValue()
                                : null);

                if (StringUtils.isBlank(preAuthId)) {
                    throw new RuntimeException("Unable to find Related Claim ID from reference.");
                }

                // Uniqueness Check: Ensure Pre-Auth ID is not repeated
                if (!preAuthIdSet.add(preAuthId)) {
                    throw new BusinessException(INVALID_DATA, "Duplicate Pre-Auth ID found in related claims: " + preAuthId);
                }

                // 1. Pre-Auth ID must be in DB.
                Optional<InsuranceClaim> insurancePreAuthClaimsOpt = claimsRepository.getClaimIdAndUse(UUID.fromString(preAuthId), PREAUTHORIZATION);
                InsuranceClaim preAuth = getInsuranceClaim(insurancePreAuthClaimsOpt, preAuthId, claimStartDate, claim);

                // 4. Ensure the pre-auth is not already attached to a DIFFERENT claim.
                // Note: We exclude the current claim from this check because preAuthId is now
                // set on the current claim's line items pre-save (by extractPreAuthShaCodeMap).
                // Without this exclusion, the current claim's own line items would falsely trigger
                // "already attached" when post-save validation runs.
                UUID currentClaimUuid = UUID.fromString(claim.getIdPart());
                List<ClaimLineItem> preAuthExists = lineItemRepository.findByPreAuthIdAndUse(UUID.fromString(preAuthId), Constants.Use.CLAIM)
                        .stream()
                        .filter(item -> !currentClaimUuid.equals(item.getClaim().getClaimId()))
                        .collect(Collectors.toList());
                if (!preAuthExists.isEmpty()) {
                    throw new BusinessException(PRE_AUTH_ALREADY_EXISTS, "Pre-Auth id : " + preAuthId + " already attached on this claim: " + preAuthExists.getFirst().getClaim().getClaimId());
                }

                // 5. Patient validation.
                validatePreAuthClaimPatient(claim, preAuth, bundle);

                // 6. Provider validation.
                validatePreAuthProvider(claim, preAuth, bundle);

                // 7. SHA code matching validation
                Optional<List<ClaimLineItem>> claimItemsOpt = lineItemRepository.findByClaimId(preAuth.getClaimId());
                String preAuthCode = checkPreAuthItems(claimItemsOpt, preAuthId, codes);
                // Defensive Check: preAuthCode must not be null or blank
                if (StringUtils.isBlank(preAuthCode)) {
                    throw new BusinessException(SHA_CODES_MISMATCH, "Pre-Auth ID " + preAuthId + " has no matching claim item code.");
                }
                // Mark this code as matched
                matchedCodes.add(preAuthCode);

                // ✅ Put in values in a map for later use.
                preAuthShaCodeMap.put(preAuthCode, preAuthId);

            }

            // Final Check: All Pre-Auth codes must have been matched to some claim code
            if (matchedCodes.size() != relatedClaims.size()) {
                throw new BusinessException(SHA_CODES_MISMATCH, "Not all Pre-Auth codes have matching claim item codes.");
            }
        }
    }

    /**
     * Extracts a map of intervention code → preAuth claim ID from the claim's {@code related} entries,
     * without performing any validation. This is used pre-save to populate {@code preAuthShaCodeMap}
     * so that {@code ClaimLineItem.preAuthId} is correctly set at creation time.
     * <p>
     * Full validation (preAuth exists in DB, is approved, patient/provider match, SHA code match)
     * is handled separately in {@code PostSaveValidationService.validateRelatedPreAuth()}.
     *
     * @param claim  The FHIR Claim resource
     * @return A map of intervention code → preAuth ID string, or empty map if no related entries / not a claim.
     */
    public Map<String, String> extractPreAuthShaCodeMap(Claim claim) {
        Map<String, String> preAuthShaCodeMap = new HashMap<>();

        if (!claim.getUse().equals(Claim.Use.CLAIM) || !claim.hasRelated()) {
            return preAuthShaCodeMap;
        }

        // Build a set of intervention codes present in this claim
        Set<String> claimCodes = claim.getItem().stream()
                .map(item -> item.getProductOrService().getCodingFirstRep().getCode())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Claim.RelatedClaimComponent relatedClaim : claim.getRelated()) {
            try {
                Reference claimReference = relatedClaim.getClaim();
                if (claimReference == null) continue;

                String preAuthId = Optional.ofNullable(claimReference.getId())
                        .filter(id -> !id.isBlank())
                        .orElseGet(() -> claimReference.getIdentifier() != null
                                ? claimReference.getIdentifier().getValue()
                                : null);

                if (StringUtils.isBlank(preAuthId)) {
                    log.warn("extractPreAuthShaCodeMap: skipping related entry with blank preAuthId");
                    continue;
                }

                // Look up the preAuth's line items to find its intervention code
                Optional<InsuranceClaim> preAuthOpt = claimsRepository.getClaimIdAndUse(
                        UUID.fromString(preAuthId), PREAUTHORIZATION);

                if (preAuthOpt.isEmpty()) {
                    log.warn("extractPreAuthShaCodeMap: preAuthId {} not found in DB — will be caught in post-save validation", preAuthId);
                    continue;
                }

                Optional<List<ClaimLineItem>> preAuthItemsOpt = lineItemRepository.findByClaimId(preAuthOpt.get().getClaimId());
                if (preAuthItemsOpt.isEmpty() || preAuthItemsOpt.get().isEmpty()) {
                    log.warn("extractPreAuthShaCodeMap: no line items found for preAuthId {}", preAuthId);
                    continue;
                }

                // Match the preAuth item code to a code in the claim
                preAuthItemsOpt.get().stream()
                        .map(ClaimLineItem::getCode)
                        .filter(Objects::nonNull)
                        .filter(claimCodes::contains)
                        .findFirst()
                        .ifPresentOrElse(
                                code -> {
                                    preAuthShaCodeMap.put(code, preAuthId);
                                    log.debug("extractPreAuthShaCodeMap: mapped code {} → preAuthId {}", code, preAuthId);
                                },
                                () -> log.warn("extractPreAuthShaCodeMap: no matching claim code for preAuthId {}", preAuthId)
                        );

            } catch (Exception e) {
                log.warn("extractPreAuthShaCodeMap: error processing related entry — will be caught in post-save validation. Cause: {}", e.getMessage());
            }
        }

        return preAuthShaCodeMap;
    }





    private String checkPreAuthItems(Optional<List<ClaimLineItem>> claimItemsOpt, String preAuthId, Map<String, BigDecimal> codes) {
        log.info("Checking pre auth items for pre auth id {}", preAuthId);
        List<ClaimLineItem> claimItems = claimItemsOpt.orElseThrow(() ->
                new BusinessException(VALIDATE_DATA_CANNOT_BLANK, "Claim items are missing for pre-auth ID: " + preAuthId)
        );

        if (claimItems.size() > 1) {
            throw new BusinessException(INVALID_DATA, "Multiple items found for pre-auth ID: " + preAuthId);
        }
        ClaimLineItem preAuthItem = claimItemsOpt.get().getFirst();
        String preAuthCode = preAuthItem.getCode();
        // Check if this pre-auth's code is in the claim's codes
        BigDecimal claimNetValue = codes.get(preAuthCode);
        if (claimNetValue == null) {
            throw new BusinessException(
                    SHA_CODES_MISMATCH,
                    "Pre-Auth SHA code " + preAuthCode + " does not match any claim code."
            );
        }
        BigDecimal netValuePreAuth = preAuthItem.getNetValue();
        BigDecimal netValueClaim= codes.get(preAuthCode);
        log.info("netValuePreAuth: {}", netValuePreAuth);
        log.info("netValueClaim: {}", netValueClaim);
        log.info("netValueClaim.compareTo(netValuePreAuth): {}", netValueClaim.compareTo(netValuePreAuth));
        if(netValueClaim.compareTo(netValuePreAuth) > 0){
            throw new BusinessException(
                    INVALID_DATA,
                    "Claim net value (" + netValueClaim +") is greater than Pre-Auth net value (" + netValuePreAuth +
                            ") for SHA code " + preAuthCode
            );
        }
        return preAuthCode;
    }

    // Package prefixes exempt from preauthorisation period/validity validation
    private static final String SHA_19_PACKAGE_PREFIX = "SHA-19-";
    private static final String PMF_07_PACKAGE_PREFIX = "PMF-07-";

    /**
     * Checks if the claim contains any codes from packages exempt from preauthorisation
     * period/validity validation (SHA-19 or PMF-07 packages).
     * If ANY item in the claim has SHA-19-XXX or PMF-07-XXX, the ENTIRE claim is exempt.
     */
    private boolean isClaimExemptFromPreAuthValidityCheck(Claim claim) {
        if (claim == null || !claim.hasItem()) {
            return false;
        }
        return claim.getItem().stream()
                .filter(item -> item.hasProductOrService() && item.getProductOrService().hasCoding())
                .map(item -> item.getProductOrService().getCodingFirstRep().getCode())
                .filter(Objects::nonNull)
                .anyMatch(code -> {
                    String upperCode = code.toUpperCase();
                    return upperCode.startsWith(SHA_19_PACKAGE_PREFIX) || upperCode.startsWith(PMF_07_PACKAGE_PREFIX);
                });
    }

    private @NotNull InsuranceClaim getInsuranceClaim(Optional<InsuranceClaim> insurancePreAuthClaimsOpt, String preAuthId, LocalDate claimStartDate, Claim claim) {
        if (insurancePreAuthClaimsOpt.isEmpty()) {
            throw new BusinessException(PRE_AUTH_NOT_EXISTS, "Pre-auth ID not found. ID: " + preAuthId);
        }


        // Check if claim contains SHA-19 or PMF-07 packages (exempt from preauth validity check)
        boolean isExemptPackage = isClaimExemptFromPreAuthValidityCheck(claim);
        if (isExemptPackage) {
            log.info("Claim [{}] contains exempt package (SHA-19 or PMF-07). Skipping Pre-Auth expiry validation.",
                    claim.getId()
            );
        }

        InsuranceClaim preAuth = insurancePreAuthClaimsOpt.get();
        long daysBetween = ChronoUnit.DAYS.between(preAuth.getCreationDate().toLocalDate() , preAuth.getExpiryDate());

        LocalDate claimEndDate = claim.getBillablePeriod().getEnd() != null
                ? FhirHelper.convertToLocalDate(claim.getBillablePeriod().getEnd())
                : null;

        // 2. Pre-Auth must be within the valid date range (SKIPPED for PMF or exempt packages like SHA-19, PMF-07)
        if (!isExemptPackage && preAuth.getExpiryDate() != null) {
            if ((claimStartDate.isBefore(preAuth.getCreationDate().toLocalDate()) || claimStartDate.isAfter(preAuth.getExpiryDate()))) {
                if (claimEndDate == null || claimStartDate.isEqual(claimEndDate) || (preAuth.getCreationDate().toLocalDate().isBefore(claimStartDate) || preAuth.getCreationDate().toLocalDate().isAfter(claimEndDate))) {
                    throw new BusinessException(PRE_AUTH_EXPIRED,
                            "Pre Auth " + preAuthId + " is only valid for " + daysBetween + " days " +
                                    "(from " + preAuth.getCreationDate().toLocalDate() + " to "
                                    + preAuth.getExpiryDate() + "). " +
                                    "The claim start date " + claimStartDate + " falls outside this validity period.");
                }
            }
        }

        // 3. Pre-Auth must be in approved state.
        if (!preAuth.getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
            throw new BusinessException(PRE_AUTH_NOT_IN_APPROVE_STATE, "Pre-Auth: " + preAuthId + " is not in APPROVED state.");
        }
        return preAuth;
    }

    // 1. Pre-auth needs to be in Approved status.
    public void validatePreAuthApproved(String preAuthId, Claim claim, Bundle inputBundle) {
        log.info("UUID:: {} ", preAuthId);

        // 1. Pre-Auth ID must be in DB.
        Optional<InsuranceClaim> insurancePreAuthClaimsOpt = claimsRepository.getClaimIdAndUse(UUID.fromString(preAuthId), PREAUTHORIZATION);
        if (insurancePreAuthClaimsOpt.isEmpty()) {
            throw new BusinessException(PRE_AUTH_NOT_EXISTS, "Pre-auth ID does not exist in DB");
        }

        // 2. Pre-Auth must be in approved state.
        if (!insurancePreAuthClaimsOpt.get().getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
            throw new BusinessException(PRE_AUTH_NOT_IN_APPROVE_STATE, "Pre-Auth is not in APPROVED state.");
        }

        // 3. Ensure the pre-auth is not already attached to a DIFFERENT claim.
        // Exclude the current claim — its InsuranceClaim.preAuthId is set pre-save, so
        // findByPreAuthIdAndUse would otherwise return it as a false-positive duplicate.
        Optional<InsuranceClaim> preAuthExists = claimsRepository.findByPreAuthIdAndUse(UUID.fromString(preAuthId), Constants.Use.CLAIM)
                .filter(existing -> !existing.getClaimId().toString().equals(claim.getIdPart()));
        if (preAuthExists.isPresent()) {
            throw new BusinessException(PRE_AUTH_ALREADY_EXISTS, "Pre-Auth already attached on this claim: " + preAuthExists.get().getClaimId());
        }
        InsuranceClaim preAuthClaim = insurancePreAuthClaimsOpt.get();

        // 4. Patient validation.
        validatePreAuthClaimPatient(claim, preAuthClaim, inputBundle);
        // 5. Provider needs to same for pre-auth and claim
        validatePreAuthProvider(claim, preAuthClaim, inputBundle);
        // 6. Validate same sha code pre-auth and claims
        Optional<List<ClaimLineItem>> claimItems = lineItemRepository.findByClaimId(preAuthClaim.getClaimId());
        // 7. SHA code validation & Amount validation
        validatePreAuthClaimShaCodesAndUnitPrice(claim, claimItems);
    }

    // 1. Pre-auth needs to be in Approved status.
    public List<String> validatePreAuthApproved(Predetermination data) {

        List<String> errors = new ArrayList<>();

        // 1. Ensure Only One Claim is Linked to a Pre-Authorization.
        Optional<InsuranceClaim> preAuthExists = claimsRepository.findByPreAuthIdAndUse(UUID.fromString(data.getPreAuthRef()), Constants.Use.CLAIM);;
        if (preAuthExists.isPresent()) {
             errors.add("Pre-Auth already attached on this claim: " + preAuthExists.get().getClaimId());
             return errors;
        }
        // 2. Pre-Auth ID must be in DB.
        Optional<InsuranceClaim> insurancePreAuthClaimsOpt = claimsRepository.getClaimIdAndUse(UUID.fromString(data.getPreAuthRef()), PREAUTHORIZATION);
        if (insurancePreAuthClaimsOpt.isEmpty()) {
            errors.add("Pre-auth ID does not exist in DB");
            return errors;
        }
            InsuranceClaim preAuthClaim = insurancePreAuthClaimsOpt.get();

        // 3. Pre-Auth must be in approved state.
        if (!preAuthClaim.getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
            errors.add("Pre-Auth is not in APPROVED state.");
        }

        LocalDateTime dateFrom = FhirHelper.convertToLocalDateTime(data.getBillableStartDate());
        LocalDateTime dateTo = FhirHelper.convertToLocalDateTime(data.getBillableEndDate());

        if (!dateFrom.equals(preAuthClaim.getDateFrom()) ||
                dateTo.isAfter(preAuthClaim.getDateTo())) {
            errors.add(String.format("Billable Start Date %s and End date %s does not match with PreAuth Approved StartDate %s and EndDate %s Range", data.getBillableStartDate(), data.getBillableEndDate(), preAuthClaim.getDateFrom(), preAuthClaim.getDateTo()));
        }

        // 4. Patient validation.
        Optional.ofNullable(validatePreAuthClaimPatient(data, preAuthClaim)).ifPresent(errors::add);
        // 5. Provider need to same for pre-auth and claim
        Optional.ofNullable(validatePreAuthProvider(data, preAuthClaim)).ifPresent(errors::add);
        // 6. Validate same sha code pre-auth and claims
        Optional<List<ClaimLineItem>> claimItems = lineItemRepository.findByClaimId(preAuthClaim.getClaimId());
        // 7. SHA code validation & Amount validation
        Optional.ofNullable(validatePreAuthClaimShaCodesAndUnitPrice(data, claimItems)).ifPresent(errors::add);

        return errors;
    }

    // 3. Patient id needs to be same for both Pre-auth and Claim.
    public void validatePreAuthClaimPatient(Claim claim, InsuranceClaim preAuthClaim, Bundle inputBundle) {
        // Extract Patient Reference from Claim
        if (!claim.hasPatient()) {
            throw new BusinessException(MISSING_PATIENT_REF, "Missing patient reference in claim.");
        }
        Patient patient = patientHelper.extractPatient(inputBundle, claim);
        patientVerifyFromBundle(preAuthClaim, patient);
    }

    // 3. Patient id needs to be same for both Pre-auth and Claim.
    public String validatePreAuthClaimPatient(Predetermination data, InsuranceClaim preAuthClaim) {
        // Extract Patient Reference from Claim
        if (!data.hasPatientId()) {
            return "Missing patient reference in claim.";
        }
        if(!StringUtils.equals(data.getPatientId() , preAuthClaim.getPatient().getCrId())){
            return "Patient Id of Claim %s and Patient Id of Pre Auth %s do not match";
        }
        return null;
    }

    public void patientVerifyFromBundle(InsuranceClaim preAuthClaim, Patient patient) {
        final String patientId  = patient.getIdElement().getIdPart();
        if (StringUtils.isBlank(patientId)) {
            throw new BusinessException(VALIDATE_DATA_CANNOT_BLANK, "Patient ID must not be blank");
        }
        if (!patientId.equals(preAuthClaim.getPatient().getCrId())) {
            throw new BusinessException(PATIENT_ASSOCIATION_CLAM, String.format("Patient Id of Claim %s and Patient Id of Pre Auth %s do not match",
                    patientId, preAuthClaim.getPatient().getCrId()));
        }
    }


    public void validatePreAuthClaimShaCodesAndUnitPrice(Claim claim, Optional<List<ClaimLineItem>> claimLineItems) {
        if (!claim.hasItem()) {
            throw new BusinessException(VALIDATE_DATA_CANNOT_BLANK, "Missing items in the FHIR bundle.");
        }

        if (claimLineItems.isPresent()) {
            // Extract SHA codes and net values from stored claim items
            Map<String, BigDecimal> storedShaCodeNetValues = claimLineItems.get().stream()
                    .filter(item -> item.getCode() != null && item.getNetValue() != null)
                    .collect(Collectors.toMap(ClaimLineItem::getCode, ClaimLineItem::getNetValue, BigDecimal::add));

            // Extract SHA codes and net values from the FHIR claim
            Map<String, BigDecimal> claimShaCodeNetValues = claim.getItem().stream()
                    .filter(item -> item.getProductOrService().hasCoding())
                    .filter(item -> item.hasNet() && item.getNet().hasValue())
                    .collect(Collectors.toMap(
                            item -> item.getProductOrService().getCodingFirstRep().getCode(),
                            item -> item.getNet().getValue(),
                            (existing, replacement) -> existing)); // Handle duplicate SHA codes by keeping the first encountered value

            log.info("SHA Codes and Net Values in FHIR: {}", claimShaCodeNetValues);
            log.info("SHA Codes and Net Values in System: {}", storedShaCodeNetValues);

            // Validate SHA codes presence
            Set<String> missingShaCodes = new HashSet<>(claimShaCodeNetValues.keySet());
            missingShaCodes.removeAll(storedShaCodeNetValues.keySet());

            if (!missingShaCodes.isEmpty()) {
                throw new BusinessException(SHA_CODES_MISMATCH,
                        "SHA code mismatch: Expected " + storedShaCodeNetValues.keySet() +
                                ", but received " + claimShaCodeNetValues.keySet() + ". Please verify and correct it.");
            }

            // Validate unit prices for matching SHA codes
            for (Map.Entry<String, BigDecimal> entry : claimShaCodeNetValues.entrySet()) {
                String shaCode = entry.getKey();
                BigDecimal claimUnitPrice = entry.getValue();
                BigDecimal preAuthUnitPrice = storedShaCodeNetValues.get(shaCode);

                if (preAuthUnitPrice != null && claimUnitPrice.compareTo(preAuthUnitPrice) > 0) {
                    throw new BusinessException(UNIT_PRICE_MISMATCH, String.format(
                            "Unit price mismatch for SHA code %s: Expected %s KES (pre-auth), but found %s KES in claim.",
                            shaCode, preAuthUnitPrice, claimUnitPrice));
                }
            }
        }
    }

    public String validatePreAuthClaimShaCodesAndUnitPrice(Predetermination data, Optional<List<ClaimLineItem>> claimLineItems) {

        if (claimLineItems.isPresent()) {
            // Extract SHA codes and net values from stored claim items
            Map<String, BigDecimal> storedShaCodeNetValues = claimLineItems.get().stream()
                    .filter(item -> item.getCode() != null && item.getNetValue() != null)
                    .collect(Collectors.toMap(ClaimLineItem::getCode, ClaimLineItem::getNetValue, BigDecimal::add));

            if (!storedShaCodeNetValues.containsKey(data.getCode())) {
                return String.format("SHA code mismatch: Expected %s, but received %s. Please verify and correct it.", storedShaCodeNetValues.keySet() , data.getCode());
            }

            BigDecimal preAuthNetPrice = storedShaCodeNetValues.get(data.getCode());

                if (preAuthNetPrice != null && data.getClaimAmount() > preAuthNetPrice.doubleValue()) {
                    return String.format(
                            "Net price mismatch for SHA code %s: Expected %s KES (pre-auth), but found %s KES in claim.",
                            data.getCode(), preAuthNetPrice, data.getClaimAmount());
                }
            }
        return null;
    }

    // 6. Provider needs to be same.
    public void validatePreAuthProvider(Claim claim, InsuranceClaim preAuthClaim, Bundle bundle) {
        String providerId = getProviderOrganizationFromClaim(bundle, claim);
        log.info("Provider ID: {}", providerId);

        if (!providerId.equals(preAuthClaim.getProvider().getFidCode())) {
            throw new BusinessException(FID_CODE_MISMATCH, String.format("FID Code mismatch: Bundle %s does not match with PreAuth %s.",
                    providerId, preAuthClaim.getProvider().getFidCode()
            ));
        }
    }

    // 6. Provider needs to be same.
    public String validatePreAuthProvider(Predetermination data, InsuranceClaim preAuthClaim) {

            // Throw an exception if the providerId does NOT match the preAuthClaim FID Code
            if (!StringUtils.equals(data.getProviderId(),preAuthClaim.getProvider().getFidCode())) {
                return String.format("FID Code mismatch: FHIR Bundle %s does not match with PreAuth %s.",
                        data.getProviderId(), preAuthClaim.getProvider().getFidCode());
            }
            return null;
    }
    private String getProviderOrganizationFromClaim(Bundle bundle, Claim claim) {
        if (!claim.hasProvider()) {
            throw new BusinessException(MISMATCH_DATA, "Provider reference does not exist in the bundle.");
        }
        // Extract provider ID
        return extractProvider(bundle, claim).getIdElement().getIdPart();
    }

    private Organization extractProvider(Bundle bundle, Claim claim) {
        Organization organization = extractCoverageAndGetProvider(bundle);

        if (organization == null) {
            // Fallback: Use utility method to process Provider from the claim
            Reference providerReference = claim.getProvider();
            if (providerReference == null || providerReference.getReference() == null) {
                throw new BusinessException(MISMATCH_DATA, "Claim provider reference is missing.");
            }

            organization = (Organization) postOfficeService.getResourceByUrl(bundle, providerReference.getReference(), providerReference.getId());

            if (organization == null) {
                throw new BusinessException(MISMATCH_DATA, "Referenced Provider: " + providerReference.getReference() + " is not found in bundle.");
            }
        }

        return organization;
    }

    private Organization extractCoverageAndGetProvider(Bundle bundle) {
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.getResource() instanceof Coverage coverage) {
                Reference beneficiaryReference = coverage.getBeneficiary();
                if (beneficiaryReference != null) {
                    return postOfficeService.getBeneficiary(bundle, beneficiaryReference.getReference());
                }
            }
        }
        return null; // Return null if no Provider is found
    }

    public List<String> extractInterventionCodes(Claim claim) {
        return Optional.ofNullable(claim.getItem())
                .stream().flatMap(Collection::stream)
                .map(Claim.ItemComponent::getProductOrService)
                .map(CodeableConcept::getCoding)
                .filter(codingList -> !codingList.isEmpty())
                .map(List::getFirst)
                .map(Coding::getCode)
                .filter(Objects::nonNull)
                .toList();
    }
    public boolean checkCodeExist(List<String> codes , String targetCode){
        return codes.stream().map(String::toLowerCase).anyMatch(code -> code.contains(targetCode.toLowerCase()));
    }

    public void validateSHACodeCombinations(Claim claim)  {

        if(claim.hasItem() && claim.getItem().size() == 1){
            log.info("Claim {} have only single item.", claim.getIdPart());
            return;
        }

        // If Item have more then 1 code we are storing them as Sequence as Key and ShaCodeList as Value
        Map< Integer , List<Coding>>  codeMap = Optional.of(claim)
                .filter(Claim::hasItem)
                .map(Claim::getItem)
                .stream()
                .flatMap(Collection::stream)
                .filter(Claim.ItemComponent::hasSequence)
                .collect(Collectors.toMap(Claim.ItemComponent::getSequence , item -> item.getProductOrService().getCoding()));

        // Sequence 1 claim Line Item is not present
        if(!codeMap.containsKey(1) || codeMap.get(1).getFirst().isEmpty()){
            log.info("Claim {} does not contain required Sequence code for Intervention Codes Validation", claim.getIdPart());
            throw new DataValidationException(MISMATCH_DATA, "Claim does not contain required Sequence code for Intervention Codes Validation");
        }

        if(claim.getBillablePeriod().getStart() == null){
            log.info("Claim {} does not contain required Billable Period for Intervention Codes Validation", claim.getIdPart());
            throw new DataValidationException(MISMATCH_DATA, "Claim does not contain required Billable Period code for Intervention Codes Validation");
        }

        String primarySequenceCode = codeMap.get(1).getFirst().getCode();

        codeMap.remove(1);

        List<String> codes = codeMap.values().stream().flatMap(Collection::stream).map(Coding::getCode).toList();


        //Get Allowed Code Combination against primary SHA Code
        Optional<InterventionCode> dbInterventionCode = interventionCodeRepository.findCodeThatSubStringOf(primarySequenceCode , claim.getBillablePeriod().getStart());
        if (dbInterventionCode.isEmpty()) {
            log.info("No Combination Present");
            return;
        }

        // Validating Allowed Code Combinations against Primary SHA Code
        List<String> unMatched = new ArrayList<>();
        List<String> unallowed;
        unallowed = codes.stream()
                .filter(code -> !validationComponent.existCode(code,dbInterventionCode.get().getAllowedCombination()))
                .toList();

        unMatched.addAll(unallowed);

        //Check to find UnAllowed Codes
        if(Objects.nonNull(dbInterventionCode.get().getUnallowedCombination()) && !dbInterventionCode.get().getUnallowedCombination().isEmpty()){
            unallowed = codes.stream()
                    .filter(code -> validationComponent.existCode(code,dbInterventionCode.get().getUnallowedCombination()))
                    .toList();

            unMatched.addAll(unallowed);
        }

        // UnAllowed SHA codes against Primary code
        if(!unMatched.isEmpty()){
            log.info("Claim {} is not allowed. Intervention Codes Combination is not allowed for {} Against Primary InterventionCode : {} " , claim.getIdPart() , unMatched , primarySequenceCode);
            throw new DataValidationException("Claim is not allowed. Intervention Codes Combination is not allowed for " + unMatched + " Against Primary InterventionCode : " + primarySequenceCode);
        }
    }

    public void validateSHACodeDuration(Claim claim)  {

        if(claim.hasItem() && claim.getItem().size() == 1){
            log.info("Claim {} have only single item.", claim.getIdPart());
            return;
        }

        List<Claim.ItemComponent> sortedItems = new ArrayList<>(claim.getItem());
        sortedItems.sort(Comparator.comparingInt(Claim.ItemComponent::getSequence));

        Date previousStartDate = null;
        String previousCode = null;
        Map<String , List<Period>> unMatched = new HashMap<>();

        for (Claim.ItemComponent item : sortedItems) {
            String interventionCode = item.getProductOrService().getCoding().getFirst().getCode();

            // Backward compatibility for serviceDate
            Date startDate;
            Date endDate;
            if (item.hasServicedDateType()) {
                Date servicedDate = item.getServicedDateType().getValue();
                startDate = servicedDate;
                endDate = servicedDate;
            } else if (item.hasServicedPeriod()) {
                startDate = item.getServicedPeriod().getStart();
                endDate = item.getServicedPeriod().getEnd();
            } else {
                log.warn("Claim {} Item {} has no serviced information", claim.getIdPart(), item.getSequence());
                continue;
            }

            if(previousStartDate == null){
                previousStartDate = startDate;
                previousCode = interventionCode;
                continue;
            }

            List<InterventionDuration> dbInterventionDuration = interventionDurationRepository.findCodeThatSubStringOf(previousCode);

            if (dbInterventionDuration.isEmpty()) {
                log.info("No Duration Present");
                previousStartDate = startDate;      //  Needs update
                previousCode = interventionCode;  //  Needs update
                continue;
            }

            Date finalPreviousStartDate = previousStartDate;
            boolean notAllowed = dbInterventionDuration.stream()
                    .anyMatch(duration ->!validationComponent.validatePeriod(duration.getDuration() , duration.getOperator().getPredicate(), finalPreviousStartDate, startDate));

            if (notAllowed) {
                if (item.hasServicedPeriod()) {
                    unMatched.computeIfAbsent(interventionCode, c -> new ArrayList<>())
                            .add(item.getServicedPeriod());
                } else {
                    Period singleDatePeriod = new Period();
                    singleDatePeriod.setStart(startDate);
                    singleDatePeriod.setEnd(endDate);
                    unMatched.computeIfAbsent(interventionCode, c -> new ArrayList<>())
                            .add(singleDatePeriod);
                }
            }
            previousStartDate = startDate;
            previousCode = interventionCode;
        }

        // UnAllowed SHA codes Period
        if(!unMatched.isEmpty()){
            log.info("Claim {} is not allowed. Intervention Codes Service Periods Durations are not allowed for {} " , claim.getIdPart() , formatPeriods(unMatched));
            throw new DataValidationException("Claim is not allowed. Intervention Codes Service Periods Durations are not allowed for " + formatPeriods(unMatched));
        }
    }

    public String formatPeriods(Map<String, List<Period>> periodMap) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, List<Period>> entry : periodMap.entrySet()) {
            result.append("[").append(entry.getKey()).append(":");
            List<Period> periods = entry.getValue();
            for (int i = 0; i < periods.size(); i++) {
                Period period = periods.get(i);
                String startStr = formatter.format(period.getStart());
                String endStr = formatter.format(period.getEnd());
                result.append("From ").append(startStr).append(" to ").append(endStr);
                if (i < periods.size() - 1) {
                    result.append(" | ");
                }
            }
            result.append("], ");
        }

        // Remove trailing comma & space
        if (result.length() >= 2) {
            result.setLength(result.length() - 2);
        }

        return result.toString();
    }

    public void validatePractitioner(Practitioner practitionerProvider, Practitioner practitionerHie){
        PractitionerDetails providerDetail = Objects.requireNonNull(PractitionerDetails.fromPractitioner(practitionerProvider));
        PractitionerDetails hieDetail = Objects.requireNonNull(PractitionerDetails.fromPractitioner(practitionerHie));

        if(!providerDetail.getPuId().equals(hieDetail.getPuId())){
            throw new DataValidationException("Practitioner details do not match with HIE for PU_ID");
        }
        if(!providerDetail.getRegNo().equals(hieDetail.getRegNo())){
            throw new DataValidationException("Practitioner details do not match with HIE for Registration Number");
        }
        if(!providerDetail.getFullNames().equalsIgnoreCase(hieDetail.getFullNames())){
            throw new DataValidationException("Practitioner details do not match with HIE for Practitioner Name");
        }
    }

    public boolean isCivilServant(Claim claim) {
        return claim.hasInsurance() &&
                claim.getInsurance().stream()
                        .map(Claim.InsuranceComponent::getCoverage)
                        .filter(Objects::nonNull)
                        .map(Reference::getReference)
                        .filter(Objects::nonNull)
                        .anyMatch(ref -> ref.toLowerCase().contains(Constants.PMF));
    }

    public void validateClaimStatusBeforeUpdate(ExtensionStateCodeEnum currentStatus , ExtensionStateCodeEnum updateStatus , String use , String claimId) {

        log.info("Validate State Transition for Current Status : {} , Update Status : {} , use :{} ", currentStatus, updateStatus , use);
        if(!Boolean.TRUE.equals(stateTransitionFlag)){
            log.info("Validate State Transition Flag is Turned False for claimId {}", claimId);
            return;
        }
        if (!currentStatus.equals(updateStatus)) {

            boolean allowed = claimStatusTransitionDefinitionRepository.existsByStatusAndAllowedStatusAndUseAndActive(currentStatus , updateStatus , use , true);

            if (!allowed) {
                throw new IllegalStateException(
                        "Invalid claim status transition: " + currentStatus + " → " + updateStatus
                );
            }
        }
    }



    public void validateInterventionCodesForPatientType(Claim claim) {
        boolean isCivilServant = isCivilServant(claim);
        List<String> interventionCodes = extractInterventionCodes(claim);

        if (isCivilServant) {
            // Civil Servants must NOT use SHA-03, SHA-07, SHA-10
            List<String> violatingCodes = interventionCodes.stream()
                    .filter(code -> CIVIL_SERVANT_RESTRICTED_SHA_CODES.stream()
                            .anyMatch(restricted -> code.toUpperCase().startsWith(restricted)))
                    .toList();
            if (!violatingCodes.isEmpty()) {
                throw new DataValidationException(CIVIL_SERVANT_INVALID_CODE_MESSAGE);
            }
        } else {
            // Normal patients must NOT use PMF-03, PMF-07, PMF-10
            List<String> violatingCodes = interventionCodes.stream()
                    .filter(code -> NORMAL_PATIENT_RESTRICTED_PMF_CODES.stream()
                            .anyMatch(restricted -> code.toUpperCase().startsWith(restricted)))
                    .toList();
            if (!violatingCodes.isEmpty()) {
                throw new DataValidationException(NORMAL_PATIENT_INVALID_CODE_MESSAGE);
            }
        }
    }
}
