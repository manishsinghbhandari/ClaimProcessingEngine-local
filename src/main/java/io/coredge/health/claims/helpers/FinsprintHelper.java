package io.coredge.health.claims.helpers;

import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.dto.eligibility.CoveragePeriodDto;
import io.coredge.health.claims.dto.eligibility.FinsprintEligibilityResponse;
import io.coredge.health.claims.dto.eligibility.FinsprintEligibilityResponse.Scheme;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.service.FinsprintEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static io.coredge.health.claims.exception.models.ErrorCode.BILLABLE_AFTER_POLICY_END;
import static io.coredge.health.claims.exception.models.ErrorCode.BILLABLE_BEFORE_POLICY_START;

/**
 * Helper class for Finsprint eligibility integration (API v5)
 * Supports multiple schemes with priority-based selection
 */
@Slf4j
@Service
public class FinsprintHelper {

    private final FinsprintEligibilityService finsprintService;

    // Configurable scheme priority for non-PHC claims (comma-separated, in order of priority)
    @Value("${finsprint.scheme.priority.non-phc:TSC,POMSF,SHIF}")
    private String nonPhcSchemePriority;

    // Configurable scheme for PHC claims
    @Value("${finsprint.scheme.priority.phc:UHC}")
    private String phcSchemePrefix;

    private static final String ACTIVE_COVERAGE_STATUS = "1";
    private static final String PRIMARY_MEMBER_TYPE = "PRIMARY";

    public FinsprintHelper(FinsprintEligibilityService finsprintService) {
        this.finsprintService = finsprintService;
    }

    /**
     * Get the ordered list of scheme prefixes for non-PHC claims
     */
    private List<String> getNonPhcSchemePrefixes() {
        return Arrays.stream(nonPhcSchemePriority.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public FinsprintEligibilityResponse checkEligibilityByNationalId(String nationalId) {
        return finsprintService.checkEligibility("2", nationalId);
    }

    public FinsprintEligibilityResponse checkEligibilityByCrNumber(String crNumber) {
        return finsprintService.checkEligibility("3", crNumber);
    }

    public FinsprintEligibilityResponse checkEligibilityByBirthCertificate(String birthCertNumber) {
        return finsprintService.checkEligibility("10", birthCertNumber);
    }

    public FinsprintEligibilityResponse checkEligibilityByHHNumber(String hhNumber) {
        return finsprintService.checkEligibility("11", hhNumber);
    }

    /**
     * Check eligibility for non-PHC claims using Member entity
     * Scheme priority: TSC > POMSF > SHIF (excludes UHC)
     * Priority: CR > Household Number > National ID > Birth Certificate
     * Implements 3-attempt retry mechanism with 1-second delay for transient failures
     */

    /**
     * Check eligibility using Member entity - returns only coverage period
     * @param member The member to check eligibility for
     * @param isPhcClaim If true, uses UHC scheme; otherwise uses TSC > POMSF > SHIF priority
     */
    @Retryable(
            retryFor = {
                    HttpServerErrorException.InternalServerError.class,
                    HttpServerErrorException.BadGateway.class,
                    HttpServerErrorException.ServiceUnavailable.class,
                    HttpServerErrorException.GatewayTimeout.class,
                    ResourceAccessException.class
            },
            maxAttemptsExpression = "${retry.finSprint.maxAttempts:3}",
            backoff = @Backoff(
                    delayExpression = "${retry.finSprint.delay:2000}",
                    multiplierExpression = "${retry.finSprint.multiplier:2}"
            )
    )
    public CoveragePeriodDto checkEligibilityFromMember(Member member, boolean isPhcClaim, LocalDate billableStartDate, LocalDate billableEndDate) {
        if (member == null) {
            throw new DataValidationException("Member is null for eligibility check");
        }

        log.info("Finsprint eligibility check attempt for member: {}, isPhcClaim: {}",
                member.getCrId(), isPhcClaim);

        return performEligibilityCheck(member, isPhcClaim, billableStartDate, billableEndDate);

    }

    /**
     * Performs the actual eligibility check without retry logic
     */
    private CoveragePeriodDto performEligibilityCheck(Member member, boolean isPhcClaim, LocalDate billableStartDate, LocalDate billableEndDate) {
        FinsprintEligibilityResponse response = null;

        // Priority 1: CR Number
        if (member.getCrId() != null && !member.getCrId().isEmpty()) {
            try {
                log.info("Checking eligibility using CR Number: {}", member.getCrId());
                response = checkEligibilityByCrNumber(member.getCrId());
                CoveragePeriodDto result = extractCoveragePeriod(response, isPhcClaim, "CR Number", member.getCrId(), billableStartDate, billableEndDate);
                if (result != null) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                log.warn("CR Number lookup failed for {}: {}", member.getCrId(), e.getMessage());
            }
        }

        // Priority 2: Household Number
        if (member.getHouseholdNumber() != null && !member.getHouseholdNumber().isEmpty()) {
            try {
                log.info("Checking eligibility using Household Number: {}", member.getHouseholdNumber());
                response = checkEligibilityByHHNumber(member.getHouseholdNumber());
                CoveragePeriodDto result = extractCoveragePeriod(response, isPhcClaim, "Household Number", member.getHouseholdNumber(), billableStartDate, billableEndDate);
                if (result != null) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Household Number lookup failed for {}: {}", member.getHouseholdNumber(), e.getMessage());
            }
        }

        // Priority 3: National ID
        if(member.getNationalId() != null && !member.getNationalId().isEmpty()) {
            try {
                log.info("Checking eligibility using National ID: {}", member.getNationalId());
                response = checkEligibilityByNationalId(member.getNationalId());
                CoveragePeriodDto result = extractCoveragePeriod(response, isPhcClaim, "National ID", member.getNationalId(), billableStartDate, billableEndDate);
                if (result != null) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                log.warn("National ID lookup failed for {}: {}", member.getNationalId(), e.getMessage());
            }
        }

        // Priority 4: Birth Certificate
        if (member.getBirthCertificate() != null && !member.getBirthCertificate().isEmpty()) {
            try {
                log.info("Checking eligibility using Birth Certificate: {}", member.getBirthCertificate());
                response = checkEligibilityByBirthCertificate(member.getBirthCertificate());
                CoveragePeriodDto result = extractCoveragePeriod(response, isPhcClaim, "Birth Certificate", member.getBirthCertificate(), billableStartDate, billableEndDate);
                if (result != null) {
                    return result;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Birth Certificate lookup failed for {}: {}", member.getBirthCertificate(), e.getMessage());
            }
        }

        String lastErrorMessage = buildSchemeNotFoundMessage(response, isPhcClaim);

        String errorMessage = lastErrorMessage != null ? lastErrorMessage :
                "BMS Service: No valid identifier found for member " + member.getCrId();
        throw new DataValidationException(errorMessage);
    }

    /**
     * Extracts coverage period from V5 response by selecting appropriate scheme
     */
    private CoveragePeriodDto extractCoveragePeriod(FinsprintEligibilityResponse response, boolean isPhcClaim,
                                                     String idType, String idValue, LocalDate billableStartDate, LocalDate billableEndDate) {
        if (response == null || response.getSchemes() == null || response.getSchemes().isEmpty()) {
            return null;
        }

        Scheme selectedScheme = isPhcClaim ?
                selectPhcScheme(response.getSchemes()) :
                selectNonPhcScheme(response.getSchemes());

        if (selectedScheme == null) {
            log.warn("No suitable {} scheme found for {} : {}",
                    isPhcClaim ? "PHC ("+phcSchemePrefix+") "  : "non-PHC ("+nonPhcSchemePriority+")", idType, idValue);
            return null;
        }

        return buildCoveragePeriodDto(selectedScheme, idType, idValue, billableStartDate, billableEndDate);
    }

    /**
     * Select scheme for non-PHC claims
     * Priority is configurable via finsprint.scheme.priority.non-phc property
     * Default: TSC > POMSF > SHIF
     * Filters: memberType = PRIMARY, coverage.status = "1", excludes PHC scheme
     * Uses prefix matching to handle variants like "POMSF-047", "TSC-001"
     */
    private Scheme selectNonPhcScheme(List<Scheme> schemes) {
        List<Scheme> validSchemes = schemes.stream()
               // .filter(s -> PRIMARY_MEMBER_TYPE.equals(s.getMemberType()))
                .filter(s -> s.getCoverage() != null && ACTIVE_COVERAGE_STATUS.equals(s.getCoverage().getStatus()))
                .filter(s -> s.getSchemeName() != null && !s.getSchemeName().startsWith(phcSchemePrefix))
                .toList();

        // Iterate through configured priority order
        for (String schemePrefix : getNonPhcSchemePrefixes()) {
            Scheme matchedScheme = validSchemes.stream()
                    .filter(s -> s.getSchemeName().startsWith(schemePrefix))
                    .findFirst()
                    .orElse(null);
            if (matchedScheme != null) {
                log.debug("Selected scheme {} based on priority prefix {}", matchedScheme.getSchemeName(), schemePrefix);
                return matchedScheme;
            }
        }
        return null;
    }

    /**
     * Select scheme for PHC claims
     * Scheme is configurable via finsprint.scheme.priority.phc property
     * Default: UHC
     * Filters: memberType = PRIMARY, coverage.status = "1"
     * Uses prefix matching to handle variants like "UHC-001"
     */
    private Scheme selectPhcScheme(List<Scheme> schemes) {
        return schemes.stream()
                //.filter(s -> PRIMARY_MEMBER_TYPE.equals(s.getMemberType()))
                .filter(s -> s.getCoverage() != null && ACTIVE_COVERAGE_STATUS.equals(s.getCoverage().getStatus()))
                .filter(s -> s.getSchemeName() != null && s.getSchemeName().startsWith(phcSchemePrefix))
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds CoveragePeriodDto from selected scheme with validation
     */
    private CoveragePeriodDto buildCoveragePeriodDto(Scheme scheme, String idType, String idValue, LocalDate billableStartDate, LocalDate billableEndDate) {
        // Validate coverage dates
        if (scheme.getCoverage() == null ||
                scheme.getCoverage().getStartDate() == null || scheme.getCoverage().getStartDate().isEmpty() ||
                scheme.getCoverage().getEndDate() == null || scheme.getCoverage().getEndDate().isEmpty()) {
            throw new BadRequestException("Missing coverage start/end date for Patient " + idType + " : " + idValue +
                    " in scheme " + scheme.getSchemeName());
        }

        // Validate policy dates
        if (scheme.getPolicy() == null ||
                scheme.getPolicy().getStartDate() == null || scheme.getPolicy().getStartDate().isEmpty() ||
                scheme.getPolicy().getEndDate() == null || scheme.getPolicy().getEndDate().isEmpty()) {
            throw new BadRequestException("Missing policy start/end date for Patient " + idType + " : " + idValue +
                    " in scheme " + scheme.getSchemeName());
        }


        LocalDate coverageStartDate = LocalDate.parse(scheme.getCoverage().getStartDate());
        LocalDate coverageEndDate = LocalDate.parse(scheme.getCoverage().getEndDate());
        LocalDate policyStartDate = LocalDate.parse(scheme.getPolicy().getStartDate());
        LocalDate policyEndDate = LocalDate.parse(scheme.getPolicy().getEndDate());
        LocalDate today = LocalDate.now();

        // Validate coverage period
        if (coverageStartDate.isAfter(today)) {
            throw new BadRequestException("Coverage period starts in the future for " + idType + " : " + idValue +
                    " Start: " + coverageStartDate + " in scheme " + scheme.getSchemeName());
        }
        if (coverageEndDate.isBefore(today)) {
            throw new BadRequestException("Coverage period has expired for " + idType + " : " + idValue +
                    " End: " + coverageEndDate + " in scheme " + scheme.getSchemeName());
        }

        // Validate policy period
        if (policyStartDate.isAfter(today)) {
            throw new BadRequestException("Policy period starts in the future for " + idType + " : " + idValue +
                    " Start: " + policyStartDate + " in scheme " + scheme.getSchemeName());
        }
        if (policyEndDate.isBefore(today)) {
            throw new BadRequestException("Policy period has expired for " + idType + " : " + idValue +
                    " End: " + policyEndDate + " in scheme " + scheme.getSchemeName());
        }

        //EPORTAL-1216
        if (billableStartDate.isBefore(policyStartDate)) {
            throw new BusinessException(BILLABLE_BEFORE_POLICY_START, "BMS Service check: Billable period cannot start before policy start date: " + policyStartDate);
        }

        if (billableEndDate.isAfter(policyEndDate)) {
            throw new BusinessException(BILLABLE_AFTER_POLICY_END, "BMS Service check: Billable period cannot exceed policy end date: " + policyEndDate);
        }

        // Extract employer details from principalContributor
        String employer = "";
        String jobGroup = "";
        if (scheme.getPrincipalContributor() != null && scheme.getPrincipalContributor().getEmployerDetails() != null) {
            employer = scheme.getPrincipalContributor().getEmployerDetails().getName() != null ?
                    scheme.getPrincipalContributor().getEmployerDetails().getName() : "";
            jobGroup = scheme.getPrincipalContributor().getEmployerDetails().getJobGroup() != null ?
                    scheme.getPrincipalContributor().getEmployerDetails().getJobGroup() : "";
        }

        log.info("Selected scheme {} for {} : {} - Coverage: {} to {}, Policy: {} to {}",
                scheme.getSchemeName(), idType, idValue, coverageStartDate, coverageEndDate,
                policyStartDate, policyEndDate);

        return new CoveragePeriodDto(
                policyStartDate,
                policyEndDate,
                coverageStartDate,
                coverageEndDate,
                scheme.getSchemeName(),
                employer,
                jobGroup
        );
    }

    /**
     * Builds error message when no suitable scheme is found
     */
    private String buildSchemeNotFoundMessage(FinsprintEligibilityResponse response, boolean isPhcClaim) {
        if (response == null) {
            return "BMS Service: No response received";
        }

        StringBuilder sb = new StringBuilder("BMS Service check: patient ")
                .append(response.getMemberCrNumber() != null ? response.getMemberCrNumber() : "Unknown")
                .append(".");

        if (response.getSchemes() != null && !response.getSchemes().isEmpty()) {
            String activeSchemes = response.getSchemes().stream()
                    .filter(s -> s.getCoverage() != null && "1".equals(s.getCoverage().getStatus()))
                    .map(Scheme::getSchemeName)
                    .collect(java.util.stream.Collectors.joining(", "));
            if (!activeSchemes.isEmpty()) {
                sb.append(" Active schemes: ").append(activeSchemes).append(".");
            }
        }

        if (isPhcClaim) {
            sb.append(" No active " + phcSchemePrefix + " scheme found for PHC claim.");
        } else {
            sb.append(" No active " + nonPhcSchemePriority + " scheme found.");
        }

        return sb.toString();
    }
}
