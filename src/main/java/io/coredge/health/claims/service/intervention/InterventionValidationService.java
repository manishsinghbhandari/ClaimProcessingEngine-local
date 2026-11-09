package io.coredge.health.claims.service.intervention;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.Member;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.enums.intervention.RuleType;
import io.coredge.health.claims.exception.intervention.RuleParsingException;
import io.coredge.health.claims.exception.intervention.RuleViolationException;
import io.coredge.health.claims.mapper.intervention.RuleMapping;
import io.coredge.health.claims.models.intervention.dto.ClaimUsage;
import io.coredge.health.claims.models.intervention.dto.InterventionRules;
import io.coredge.health.claims.utils.FhirHelper;
import io.coredge.health.claims.utils.PreAuthLimits;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.intervention.InterventionConstants.*;
import static io.coredge.health.claims.enums.intervention.RuleType.SURGICAL;
import static io.coredge.health.claims.exception.models.ErrorCode.BUSINESS_RULE_EXCEPTION;

@Slf4j
@Service
public class InterventionValidationService {


    @Value("${renal.care.enable.flag:true}")
    private boolean renalCareActive;

    private final ObjectMapper objectMapper;

    public Map<String, InterventionRules> rulesCache;

    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final BenefitsMasterRepository benefitsMasterRepository;
    private final InterventionValidatorHelper interventionValidatorHelper;
    private final PreAuthLimits preAuthLimits;

    public InterventionValidationService(ObjectMapper objectMapper, InsuranceClaimsRepository insuranceClaimsRepository,
                                         BenefitsMasterRepository benefitsMasterRepository, InterventionValidatorHelper interventionValidatorHelper,
                                         PreAuthLimits preAuthLimits) {
        this.objectMapper = objectMapper;
        this.insuranceClaimsRepository = insuranceClaimsRepository;
        this.benefitsMasterRepository = benefitsMasterRepository;
        this.interventionValidatorHelper = interventionValidatorHelper;
        this.preAuthLimits = preAuthLimits;
    }


    @PostConstruct
    public void loadRules() {
        rulesCache = benefitsMasterRepository.findAll().stream()
                .filter(usage -> usage.getGroupName() != null && !usage.getGroupName().isBlank())
                .collect(Collectors.toMap(
                        BenefitsMaster::getInterventionCode,
                        this::parseRulesSafely
                ));
        log.debug("Loaded {} intervention rules into cache.", rulesCache.size());
    }

    public void fetchRulesAndValidate(Claim claim, Member member, String level, LocalDateTime policyStartDate, LocalDateTime policyEndDate) {
        fetchRulesAndValidate(claim, member, level, policyStartDate, policyEndDate, null);
    }

    public void fetchRulesAndValidate(Claim claim, Member member, String level, LocalDateTime policyStartDate, LocalDateTime policyEndDate, UUID excludeClaimId) {
        String householdNumber = member.getHouseholdNumber();
        String crId = member.getCrId();
        // Extract codes from claim
        List<String> allCodes = interventionValidatorHelper.extractAllInterventionCodes(claim);
        // Only consider those that exist in rulesCache (which already filtered groupName)
        List<String> applicableCodes = allCodes.stream()
                .filter(rulesCache::containsKey)
                .toList();
        if (applicableCodes.isEmpty()) {
            log.info(NO_RULES_APPLIED_MSG);
            return;
        }
        boolean pmfCoverage = claim.getInsurance().stream().anyMatch(
                insurance -> insurance.getCoverage().getReference().contains("pmf"));

        Date birthDate = member.getBirthDate();
        log.debug("Applicable intervention codes with rules: {}", applicableCodes);
        for (String shaCode : applicableCodes) {
            // Try to find the serviced Period (start and end) for the given SHA code
            var servicedPeriodOpt = claim.getItem().stream()
                    .filter(item -> {
                        var coding = item.getProductOrService().getCodingFirstRep();
                        return coding != null && shaCode.equals(coding.getCode());
                    })
                    .map(Claim.ItemComponent::getServiced)
                    .filter(Period.class::isInstance)
                    .map(Period.class::cast)
                    .findFirst();

            Date startPeriod = null;
            Date endPeriod = null;

            if (servicedPeriodOpt.isPresent()) {
                var servicedPeriod = servicedPeriodOpt.get();
                startPeriod = servicedPeriod.getStart();
                endPeriod = servicedPeriod.getEnd();
            } else {
                log.info("No serviced Period found in claimId: {} for code: {}", claim.getIdPart(), shaCode);
                throw new IllegalArgumentException("No serviced Period found in claimId: " + claim.getIdPart() + " for code: " + shaCode);
            }
            // Convert start date to LocalDate (if present) for serviceDate
            LocalDate startServiceDate = FhirHelper.convertToLocalDate(startPeriod);
            LocalDate endServiceDate = FhirHelper.convertToLocalDate(endPeriod);
//            LocalDate startServiceDate = startPeriod.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
//            LocalDate endServiceDate = startPeriod.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

            var usage = buildClaimUsage(claim, shaCode, birthDate, startPeriod, endPeriod);
            validateInterventionRules(householdNumber, startServiceDate, endServiceDate, usage, level, crId, pmfCoverage, policyStartDate, policyEndDate, excludeClaimId);
        }
    }

    public void validateInterventionRules(String householdNumber, LocalDate startServiceDate, LocalDate endServiceDate, ClaimUsage usage,
                                          String facilityLevel, String crId, boolean pmfCoverage, LocalDateTime policyStartDate, LocalDateTime policyEndDate) {
        validateInterventionRules(householdNumber, startServiceDate, endServiceDate, usage, facilityLevel, crId, pmfCoverage, policyStartDate, policyEndDate, null);
    }

    public void validateInterventionRules(String householdNumber, LocalDate startServiceDate, LocalDate endServiceDate, ClaimUsage usage,
                                          String facilityLevel, String crId, boolean pmfCoverage, LocalDateTime policyStartDate, LocalDateTime policyEndDate, UUID excludeClaimId) {
        var ruleSet = rulesCache.get(usage.getInterventionCode());
        if (ruleSet == null) {
            log.info(NO_RULES_APPLIED_MSG);
            return ;
        }
        var rules = ruleSet.getRules();

        LocalDate policyStartLocalDate = policyStartDate != null ? policyStartDate.toLocalDate() : null;
        LocalDate policyEndLocalDate = policyEndDate != null ? policyEndDate.toLocalDate() : null;

        // Validate first for the same service date cannot place again for same claim.
      Optional<String> status = validateDuplicateClaimOrPreAuth(crId, usage.getInterventionCode(), startServiceDate, usage.getUse(), excludeClaimId);
      log.debug("Status of validateDuplicateClaimOrPreAuth {}", status.isPresent() ? status.get(): Optional.empty());

        // Map all rule keys to enum types
        Set<RuleType> ruleTypes = rules.keySet().stream()
                .map(RuleMapping::mapToRuleType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        log.info("Resolved RuleTypes for interventionCode {}: {}", usage.getInterventionCode(), ruleTypes);

        // Use switch expression for clean dispatch
        for (RuleType ruleType : ruleTypes) {
            switch (ruleType) {
                // 1. SHA-19 Rule – Surgical cases
                case SURGICAL -> {
                        if (checkNullOrEmptyHouseholdNumber(householdNumber)) {
                            log.info("Executing validation: SURGICAL for interventionCode={}, household={}, startServiceDate={}, endServiceDate={}", usage.getInterventionCode(), householdNumber, startServiceDate, endServiceDate);
                            validateHouseholdLimits(householdNumber, usage.getInterventionCode(), startServiceDate, rules, facilityLevel, usage.getUse());
                        } else {
                            logInfo(SURGICAL.toString());
                        }
                }
                // 2. SHA-16-001 & SHA-16-002: Renal Care (Hemodialysis / Haemodiafiltration)
                case RENAL_CARE -> {
                    log.debug("Renal care validation:{}", renalCareActive);
                    if (renalCareActive) {
                        log.info("Executing validation: RENAL_CARE for interventionCode={}, CR_ID={}, startServiceDate={}, endServiceDate={}",
                                usage.getInterventionCode(), crId, startServiceDate, endServiceDate);
                        if (!pmfCoverage) {
                            boolean isShifPatient = true;
                            if (PRE_AUTHORIZATION.equalsIgnoreCase(usage.getUse())) {
                                validateRenalCarePreAuth(crId, usage.getInterventionCode(), startServiceDate, usage.getUse(), isShifPatient , policyStartLocalDate , policyEndLocalDate);
                            } else {
                                validateRenalCare(crId, usage.getInterventionCode(), startServiceDate, usage.getUse(), isShifPatient , policyStartLocalDate , policyEndLocalDate);
                            }
                        }
                    }
                }
                // 3. SHA-07-001 to SHA-07-004, SHA-10-001 to SHA-10-004, SHA-13: Inpatient Per Diem
                case PER_DIEM -> {
                    log.info("Executing validation: PER_DIEM for household={}, serviceDate={}, interventionCode={}", householdNumber, startServiceDate, usage.getInterventionCode());
                    if (checkNullOrEmptyHouseholdNumber(householdNumber)) {
                        validatePerDiem(householdNumber, usage, policyStartLocalDate, policyEndLocalDate);
                    } else {
                        logInfo(PER_DIEM);
                    }
                }
                // Moved this in camunda EPORTAL-1537
                // 4. Inpatient overnight validation
/*
               case INPATIENT -> {
                    log.info("Executing validation: INPATIENT for interventionCode={}", usage.getInterventionCode());
                    validateOvernightStay(usage.getInterventionCode(), usage);
                }
*/
                // 5. SHA-16-004: Peritoneal Dialysis
                case PERITONEAL_DIALYSIS -> {
                    if (checkNullOrEmptyHouseholdNumber(householdNumber)) {
                        log.info("Executing validation: PERITONEAL_DIALYSIS for interventionCode={}, household={}, serviceDate={}",
                                usage.getInterventionCode(), householdNumber, startServiceDate);
                        validatePeritonealDialysis(usage.getInterventionCode(), householdNumber, startServiceDate, usage.getUse() , policyStartLocalDate , policyEndLocalDate);
                    } else {
                        logInfo(PERITONEAL_DIALYSIS);
                    }
                }
                // 6. SHA-16: Acute Dialysis
                case ACUTE_DIALYSIS -> {
                    if (checkNullOrEmptyHouseholdNumber(householdNumber)) {
                        log.info("Executing validation: ACUTE_DIALYSIS for interventionCode={}, household={}", usage.getInterventionCode(), householdNumber);
                        validateAcuteDialysis(usage, householdNumber);
                    } else {
                        logInfo(ACUTE_DIALYSIS);
                    }
                }
                // 7. SHA-10-005: Rehab
                case REHAB -> {
                    log.info("Executing validation: REHAB for interventionCode={}", usage.getInterventionCode());
                    validateAndCalculateRehabBilling(usage, pmfCoverage);
                }
                //SHA-05-001: Eye Care
                case EYE_CARE -> {
                    if (checkNullOrEmptyHouseholdNumber(householdNumber)) {
                        log.info("Executing validation: EYE_CARE for interventionCode={}, household={}, serviceDate={}",
                                usage.getInterventionCode(), householdNumber, startServiceDate);
                        eyeCareValidate(householdNumber, startServiceDate, rules, usage);
                    } else {
                        logInfo(EYE_CARE);
                    }
                }
                default -> log.warn("No matching validation type for interventionCode={}", usage.getInterventionCode());
            }
        }
    }

    private void eyeCareValidate(String householdNumber, LocalDate serviceDate, Map<String, InterventionRules.Rule> rules, ClaimUsage usage) {
        if (rules.containsKey(EYE_CARE)) {
            validateEyeCareUsage(householdNumber, usage, serviceDate);
        }
    }

    private void validateHouseholdLimits(String householdNumber, String interventionCode, LocalDate serviceDate, Map<String, InterventionRules.Rule> minorRule,
                                         String facilityLevel, String use) {
        // Determine the year boundary for service period: from serviceDate to serviceDate.plusYears(1)
        LocalDate startDate = serviceDate.minusYears(ONE_YEAR);
        log.debug("Service start date: {}, Household : {}", startDate, householdNumber);
        log.debug("Service end date: {}, Household : {}", serviceDate, householdNumber);

        int totalUsed = 0;

        if (StringUtils.isNotBlank(facilityLevel) && minorRule.containsKey(MINOR)) {
            // Normalize to lowercase for comparison
            String normalizedLevel = facilityLevel.toLowerCase(Locale.ROOT);

            boolean isLevel3 = minorRule.values().stream()
                    .flatMap(rule -> rule.getFacilityLevels().stream())
                    .anyMatch(level -> level.equals(normalizedLevel));
            if (isLevel3) {
                benefitsMasterRepository.findByInterventionCode(interventionCode).ifPresent(benefits -> {
                    if (!benefits.isEmpty()) {
                        BenefitsMaster benefit = benefits.getFirst();
                        // Only trigger the validation if the surgery is explicitly "Minor"
                        if ("Minor".equalsIgnoreCase(benefit.getComplexity())) {
                            log.info("Intervention {} is a Minor Surgery. Applying Level 3 household limits.", interventionCode);
                            validateMinorSurgeryLevel3(householdNumber, use);
                        } else {
                            log.info("Intervention {} is not a Minor Surgery (Complexity: {}). Skipping Level 3 household limit validation.", interventionCode, benefit.getComplexity());
                        }
                    }
                });
            }
        }

        // Always check usage limit, regardless of facility level check
        checkSurgicalUsageLimit(interventionCode, totalUsed);
    }

    public void checkSurgicalUsageLimit(String interventionCode, int totalUsed) {
        // Get the rule set for the intervention code from the cache
        InterventionRules ruleSet = rulesCache.get(interventionCode);

        // Retrieve the surgical rule for the given intervention code
        Optional<InterventionRules.Rule> surgicalRule = findSurgicalRule(ruleSet);

        // If no surgical rule exists, no validation is applied
        if (surgicalRule.isEmpty()) {
            return; // No validation required, exit early
        }

        // Get the max usage per period from the rule
        int maxPerPeriod = surgicalRule.get().getMaxPerYear();

        // Check if the total used exceeds the limit for the service period
        if (totalUsed >= maxPerPeriod) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                    String.format(
                            "%s usage denied. Only %d usage%s allowed per year, but this is the %s attempt.",
                            interventionCode,
                            maxPerPeriod,
                            maxPerPeriod > 1 ? "s" : "",
                            toOrdinal(totalUsed + 1)));

        }
    }


    public boolean validateRenalCarePreAuth(String crId, String interventionCode, LocalDate serviceDate, String use, boolean isShifPatient, LocalDate policyStartDate, LocalDate policyEndDate) {

        ClaimUsage usage = calculateWeeklyUsage(crId, interventionCode, serviceDate, policyStartDate, policyEndDate, use, PRE_AUTH_APPROVED_STATUS);
        if (usage.getLastUsageDate() != null) {
            log.info("Claim item serviced date for pre-auth: {}", usage.getLastUsageDate());
            return validateWeeklyAndYearlyUsage(usage, usage.getLastUsageDate(), isShifPatient);
        }
        return false;
    }

    public ClaimUsage calculateWeeklyUsage(String crId, String interventionCode,LocalDate serviceDate, LocalDate policyStartDate , LocalDate policyEndDate, String use, Set<String> status){

        LocalDate weekStart = serviceDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(DAYS_PER_WEEK_INCLUSIVE); // Sunday

        log.info("Params to countUsedInterventionsPerCustomWeek validateRenalCarePreAuth -> crId={}, interventionCode={}, windowStart={}, windowEnd={}, use={}, statuses={}",
                crId, interventionCode, weekStart, weekEnd, use, status);
        return ClaimUsage.builder()
                .interventionCode(interventionCode)
                .totalUsedThisYear(insuranceClaimsRepository.countUsedInterventionsPerCustomYear(
                        crId, interventionCode, policyStartDate, policyEndDate,
                        new ArrayList<>(status), use))
                .totalUsedThisWeek(insuranceClaimsRepository.countUsedInRollingWeeklyWindow(
                        crId, interventionCode, weekStart, weekEnd,
                        new ArrayList<>(status), use))
                .lastUsageDate(getLastUsageDate(crId, interventionCode, new ArrayList<>(status), use))
                .build();
    }

    public boolean validateRenalCare(String crId, String interventionCode, LocalDate serviceDate, String use, boolean isShifPatient, LocalDate policyStartDate, LocalDate policyEndDate) {
        ClaimUsage usage = calculateWeeklyUsage(crId, interventionCode, serviceDate, policyStartDate, policyEndDate, use, APPROVED_STATUS);
        if (usage.getLastUsageDate() != null) {
            log.info("Claim item serviced date for claim: {}", usage.getLastUsageDate());
            return validateWeeklyAndYearlyUsage(usage, usage.getLastUsageDate(), isShifPatient);
        }
        return false;
    }

    private boolean validateWeeklyAndYearlyUsage(ClaimUsage claimUsage, LocalDate localDate, boolean isShifPatient) {
        var ruleSet = rulesCache.get(claimUsage.getInterventionCode());

        if (ruleSet == null || ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
            return false;
        }

        var renalCareRule = ruleSet.getRules().get(RENAL_CARE);

        if (renalCareRule == null || !INDIVIDUAL.equalsIgnoreCase(renalCareRule.getScope())) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "This rule applicable only for individual");
        }

        boolean exceededYear = renalCareRule.getMaxPerYear() != null &&
                claimUsage.getTotalUsedThisYear() >= renalCareRule.getMaxPerYear();

        boolean exceededWeek = renalCareRule.getMaxPerWeek() != null &&
                claimUsage.getTotalUsedThisWeek() >= renalCareRule.getMaxPerWeek();

        if (exceededYear || exceededWeek) {
            if (isShifPatient) {
                String message = buildErrorMessage(claimUsage, renalCareRule, exceededYear, exceededWeek, localDate);
                log.warn("Intervention [{}] rule violation: {}", claimUsage.getInterventionCode(), message);
                throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, message);
            } else {
                return true;
            }
        }
        return false;
    }

    private String buildErrorMessage(ClaimUsage usage, InterventionRules.Rule renalCareRule,
                                     boolean exceededYear, boolean exceededWeek, LocalDate latestServiceDate) {
        if (exceededYear && exceededWeek) {
            return String.format(
                    "You have reached both the yearly and weekly limits. " +
                            "Used this year: %d out of %d allowed, and used this week: %d out of %d allowed. ",
                    usage.getTotalUsedThisYear(), renalCareRule.getMaxPerYear(),
                    usage.getTotalUsedThisWeek(), renalCareRule.getMaxPerWeek()
            );
        } else if (exceededYear) {
            return String.format(
                    "You have already used %d out of %d allowed services this year. " +
                            "Next service will be available after one year from the earliest usage.",
                    usage.getTotalUsedThisYear(), renalCareRule.getMaxPerYear()
            );
        } else {
            // Align to Monday–Sunday window
            LocalDate weekStart = latestServiceDate.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(DAYS_PER_WEEK_INCLUSIVE);
            LocalDate nextWindowStart = weekEnd.plusDays(ONE_DAY);
            LocalDate today = LocalDate.now();

            // Business correction: prevent stale windows
            if (nextWindowStart.isBefore(today)) {
                nextWindowStart = today;
            }

            return String.format(
                    "You have already taken %d services during the current window (%s to %s). " +
                            "Your next available service window starts on %s.",
                    usage.getTotalUsedThisWeek(),
                    weekStart,
                    weekEnd,
                    nextWindowStart
            );
        }
    }

    public Integer calculateUtilizedPerDiem(String householdNumber, LocalDate policyStartDate, LocalDate policyEndDate){
        List<String> perDiemCodes = benefitsMasterRepository.findPerDiemClubInterventionCodes();

        return insuranceClaimsRepository.sumPerDiemDaysUsedInYear(
                householdNumber, perDiemCodes, policyStartDate, policyEndDate, Constants.Use.CLAIM, new ArrayList<>(APPROVED_STATUS)
        );
    }

    private void validatePerDiem(String householdNumber, ClaimUsage usage , LocalDate policyStartDate, LocalDate policyEndDate) {

        // Rolling window: 1 year before admission → until discharge
        LocalDate startDate = usage.getAdmissionDate().minusDays(YEAR_DAYS);
        LocalDate endDate  = usage.getDischargeDate();
        log.info("Checking Per Diem from {} to {}, Household : {}", startDate, endDate, householdNumber);

        int alreadyUsedDays = calculateUtilizedPerDiem(householdNumber , policyStartDate , policyEndDate);
        // current claim's usage (LOS)
        int currentUsageDays = calculateUsageDays(usage); // last day not included
        // convert here safely

        log.info("Per Diem used days from past claims: {}, Current usage: {}, Household : {}", alreadyUsedDays, currentUsageDays, householdNumber);

        usage.setTotalUsedThisYear(alreadyUsedDays + currentUsageDays);

        if (isPerDiemViolated(usage)) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                    "Per Diem limit exceeded for intervention '" + usage.getInterventionCode() + "'. " +
                            "Requested: " + usage.getTotalUsedThisYear() + " days, Limit: " +
                            rulesCache.get(usage.getInterventionCode()).getRules().get(PER_DIEM).getMaxPerYear());
        }
    }


    private void validateOvernightStay(String shaCode, ClaimUsage usage) {
        if (shaCode.equalsIgnoreCase(usage.getInterventionCode()) && !isOvernightStayValid(usage)) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                    "Overnight stay violation for intervention " + usage.getInterventionCode() + ". The admission and discharge dates do not meet the required criteria.");
        }
    }

    private void validatePeritonealDialysis(String shaCode, String householdNumber, LocalDate currentDate, String use, LocalDate policyStartDate, LocalDate policyEndDate) {
        ClaimUsage usage = buildPeritonealClaimUsage(shaCode, householdNumber, currentDate, use, policyStartDate, policyEndDate);
        peritonealDialysisViolated(usage);
    }

    private void validateAcuteDialysis(ClaimUsage usage, String householdNumber) {
        int usageCount = insuranceClaimsRepository.countAcuteDialysisUsageDuringAdmission(
                householdNumber,
                usage.getInterventionCode(),
                usage.getAdmissionDate(),
                usage.getDischargeDate(),
                new ArrayList<>(APPROVED_STATUS), usage.getUse());
        usage.setTotalUsedThisAdmission(usageCount);
        log.debug("Usages count for AcuteDialysis: {}", usageCount);
        validateAcuteDialysisUsage(usage);
    }


    private ClaimUsage buildClaimUsage(Claim claim, String interventionCode, Date birthDate, Date startPeriod, Date endPeriod) {
        log.debug("AdmissionDate: {}, claimId: {}", startPeriod, claim.getIdPart());
        log.debug("DischargeDate: {}, claimId: {}", endPeriod, claim.getIdPart());
        BigDecimal totalAmount = interventionValidatorHelper.getClaimsTotal(claim);
        Double totalAmountDouble = totalAmount.doubleValue();
        log.debug("Total amount: {}, claimId: {}", totalAmountDouble, claim.getIdPart());
        Claim.Use useType = claim.getUse();
        String use = useType.getDisplay();
        log.debug("USE is : {}", use);

        return buildClaimUsage(startPeriod, endPeriod, interventionCode, birthDate, totalAmountDouble, use);
    }

    public ClaimUsage buildClaimUsage(Date admissionDate, Date dischargeDate, String interventionCode, Date birthDate, Double totalAmount, String use) {
        log.debug("BirthDay: {}", birthDate);
        int age = FhirHelper.calculateAge(birthDate);
        log.debug("Age: {}", age);

        return ClaimUsage.builder()
                .admissionDate(FhirHelper.convertToLocalDate(admissionDate))
                .dischargeDate(FhirHelper.convertToLocalDate(dischargeDate))
                .interventionCode(interventionCode)
                .patientAge(age)
                .totalAmount(totalAmount)
                .use(use)
                .build();
    }


    private InterventionRules parseRulesSafely(BenefitsMaster benefitsMaster) {
        try {
            // Read JSON from DB as Map<String, Rule>
            Map<String, InterventionRules.Rule> parsedRules = objectMapper.readValue(
                    benefitsMaster.getGroupName(),
                    new TypeReference<Map<String, InterventionRules.Rule>>() {

                    }
            );
            InterventionRules wrapper = new InterventionRules();
            wrapper.setRules(parsedRules);
            return wrapper;
        } catch (JsonProcessingException ex) {
            log.error("Error while parsing rules {}: ", ex.getMessage(), ex);
            throw new RuleParsingException("Failed to parse rules for intervention: " + benefitsMaster.getInterventionCode(), ex);
        }

    }


    public boolean isPerDiemViolated(ClaimUsage claimUsage) {
        InterventionRules ruleSet = rulesCache.get(claimUsage.getInterventionCode());

        if (ruleSet == null || ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
            return false; // No rules configured → treat as valid
        }

        InterventionRules.Rule perDiemRule = ruleSet.getRules().get(PER_DIEM);

        if (perDiemRule == null || !HOUSEHOLD.equalsIgnoreCase(perDiemRule.getScope())) {
            return false; // No per diem rule or not household scoped → treat as valid
        }

        int used = claimUsage.getTotalUsedThisYear();
        int maxAllowed = perDiemRule.getMaxPerYear();

        if (used > maxAllowed) {
            log.warn("Per Diem rule violated for [{}]. Used: {} days, Max allowed: {} days",
                    claimUsage.getInterventionCode(), used, maxAllowed);
            return true; // violation occurred
        }

        log.debug("Per Diem rule OK for [{}]. Used: {} days, Max allowed: {} days",
                claimUsage.getInterventionCode(), used, maxAllowed);
        return false; // no violation
    }

    public boolean isOvernightStayValid(ClaimUsage usage) {
        var rule = rulesCache.get(usage.getInterventionCode()).getRules().get(INPATIENT);
        if (rule == null || !INDIVIDUAL.equalsIgnoreCase(rule.getScope())) {
            return false;
        }

        var admission = usage.getAdmissionDate();
        var discharge = usage.getDischargeDate();
        boolean valid = admission != null && discharge != null && discharge.isAfter(admission);

        if (!valid) {
            log.info("Overnight Stay rule violated for [{}]. Admission: {}, Discharge: {}",
                    usage.getInterventionCode(), admission, discharge);
        }
        return valid;
    }

    public void peritonealDialysisViolated(ClaimUsage usage) {
        var ruleSet = rulesCache.get(usage.getInterventionCode());
        if (ruleSet == null || ruleSet.getRules() == null) return;

        var rule = ruleSet.getRules().get(PERITONEAL_DIALYSIS);
        if (rule == null) return;

        boolean exceededYearly = rule.getMaxPerYear() != null &&
                usage.getTotalUsedThisYear() >= rule.getMaxPerYear();

        boolean tooSoon = rule.getMinDaysBetweenUses() != null &&
                usage.getLastUsageDate() != null &&
                usage.getCurrentClaimDate() != null &&
                usage.getLastUsageDate().plusDays(rule.getMinDaysBetweenUses())
                        .isAfter(usage.getCurrentClaimDate());

        if (exceededYearly) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, String.format(
                    "Exceeded yearly limit for Peritoneal Dialysis. Used: %d, Max: %d",
                    usage.getTotalUsedThisYear(), rule.getMaxPerYear()));
        }

        if (tooSoon) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, String.format(
                    "Minimum 30-day gap not maintained. Last used: %s, Current: %s",
                    usage.getLastUsageDate(), usage.getCurrentClaimDate()
            ));
        }
    }


    // SHA-16-007
    public void checkAcuteDialysisRule(ClaimUsage claimUsage) {
        var ruleSet = rulesCache.get(claimUsage.getInterventionCode());

        if (ruleSet == null || ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
            return; // No rule available, no validation needed
        }

        var rule = ruleSet.getRules().get(ACUTE_DIALYSIS);

        boolean isInPatient = claimUsage.getAdmissionDate() != null &&
                claimUsage.getDischargeDate() != null &&
                !claimUsage.getAdmissionDate().isEqual(claimUsage.getDischargeDate());

        if (!isInPatient) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, String.format(
                    "Acute Dialysis is only allowed for inpatient care. Admission: %s, Discharge: %s",
                    claimUsage.getAdmissionDate(), claimUsage.getDischargeDate()));
        }

        boolean exceeded = rule.getMaxPerAdmission() != null &&
                claimUsage.getTotalUsedThisAdmission() >= rule.getMaxPerAdmission();

        if (exceeded) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, String.format(
                    "Exceeded Acute Dialysis limit per admission. Used: %d, Max: %d",
                    claimUsage.getTotalUsedThisAdmission(), rule.getMaxPerAdmission()));
        }
    }

    /**
     * Calculates the number of days between admission and discharge for a given claim.
     * <p>
     * If both admission and discharge dates are present and discharge is after admission, it returns the actual
     * number of stay days. Otherwise, it defaults to 1 day, treating the claim as an outpatient service.
     *
     * @param claim The {@link ClaimUsage} object containing admission and discharge dates.
     * @return The number of usage days. Returns actual stay duration for inpatients or 1 day for outpatients.
     */
    private int calculateUsageDays(ClaimUsage claim) {
        LocalDate start = claim.getAdmissionDate();
        LocalDate end = claim.getDischargeDate();

        if (start != null && end != null && end.isAfter(start)) {
            return (int) ChronoUnit.DAYS.between(start, end);
        }
        return MINIMUM_LOS; // outpatient: count as 1 service day
    }

    /**
     * Finds the first surgical rule from a given rule set for any of the defined surgical categories.
     * This method looks up the surgical rule in the provided rule set for categories such as specialized, major,
     * or minor surgeries, and returns the first non-null rule found.
     *
     * @param ruleSet The InterventionRules object containing the set of rules for various interventions.
     * @return An Optional containing the first non-null surgical rule found, or an empty Optional if no rule is found.
     */
    private Optional<InterventionRules.Rule> findSurgicalRule(InterventionRules ruleSet) {
        return SURGICAL_CATEGORIES.stream()
                .map(ruleSet.getRules()::get)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Validates the usage of acute dialysis for a given ClaimUsage within an admission period.
     * This method counts the number of acute dialysis usages for the specified intervention code
     * during the admission period and checks if it adheres to the acute dialysis rules.
     *
     * @param usages The ClaimUsage object containing the intervention details and dates.
     * @throws RuleViolationException if the acute dialysis usage does not comply with the defined rules.
     */
    public void validateAcuteDialysisUsage(ClaimUsage usages) {
        checkAcuteDialysisRule(usages);
    }

    /**
     * Validates and calculates the rehab billing for a given ClaimUsage based on the intervention rule.
     * The method checks the following:
     * - Validates the scope is individual.
     * - Validates the patient's age for the intervention.
     * - Validates the admission and discharge dates.
     * - Calculates the number of stay days.
     * - Retrieves the valid billing policy for the intervention.
     * - Calculates the billing amount based on the stay duration.
     *
     * @param usage The ClaimUsage object containing details of the intervention and patient.
     * @throws RuleViolationException if any validation fails or if billing information is invalid.
     */
    public void validateAndCalculateRehabBilling(ClaimUsage usage, boolean pmfCoverage) {
        var rule = getRehabRule(usage);
        if (Objects.nonNull(rule)) {
            validateScopeIsIndividual(rule.getScope(), usage.getInterventionCode());
            validatePatientAge(rule, usage);
            validateAdmissionAndDischargeDates(usage);

            long stayDays = calculateStayDays(usage);
            var billing = getValidBillingPolicy(rule);
            Double totalAmount = usage.getTotalAmount();
            long amount = totalAmount.longValue();
            calculateBilling(billing, stayDays, amount, pmfCoverage);
        }
    }

    /**
     * Returns the rehab SHIF limit for the given intervention code and LOS, based on the cached billing bands.
     * Returns empty if no RehabRule or no matching band exists.
     */
    public Optional<BigDecimal> getRehabLimit(String interventionCode, long stayDays) {
        var ruleSet = rulesCache.get(interventionCode);
        if (ruleSet == null || ruleSet.getRules() == null) return Optional.empty();
        var rule = ruleSet.getRules().get(REHAB);
        if (rule == null || rule.getBillingPolicy() == null || rule.getBillingPolicy().getBands() == null) return Optional.empty();
        return rule.getBillingPolicy().getBands().stream()
                .filter(band -> isBandMatch(band, stayDays))
                .findFirst()
                .map(band -> {
                    if (band.getRatePerDay() != null) return BigDecimal.valueOf(stayDays * band.getRatePerDay());
                    if (band.getFlatAmount() != null) return BigDecimal.valueOf(band.getFlatAmount());
                    return null;
                });
    }

    /**
     * Retrieves the Rehab rule for the given ClaimUsage based on the intervention code.
     *
     * @param usage The ClaimUsage object containing details of the intervention.
     * @return The Rehab rule associated with the intervention code, or null if not found.
     */
    private InterventionRules.Rule getRehabRule(ClaimUsage usage) {
        return Optional.ofNullable(rulesCache.get(usage.getInterventionCode()))
                .map(InterventionRules::getRules)
                .map(rules -> rules.get(REHAB))
                .orElse(null);
    }


    /**
     * Retrieves the EyeCare rule for the given ClaimUsage based on the intervention code.
     *
     * @param usage The ClaimUsage object containing details of the intervention.
     * @return The EyeCare rule associated with the intervention code, or null if not found.
     */
    private InterventionRules.Rule getEyeCareRule(ClaimUsage usage) {
        return Optional.ofNullable(rulesCache.get(usage.getInterventionCode()))
                .map(InterventionRules::getRules)
                .map(rules -> rules.get(EYE_CARE))
                .orElse(null);
    }

    /**
     * Validates that the scope is set to "individual" for the given intervention.
     *
     * @param scope            The scope to validate, typically related to the rehabilitation rule.
     * @param interventionCode The code for the intervention being validated.
     * @throws RuleViolationException if the scope is not "individual".
     */
    private void validateScopeIsIndividual(String scope, String interventionCode) {
        if (!INDIVIDUAL.equalsIgnoreCase(scope)) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "Rehab rule scope is not 'individual' for intervention: " + interventionCode);
        }
    }

    /**
     * Validates that the patient's age meets the minimum age requirement for the intervention rule.
     *
     * @param rule  The {@link InterventionRules.Rule} containing the minimum age constraint.
     * @param usage The {@link ClaimUsage} object containing the patient's age.
     * @throws RuleViolationException if the patient's age is below the minimum required age for rehabilitation.
     */
    private void validatePatientAge(InterventionRules.Rule rule, ClaimUsage usage) {
        if (rule.getMinAge() != null && (usage.getPatientAge() == null || usage.getPatientAge() < rule.getMinAge())) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "Patient below minimum age (" + rule.getMinAge() + ") for rehabilitation. Patient age: " + usage.getPatientAge());
        }
    }

    /**
     * Validates that both admission and discharge dates are provided in the given {@link ClaimUsage}.
     *
     * @param usage The {@link ClaimUsage} object containing the admission and discharge dates.
     * @throws RuleViolationException if either the admission date or discharge date is null.
     */
    private void validateAdmissionAndDischargeDates(ClaimUsage usage) {
        if (usage.getAdmissionDate() == null || usage.getDischargeDate() == null) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "Admission and discharge dates must be provided for rehab billing calculation.");
        }
    }

    /**
     * Calculates the number of stay days based on the admission and discharge dates in the given {@link ClaimUsage}.
     *
     * @param usage The {@link ClaimUsage} object containing the admission and discharge dates.
     * @return The number of days between the admission and discharge dates.
     * @throws RuleViolationException if the calculated stay duration is less than or equal to 0 days.
     */
    private long calculateStayDays(ClaimUsage usage) {
        long days = ChronoUnit.DAYS.between(usage.getAdmissionDate(), usage.getDischargeDate());
        if (days <= 0) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "Invalid stay duration. Stay must be at least 1 day.");
        }
        return days;
    }

    /**
     * Retrieves the valid billing policy from the given rule. Ensures that the billing policy exists and is of type "DURATION".
     *
     * @param rule The {@link InterventionRules.Rule} containing the billing policy.
     * @return The {@link InterventionRules.BillingPolicy} object if valid.
     * @throws RuleViolationException if the billing policy is missing or its type is not "DURATION".
     */
    private InterventionRules.BillingPolicy getValidBillingPolicy(InterventionRules.Rule rule) {
        var billing = rule.getBillingPolicy();
        if (billing == null || !DURATION.equalsIgnoreCase(billing.getType())) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, "Invalid or missing billing policy for rehab service.");
        }
        return billing;
    }

    /**
     * Validates that the calculated rehabilitation billing amount matches the expected total
     * based on the applicable billing policy rules.
     * <p>
     * The calculation uses the first matching billing band where:
     * <ul>
     *   <li>If {@code ratePerDay} is specified: {@code amount = stayDays * ratePerDay}</li>
     *   <li>If {@code flatAmount} is specified: {@code amount = flatAmount}</li>
     * </ul>
     *
     * @param billing the billing policy containing rate bands, must not be {@code null}
     * @param stayDays the duration of the rehabilitation stay in days, must be ≥ 1
     * @param totalAmount the expected total amount to validate against
     * @throws NullPointerException if {@code billing} or its bands are {@code null}
     * @throws RuleViolationException with specific messages when:
     *         <ul>
     *           <li>No matching billing band exists for the given stay duration</li>
     *           <li>Matched band has neither {@code ratePerDay} nor {@code flatAmount}</li>
     *           <li>Calculated amount doesn't match {@code totalAmount}</li>
     *         </ul>
     */
    private void calculateBilling(InterventionRules.BillingPolicy billing, long stayDays, long totalAmount, boolean pmfCoverage) {
        Objects.requireNonNull(billing, "Billing policy not provided");
        Objects.requireNonNull(billing.getBands(), "Billing bands not provided");

        var matchedBand = billing.getBands().stream()
                .filter(band -> isBandMatch(band, stayDays))
                .findFirst()
                .orElseThrow(() -> new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                        "No billing band found for " + stayDays + " days"));

        long calculatedAmount = switch (matchedBand) {
            case InterventionRules.Band b when b.getRatePerDay() != null ->
                    stayDays * b.getRatePerDay();
            case InterventionRules.Band b when b.getFlatAmount() != null ->
                    b.getFlatAmount();
            default -> throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                    "REHAB Billing band missing rate/flat amount");
        };

        if (calculatedAmount < totalAmount) {
            if (pmfCoverage) {
                log.info("Rehab billing: claim amount {} exceeds LOS-based SHIF limit {}. Excess will be routed to PMF.",
                        totalAmount, calculatedAmount);
            } else {
                throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                        "SHA-10-005 rule is not applicable as the LOS amount is "
                                + calculatedAmount + ", while the claim amount is " + totalAmount);
            }
        }
    }

    /**
     * Checks if the given number of stay days matches the defined range in the specified intervention band.
     *
     * @param band     The {@link InterventionRules.Band} object containing the minimum and maximum days.
     * @param stayDays The number of stay days to be checked.
     * @return {@code true} if the stay days are within the defined range (or if no range is specified), {@code false} otherwise.
     */
    private boolean isBandMatch(InterventionRules.Band band, long stayDays) {
        return (band.getMinDays() == null || stayDays >= band.getMinDays()) &&
                (band.getMaxDays() == null || stayDays <= band.getMaxDays());
    }


    /**
     * Builds a {@link ClaimUsage} object for Peritoneal Dialysis interventions.
     * Retrieves the total number of days used for the intervention within the current year
     * and the date of the last usage.
     *
     * @param interventionCode The code for the intervention.
     * @param householdNumber  The household number associated with the claim.
     * @param currentDate      The current date for the claim.
     * @param use
     * @return A {@link ClaimUsage} object containing the intervention details, total used days,
     * and the last usage date for the specified intervention and household.
     */
    public ClaimUsage buildPeritonealClaimUsage(String interventionCode, String householdNumber, LocalDate currentDate, String use , LocalDate policyStartDate, LocalDate policyEndDate) {

        int totalUsed = insuranceClaimsRepository.countPeritonealClaim(householdNumber, policyStartDate , policyEndDate, new ArrayList<>(APPROVED_STATUS), use);
        LocalDate lastUsage = insuranceClaimsRepository.findLastUsedDateForIntervention(interventionCode, householdNumber, policyStartDate , policyEndDate, currentDate,
                new ArrayList<>(APPROVED_STATUS), use);
        log.info("Total used count: {}, and last usages for PeritonealDialysis {}, Household : {}", totalUsed, lastUsage, householdNumber);

        return ClaimUsage.builder()
                .interventionCode(interventionCode)
                .currentClaimDate(currentDate)
                .totalUsedThisYear(totalUsed)
                .lastUsageDate(lastUsage)
                .build();
    }

    /**
     * Validates the usage of an EyeCare intervention based on the rules for a specified household.
     * This method checks if the number of EyeCare interventions used within a specified policy period
     * exceeds the allowed maximum for the household. If the usage count exceeds the limit, a
     * {@link RuleViolationException} is thrown.
     *
     * <p>The validation considers the intervention code, policy period, and the maximum allowed
     * interventions within that period. If no EyeCare rule is found, the method simply returns without
     * performing any validation.</p>
     *
     * @param householdNumber the identifier of the household for which the EyeCare intervention usage is being validated
     * @param usage           the {@link ClaimUsage} object containing details about the intervention being validated
     * @param serviceDate     the date of the service for which the intervention usage is being validated
     * @throws RuleViolationException if the number of EyeCare interventions exceeds the allowed maximum within the policy period
     */
    private void validateEyeCareUsage(String householdNumber, ClaimUsage usage, LocalDate serviceDate) {
        log.info("EyeCare method executing for this SHA-Code: {}, Household : {}", usage.getInterventionCode(), householdNumber);
        InterventionRules.Rule eyeCareRule = getEyeCareRule(usage);
        if (eyeCareRule == null) {
            log.warn("No EyeCare rule found for intervention {}, Household : {}", usage.getInterventionCode(), householdNumber);
            return; // No rule, no validation needed
        }

        int maxPerPolicyPeriod = eyeCareRule.getMaxPerPolicyPeriod(); // e.g., 1
        int policyPeriodYears = eyeCareRule.getPolicyPeriodYears();   // e.g., 2
        String interventionCode = usage.getInterventionCode();

        LocalDate startDate = serviceDate.minusYears(policyPeriodYears);

        int usedCount = insuranceClaimsRepository.countUsedInterventionsInRollingYear(
                householdNumber,
                interventionCode,
                startDate,
                serviceDate,
                usage.getUse(),
                new ArrayList<>(APPROVED_STATUS)
        );
        log.debug("Used count for eye care {}", usedCount);

        if (usedCount >= maxPerPolicyPeriod) {
            throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                    String.format("EyeCare intervention %s usage exceeded: allowed once every %d years per household.",
                            interventionCode,
                            policyPeriodYears)
            );
        }

        log.debug("EyeCare validation passed for household {} (Used: {} / Allowed: {})", householdNumber, usedCount, maxPerPolicyPeriod);
    }

    public static String toOrdinal(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) {
            return number + "th";
        }
        return switch (number % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }


    /**
     * Validates whether a duplicate claim or pre-authorization exists for a given household, intervention,
     * service date, and claim type. Allows or blocks submission based on business rules and claim status.
     *
     * @param crId   the individual identifier
     * @param interventionCode  the intervention (service) code
     * @param serviceDate       the date the service was provided
     * @param claimType         the type of claim ("CLAIM" or "Preauthorization")
     * @return an Optional message indicating successful validation; empty if no duplicate found
     * @throws RuleViolationException if a duplicate submission is not allowed based on existing claim status
     */

    public Optional<String> validateDuplicateClaimOrPreAuth(String crId, String interventionCode,
                                                            LocalDate serviceDate, String claimType) {
        return validateDuplicateClaimOrPreAuth(crId, interventionCode, serviceDate, claimType, null);
    }

    public Optional<String> validateDuplicateClaimOrPreAuth(String crId, String interventionCode,
                                                            LocalDate serviceDate, String claimType, UUID excludeClaimId) {
        log.info("Validating duplicate claim for individual [{}], interventionCode [{}], serviceDate [{}]",
                crId, interventionCode, serviceDate);

        var existingClaims = (excludeClaimId != null)
                ? insuranceClaimsRepository.findLatestClaimForServiceDateExcluding(crId, interventionCode, serviceDate, claimType, excludeClaimId)
                : insuranceClaimsRepository.findLatestClaimForServiceDate(crId, interventionCode, serviceDate, claimType);

        if (existingClaims.isEmpty()) {
            log.debug("No existing claim found for the given individual, code, and date. Validation passed.");
            return Optional.of("No duplicate found. Validation passed.");
        }

        var existingClaim = existingClaims.getFirst(); // Latest due to ORDER BY
        var existingUse = existingClaim.getUse(); // "CLAIM" or "PREAUTHORIZATION"
        var status = existingClaim.getStatus().name(); // Enum name

        log.debug("Found existing claim with ID [{}], use [{}], status [{}]",
                existingClaim.getClaimId(), existingUse, status);

        var isClaim = Constants.Use.CLAIM.equals(claimType);
        var isPreAuth = Constants.Use.PREAUTHORIZATION.equals(claimType);

        return switch (status) {
            case String s when isClaim && PRE_AUTH_APPROVED_STATUS.contains(s)
                    && Constants.Use.PREAUTHORIZATION.equals(existingUse) -> {
                log.debug("Claim based on approved pre-auth. Allowing submission.");
                yield Optional.of("Claim allowed based on approved pre-auth.");
            }

            case String s when isPreAuth && PRE_AUTH_DECLINED_STATUS.contains(s) -> {
                log.debug("Pre-auth was declined. Allowing resubmission.");
                yield Optional.of("Pre-auth declined. Resubmission allowed.");
            }

            case String s when isPreAuth && PRE_AUTH_APPROVED_STATUS.contains(s) -> {
                log.warn("Pre-auth already approved. Resubmission not allowed.");
                throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                        "A preauthorization already exists for the same service: " + interventionCode +" on the date : " + serviceDate +". Claim ID: "+ existingClaim.getClaimId());
            }

            case String s when isClaim && CLAIM_DECLINED_STATUS.contains(s) -> {
                log.debug("Claim was declined. Allowing resubmission.");
                yield Optional.of("Claim declined. Resubmission allowed.");
            }

            case String s when isClaim && APPROVED_STATUS.contains(s) -> {
                log.warn("Claim already approved. Resubmission not allowed.");
                throw new RuleViolationException(BUSINESS_RULE_EXCEPTION,
                        "Approved claims cannot be resubmitted for this claimId: " + existingClaim.getClaimId());
            }

            default -> {
                var errorMessage = "Duplicate claim/pre-auth exists for service date %s with status %s claimId=%s"
                        .formatted(serviceDate, status, existingClaim.getClaimId());
                log.warn("Duplicate validation failed: {}", errorMessage);
                throw new RuleViolationException(BUSINESS_RULE_EXCEPTION, errorMessage);
            }
        };
    }


    /**
     * Validates that the total number of minor surgeries performed at Level 3 facilities
     * for the given household does not exceed the allowed limit.
     * <p>
     * This check enforces a cap of {@code ALLOWED_MINOR_SURGERIES} minor surgeries
     * per household specifically at Level 3 hospitals (e.g., Level 3, Level 3A).
     * It counts all previous approved claims for the given intervention code and adds 1
     * for the current request.
     * <p>
     * If the limit is exceeded, a {@link RuleViolationException} is thrown.
     *
     * @param householdNumber  the household number associated with the patient
     * @throws RuleViolationException if the allowed minor surgery limit at Level 3 is exceeded
     */
    private void validateMinorSurgeryLevel3(String householdNumber, String use) {


        List<String> minorSurgeryCodes = benefitsMasterRepository.findInterventionCodesByPackageCodeAndComplexity("SHA-19", "Minor");

        if (minorSurgeryCodes.isEmpty()) {
            log.warn("No minor surgery codes found for package SHA-19. Skipping Level 3 validation.");
            return;
        }

        int totalCountMinorSurgeries;
        if (use.equals(Constants.Use.PREAUTHORIZATION)) {
            totalCountMinorSurgeries = Math.toIntExact(preAuthLimits.countValidOrConsumedClaimsAtLevel3(
                    householdNumber,
                    minorSurgeryCodes,
                    use,
                    new ArrayList<>(APPROVED_STATUS),
                    null));
        } else {
            totalCountMinorSurgeries = insuranceClaimsRepository.countMinorSurgeryUsedInterventionsAtLevel3(
                    householdNumber,
                    minorSurgeryCodes,
                    use,
                    new ArrayList<>(APPROVED_STATUS),
                   null,
                    null
            );
        }
        // Add 1 for the current minor surgery at Level 3
        int totalIncludingCurrent = totalCountMinorSurgeries + MINOR_SURGERY_COUNT;

        log.info("Total minor surgeries at Level 3 including current: {}, Household : {}", totalIncludingCurrent, householdNumber);
        if (totalIncludingCurrent > ALLOWED_MINOR_SURGERIES) {
            throw new RuleViolationException(
                    BUSINESS_RULE_EXCEPTION,
                    String.format(
                            "Level 3 facilities may perform up to %d minor surgeries per household. Current including this: %d.",
                            ALLOWED_MINOR_SURGERIES, totalIncludingCurrent)
            );
        }
    }

    private record ServiceWindow(LocalDate startDate, LocalDate endDate) {
        boolean contains(LocalDate date) {
                return !date.isBefore(startDate) && !date.isAfter(endDate);
            }
    }

    public boolean checkNullOrEmptyHouseholdNumber(String householdNumber) {
        return Objects.nonNull(householdNumber) && !householdNumber.isBlank();
    }

    public void logInfo(String type) {
        log.info("Household not found in the bundle. for rule type : {}", type);
    }

    public LocalDate getLastUsageDate(String crId, String interventionCode, List<String> approvedStatuses, String use) {
        return insuranceClaimsRepository.findLatestServicedDate(interventionCode, crId, approvedStatuses, use);
    }

    public LocalDate getLastUsageDate(String crId, List<String> interventionCodes, List<String> approvedStatuses, List<String> use) {
        return insuranceClaimsRepository.findLatestServicedDate(interventionCodes, crId, approvedStatuses, use);
    }
}
