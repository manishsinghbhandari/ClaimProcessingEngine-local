package io.coredge.health.claims.service;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.InterventionDurationRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.helpers.ClaimValidationUtility;
import io.coredge.health.claims.service.intervention.InterventionValidationService;
import io.coredge.health.claims.utils.ClaimsValidator;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static io.coredge.health.claims.constants.Constants.*;
import static io.coredge.health.claims.constants.FhirSystemConstants.VALID_CLAIM_SUB_TYPES;
import static org.hl7.fhir.r4.model.Claim.Use.CLAIM;

/**
 * Service responsible for running post-save business validations on claims and pre-authorizations.
 * <p>
 * These validations were previously run before DB save, blocking submission entirely.
 * Now they run as a BPMN workflow step after the claim/preauth has been persisted,
 * allowing the provider to see the claim in the system and correct issues via resubmission.
 * <p>
 * If any validation fails, the claim/preauth is set to SENT_BACK with error details.
 */
@Slf4j
@Service
public class PostSaveValidationService {

    @Value("${benefitsRestrictionsEnabled:true}")
    private boolean benefitsRestrictionsEnabled;

    @Value("${preAuthDocReq}")
    private boolean isPreAuthDocReq;

    @Value("${claimDocReq}")
    private boolean isClaimDocReq;

    @Value("#{'${interventions.reqPreAuth}'.split(',')}")
    private List<String> interventionsReqPreAuth;

    @Value("${finsprint.eligibility.enabled}")
    private boolean finsprintEligibilityEnabled;

    @Value("${dnm.extended.validation.enable:false}")
    private boolean validateDnmExtendedEnable;

    private final BenefitMasterService benefitMasterService;
    private final InsuranceClaimsRepository claimsRepository;
    private final ClaimsValidator claimsValidator;
    private final MaternityApprovalService maternityApprovalService;
    private final InterventionDurationRepository interventionDurationRepository;
    private final ClaimBundleTransactionalService claimBundleTransactionalService;
    private final InterventionValidationService interventionValidationService;

    public PostSaveValidationService(BenefitMasterService benefitMasterService,
                                     InsuranceClaimsRepository claimsRepository,
                                     ClaimsValidator claimsValidator,
                                     MaternityApprovalService maternityApprovalService,
                                     InterventionDurationRepository interventionDurationRepository,
                                     ClaimBundleTransactionalService claimBundleTransactionalService,
                                     InterventionValidationService interventionValidationService) {
        this.benefitMasterService = benefitMasterService;
        this.claimsRepository = claimsRepository;
        this.claimsValidator = claimsValidator;
        this.maternityApprovalService = maternityApprovalService;
        this.interventionDurationRepository = interventionDurationRepository;
        this.claimBundleTransactionalService = claimBundleTransactionalService;
        this.interventionValidationService = interventionValidationService;
    }

    /**
     * Runs all post-save validations on a claim/preauth.
     * Returns an empty list if all validations pass.
     *
     * @param claim         The FHIR Claim resource
     * @param bundle        The original FHIR Bundle (may be null for reprocessing)
     * @param organization  The provider Organization resource (may be null)
     * @param provider      The Provider entity (may be null)
     * @return List of validation error messages (empty = all passed)
     */
    public List<String> validate(Claim claim, Bundle bundle, Organization organization, Provider provider) {
        List<String> errors = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        log.info("Starting validation for claim: {} and bundle: {}", claim.getId(), bundle.getId());

        // 1. Billable end date not in the future
        validateBillableEndDateNotFuture(claim, sdf, errors);

        // 2. SHA benefit package start date (before 2024-10-01)
        validateShaBenefitStartDate(claim, sdf, errors);

        // 3. Attachment requirement
        validateAttachments(claim, errors);

        // 4. Intervention code validity
        validateInterventionCodeValidity(claim, errors);

        // 5. Claim sub-type validation
        validateClaimSubType(claim, errors);

        // 6. Claim total amount check
        validateClaimTotal(claim, errors);

        // 7. Maternity 10-month lookback
        validateMaternityLookback(claim, errors);

        // 8. SHA code combination validation
        validateShaCodeCombinations(claim, errors);

        // 9. SHA code duration validation
        validateShaCodeDuration(claim, errors);

        // 10. Per-diem / LOS billable period validation
        validatePerDiemBillablePeriod(claim, sdf, errors);

        // 11. Pre-auth for long stay (>12 days)
        validatePreAuthForLongStay(claim, sdf, errors);

        // 12. Related pre-auth validation
        validateRelatedPreAuth(claim, bundle, errors);

        // 13. Pre-auth claims validation (EPORTAL-181)
        validatePreAuthClaims(claim, bundle, errors);

        // 14. Pre-auth required check (benefit matrix)
        validatePreauthRequired(claim, errors);

        // 15. SHA-01 eligibility
        validateSha01Eligibility(claim, provider, errors);

        // 16. Finsprint eligibility validation
        validatePolicyPeriod(claim, errors);

        // 17. Intervention code patient-type restriction (civil servant vs normal patient)
        validateInterventionCodesForPatientType(claim, errors);

        // 18. DNM extended intervention rules
        validateDnmRules(claim, provider, errors);

        return errors;
    }

    // --- Individual Validation Methods ---

    private void validateBillableEndDateNotFuture(Claim claim, SimpleDateFormat sdf, List<String> errors) {
        try {
            if (claim.hasBillablePeriod() && claim.getBillablePeriod().hasEnd() && claim.getUse().equals(CLAIM)) {
                Date billableEndDate = claim.getBillablePeriod().getEnd();
                LocalDate billableEndLocalDate = FhirHelper.convertToLocalDate(billableEndDate);
                LocalDate today = io.coredge.health.claims.utils.DateUtils.nowNairobiLocalDate();
                if (billableEndLocalDate.isAfter(today)) {
                    errors.add("Billable Period End date cannot be in the future: " + sdf.format(billableEndDate) + " for Claim ID: " + claim.getIdPart());
                }
            }
        } catch (Exception e) {
            errors.add("Error validating billable end date: " + e.getMessage());
        }
    }

    private void validateShaBenefitStartDate(Claim claim, SimpleDateFormat sdf, List<String> errors) {
        try {
            if (claim.hasBillablePeriod() && claim.getBillablePeriod().hasStart()) {
                Date billableStartDate = claim.getBillablePeriod().getStart();
                Date shaServiceStartDate = sdf.parse("2024-10-01");
                if (billableStartDate.before(shaServiceStartDate)) {
                    errors.add("SHA BENEFIT PACKAGE start date cannot be before 01/10/2024 for Claim ID: " + claim.getIdPart());
                }
            }
        } catch (ParseException e) {
            errors.add("Invalid date format in Billable Start Period: " + e.getMessage());
        }
    }

    private void validateAttachments(Claim claim, List<String> errors) {
        try {
            if ((isPreAuthDocReq && claim.getUse().equals(Claim.Use.PREAUTHORIZATION))
                    || (isClaimDocReq && claim.getUse().equals(CLAIM))) {
                if (!ClaimValidationUtility.hasAtLeastOneAttachment(claim)) {
                    errors.add("Attachment(s) required. At least one attachment needed in claim.");
                }
            }
        } catch (Exception e) {
            errors.add("Error validating attachments: " + e.getMessage());
        }
    }

    private void validateInterventionCodeValidity(Claim claim, List<String> errors) {
        try {
            if (benefitsRestrictionsEnabled) {
                List<String> invalidCodes = ClaimValidationUtility.listInvalidCodes(claim, benefitMasterService);
                if (invalidCodes != null && !invalidCodes.isEmpty()) {
                    errors.add("Invalid intervention codes found: " + invalidCodes);
                }
            }

            // Check that all intervention codes on the claim exist in benefits_master.
            // Unknown codes cause a NoSuchElementException crash in auto-adjudication.
            // We catch them here so the claim is SENT_BACK with a clear message instead.
            // NOTE: We use benefitMasterService.existsByInterventionCode() — NOT rulesCache,
            // because rulesCache only loads codes that have a groupName set (i.e. have DNM rules).
            // Many valid codes exist in benefits_master without rules and would be false-positives.
            if (claim.hasItem() && claim.getUse().equals(CLAIM)) {
                List<String> unknownCodes = claim.getItem().stream()
                        .filter(item -> item.getProductOrService().hasCoding())
                        .map(item -> item.getProductOrService().getCodingFirstRep().getCode())
                        .filter(code -> code != null && !code.isBlank())
                        .filter(code -> !benefitMasterService.existsByInterventionCode(code))
                        .distinct()
                        .collect(java.util.stream.Collectors.toList());
                if (!unknownCodes.isEmpty()) {
                    errors.add("Intervention code(s) not found in benefits master: " + unknownCodes
                            + ". Please verify the codes are correctly configured in the system.");
                }
            }
        } catch (Exception e) {
            errors.add("Error validating intervention codes: " + e.getMessage());
        }
    }

    private void validateClaimSubType(Claim claim, List<String> errors) {
        try {
            if (claim.hasSubType() && claim.getSubType().hasCoding()) {
                String subTypeCode = claim.getSubType().getCoding().getFirst().getCode();
                if (!VALID_CLAIM_SUB_TYPES.contains(subTypeCode)) {
                    errors.add("Invalid Claim sub Type used: " + subTypeCode + ". Valid codes are " + VALID_CLAIM_SUB_TYPES);
                }
            }
        } catch (Exception e) {
            errors.add("Error validating claim sub type: " + e.getMessage());
        }
    }

    private void validateClaimTotal(Claim claim, List<String> errors) {
        try {
            BigDecimal calculatedTotal = claim.getItem().stream()
                    .map(item -> item.getNet().getValue().multiply(item.getQuantity().getValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal claimTotal = claim.getTotal().getValue();

            if (calculatedTotal.compareTo(claimTotal) != 0) {
                errors.add(String.format("The total net value of all claim items %s does not match the claim's total amount %s.",
                        calculatedTotal, claimTotal));
            }
        } catch (Exception e) {
            errors.add("Error validating claim total: " + e.getMessage());
        }
    }

    private void validateMaternityLookback(Claim claim, List<String> errors) {
        try {
            String patientId = extractPatientId(claim);
            for (var item : claim.getItem()) {
                String code = Optional.ofNullable(item.getProductOrService())
                        .map(CodeableConcept::getCoding)
                        .filter(c -> !c.isEmpty())
                        .map(List::getFirst)
                        .map(Coding::getCode)
                        .orElse(null);
                if (code != null && patientId != null && maternityApprovalService.isLookBackRequired(code)) {
                    if (!maternityApprovalService.isLastServiceOlderThanTenMonths(patientId, code)) {
                        errors.add(String.format("Service for intervention code: '%s', already taken by the Patient '%s' in the last 10 months.", code, patientId));
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Error validating maternity lookback: " + e.getMessage());
        }
    }

    private void validateShaCodeCombinations(Claim claim, List<String> errors) {
        try {
            claimsValidator.validateSHACodeCombinations(claim);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void validateShaCodeDuration(Claim claim, List<String> errors) {
        try {
            claimsValidator.validateSHACodeDuration(claim);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void validatePerDiemBillablePeriod(Claim claim, SimpleDateFormat sdf, List<String> errors) {
        try {
            List<Claim.ItemComponent> sortedItems = new ArrayList<>(claim.getItem());
            sortedItems.sort(Comparator.comparingInt(Claim.ItemComponent::getSequence));

            if ((!sortedItems.getFirst().hasServicedPeriod() || !sortedItems.getLast().hasServicedPeriod())
                    || sortedItems.getFirst().hasServicedDateType()) {
                errors.add("Claim items require servicedPeriod for validation.");
                return;
            }

            List<Claim.ItemComponent> losItems = new ArrayList<>();
            List<Claim.ItemComponent> nonLosItems = new ArrayList<>();

            for (Claim.ItemComponent item : sortedItems) {
                Date startDate = item.getServicedPeriod().getStart();
                Date endDate = item.getServicedPeriod().getEnd();
                if (!sdf.format(startDate).equals(sdf.format(endDate))) {
                    losItems.add(item);
                } else {
                    nonLosItems.add(item);
                }
            }

            // Validate LOS item sequencing
            losItems.sort(Comparator.comparingInt(Claim.ItemComponent::getSequence));
            Date previousEndDate = null;
            String previousCode = null;
            LocalDateTime claimStartDate = FhirHelper.convertToLocalDateTime(claim.getBillablePeriod().getStart());

            for (Claim.ItemComponent item : losItems) {
                String interventionCode = item.getProductOrService().getCoding().getFirst().getCode();
                if (interventionCode.contains("SHA-12") || interventionCode.contains("PMF-12")) {
                    continue;
                }

                Date startDate = item.getServicedPeriod().getStart();
                Date endDate = item.getServicedPeriod().getEnd();

                if (previousEndDate != null && startDate.before(previousEndDate)) {
                    List<InterventionDuration> dbDuration = interventionDurationRepository.findCodeThatSubStringOf(previousCode);
                    boolean allowed = false;
                    if (!dbDuration.isEmpty()) {
                        boolean isAllowed = dbDuration.stream()
                                .filter(d -> Objects.isNull(d.getEffectiveDate()) || d.getEffectiveDate().isBefore(claimStartDate))
                                .map(InterventionDuration::getAllowedCombination)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .filter(Objects::nonNull)
                                .anyMatch(c -> interventionCode.toLowerCase().contains(c.toLowerCase()));

                        boolean unallowed = dbDuration.stream()
                                .filter(d -> Objects.isNull(d.getEffectiveDate()) || d.getEffectiveDate().isBefore(claimStartDate))
                                .map(InterventionDuration::getUnallowedCombination)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .filter(Objects::nonNull)
                                .anyMatch(c -> interventionCode.toLowerCase().contains(c.toLowerCase()));

                        if (isAllowed && !unallowed) {
                            allowed = true;
                        }
                    }
                    if (!allowed) {
                        errors.add("LOS Intervention sequence " + item.getSequence() + " must start on or after the previous LOS sequence ended. Found start: "
                                + sdf.format(startDate) + ", previous end: " + sdf.format(previousEndDate));
                    }
                }

                previousEndDate = endDate;
                previousCode = interventionCode;
            }

            // Validate overall billable period matches serviced period
            Date billableStartDate = claim.getBillablePeriod().getStart();
            Date billableEndDate = claim.getBillablePeriod().getEnd();

            Date servicedStart = sortedItems.stream()
                    .map(item -> item.getServicedPeriod().getStart())
                    .min(Date::compareTo).orElse(null);
            Date servicedEnd = sortedItems.stream()
                    .map(item -> item.getServicedPeriod().getEnd())
                    .max(Date::compareTo).orElse(null);

            if (servicedStart != null && servicedEnd != null) {
                if (!isSameDay(billableStartDate, servicedStart) || !isSameDay(billableEndDate, servicedEnd)) {
                    errors.add("Claim billablePeriod must match servicedPeriod of LOS items. Expected start: "
                            + sdf.format(billableStartDate) + ", end: " + sdf.format(billableEndDate) + ". Found: start: "
                            + sdf.format(servicedStart) + ", end: " + sdf.format(servicedEnd));
                }
            }
        } catch (Exception e) {
            errors.add("Error validating per-diem billable period: " + e.getMessage());
        }
    }

    private void validatePreAuthForLongStay(Claim claim, SimpleDateFormat sdf, List<String> errors) {
        try {
            if (!claim.getUse().equals(CLAIM) || !claim.hasItem()) return;

            for (Claim.ItemComponent item : claim.getItem()) {
                if (!item.hasServicedPeriod()) continue;
                String code = item.getProductOrService().getCoding().getFirst().getCode();
                Date startDate = item.getServicedPeriod().getStart();
                Date endDate = item.getServicedPeriod().getEnd();
                if (sdf.format(startDate).equals(sdf.format(endDate))) continue; // non-LOS
               // Skip validation for relaxed SHA codes
                // EPORTAL-1499
                if (interventionsReqPreAuth.contains(code)) {
                    continue;
                }
                int los = FhirHelper.calculateLoS(startDate, endDate);
                boolean requiresPreAuth = interventionsReqPreAuth.stream().anyMatch(code::contains) && los > 12;

                if (requiresPreAuth) {
                    if (!claim.hasRelated()) {
                        errors.add(String.format("Require Pre Auth not found after 12 days rule. Input LOS: %d, SHA code: %s, Claim ID: %s",
                                los, code, claim.getIdPart()));
                    } else {
                        for (Claim.RelatedClaimComponent relatedClaim : claim.getRelated()) {
                            Reference claimReference = relatedClaim.getClaim();
                            String relatedClaimId = Optional.ofNullable(claimReference.getId())
                                    .filter(id -> !id.isBlank())
                                    .orElseGet(() -> claimReference.getIdentifier() != null
                                            ? claimReference.getIdentifier().getValue() : null);
                            if (relatedClaimId == null || relatedClaimId.isBlank()) {
                                errors.add("Unable to find Related Claim ID from reference.");
                                continue;
                            }
                            Optional<InsuranceClaim> preAuthOpt = claimsRepository.findByClaimIdAndUseAndStatus(
                                    UUID.fromString(relatedClaimId), Constants.Use.PREAUTHORIZATION, ExtensionStateCodeEnum.APPROVED);
                            if (preAuthOpt.isEmpty()) {
                                errors.add(String.format("Approved Pre Auth not found for related claim ID: %s (long stay rule)", relatedClaimId));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Error validating pre-auth for long stay: " + e.getMessage());
        }
    }

    private void validateRelatedPreAuth(Claim claim, Bundle bundle, List<String> errors) {
        try {
            Map<String, String> preAuthShaCodeMap = new HashMap<>();
            claimsValidator.checkRelatedPreAuth(claim, bundle, preAuthShaCodeMap);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void validatePreAuthClaims(Claim claim, Bundle bundle, List<String> errors) {
        try {
            claimsValidator.validatePreAuthClaims(claim, bundle);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    private void validatePreauthRequired(Claim claim, List<String> errors) {
        try {
            if (claim.getUse() != Claim.Use.PREAUTHORIZATION) return;
            for (Claim.ItemComponent item : claim.getItem()) {
                for (Coding coding : item.getProductOrService().getCoding()) {
                    String code = coding.getCode();
                    BenefitsMaster bm = benefitMasterService.fetchByInterventionCode(code);
                    if (!bm.isNeedsPreAuth()) {
                        errors.add("Pre-authorization is not required for intervention code " + code + " as per the benefit matrix.");
                    }
                }
            }
        } catch (Exception e) {
            errors.add("Error validating pre-auth required: " + e.getMessage());
        }
    }

    private void validateSha01Eligibility(Claim claim, Provider provider, List<String> errors) {
        try {
            List<String> codes = claimsValidator.extractInterventionCodes(claim);
            if (provider != null && claimsValidator.checkCodeExist(codes, SHA_01_INTERVENTION_CODE)) {
                if (!GOVERNMENT_FACILITIES_TYPES.isEmpty() &&
                        GOVERNMENT_FACILITIES_TYPES.stream()
                                .noneMatch(type -> type.equalsIgnoreCase(provider.getFacilityAgent().trim()))) {
                    errors.add("Provider not Allowed for submitting claim of SHA-01, since It is not a Government Facility");
                }
                if (!FACILITY_LEVEL_ALLOWED_FOR_SHA_01.isEmpty() &&
                        FACILITY_LEVEL_ALLOWED_FOR_SHA_01.stream()
                                .noneMatch(level -> provider.getLevel().toLowerCase().trim().contains(level.toLowerCase()))) {
                    errors.add("Provider not Allowed for submitting claim of SHA-01, Facility Level is not Authorized to Submit Claim");
                }
            }
        } catch (Exception e) {
            errors.add("Error validating SHA-01 eligibility: " + e.getMessage());
        }
    }

    private void validatePolicyPeriod(Claim claim, List<String> errors) {
        if (!finsprintEligibilityEnabled || !claim.getUse().equals(CLAIM)) return;
        try {
            // Fetch the saved InsuranceClaim to get the member and preauth policy period
            List<InsuranceClaim> savedClaims = claimsRepository.findByExternalId(claim.getIdPart())
                    .orElse(Collections.emptyList());
            if (savedClaims.isEmpty()) {
                log.warn("validatePolicyPeriod: InsuranceClaim not found for claimId: {}", claim.getIdPart());
                return;
            }
            InsuranceClaim iClaim = savedClaims.getFirst();

            // If policyStartDate/policyEndDate already stored (from preauth or prior Finsprint call), skip re-check
            if (iClaim.getPolicyStartDate() != null && iClaim.getPolicyEndDate() != null) {
                log.debug("Policy period already set for claim {}, skipping Finsprint re-check", claim.getIdPart());
                return;
            }

            Member member = iClaim.getPatient();
            if (member == null) {
                errors.add("Cannot validate Finsprint eligibility: member not found on claim " + claim.getIdPart());
                return;
            }

            LocalDate billableStartDate = FhirHelper.convertToLocalDate(claim.getBillablePeriod().getStart());
            LocalDate billableEndDate = FhirHelper.convertToLocalDate(claim.getBillablePeriod().getEnd());
            boolean isPhcClaim = claimBundleTransactionalService.isPhcClaim(claim);

            if (isPhcClaim) {
                claimBundleTransactionalService.validatePhcEligibilityWithFinsprint(member, claim.getIdPart(), billableStartDate, billableEndDate);
            } else {
                claimBundleTransactionalService.validateEligibilityWithFinsprint(member, claim.getIdPart(), billableStartDate, billableEndDate);
            }
        } catch (Exception e) {
            errors.add("Eligibility validation failed: " + e.getMessage());
        }
    }

    private void validateDnmRules(Claim claim, Provider provider, List<String> errors) {
        if (!validateDnmExtendedEnable || !claim.getUse().equals(CLAIM)) return;
        try {
            List<InsuranceClaim> savedClaims = claimsRepository.findByExternalId(claim.getIdPart())
                    .orElse(Collections.emptyList());
            if (savedClaims.isEmpty()) {
                log.warn("validateDnmRules: InsuranceClaim not found for claimId: {}", claim.getIdPart());
                return;
            }
            InsuranceClaim iClaim = savedClaims.getFirst();
            Member member = iClaim.getPatient();
            if (member == null) {
                log.warn("validateDnmRules: member not found on claim {}, skipping", claim.getIdPart());
                return;
            }
            String facilityLevel = provider != null ? provider.getLevel() : null;
            LocalDateTime policyStartDate = iClaim.getPolicyStartDate();
            LocalDateTime policyEndDate = iClaim.getPolicyEndDate();
            interventionValidationService.fetchRulesAndValidate(claim, member, facilityLevel, policyStartDate, policyEndDate, iClaim.getClaimId());
        } catch (Exception e) {
            errors.add("Rule validation failed: " + e.getMessage());
        }
    }

    private void validateInterventionCodesForPatientType(Claim claim, List<String> errors) {
        try {
            claimsValidator.validateInterventionCodesForPatientType(claim);
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
    }

    // --- Helper Methods ---

    private String extractPatientId(Claim claim) {
        Reference patientReference = claim.getPatient();
        if (patientReference != null && patientReference.hasReference()) {
            String reference = patientReference.getReference();
            return reference.substring(reference.lastIndexOf("/") + 1);
        }
        return null;
    }

    private boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
